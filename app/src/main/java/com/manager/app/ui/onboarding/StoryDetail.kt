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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
 * The surface the tapped object turns into.
 *
 * Nothing here is presented; it is *grown*. The caller hands over [morph] — the object's own
 * rectangle interpolating into the sheet's — and this draws whatever shape that currently is,
 * corner radius included. At morph 0 it is indistinguishable from the chip that was tapped; at 1
 * it is a detail sheet. There is no frame in which two things exist.
 *
 * [reveal] is the finger. Dragging up grows the sheet and fills the numbers in as it goes: each
 * row counts from zero to its real value across its own slice of the drag, so the figures are
 * not decoration that happens to animate — they are the thing the gesture is producing.
 */
@Composable
fun StoryDetailSurface(
    obj: StoryObject,
    morph: Float,
    reveal: Float,
    stageWidth: Dp,
    stageHeight: Dp,
    chipWidth: Dp,
    chipCentre: androidx.compose.ui.geometry.Offset,
    onReveal: (Float) -> Unit,
    onRevealSettled: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val eased = ManagerTheme.motion.emphasized.transform(morph.coerceIn(0f, 1f))

    // Where the chip was, and where the sheet goes. Everything between is a straight lerp, so
    // the surface has one continuous identity across the whole move.
    val chipHeight = 54.dp
    val sheetWidth = stageWidth - 20.dp
    val sheetHeight = lerpDp(stageHeight * 0.44f, stageHeight * 0.74f, reveal)

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

    val dragRange = with(androidx.compose.ui.platform.LocalDensity.current) { (stageHeight * 0.30f).toPx() }
    val dragState = rememberDraggableState { delta ->
        // Up is negative; up reveals.
        onReveal((reveal - delta / dragRange).coerceIn(0f, 1f))
    }

    Box(
        modifier = modifier
            .padding(start = left, top = top)
            .width(width)
            .height(height)
            .shadow(
                elevation = lerpDp(14.dp, 30.dp, eased),
                shape = SquircleShape(corner, 0.78f),
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.34f),
                spotColor = colors.ink.copy(alpha = 0.26f),
            )
            .clip(SquircleShape(corner, 0.78f))
            .background(colors.surface)
            .border(1.dp, colors.hairline, SquircleShape(corner, 0.78f))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    // Velocity-aware: a flick commits even from low travel, a slow drag needs to
                    // have meant it.
                    val target = when {
                        velocity < -600f -> 1f
                        velocity > 600f -> 0f
                        reveal > 0.4f -> 1f
                        else -> 0f
                    }
                    onRevealSettled(target)
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
                        bottom = lerpDp(11.dp, 20.dp, eased),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StoryTile(obj = obj, selected = false, size = lerpDp(28.dp, 52.dp, eased))
                Spacer(Modifier.width(lerpDp(11.dp, 16.dp, eased)))
                Column(Modifier.weight(1f)) {
                    Txt(
                        obj.label.uppercase(),
                        style = ManagerTheme.type.eyebrow,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = 1f - eased },
                    )
                    if (eased > 0.35f) {
                        Txt(
                            obj.label,
                            style = ManagerTheme.type.titleL,
                            color = colors.ink,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = ((eased - 0.35f) / 0.65f) },
                        )
                        Spacer(Modifier.height(3.dp))
                        Txt(
                            "com.sample.${obj.id}",
                            style = ManagerTheme.type.metaS,
                            color = colors.inkTertiary,
                            maxLines = 1,
                            modifier = Modifier.graphicsLayer { alpha = ((eased - 0.5f) / 0.5f).coerceIn(0f, 1f) },
                        )
                    }
                }
            }

            if (eased > 0.55f) {
                RevealBody(
                    obj = obj,
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
 * The body the drag produces.
 *
 * Each row owns a slice of the reveal. Within its slice it rises, fades, and counts up — so the
 * numbers arrive as a consequence of the finger's travel rather than on a timer that happens to
 * run alongside it.
 */
@Composable
private fun RevealBody(obj: StoryObject, reveal: Float, alpha: Float) {
    val colors = ManagerTheme.colors
    val facts = remember(obj.id) { obj.reveal() }

    Column(Modifier.graphicsLayer { this.alpha = alpha }) {
        // The composition bar grows from the left as the storage rows fill in.
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                val shown = (obj.totalBytes * ease(reveal / 0.55f)).roundToLong()
                val (value, unit) = Format.bytesParts(shown)
                Txt(value, style = ManagerTheme.type.displayM, color = colors.ink, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                Txt(unit, style = ManagerTheme.type.titleM, color = colors.inkTertiary, maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth(ease(reveal / 0.5f).coerceAtLeast(0.02f))) {
                    CompositionBar(
                        segments = listOf(
                            VizSegment("App", obj.appBytes, colors.plot1),
                            VizSegment("Data", obj.dataBytes, colors.plot3),
                            VizSegment("Cache", obj.cacheBytes, colors.plot5),
                        ),
                        height = 10.dp,
                        animate = false,
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Hairline(Modifier.padding(horizontal = 24.dp))

        facts.forEachIndexed { index, fact ->
            // Rows share the drag between them, each starting a little after the last.
            val start = 0.12f + index * 0.13f
            val local = ((reveal - start) / 0.22f).coerceIn(0f, 1f)
            if (local <= 0.001f) return@forEachIndexed
            RevealRow(fact = fact, progress = local)
        }
    }
}

@Composable
private fun RevealRow(fact: RevealFact, progress: Float) {
    val colors = ManagerTheme.colors
    val eased = ease(progress)
    val shown = when (fact.unit) {
        FactUnit.Bytes -> Format.bytes((fact.amount * eased).roundToLong())
        FactUnit.Duration -> Format.duration((fact.amount * eased).roundToLong())
        FactUnit.Text -> fact.text
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = eased
                translationY = (1f - eased) * 14.dp.toPx()
            }
            .padding(horizontal = 24.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(fact.label, style = ManagerTheme.type.meta, color = colors.inkTertiary, maxLines = 1)
        Txt(shown, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
    }
}

/** A soft ease so counting numbers decelerate into their real value rather than snapping. */
private fun ease(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}
