package com.manager.app.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.manager.app.design.LocalContentColor
import com.manager.app.design.ManagerTheme

enum class ButtonTone { Signal, Quiet, Outline, Destructive }

/**
 * The product's button.
 *
 * Three things happen on press, all inside 120ms: the surface compresses, its fill deepens, and
 * the label tracks in very slightly. None of them is visible on its own; together they read as
 * the button taking the weight of the finger.
 */
@Composable
fun ManagerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Signal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    compact: Boolean = false,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val container = when (tone) {
        ButtonTone.Signal -> if (pressed) colors.signalPressed else colors.signal
        ButtonTone.Quiet -> if (pressed) colors.signalSoftStrong else colors.signalSoft
        ButtonTone.Outline -> if (pressed) colors.canvasSunken else Color.Transparent
        ButtonTone.Destructive -> if (pressed) colors.ember else colors.emberSoft
    }
    val content = when (tone) {
        ButtonTone.Signal -> colors.onSignal
        ButtonTone.Quiet -> colors.signal
        ButtonTone.Outline -> colors.ink
        ButtonTone.Destructive -> if (pressed) colors.onEmber else colors.ember
    }
    val animatedContainer by animateColorAsState(
        targetValue = if (enabled) container else colors.canvasSunken,
        animationSpec = tween(140),
        label = "buttonContainer",
    )
    val animatedContent by animateColorAsState(
        targetValue = if (enabled) content else colors.inkDisabled,
        animationSpec = tween(140),
        label = "buttonContent",
    )
    val tracking by animateFloatAsState(
        targetValue = if (pressed) -0.35f else 0f,
        animationSpec = tween(120),
        label = "buttonTracking",
    )

    val shape = ManagerTheme.shapes.capsule
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .pressResponse(interaction, enabled, pressedScale = 0.972f)
            .clip(shape)
            .background(animatedContainer)
            .then(
                if (tone == ButtonTone.Outline) Modifier.border(1.dp, colors.hairlineStrong, shape) else Modifier,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = if (compact) 44.dp else 52.dp)
            .padding(
                horizontal = if (compact) 18.dp else 26.dp,
                vertical = if (compact) 10.dp else 15.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            ManagerIcon(icon, null, tint = animatedContent, size = if (compact) 16.dp else 18.dp)
            Box(Modifier.width(if (compact) 7.dp else 9.dp))
        }
        Txt(
            text = label,
            style = (if (compact) ManagerTheme.type.labelS else ManagerTheme.type.label)
                .copy(letterSpacing = tracking.sp),
            color = animatedContent,
            maxLines = 1,
        )
    }
}

/**
 * A round icon control. On press the tile darkens and the glyph shrinks a touch faster than the
 * tile, which makes the whole thing feel like it has depth rather than being a flat hit target.
 */
@Composable
fun ManagerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ManagerTheme.colors.ink,
    container: Color = Color.Transparent,
    pressedContainer: Color = ManagerTheme.colors.canvasSunken,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    rotation: Float = 0f,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val glyphScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 900f),
        label = "iconGlyph",
    )
    val fill by animateColorAsState(
        targetValue = if (pressed) pressedContainer else container,
        animationSpec = tween(130),
        label = "iconFill",
    )
    val shape = ManagerTheme.shapes.capsule

    Box(
        modifier = modifier
            .size(size)
            .pressResponse(interaction, enabled, pressedScale = 0.94f)
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ManagerIcon(
            icon,
            contentDescription,
            tint = if (enabled) tint else ManagerTheme.colors.inkDisabled,
            size = iconSize,
            modifier = Modifier
                .scale(glyphScale)
                .rotate(rotation),
        )
    }
}

/**
 * A text action that underlines itself on press instead of flashing a background — used where a
 * filled button would be too loud, such as inside dense metadata.
 */
@Composable
fun ManagerTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = ManagerTheme.colors.signal,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shift by animateFloatAsState(
        targetValue = if (pressed) 2f else 0f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 1000f),
        label = "textActionShift",
    )
    Row(
        modifier = modifier
            .clip(ManagerTheme.shapes.capsule)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            // Small type, full-size target: the label is quiet, the hit area is not.
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .graphicsLayer { translationX = shift },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Txt(label, style = ManagerTheme.type.labelS, color = if (enabled) color else ManagerTheme.colors.inkDisabled, maxLines = 1)
        if (icon != null) ManagerIcon(icon, null, tint = if (enabled) color else ManagerTheme.colors.inkDisabled, size = 14.dp)
    }
}

/** A row of actions laid out with the product's own spacing rather than a Material button bar. */
@Composable
fun ActionRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ManagerIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp,
) {
    // Drawn directly rather than through Material's Icon: this is the product's only icon
    // primitive, and routing it through a component library that supplies nothing else would
    // put a whole Material dependency behind one tint.
    Image(
        imageVector = icon,
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint),
    )
}

/**
 * A glyph control on the inverse capsule — the action bar's only button shape.
 *
 * It has no container of its own until pressed, because a row of filled buttons inside an already
 * filled capsule reads as a toolbar rather than as one object. The 19dp glyph plus its padding is
 * exactly the 44dp minimum target.
 */
@Composable
fun InverseGlyphButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) tint.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(130),
        label = "inverseGlyphFill",
    )
    Box(
        modifier
            .pressResponse(interaction, enabled, pressedScale = 0.9f)
            .clip(ManagerTheme.shapes.capsule)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(12.5.dp),
    ) {
        ManagerIcon(
            icon,
            description,
            tint = if (enabled) tint else tint.copy(alpha = 0.35f),
            size = 19.dp,
        )
    }
}

/** Provides a content colour to a subtree without pulling in Material's colour scheme. */
@Composable
fun ProvideContentColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides color, content = content)
}
