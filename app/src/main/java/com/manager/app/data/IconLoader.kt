package com.manager.app.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.manager.app.design.squirclePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Real app icons, rendered into the product's own geometry.
 *
 * Android hands adaptive icons over as separate background and foreground layers and leaves the
 * mask to the caller. Rather than accept whatever shape the launcher happens to use, Manager
 * re-masks them with the same squircle the rest of the UI is cut from — so a screen full of
 * third-party icons still reads as one designed surface. Legacy icons are drawn untouched,
 * because forcing a tile behind them looks worse than leaving them alone.
 */
class IconLoader(context: Context) {

    private val pm: PackageManager = context.packageManager

    // Decoding icons is resource-heavy; a small gate keeps a fast fling from saturating IO.
    private val gate = Semaphore(4)

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    fun peek(packageName: String): ImageBitmap? = cache.get(packageName)

    suspend fun load(packageName: String): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return gate.withPermit {
            cache.get(packageName)?.let { return@withPermit it }
            val bitmap = withContext(Dispatchers.IO) { render(packageName) }
            bitmap?.also { cache.put(packageName, it) }
        }
    }

    fun evict(packageName: String) {
        cache.remove(packageName)
    }

    private fun render(packageName: String): ImageBitmap? {
        val drawable: Drawable = runCatching { pm.getApplicationIcon(packageName) }.getOrNull() ?: return null
        return runCatching {
            when {
                drawable is AdaptiveIconDrawable -> renderAdaptive(drawable)
                else -> renderLegacy(drawable)
            }
        }.getOrNull()
    }

    private fun renderAdaptive(drawable: AdaptiveIconDrawable): ImageBitmap {
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)

        // Layers are authored on a 108 grid with the middle 72 visible: scale by 1.5 and centre.
        val overscan = ((SIZE * ADAPTIVE_SCALE) - SIZE) / 2f
        val bounds = Rect(
            (-overscan).toInt(),
            (-overscan).toInt(),
            (SIZE + overscan).toInt(),
            (SIZE + overscan).toInt(),
        )

        val layer = canvas.saveLayer(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), null)
        drawable.background?.let { it.bounds = bounds; it.draw(canvas) }
        drawable.foreground?.let { it.bounds = bounds; it.draw(canvas) }

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawPath(maskPath, maskPaint)
        canvas.restoreToCount(layer)
        return bitmap.asImageBitmap()
    }

    private fun renderLegacy(drawable: Drawable): ImageBitmap {
        (drawable as? BitmapDrawable)?.bitmap?.let { source ->
            if (source.width in 1..SIZE && source.height in 1..SIZE) {
                return source.copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
            }
        }
        val bitmap = createBitmap(SIZE, SIZE)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, SIZE, SIZE)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }

    private val maskPath by lazy {
        val radius = SIZE * 0.295f
        squirclePath(Size(SIZE.toFloat(), SIZE.toFloat()), radius, radius, radius, radius, 0.85f)
            .asAndroidPath()
    }

    private companion object {
        const val SIZE = 160
        const val ADAPTIVE_SCALE = 1.5f
        const val CACHE_BYTES = 20 * 1024 * 1024
    }
}
