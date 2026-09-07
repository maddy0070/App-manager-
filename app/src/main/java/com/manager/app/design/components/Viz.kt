package com.manager.app.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** One slice of a composition bar. Zero-weight segments are dropped, never drawn as slivers. */
data class VizSegment(val label: String, val value: Long, val color: Color)

/**
 * Storage composition: a single capsule split into weighted segments with a hairline gap between
 * them, rather than a pie or a stacked chart legend nobody reads.
 *
 * The gaps are drawn as canvas background, so the bar keeps its capsule silhouette even when one
 * segment dominates — which, for app storage, it almost always does.
 */
@Composable
fun CompositionBar(
    segments: List<VizSegment>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    animate: Boolean = true,
    split: Float = 1f,
) {
    val colors = ManagerTheme.colors
    val visible = segments.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1L)
    // How far the bar has been taken apart. At 1 it is the composition; at 0 it is one undivided
    // block of a single colour — the same quantity, before anyone has said what it is made of.
    val apart = split.coerceIn(0f, 1f)
    val whole = visible.firstOrNull()?.color ?: colors.signal

    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val easing = ManagerTheme.motion.emphasized
    LaunchedEffect(visible.size, total) {
        if (animate) progress.animateTo(1f, tween(700, easing = easing))
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.canvasSunken),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (visible.isEmpty()) return@Canvas
            val gap = size.height * 0.22f * apart
            val usable = size.width - gap * (visible.size - 1).coerceAtLeast(0)
            var x = 0f
            visible.forEach { segment ->
                val width = usable * (segment.value.toFloat() / total) * progress.value
                if (width > 0.4f) {
                    drawRoundRect(
                        color = lerp(whole, segment.color, apart),
                        topLeft = Offset(x, 0f),
                        size = Size(width, size.height),
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }
                x += width + gap
            }
        }
    }
}

/**
 * The usage ranking bar.
 *
 * The value is drawn as a filled capsule against a faint track; the fill is the signal colour at
 * an opacity that steps down with rank, so first place is unmistakable without a second hue.
 * Bars grow from the left on first appearance, staggered by rank.
 */
@Composable
fun RankBar(
    fraction: Float,
    rank: Int,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = ManagerTheme.colors.signal,
) {
    val target = fraction.coerceIn(0f, 1f)
    val grow = remember { Animatable(0f) }
    LaunchedEffect(target, rank) {
        grow.animateTo(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.9f, stiffness = 260f),
        )
    }
    val alpha = when (rank) {
        0 -> 1f
        1 -> 0.82f
        2 -> 0.66f
        3 -> 0.52f
        else -> 0.4f
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(ManagerTheme.shapes.capsule)
            .background(ManagerTheme.colors.canvasSunken),
    ) {
        Box(
            Modifier
                .fillMaxWidth(grow.value.coerceAtLeast(0.012f))
                .height(height)
                .clip(ManagerTheme.shapes.capsule)
                .background(color.copy(alpha = alpha)),
        )
    }
}

/**
 * A radial gauge for a single proportion — used on the dashboard for the user/system split.
 *
 * It opens with a sweep from the twelve o'clock position and leaves a deliberate gap at the
 * bottom, so the arc reads as a measurement rather than a loading spinner.
 */
@Composable
fun ArcGauge(
    fraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    thickness: Dp = 9.dp,
    color: Color = ManagerTheme.colors.signal,
    track: Color = ManagerTheme.colors.signalSoft,
    content: @Composable () -> Unit = {},
) {
    val target = fraction.coerceIn(0f, 1f)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(target) {
        sweep.animateTo(target, spring(dampingRatio = 1f, stiffness = 180f))
    }
    Box(Modifier.size(size), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(modifier.size(size)) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            val total = 280f
            val start = 130f
            drawArc(
                color = track,
                startAngle = start,
                sweepAngle = total,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (sweep.value > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = start,
                    sweepAngle = total * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

/**
 * The install timeline: one tick per bucket, height proportional to how many apps landed in it.
 *
 * This is the only chart on the dashboard with a time axis, and it answers one question — when
 * did this phone fill up — so it carries no gridlines, no axis labels and no legend.
 */
@Composable
fun TimelineBars(
    values: List<Int>,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    highlightIndex: Int = -1,
    color: Color = ManagerTheme.colors.signal,
    mutedColor: Color = ManagerTheme.colors.signalSoftStrong,
) {
    val peak = max(values.maxOrNull() ?: 0, 1)
    val grow = remember(values.size) { Animatable(0f) }
    val easing = ManagerTheme.motion.emphasized
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(620, easing = easing))
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = size.width * 0.018f
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val radius = CornerRadius(barWidth.coerceAtMost(size.height) / 2.6f)
        values.forEachIndexed { index, value ->
            val ratio = value.toFloat() / peak
            // A floor keeps empty buckets visible as a baseline tick rather than a hole.
            val barHeight = (size.height * ratio * grow.value).coerceAtLeast(barWidth * 0.42f)
            drawRoundRect(
                color = if (index == highlightIndex) color else mutedColor,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}

/**
 * A drifting field of squircle motes used behind the onboarding headline.
 *
 * Each mote traces a slow independent Lissajous path, so the composition never repeats within a
 * viewing and never resolves into an obvious loop. Amplitudes are a few dp — this is atmosphere,
 * not animation.
 */
fun driftOffset(phase: Float, seed: Int, amplitude: Float): Offset {
    val a = 1f + (seed % 3) * 0.37f
    val b = 1.3f + (seed % 5) * 0.29f
    val shift = seed * 0.7f
    return Offset(
        x = sin((phase * a + shift).toDouble()).toFloat() * amplitude,
        y = cos((phase * b + shift * 1.3f).toDouble()).toFloat() * amplitude * 0.8f,
    )
}
