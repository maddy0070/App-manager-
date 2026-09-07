package com.manager.app.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.util.Format
import kotlin.math.roundToLong

/**
 * Weight, and what happens when it leaves.
 *
 * Manager's whole argument is that apps have mass, so the two pieces here are the vocabulary that
 * argument is written in. A figure that jumps between values is a label; a figure that travels
 * between them is a measurement, so the readout counts. And an amount described only in words is
 * a promise, so removal is drawn as blocks physically leaving a rail rather than asserted.
 */

/**
 * A byte figure that moves to its new value instead of being replaced by it.
 *
 * Critically damped on purpose: this is a measurement settling, not a slot machine landing. The
 * first composition starts already reading the right number — only *changes* are worth travelling,
 * and a bar that counts up from zero every time it appears is a gimmick.
 */
@Composable
fun CountingBytes(
    bytes: Long,
    valueStyle: TextStyle,
    unitStyle: TextStyle,
    valueColor: Color,
    unitColor: Color,
    modifier: Modifier = Modifier,
    prefix: String = "",
    gap: Dp = 4.dp,
) {
    val shown = remember { Animatable(bytes.toFloat()) }
    LaunchedEffect(bytes) {
        shown.animateTo(bytes.toFloat(), spring(dampingRatio = 1f, stiffness = 220f))
    }
    val (value, unit) = Format.bytesParts(shown.value.roundToLong().coerceAtLeast(0L))
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Txt(prefix + value, style = valueStyle, color = valueColor, maxLines = 1)
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(gap))
            Txt(unit, style = unitStyle, color = unitColor, maxLines = 1)
        }
    }
}

/**
 * The batch, drawn as the space it occupies — and then as the space it leaves behind.
 *
 * Each app is one block, sized against the others, docked in a single rail. As [lift] rises the
 * blocks rise out of the rail in turn and the track is left empty, which is the whole point: the
 * gap is the reclaim. Nothing here claims to know the size of the device, because Manager does
 * not — the rail is the selection, never the phone.
 */
@Composable
fun ReclaimRail(
    blocks: List<VizSegment>,
    modifier: Modifier = Modifier,
    lift: Float = 0f,
    height: Dp = 16.dp,
    travelRoom: Dp = height * 2f,
) {
    val colors = ManagerTheme.colors
    val visible = blocks.filter { it.value > 0 }
    val total = visible.sumOf { it.value }.coerceAtLeast(1L)
    val travel = lift.coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            // Room above the rail for the blocks to leave through — none where nothing leaves.
            .height(height + travelRoom),
    ) {
        val railHeight = height.toPx()
        val top = size.height - railHeight
        val radius = CornerRadius(railHeight / 2f)

        drawRoundRect(
            color = colors.canvasSunken,
            topLeft = Offset(0f, top),
            size = Size(size.width, railHeight),
            cornerRadius = radius,
        )
        if (visible.isEmpty()) return@Canvas

        val gap = railHeight * 0.22f
        val usable = size.width - gap * (visible.size - 1)
        var x = 0f
        visible.forEachIndexed { index, block ->
            val width = usable * (block.value.toFloat() / total)
            // Staggered: the batch leaves in order, so five blocks read as five departures
            // rather than one shape sliding away.
            val start = index * 0.11f
            val local = ((travel - start) / 0.56f).coerceIn(0f, 1f)
            val eased = local * local * (3f - 2f * local)
            if (width > 0.4f && eased < 0.999f) {
                drawRoundRect(
                    color = block.color.copy(alpha = 1f - eased),
                    topLeft = Offset(x, top - railHeight * 1.9f * eased),
                    size = Size(width, railHeight),
                    cornerRadius = radius,
                )
            }
            x += width + gap
        }
    }
}

/**
 * The rail plus the figure it measures — the shape the product uses wherever a batch is about to
 * be acted on, so the confirmation and the onboarding are demonstrably the same moment.
 */
@Composable
fun ReclaimBlock(
    caption: String,
    bytes: Long,
    blocks: List<VizSegment>,
    modifier: Modifier = Modifier,
    lift: Float = 0f,
    note: String? = null,
    departs: Boolean = false,
) {
    val colors = ManagerTheme.colors
    Box(
        modifier
            // One reading. Split across an eyebrow, a figure, a unit and a canvas, a screen reader
            // hears four fragments and no sentence.
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(caption.lowercase().replaceFirstChar { it.uppercase() })
                    append(", ")
                    append(Format.bytes(bytes))
                    if (note != null) append(". $note")
                }
            },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Txt(caption, style = ManagerTheme.type.eyebrow, color = colors.inkTertiary, maxLines = 1)
            Spacer(Modifier.height(9.dp))
            CountingBytes(
                bytes = bytes,
                valueStyle = ManagerTheme.type.displayM,
                unitStyle = ManagerTheme.type.titleM,
                valueColor = colors.ink,
                unitColor = colors.inkTertiary,
                gap = 5.dp,
            )
            Spacer(Modifier.height(10.dp))
            ReclaimRail(
                blocks = blocks,
                lift = lift,
                travelRoom = if (departs) 32.dp else 0.dp,
            )
            if (note != null) {
                Spacer(Modifier.height(12.dp))
                Txt(note, style = ManagerTheme.type.metaS, color = colors.inkTertiary)
            }
        }
    }
}
