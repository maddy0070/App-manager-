package com.manager.app.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Copies the APK files Android actually exposes for a package into shared Downloads.
 *
 * Split installs are common on a modern Pixel — most Play-installed apps ship a base plus
 * configuration splits. Pretending they are one file would produce an APK that cannot be
 * installed, so those are written as a single `.apks` archive containing every part, which is
 * the format split installers expect.
 */
class ApkExtractor(private val context: Context) {

    sealed interface Outcome {
        data class Success(
            val packageName: String,
            val label: String,
            val uri: Uri,
            val displayName: String,
            val bytes: Long,
            val partCount: Int,
        ) : Outcome {
            val isArchive: Boolean get() = partCount > 1
        }

        data class Failure(
            val packageName: String,
            val label: String,
            val reason: String,
        ) : Outcome
    }

    /**
     * @param onBytes called with bytes written so far and the total expected, on a worker thread.
     */
    suspend fun extract(
        entry: AppEntry,
        onBytes: (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Outcome = withContext(Dispatchers.IO) {
        val parts = buildList {
            entry.sourceDir?.let { add(File(it)) }
            entry.splitSourceDirs.forEach { add(File(it)) }
        }.filter { it.exists() }

        if (parts.isEmpty()) {
            return@withContext Outcome.Failure(
                entry.packageName,
                entry.label,
                "Android does not expose this package's APK files to other apps. Protected system packages are usually the reason.",
            )
        }
        if (parts.none { it.canRead() }) {
            return@withContext Outcome.Failure(
                entry.packageName,
                entry.label,
                "The APK exists but is not readable by Manager. Android restricts this for some system packages.",
            )
        }

        val readable = parts.filter { it.canRead() }
        val total = readable.sumOf { it.length() }
        val archive = readable.size > 1
        val name = fileName(entry, archive)

        try {
            val target = createTarget(name, archive)
                ?: return@withContext Outcome.Failure(
                    entry.packageName,
                    entry.label,
                    "Could not create a file in Downloads. Free storage may be low.",
                )

            var written = 0L
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                if (archive) {
                    ZipOutputStream(out.buffered(BUFFER)).use { zip ->
                        // Store, don't deflate: an APK is already compressed, and copying at
                        // disk speed keeps bulk extraction responsive.
                        zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
                        readable.forEach { part ->
                            coroutineContext.ensureActive()
                            val completedBefore = written
                            zip.putNextEntry(ZipEntry(partName(part, entry)))
                            part.inputStream().buffered(BUFFER).use { input ->
                                written = completedBefore + copy(input, zip) { soFar ->
                                    onBytes(completedBefore + soFar, total)
                                }
                            }
                            zip.closeEntry()
                        }
                    }
                } else {
                    out.buffered(BUFFER).use { sink ->
                        readable.first().inputStream().buffered(BUFFER).use { input ->
                            written = copy(input, sink) { onBytes(it, total) }
                        }
                    }
                }
            } ?: return@withContext Outcome.Failure(
                entry.packageName,
                entry.label,
                "Downloads could not be opened for writing.",
            )

            target.publish()

            Outcome.Success(
                packageName = entry.packageName,
                label = entry.label,
                uri = target.uri,
                displayName = target.displayName,
                bytes = if (archive) target.size() else written,
                partCount = readable.size,
            )
        } catch (io: FileNotFoundException) {
            Outcome.Failure(entry.packageName, entry.label, "The APK moved or was removed while Manager was copying it.")
        } catch (io: IOException) {
            Outcome.Failure(entry.packageName, entry.label, io.message?.takeIf { it.isNotBlank() }?.let { "Copy failed: $it" } ?: "The copy did not finish. Storage may be full.")
        } catch (se: SecurityException) {
            Outcome.Failure(entry.packageName, entry.label, "Android blocked access to this package's files.")
        }
    }

    private inline fun copy(input: java.io.InputStream, output: OutputStream, onProgress: (Long) -> Unit): Long {
        val buffer = ByteArray(BUFFER)
        var total = 0L
        var lastReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
            if (total - lastReport > REPORT_EVERY) {
                lastReport = total
                onProgress(total)
            }
        }
        onProgress(total)
        return total
    }

    /** `base.apk` plus each split under its own name, so the archive is self-describing. */
    private fun partName(file: File, entry: AppEntry): String {
        val raw = file.name
        return if (raw == "base.apk" || file.absolutePath == entry.sourceDir) "base.apk" else raw
    }

    /** `Signal_7.42.1.apk` — readable at a glance, sortable, no timestamps in the way. */
    private fun fileName(entry: AppEntry, archive: Boolean): String {
        val label = entry.label
            .replace(Regex("[\\\\/:*?\"<>|]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(' ', '_')
            .take(48)
            .ifBlank { entry.packageName }
        val version = (entry.versionName ?: entry.versionCode.toString())
            .replace(Regex("[^A-Za-z0-9._-]"), "")
            .take(24)
            .ifBlank { entry.versionCode.toString() }
        return "${label}_$version.${if (archive) "apks" else "apk"}"
    }

    private class Target(
        val uri: Uri,
        val displayName: String,
        val publish: () -> Unit,
        val size: () -> Long,
    )

    private fun createTarget(name: String, archive: Boolean): Target? {
        val mime = if (archive) "application/octet-stream" else "application/vnd.android.package-archive"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull()
        if (uri != null) {
            return Target(
                uri = uri,
                displayName = name,
                publish = {
                    runCatching {
                        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    }
                },
                size = {
                    runCatching {
                        resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                    }.getOrNull() ?: 0L
                },
            )
        }

        // MediaStore refused — fall back to app-scoped storage so extraction still succeeds.
        val dir = File(context.getExternalFilesDir(null), "extracted").apply { mkdirs() }
        val file = uniqueFile(dir, name)
        return runCatching {
            Target(
                uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file),
                displayName = file.name,
                publish = {},
                size = { file.length() },
            )
        }.getOrNull()
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val stem = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (candidate.exists() && i < 999) {
            candidate = File(dir, "$stem ($i)${if (ext.isEmpty()) "" else ".$ext"}")
            i++
        }
        return candidate
    }

    fun shareIntent(outcome: Outcome.Success): Intent = Intent(Intent.ACTION_SEND).apply {
        type = if (outcome.isArchive) "application/octet-stream" else "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, outcome.uri)
        putExtra(Intent.EXTRA_TITLE, outcome.displayName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun viewIntent(outcome: Outcome.Success): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(outcome.uri, "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private companion object {
        const val BUFFER = 256 * 1024
        const val REPORT_EVERY = 512 * 1024L
        const val FOLDER = "Manager"
    }
}
