package com.manager.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.CompositionBar
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.Txt
import com.manager.app.design.components.VizSegment
import com.manager.app.util.Format
import kotlin.math.roundToLong

/**
 * The surface the touched object turns into.
 *
 * Nothing here is presented; it is grown. The caller hands over [morph] — the object's own
 * rectangle interpolating into the sheet's — and this draws whatever shape that currently is,
 * corner radius included. At morph 0 it is indistinguishable from the chip that was touched; at 1
 * it is a detail sheet. There is no frame in which two things exist.
 *
 * [reveal] is the finger, and what it does is the point of the whole beat. The object already
 * carried one number — its size — and the drag *takes that number apart*: the single block
 * separates into app, data and cache, and the rest of what Android knows arrives underneath. The
 * information is not loaded onto the screen, it is pulled out of a figure the user was already
 * looking at.
 */
@Composable
fun StoryDetailSurface(
    app: StoryApp,
    morph: Float,
    reveal: Float,
    stageWidth: Dp,
    stageHeight: Dp,
    chipWidth: Dp,
    chipCentre: androidx.compose.ui.geometry.Offset,
    onReveal: (Float) -> Unit,
    onRevealSettled: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val eased = ManagerTheme.motion.emphasized.transform(morph.coerceIn(0f, 1f))

    // Where the chip was, and where the sheet goes. Everything between is a straight lerp, so the
    // surface has one continuous identity across the whole move.
    val chipHeight = 57.dp
    val sheetWidth = stageWidth - 20.dp
    val sheetHeight = lerpDp(stageHeight * 0.46f, stageHeight * 0.78f, reveal)

    val width = lerpDp(chipWidth, sheetWidth, eased)
    val height = lerpDp(chipHeight, sheetHeight, eased)
    val left = lerpDp(
        stageWidth * chipCentre.x - chipWidth / 2f,
        (stageWidth - sheetWidth) / 2f,
        eased,
    )
    val top = lerpDp(
        stageHeight * chipCentre.y - chipHeight / 2f,
        stageHeight - sheetHeight - 10.dp,
        eased,
    )
    val corner = lerpDp(18.dp, 34.dp, eased)
    val shape = SquircleShape(corner, 0.78f)

    val dragRange = with(LocalDensity.current) { (stageHeight * 0.30f).toPx() }
    val dragState = rememberDraggableState { delta ->
        // Up is negative; up takes the number apart.
        onReveal((reveal - delta / dragRange).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .padding(start = left, top = top)
            .width(width)
            .height(height)
            .shadow(
                elevation = lerpDp(13.dp, 30.dp, eased),
                shape = shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.34f),
                spotColor = colors.ink.copy(alpha = 0.26f),
            )
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    // Velocity-aware: a flick commits from low travel, a slow drag has to mean it.
                    // A downward throw from the closed position puts the object back in the field,
                    // which is the same gesture that dismisses the real detail sheet.
                    when {
                        reveal < 0.06f && velocity > 420f -> onDismiss()
                        velocity < -600f -> onRevealSettled(1f)
                        velocity > 600f -> onRevealSettled(0f)
                        reveal > 0.4f -> onRevealSettled(1f)
                        else -> onRevealSettled(0f)
                    }
                },
            ),
    ) {
        // The header is the chip's own content, still in the chip's place, growing with it.
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = lerpDp(13.dp, 24.dp, eased),
                        end = lerpDp(13.dp, 24.dp, eased),
                        top = lerpDp(11.dp, 26.dp, eased),
                        bottom = lerpDp(11.dp, 18.dp, eased),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryTile(app = app, selected = false, size = lerpDp(30.dp, 52.dp, eased))
                Spacer(Modifier.width(lerpDp(11.dp, 16.dp, eased)))
                Column(Modifier.weight(1f)) {
                    if (eased < 0.99f) {
                        Column(Modifier.graphicsLayer { alpha = 1f - eased }) {
                            Txt(app.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
                            Txt(
                                Format.bytes(app.totalBytes),
                                style = ManagerTheme.type.numericS,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (eased > 0.35f) {
                        Column(Modifier.graphicsLayer { alpha = ((eased - 0.35f) / 0.65f) }) {
                            Txt(app.label, style = ManagerTheme.type.titleL, color = colors.ink, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Txt(
                                app.packageName,
                                style = ManagerTheme.type.metaS,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            if (eased > 0.55f) {
                DecomposeBody(
                    app = app,
                    reveal = reveal,
                    alpha = ((eased - 0.55f) / 0.45f).coerceIn(0f, 1f),
                )
            }
        }

        // The grab affordance: a handle that is only a handle once there is something to pull.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 7.dp)
                .width(34.dp)
                .height(4.dp)
                .graphicsLayer { alpha = eased * (1f - reveal * 0.7f) }
                .clip(ManagerTheme.shapes.capsule)
                .background(colors.hairlineStrong),
        )
    }
}

/**
 * The number, coming apart.
 *
 * The first half of the drag separates the total into its three real parts — the bar splits, the
 * colours arrive, and each part counts up from nothing to its share. The second half brings the
 * rest of the record. Both halves are driven by the finger's position rather than a timer, so the
 * facts are produced by the gesture instead of merely appearing during it.
 */
@Composable
private fun DecomposeBody(app: StoryApp, reveal: Float, alpha: Float) {
    val colors = ManagerTheme.colors
    val gutter = ManagerTheme.space.sheetGutter

    // The split owns the first 42% of the travel; the record owns the rest.
    val split = (reveal / 0.42f).coerceIn(0f, 1f)
    val segments = listOf(
        VizSegment("App", app.appBytes, colors.plot1),
        VizSegment("Data", app.dataBytes, colors.plot3),
        VizSegment("Cache", app.cacheBytes, colors.plot5),
    )

    Column(Modifier.graphicsLayer { this.alpha = alpha }) {
        Column(Modifier.padding(horizontal = gutter)) {
            Row(verticalAlignment = Alignment.Bottom) {
                val (value, unit) = Format.bytesParts(app.totalBytes)
                Txt(value, style = ManagerTheme.type.displayM, color = colors.ink, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                Txt(unit, style = ManagerTheme.type.titleM, color = colors.inkTertiary, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            CompositionBar(segments = segments, height = 11.dp, animate = false, split = split)
        }

        // Each part takes its own slice of the split, so they separate in order rather than
        // all three arriving at once.
        segments.forEachIndexed { index, segment ->
            val local = ((split - 0.18f - index * 0.2f) / 0.34f).coerceIn(0f, 1f)
            if (local <= 0.001f) return@forEachIndexed
            PartRow(
                color = segment.color,
                label = segment.label,
                value = Format.bytes((segment.value * ease(local)).roundToLong()),
                progress = local,
                gutter = gutter,
            )
        }

        if (reveal > 0.44f) {
            Spacer(Modifier.height(14.dp))
            Hairline(Modifier.padding(horizontal = gutter))
        }

        val record = listOf(
            "Screen time" to Format.duration(app.screenTimeMs),
            "Version" to app.version,
            "Installed" to app.installed,
        )
        record.forEachIndexed { index, (label, value) ->
            val local = ((reveal - 0.46f - index * 0.11f) / 0.2f).coerceIn(0f, 1f)
            if (local <= 0.001f) return@forEachIndexed
            FactRow(label = label, value = value, progress = local, gutter = gutter)
        }
    }
}

@Composable
private fun PartRow(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    value: String,
    progress: Float,
    gutter: Dp,
) {
    val colors = ManagerTheme.colors
    val eased = ease(progress)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = eased
                translationY = (1f - eased) * 12.dp.toPx()
            }
            .padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(ManagerTheme.shapes.capsule)
                .background(color),
        )
        Spacer(Modifier.width(10.dp))
        Txt(label, style = ManagerTheme.type.meta, color = colors.inkSecondary, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Txt(value, style = ManagerTheme.type.numericS, color = colors.ink, maxLines = 1)
    }
}

@Composable
private fun FactRow(label: String, value: String, progress: Float, gutter: Dp) {
    val colors = ManagerTheme.colors
    val eased = ease(progress)
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = eased
                translationY = (1f - eased) * 14.dp.toPx()
            }
            .padding(horizontal = gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(label, style = ManagerTheme.type.meta, color = colors.inkTertiary, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Txt(value, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
    }
}

/** A soft ease so values decelerate into place rather than snapping. */
private fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}
