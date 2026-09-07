package com.manager.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.plotTint
import com.manager.app.design.components.SelectionMark
import com.manager.app.design.components.Txt
import com.manager.app.util.Format

/**
 * One app, suspended in the field.
 *
 * Its width is not a layout choice — it is the datum. Everything else about the chip is the app
 * row the product actually ships: the same icon tile, the same name-over-size stack, the same
 * selection mark. What the user learns to read here is what they will be reading a tap later.
 *
 * On selection the object also grows a little and its shadow deepens. That is the same idea as the
 * width: the thing you just chose has weight, and the interface should behave as though it does.
 */
@Composable
fun StoryChip(
    app: StoryApp,
    selected: Boolean,
    width: Dp,
    measure: StoryStaging.Measure,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val shape = SquircleShape(18.dp, 0.72f)

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
                elevation = if (selected) 20.dp else 13.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.22f),
                spotColor = colors.ink.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(container)
            .border(if (selected) 1.5.dp else 1.dp, outline, shape)
            .padding(horizontal = 13.dp, vertical = 11.dp)
            .semantics {
                contentDescription = "${app.label}, ${app.reading(measure)}"
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoryTile(app = app, selected = selected, size = 30.dp)
        Spacer(Modifier.width(11.dp))
        Column {
            Txt(app.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
            // The second line is whichever fact the field is currently measured by, so the
            // object always states the number its own width is drawn from.
            Txt(
                app.reading(measure),
                style = ManagerTheme.type.numericS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * The object's face: its initial on a band from the product's chart ramp.
 *
 * The band is the app's identity for the rest of the story — when the batch is removed, each app
 * becomes a block of exactly this colour, which is what makes "these five things became that
 * measurement" legible without a single label.
 */
@Composable
fun StoryTile(
    app: StoryApp,
    selected: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val tint = plotTint(app.tint)

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer { alpha = if (selected) 0f else 1f }
                .clip(SquircleShape(size * 0.32f, 0.85f))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Txt(
                app.initial,
                style = ManagerTheme.type.titleM.copy(fontSize = (size.value * 0.42f).sp),
                color = tint,
                maxLines = 1,
            )
        }
        if (selected) SelectionMark(selected = true, size = size)
    }
}

/** What this object reads as in the dimension the field is currently measured in. */
internal fun StoryApp.reading(measure: StoryStaging.Measure): String = when (measure) {
    StoryStaging.Measure.Usage -> Format.duration(screenTimeMs)
    else -> Format.bytes(totalBytes)
}

internal fun lerpDp(from: Dp, to: Dp, t: Float): Dp = from + (to - from) * t.coerceIn(0f, 1f)
