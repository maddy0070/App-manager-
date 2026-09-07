package com.manager.app.data

import android.app.usage.StorageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The single source of truth for what is installed.
 *
 * Scanning happens in two passes on purpose. The first pass reads only what the PackageManager
 * hands over cheaply, so the list can paint almost immediately. The second pass fills in
 * StorageStats and OBB sizes and streams updates in, so the user never watches a spinner while a
 * few hundred packages are measured.
 */
class PackageRepository(
    private val context: Context,
    private val permissions: PermissionMonitor,
    private val scope: CoroutineScope,
) {
    private val pm: PackageManager = context.packageManager

    private val _state = MutableStateFlow(InventoryState())
    val state: StateFlow<InventoryState> = _state.asStateFlow()

    private val scanMutex = Mutex()
    private var detailJob: Job? = null
    private var packageWatcher: BroadcastReceiver? = null

    /**
     * Rescans everything. Cheap metadata is published first; storage detail streams in after.
     * Safe to call repeatedly — a second call cancels the in-flight detail pass.
     */
    fun refresh(force: Boolean = false) {
        scope.launch {
            if (scanMutex.isLocked && !force) return@launch
            scanMutex.withLock {
                detailJob?.cancel()
                _state.value = _state.value.copy(loading = true, error = null, detailProgress = 0f)
                val base = runCatching { scanBaseInventory() }
                    .onFailure { Log.w(TAG, "base scan failed", it) }
                    .getOrNull()

                if (base == null) {
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "Android would not hand over the package list. Pull down to try again.",
                    )
                    return@withLock
                }

                _state.value = InventoryState(
                    apps = base,
                    loading = false,
                    detailProgress = 0f,
                    scannedAt = System.currentTimeMillis(),
                )
            }
            detailJob = scope.launch { enrichWithStorage() }
        }
    }

    /** Drops a package from the snapshot without a full rescan — used after an uninstall. */
    fun forget(packageName: String) {
        _state.value = _state.value.copy(apps = _state.value.apps.filterNot { it.packageName == packageName })
    }

    fun observePackageChanges() {
        if (packageWatcher != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                // Replacements arrive as remove+add; the removal half carries EXTRA_REPLACING.
                if (intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true &&
                    intent.action == Intent.ACTION_PACKAGE_REMOVED
                ) return
                refresh(force = true)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }.onSuccess { packageWatcher = receiver }
    }

    fun stopObserving() {
        packageWatcher?.let { runCatching { context.unregisterReceiver(it) } }
        packageWatcher = null
    }

    private suspend fun scanBaseInventory(): List<AppEntry> = withContext(Dispatchers.IO) {
        // Branch on the version rather than catching the failure: relying on NoSuchMethodError as
        // control flow would run the whole enumeration twice on every scan below API 33.
        // A failure here is left to throw: the caller turns it into the honest "Android would not
        // hand over the package list" state, where swallowing it would read as an empty phone.
        val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA)
        }

        // Label loading dominates this pass, so fan it out across cores.
        coroutineScope {
            packages.chunked(CHUNK).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.mapNotNull { info ->
                        ensureActive()
                        runCatching { info.toEntry() }.getOrNull()
                    }
                }
            }.awaitAll().flatten()
        }.sortedBy { it.label.lowercase() }
    }

    private fun PackageInfo.toEntry(): AppEntry? {
        val app = applicationInfo ?: return null
        val label = runCatching { app.loadLabel(pm).toString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: packageName

        val splits = app.splitSourceDirs?.filterNotNull().orEmpty()
        val apkBytes = buildList {
            app.sourceDir?.let { add(it) }
            addAll(splits)
        }.sumOf { path -> runCatching { File(path).length() }.getOrDefault(0L) }

        val system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0
        val updatedSystem = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0

        return AppEntry(
            packageName = packageName,
            label = label,
            versionName = versionName,
            versionCode = longVersionCode,
            installedAt = firstInstallTime,
            updatedAt = lastUpdateTime,
            isSystem = system || updatedSystem,
            isUpdatedSystemApp = updatedSystem,
            isEnabled = app.enabled,
            isDebuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            minSdk = app.minSdkVersion,
            targetSdk = app.targetSdkVersion,
            sourceDir = app.sourceDir,
            splitSourceDirs = splits,
            installerPackage = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(packageName)
                }
            }.getOrNull(),
            hasLaunchIntent = runCatching { pm.getLaunchIntentForPackage(packageName) != null }.getOrDefault(false),
            apkBytes = apkBytes,
        )
    }

    /**
     * Second pass: real storage numbers. Requires usage access, so it simply does not run without
     * it — the UI already knows to present APK size as the honest fallback.
     */
    private suspend fun enrichWithStorage() = withContext(Dispatchers.IO) {
        val snapshot = _state.value.apps
        if (snapshot.isEmpty()) return@withContext

        // Android/obb is off-limits to ordinary apps on modern releases. Probe once: a null root
        // means the sizes are genuinely unknowable, which the UI states rather than guessing at.
        val obbRoot = runCatching {
            File(Environment.getExternalStorageDirectory(), "Android/obb")
                .takeIf { it.canRead() && it.list() != null }
        }.getOrNull()
        val obbReadable = obbRoot != null

        val statsManager = if (permissions.hasUsageAccess()) {
            runCatching { context.getSystemService(StorageStatsManager::class.java) }.getOrNull()
        } else {
            null
        }

        if (statsManager == null && !obbReadable) {
            _state.value = _state.value.copy(detailProgress = 1f)
            return@withContext
        }

        val user = Process.myUserHandle()
        val updated = snapshot.toMutableList()
        var emittedAt = 0

        snapshot.forEachIndexed { index, entry ->
            ensureActive()
            var next = entry
            if (statsManager != null) {
                val uuid = runCatching {
                    pm.getApplicationInfo(entry.packageName, 0).storageUuid
                }.getOrNull()
                if (uuid != null) {
                    runCatching { statsManager.queryStatsForPackage(uuid, entry.packageName, user) }
                        .getOrNull()
                        ?.let { stats ->
                            next = next.copy(
                                storage = StorageBreakdown(
                                    appBytes = stats.appBytes,
                                    dataBytes = (stats.dataBytes - stats.cacheBytes).coerceAtLeast(0L),
                                    cacheBytes = stats.cacheBytes,
                                ),
                            )
                        }
                }
            }
            if (obbRoot != null) {
                next = next.copy(obbBytes = directorySize(File(obbRoot, entry.packageName)))
            }
            updated[index] = next

            // Publish in batches: enough to feel live, rare enough not to thrash recomposition.
            if (index - emittedAt >= EMIT_EVERY || index == snapshot.lastIndex) {
                emittedAt = index
                val progress = (index + 1f) / snapshot.size
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(apps = updated.toList(), detailProgress = progress)
                }
            }
        }
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    private companion object {
        const val TAG = "PackageRepository"
        const val CHUNK = 40
        const val EMIT_EVERY = 24
    }
}
