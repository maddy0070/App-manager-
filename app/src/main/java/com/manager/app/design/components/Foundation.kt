package com.manager.app.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.LocalContentColor
import com.manager.app.design.LocalTextStyle
import com.manager.app.design.ManagerTheme

/**
 * Text, without Material's typography defaults in the way. Every call resolves its style from the
 * product's own scale, so nothing can accidentally inherit a Material role.
 */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        color = { color },
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * The product's only surface primitive: squircle geometry, an optional hairline, and a soft
 * warm-tinted shadow instead of Material's neutral grey elevation.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = ManagerTheme.shapes.md,
    color: Color = ManagerTheme.colors.surface,
    border: Color? = ManagerTheme.colors.hairline,
    elevation: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = ManagerTheme.colors
    Box(
        modifier = modifier
            .then(
                if (elevation > 0.dp) {
                    Modifier.shadow(
                        elevation = elevation,
                        shape = shape,
                        clip = false,
                        ambientColor = colors.ink.copy(alpha = 0.34f),
                        spotColor = colors.ink.copy(alpha = 0.22f),
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier),
        content = content,
    )
}

/**
 * The house press response.
 *
 * Every tappable thing in the product compresses very slightly and springs back — enough to feel
 * like the surface answered the finger, short enough that a fast tapper never waits on it. The
 * release spring is looser than the press so the return reads as elastic rather than mechanical.
 */
@Composable
fun Modifier.pressResponse(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.965f,
    pressedAlpha: Float = 1f,
): Modifier {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val target by rememberUpdatedState(pressedScale)

    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) return@LaunchedEffect
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scale.animateTo(target, PressSpec)
                    if (pressedAlpha != 1f) alpha.animateTo(pressedAlpha, PressSpec)
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    scale.animateTo(1f, ReleaseSpec)
                    if (pressedAlpha != 1f) alpha.animateTo(1f, ReleaseSpec)
                }
            }
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        this.alpha = alpha.value
    }
}

private val PressSpec = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 1f,
    stiffness = 2600f,
)
private val ReleaseSpec = androidx.compose.animation.core.spring<Float>(
    dampingRatio = 0.45f,
    stiffness = 700f,
)

/** A rule that stays one physical pixel at any density, so it never thickens into a border. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = ManagerTheme.colors.hairline) {
    val thickness = with(LocalDensity.current) { (1f / density).dp }
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}
