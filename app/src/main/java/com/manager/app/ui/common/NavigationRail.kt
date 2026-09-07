package com.manager.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse
import com.manager.app.ui.Destination

/**
 * The floating navigation bar.
 *
 * Only the active destination shows its label; the others are icons. Switching does not swap two
 * backgrounds — the active pill physically grows at the new position while the old one collapses,
 * so the bar reads as one object rearranging rather than three buttons repainting.
 */
@Composable
fun NavigationRail(
    current: Destination,
    onSelect: (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    Row(
        modifier = modifier
            .shadow(
                elevation = 22.dp,
                shape = ManagerTheme.shapes.capsule,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.4f),
                spotColor = colors.ink.copy(alpha = 0.3f),
            )
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.surface)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Destination.entries.forEach { destination ->
            NavigationItem(
                destination = destination,
                icon = destination.icon(),
                selected = destination == current,
                onClick = { onSelect(destination) },
            )
        }
    }
}

private fun Destination.icon(): ImageVector = when (this) {
    Destination.Dashboard -> ManagerIcons.Layers
    Destination.Apps -> ManagerIcons.Grid
    Destination.Usage -> ManagerIcons.Pulse
}

@Composable
private fun NavigationItem(
    destination: Destination,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        targetValue = if (selected) colors.signal else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(240, easing = ManagerTheme.motion.standardEase),
        label = "navContainer",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) colors.onSignal else colors.inkTertiary,
        animationSpec = tween(240),
        label = "navTint",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "navLift",
    )

    Row(
        modifier = Modifier
            .pressResponse(interaction, pressedScale = 0.93f)
            .clip(ManagerTheme.shapes.capsule)
            .background(container)
            .clickable(interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 480f))
            .padding(horizontal = if (selected) 19.dp else 16.dp, vertical = 13.dp)
            .semantics {
                this.selected = selected
                contentDescription = destination.title
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerIcon(
            icon,
            null,
            tint = tint,
            size = 19.dp,
            modifier = Modifier.graphicsLayer { translationY = -lift * 0.5f },
        )
        if (selected) {
            Spacer(Modifier.width(9.dp))
            Txt(
                destination.title,
                style = ManagerTheme.type.labelS,
                color = tint,
                maxLines = 1,
                modifier = Modifier
                    .clearAndSetSemantics { }
                    .graphicsLayer { alpha = lift },
            )
        }
    }
}

/**
 * The selection action bar occupies the same slot as the navigation bar and shares its capsule,
 * so entering selection mode reads as the bar changing job rather than a new bar appearing.
 */
@Composable
fun SelectionBar(
    count: Int,
    allSelected: Boolean,
    canExtract: Boolean,
    canUninstall: Boolean,
    onSelectAll: () -> Unit,
    onExtract: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    Row(
        modifier = modifier
            .shadow(
                elevation = 24.dp,
                shape = ManagerTheme.shapes.capsule,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.45f),
                spotColor = colors.ink.copy(alpha = 0.32f),
            )
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.surfaceInverse)
            .padding(start = 8.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SelectionGlyphButton(ManagerIcons.Close, "Leave selection", onDismiss, colors.onSurfaceInverse)

        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val up = targetState > initialState
                (
                    fadeIn(tween(160)) + scaleIn(initialScale = if (up) 0.7f else 1.25f, animationSpec = tween(200))
                    ) togetherWith (
                    fadeOut(tween(120)) + scaleOut(targetScale = if (up) 1.25f else 0.7f, animationSpec = tween(160))
                    )
            },
            label = "selectionCount",
        ) { value ->
            Txt(
                "$value",
                style = ManagerTheme.type.titleM,
                color = colors.onSurfaceInverse,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Txt(
            if (count == 1) "app" else "apps",
            style = ManagerTheme.type.meta,
            color = colors.onSurfaceInverse.copy(alpha = 0.6f),
            maxLines = 1,
        )

        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .width(1.dp)
                .height(22.dp)
                .background(colors.onSurfaceInverse.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(2.dp))

        SelectionGlyphButton(
            icon = ManagerIcons.CheckSolid,
            description = if (allSelected) "Deselect all" else "Select all",
            onClick = onSelectAll,
            tint = if (allSelected) colors.onSurfaceInverse else colors.onSurfaceInverse.copy(alpha = 0.72f),
        )
        SelectionGlyphButton(
            icon = ManagerIcons.Extract,
            description = "Extract APKs",
            onClick = onExtract,
            tint = colors.onSurfaceInverse,
            enabled = canExtract,
        )
        SelectionGlyphButton(
            icon = ManagerIcons.Trash,
            description = "Uninstall",
            onClick = onUninstall,
            tint = colors.emberOnInverse,
            enabled = canUninstall,
        )
    }
}

@Composable
private fun SelectionGlyphButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) tint.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(130),
        label = "selectionGlyphFill",
    )
    Box(
        Modifier
            .pressResponse(interaction, enabled, pressedScale = 0.9f)
            .clip(ManagerTheme.shapes.capsule)
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            // 19dp glyph plus this padding is exactly the 44dp minimum.
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
