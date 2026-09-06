package com.manager.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.SelectionMark
import com.manager.app.design.components.Txt

/**
 * A story object, in whichever form the current beat calls for.
 *
 * There is one composable rather than a "chip" and a "row", because the last beat needs the chip
 * to *become* a row. [rowness] drives that: the surface flattens its shadow, tightens its corner,
 * and cross-fades between two readings of the same two facts inside a background that never
 * leaves the screen. The object reorganises; it is not swapped for a different object wearing the
 * same colour.
 *
 * Width is always explicit and always animated by the caller. Uniform widths make the floating
 * lattice provably non-overlapping, and they turn the chip-to-row change into a single continuous
 * measurement rather than a reflow.
 */
@Composable
fun StoryChip(
    obj: StoryObject,
    selected: Boolean,
    rowness: Float,
    width: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val shape = SquircleShape(lerpDp(18.dp, 15.dp, rowness), 0.72f)

    val container by animateColorAsState(
        targetValue = if (selected) colors.signalSoft else colors.surface,
        animationSpec = tween(200, easing = ManagerTheme.motion.standardEase),
        label = "storyChipContainer",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) colors.signal else colors.hairline,
        animationSpec = tween(200),
        label = "storyChipOutline",
    )

    Row(
        modifier = modifier
            .width(width)
            .shadow(
                elevation = lerpDp(14.dp, 2.dp, rowness),
                shape = shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.22f),
                spotColor = colors.ink.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTile(obj = obj, selected = selected, size = 28.dp)
        Spacer(Modifier.width(11.dp))

        Box(Modifier.weight(1f)) {
            if (rowness < 0.99f) {
                Column(Modifier.graphicsLayer { alpha = 1f - rowness }) {
                    Txt(
                        obj.label.uppercase(),
                        style = ManagerTheme.type.eyebrow,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Txt(obj.summary, style = ManagerTheme.type.numericS, color = colors.ink, maxLines = 1)
                }
            }
            if (rowness > 0.01f) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = rowness },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(
                        obj.label,
                        style = ManagerTheme.type.strong,
                        color = colors.ink,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(10.dp))
                    Txt(obj.summary, style = ManagerTheme.type.numericS, color = colors.inkSecondary, maxLines = 1)
                }
            }
        }
    }
}

/**
 * The object's face.
 *
 * Apps get their initial, facts get a glyph — identity versus meaning. It also means an app tile
 * never has to borrow an icon that already carries a different meaning elsewhere in the product.
 * On selection the tile hands over to the same mark the real app list uses, so the gesture the
 * story teaches is literally the gesture that ships.
 */
@Composable
fun StoryTile(
    obj: StoryObject,
    selected: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val tint = appTint(obj.tint)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { alpha = if (selected) 0f else 1f }
                .clip(SquircleShape(size * 0.32f, 0.85f))
                .background(if (obj.isApp) tint.copy(alpha = 0.16f) else colors.canvasSunken),
            contentAlignment = Alignment.Center,
        ) {
            if (obj.isApp) {
                Txt(
                    obj.initial,
                    style = ManagerTheme.type.titleM.copy(fontSize = (size.value * 0.42f).sp),
                    color = tint,
                    maxLines = 1,
                )
            } else {
                obj.icon?.let { ManagerIcon(it, null, tint = colors.inkSecondary, size = size * 0.5f) }
            }
        }
        if (selected) SelectionMark(selected = true, size = size)
    }
}

/** The plot ramp doubles as the app palette — five tints that already belong to the system. */
@Composable
fun appTint(index: Int): Color {
    val colors = ManagerTheme.colors
    return when (index % 5) {
        0 -> colors.plot1
        1 -> colors.plot2
        2 -> colors.plot3
        3 -> colors.signal
        else -> colors.ember
    }
}

internal fun lerpDp(from: Dp, to: Dp, t: Float): Dp = from + (to - from) * t.coerceIn(0f, 1f)
