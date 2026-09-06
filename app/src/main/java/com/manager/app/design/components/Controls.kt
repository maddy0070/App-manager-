package com.manager.app.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme

/**
 * A chip that inverts rather than tints when selected. The fill animates from the soft tint to
 * the full signal colour while the label crossfades — no border toggling, no checkmark appearing
 * out of nowhere.
 */
@Composable
fun ManagerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailing: String? = null,
    enabled: Boolean = true,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val shape = ManagerTheme.shapes.capsule

    val container by animateColorAsState(
        targetValue = when {
            !enabled -> colors.canvasSunken
            selected -> colors.signal
            else -> colors.surface
        },
        animationSpec = tween(180, easing = ManagerTheme.motion.standardEase),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> colors.inkDisabled
            selected -> colors.onSignal
            else -> colors.inkSecondary
        },
        animationSpec = tween(180),
        label = "chipContent",
    )
    val outline by animateColorAsState(
        targetValue = if (selected || !enabled) Color.Transparent else colors.hairline,
        animationSpec = tween(180),
        label = "chipOutline",
    )

    Row(
        modifier = modifier
            .pressResponse(interaction, enabled, pressedScale = 0.94f)
            .clip(shape)
            .background(container)
            .border(1.dp, outline, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            )
            // The label is small on purpose; the target never is.
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) ManagerIcon(icon, null, tint = content, size = 15.dp)
        Txt(label, style = ManagerTheme.type.labelS, color = content, maxLines = 1)
        if (trailing != null) {
            Txt(
                trailing,
                style = ManagerTheme.type.metaS,
                color = if (selected) colors.onSignal.copy(alpha = 0.66f) else colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

/**
 * A segmented control where a single filled thumb slides between options.
 *
 * The thumb is a real animated element rather than a per-segment background swap, so switching
 * ranges reads as one object moving — which is what makes the control feel physical.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val shape = ManagerTheme.shapes.capsule
    var trackWidth by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    val segmentWidth = if (options.isEmpty()) 0.dp else with(density) { (trackWidth / options.size).toDp() }
    val thumbOffset by animateDpAsState(
        targetValue = segmentWidth * index,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "segmentThumb",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.canvasSunken)
            .padding(3.dp)
            .onSizeChanged { trackWidth = it.width - with(density) { 6.dp.roundToPx() } },
    ) {
        if (trackWidth > 0) {
            Box(
                Modifier
                    .offset(x = thumbOffset)
                    .width(segmentWidth)
                    .height(34.dp)
                    .clip(shape)
                    .background(colors.surface)
                    .border(1.dp, colors.hairline, shape),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            options.forEach { option ->
                val isSelected = option == selected
                val interaction = remember(option) { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.92f else 1f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 1000f),
                    label = "segmentPress",
                )
                val tint by animateColorAsState(
                    targetValue = if (isSelected) colors.ink else colors.inkTertiary,
                    animationSpec = tween(180),
                    label = "segmentTint",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(shape)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                        ) { onSelect(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Txt(
                        label(option),
                        style = ManagerTheme.type.labelS,
                        color = tint,
                        maxLines = 1,
                        modifier = Modifier.scale(scale),
                    )
                }
            }
        }
    }
}

/**
 * Search, built from a bare text field so nothing Material shows through.
 *
 * The magnifier slides left and shrinks as soon as there is a query, handing its space to the
 * text; the clear control grows in from nothing. Both are the same 180ms so it reads as one move.
 */
@Composable
fun ManagerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search apps or packages",
    focusRequester: FocusRequester? = null,
    onSubmit: () -> Unit = {},
) {
    val colors = ManagerTheme.colors
    val shape = ManagerTheme.shapes.capsule
    val hasQuery = query.isNotEmpty()
    val keyboard = LocalSoftwareKeyboardController.current

    val iconScale by animateFloatAsState(
        targetValue = if (hasQuery) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "searchIcon",
    )
    val clearScale by animateFloatAsState(
        targetValue = if (hasQuery) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 620f),
        label = "searchClear",
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.hairline, shape)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerIcon(
            ManagerIcons.Search,
            null,
            tint = if (hasQuery) colors.signal else colors.inkTertiary,
            size = 18.dp,
            modifier = Modifier.scale(iconScale),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, end = 6.dp, top = 15.dp, bottom = 15.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Txt(placeholder, style = ManagerTheme.type.body, color = colors.inkTertiary, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = ManagerTheme.type.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.signal),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); onSubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder }
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = clearScale
                    scaleY = clearScale
                    alpha = clearScale
                },
            contentAlignment = Alignment.Center,
        ) {
            if (clearScale > 0.01f) {
                ManagerIconButton(
                    icon = ManagerIcons.Close,
                    contentDescription = "Clear search",
                    onClick = { onQueryChange("") },
                    size = 34.dp,
                    iconSize = 15.dp,
                    tint = colors.inkSecondary,
                    container = colors.canvasSunken,
                    pressedContainer = colors.hairlineStrong,
                )
            }
        }
    }
}

/**
 * The selection mark.
 *
 * Not a checkbox: an empty ring that fills with signal and stamps a tick, so a selected row is
 * legible from across the screen and never relies on colour alone.
 */
@Composable
fun SelectionMark(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val colors = ManagerTheme.colors
    val fill by animateColorAsState(
        targetValue = if (selected) colors.signal else Color.Transparent,
        animationSpec = tween(180, easing = ManagerTheme.motion.standardEase),
        label = "markFill",
    )
    val ring by animateColorAsState(
        targetValue = if (selected) colors.signal else colors.hairlineStrong,
        animationSpec = tween(180),
        label = "markRing",
    )
    val tick by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "markTick",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(ManagerTheme.shapes.capsule)
            .background(fill)
            .border(1.5.dp, ring, ManagerTheme.shapes.capsule),
        contentAlignment = Alignment.Center,
    ) {
        if (tick > 0.02f) {
            ManagerIcon(
                ManagerIcons.CheckSolid,
                null,
                tint = colors.onSignal,
                size = size * 0.62f,
                modifier = Modifier.graphicsLayer {
                    scaleX = tick
                    scaleY = tick
                    alpha = tick
                },
            )
        }
    }
}

/**
 * A switch built from the product's geometry: a capsule track and a squircle thumb that stretches
 * slightly as it travels, the way a physical toggle would.
 */
@Composable
fun ManagerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> colors.canvasSunken
            checked -> colors.signal
            else -> colors.hairlineStrong
        },
        animationSpec = tween(200),
        label = "switchTrack",
    )
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 700f),
        label = "switchThumb",
    )
    val stretch by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "switchStretch",
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(ManagerTheme.shapes.capsule)
            .background(track)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .offset(x = offset)
                .size(22.dp)
                .graphicsLayer {
                    scaleX = stretch
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (checked) 1f else 0f, 0.5f)
                }
                .clip(ManagerTheme.shapes.capsule)
                .background(colors.surface),
        )
    }
}

/** A dot-and-label pair for legends; never the only carrier of meaning. */
@Composable
fun LegendSwatch(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(ManagerTheme.shapes.capsule)
                .background(color),
        )
        Txt(label, style = ManagerTheme.type.meta, color = ManagerTheme.colors.inkSecondary, maxLines = 1)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Txt(value, style = ManagerTheme.type.numericS, color = ManagerTheme.colors.ink, maxLines = 1)
    }
}
