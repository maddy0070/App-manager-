package com.manager.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Continuous-curvature ("squircle") geometry.
 *
 * A plain rounded rectangle changes curvature discontinuously where the straight edge meets the
 * arc — the eye reads that as a hard seam at large radii. This builds each corner as
 * cubic → arc → cubic, so curvature ramps in and out. `smoothing` is the same 0..1 quantity as
 * Figma's corner smoothing: 0 is a circular corner, 0.6 is the house default, 1.0 is fully
 * continuous.
 *
 * Every card, sheet, chip, button, field and icon tile in the product is cut from this one
 * function, which is what makes the shapes feel like a family rather than a set of radii.
 */
@Immutable
class SquircleShape(
    private val topStart: Dp,
    private val topEnd: Dp,
    private val bottomEnd: Dp,
    private val bottomStart: Dp,
    private val smoothing: Float = DEFAULT_SMOOTHING,
) : Shape {

    constructor(radius: Dp, smoothing: Float = DEFAULT_SMOOTHING) :
        this(radius, radius, radius, radius, smoothing)

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (size.minDimension <= 0f) return Outline.Rectangle(size.toRect())
        val mirrored = layoutDirection == LayoutDirection.Rtl
        val ts = with(density) { (if (mirrored) topEnd else topStart).toPx() }
        val te = with(density) { (if (mirrored) topStart else topEnd).toPx() }
        val be = with(density) { (if (mirrored) bottomStart else bottomEnd).toPx() }
        val bs = with(density) { (if (mirrored) bottomEnd else bottomStart).toPx() }
        return Outline.Generic(squirclePath(size, ts, te, be, bs, smoothing))
    }

    override fun equals(other: Any?): Boolean = other is SquircleShape &&
        topStart == other.topStart && topEnd == other.topEnd &&
        bottomEnd == other.bottomEnd && bottomStart == other.bottomStart &&
        smoothing == other.smoothing

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        result = 31 * result + smoothing.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_SMOOTHING = 0.6f
    }
}

private fun Size.toRect() = androidx.compose.ui.geometry.Rect(Offset.Zero, this)

/** Per-corner geometry for the cubic → arc → cubic construction. */
private class CornerParams(val radius: Float, val a: Float, val b: Float, val c: Float, val d: Float, val p: Float, val arcSection: Float, val arcSweep: Float)

private fun cornerParams(radius: Float, smoothing: Float, budget: Float): CornerParams {
    if (radius <= 0.01f || budget <= 0.01f) {
        return CornerParams(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
    // Keep the requested radius and spend whatever smoothing still fits the available edge.
    val r = min(radius, budget)
    val s = if ((1f + smoothing) * r > budget) ((budget / r) - 1f).coerceIn(0f, smoothing) else smoothing

    val p = (1f + s) * r
    val arcMeasureDeg = 90f * (1f - s)
    val arcMeasure = Math.toRadians(arcMeasureDeg.toDouble()).toFloat()
    val arcSection = (sin((arcMeasure / 2f).toDouble()) * r * SQRT2).toFloat()

    val angleAlpha = Math.toRadians(((90f - arcMeasureDeg) / 2f).toDouble())
    val p3ToP4 = r * tan(angleAlpha / 2.0)
    val angleBeta = Math.toRadians((45f * s).toDouble())
    val c = (p3ToP4 * cos(angleBeta)).toFloat()
    val d = (c * tan(angleBeta)).toFloat()

    val b = ((p - arcSection - c - d) / 3f).coerceAtLeast(0f)
    val a = 2f * b
    return CornerParams(r, a, b, c, d, p, arcSection, arcMeasure)
}

private const val SQRT2 = 1.4142135f

/**
 * Approximates the corner arc with a single cubic. For sweeps of 90 degrees or less the error is
 * far below a physical pixel, and it keeps the whole outline as one continuous cubic chain.
 */
private fun Path.arcAsCubic(chordX: Float, chordY: Float, radius: Float, sweep: Float) {
    if (radius <= 0f || sweep <= 0f) {
        relativeLineTo(chordX, chordY)
        return
    }
    val chordLen = kotlin.math.hypot(chordX, chordY)
    if (chordLen <= 0.0001f) return
    val dirX = chordX / chordLen
    val dirY = chordY / chordLen
    val half = sweep / 2f
    val cosH = cos(half.toDouble()).toFloat()
    val sinH = sin(half.toDouble()).toFloat()
    // Tangents are the chord direction rotated by minus/plus half the sweep.
    val startX = dirX * cosH + dirY * sinH
    val startY = -dirX * sinH + dirY * cosH
    val endX = dirX * cosH - dirY * sinH
    val endY = dirX * sinH + dirY * cosH
    val k = (4f / 3f) * tan((sweep / 4f).toDouble()).toFloat() * radius
    relativeCubicTo(
        k * startX, k * startY,
        chordX - k * endX, chordY - k * endY,
        chordX, chordY,
    )
}

/**
 * Splits an edge between the two corners that share it, proportionally to their radii, so a
 * large corner next to a small one degrades predictably instead of collapsing.
 */
private fun budgetFor(radius: Float, neighbour: Float, edge: Float): Float {
    if (radius <= 0f) return 0f
    val total = radius + neighbour
    return if (total <= 0f) edge / 2f else edge * (radius / total)
}

internal fun squirclePath(
    size: Size,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
    smoothing: Float,
): Path {
    val w = size.width
    val h = size.height
    val s = smoothing.coerceIn(0f, 1f)

    val ts = cornerParams(topStart, s, min(budgetFor(topStart, topEnd, w), budgetFor(topStart, bottomStart, h)))
    val te = cornerParams(topEnd, s, min(budgetFor(topEnd, topStart, w), budgetFor(topEnd, bottomEnd, h)))
    val be = cornerParams(bottomEnd, s, min(budgetFor(bottomEnd, bottomStart, w), budgetFor(bottomEnd, topEnd, h)))
    val bs = cornerParams(bottomStart, s, min(budgetFor(bottomStart, bottomEnd, w), budgetFor(bottomStart, topStart, h)))

    return Path().apply {
        moveTo(w - te.p, 0f)
        // Top-right
        if (te.radius > 0f) {
            relativeCubicTo(te.a, 0f, te.a + te.b, 0f, te.a + te.b + te.c, te.d)
            arcAsCubic(te.arcSection, te.arcSection, te.radius, te.arcSweep)
            relativeCubicTo(te.d, te.c, te.d, te.b + te.c, te.d, te.a + te.b + te.c)
        } else {
            lineTo(w, 0f)
        }
        lineTo(w, h - be.p)
        // Bottom-right
        if (be.radius > 0f) {
            relativeCubicTo(0f, be.a, 0f, be.a + be.b, -be.d, be.a + be.b + be.c)
            arcAsCubic(-be.arcSection, be.arcSection, be.radius, be.arcSweep)
            relativeCubicTo(-be.c, be.d, -(be.b + be.c), be.d, -(be.a + be.b + be.c), be.d)
        } else {
            lineTo(w, h)
        }
        lineTo(bs.p, h)
        // Bottom-left
        if (bs.radius > 0f) {
            relativeCubicTo(-bs.a, 0f, -(bs.a + bs.b), 0f, -(bs.a + bs.b + bs.c), -bs.d)
            arcAsCubic(-bs.arcSection, -bs.arcSection, bs.radius, bs.arcSweep)
            relativeCubicTo(-bs.d, -bs.c, -bs.d, -(bs.b + bs.c), -bs.d, -(bs.a + bs.b + bs.c))
        } else {
            lineTo(0f, h)
        }
        lineTo(0f, ts.p)
        // Top-left
        if (ts.radius > 0f) {
            relativeCubicTo(0f, -ts.a, 0f, -(ts.a + ts.b), ts.d, -(ts.a + ts.b + ts.c))
            arcAsCubic(ts.arcSection, -ts.arcSection, ts.radius, ts.arcSweep)
            relativeCubicTo(ts.c, -ts.d, ts.b + ts.c, -ts.d, ts.a + ts.b + ts.c, -ts.d)
        } else {
            lineTo(0f, 0f)
        }
        close()
    }
}

/** A capsule: the radius is always half the height, so smoothing naturally resolves to zero. */
@Immutable
object CapsuleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(squirclePath(size, size.height / 2f, size.height / 2f, size.height / 2f, size.height / 2f, 0f))
}

@Immutable
data class ManagerShapes(
    /** Chips, small controls, inline badges. */
    val xs: Shape = SquircleShape(10.dp, 0.55f),
    /** Inner tiles, icon containers, list rows. */
    val sm: Shape = SquircleShape(16.dp, 0.6f),
    /** Standard cards. */
    val md: Shape = SquircleShape(22.dp, 0.65f),
    /** Hero cards, panels. */
    val lg: Shape = SquircleShape(28.dp, 0.7f),
    /** Floating surfaces. */
    val xl: Shape = SquircleShape(36.dp, 0.75f),
    /** The app detail surface. */
    val sheet: Shape = SquircleShape(38.dp, 38.dp, 38.dp, 38.dp, 0.8f),
    val capsule: Shape = CapsuleShape,
    /** App icon masking — heavily smoothed so real icons sit inside the product's geometry. */
    val iconSmoothing: Float = 0.85f,
)

val LocalManagerShapes = staticCompositionLocalOf { ManagerShapes() }

/** Icon tiles scale their radius with their size so a 28dp and a 64dp tile read identically. */
fun iconTileShape(sizeDp: Dp, smoothing: Float = 0.85f): Shape =
    SquircleShape(sizeDp * 0.295f, smoothing)
