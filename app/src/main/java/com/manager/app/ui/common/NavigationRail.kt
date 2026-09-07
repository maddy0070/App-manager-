package com.manager.app.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.manager.app.design.components.CountingBytes
import com.manager.app.design.components.InverseGlyphButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse
import com.manager.app.ui.Destination
import com.manager.app.util.Format

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
 *
 * It carries the weight of the selection, not just its count, because the count is rarely the
 * question. "Three apps" is trivia; "three apps, 4.82 GB" is a decision. The figure travels
 * between values rather than being replaced, so adding an app reads as weight accumulating.
 *
 * @param measured false when at least one selected app has no StorageStats figure and its size is
 * the APK on disk. The total is then prefixed rather than quietly presented as the whole truth.
 */
@Composable
fun SelectionBar(
    count: Int,
    bytes: Long,
    measured: Boolean,
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
    val emphasized = ManagerTheme.motion.emphasized
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
            .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        InverseGlyphButton(ManagerIcons.Close, "Leave selection", onDismiss, colors.onSurfaceInverse)

        Column(
            Modifier
                .padding(start = 2.dp, end = 10.dp)
                // One reading, not three fragments: a screen reader should hear the decision.
                .clearAndSetSemantics {
                    contentDescription = buildString {
                        append(count)
                        append(if (count == 1) " app selected, " else " apps selected, ")
                        append(if (measured) "" else "about ")
                        append(Format.bytes(bytes))
                        if (!measured) append(", measured from APK size on disk")
                    }
                },
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (
                        fadeIn(tween(140)) + slideInVertically(tween(200, easing = emphasized)) {
                            if (up) it / 2 else -it / 2
                        }
                        ) togetherWith (
                        fadeOut(tween(100)) + slideOutVertically(tween(160)) { if (up) -it / 2 else it / 2 }
                        )
                },
                label = "selectionCount",
            ) { value ->
                Txt(
                    "$value selected",
                    style = ManagerTheme.type.metaS,
                    color = colors.onSurfaceInverse.copy(alpha = 0.62f),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(1.dp))
            CountingBytes(
                bytes = bytes,
                valueStyle = ManagerTheme.type.titleM,
                unitStyle = ManagerTheme.type.metaS,
                valueColor = colors.onSurfaceInverse,
                unitColor = colors.onSurfaceInverse.copy(alpha = 0.62f),
                prefix = if (measured) "" else "≈",
                gap = 3.dp,
            )
        }

        Box(
            Modifier
                .width(1.dp)
                .height(24.dp)
                .background(colors.onSurfaceInverse.copy(alpha = 0.18f)),
        )
        Spacer(Modifier.width(1.dp))

        InverseGlyphButton(
            icon = ManagerIcons.CheckSolid,
            description = if (allSelected) "Deselect all" else "Select all",
            onClick = onSelectAll,
            tint = if (allSelected) colors.onSurfaceInverse else colors.onSurfaceInverse.copy(alpha = 0.72f),
        )
        InverseGlyphButton(
            icon = ManagerIcons.Extract,
            description = "Extract APKs",
            onClick = onExtract,
            tint = colors.onSurfaceInverse,
            enabled = canExtract,
        )
        InverseGlyphButton(
            icon = ManagerIcons.Trash,
            description = "Uninstall",
            onClick = onUninstall,
            tint = colors.emberOnInverse,
            enabled = canUninstall,
        )
    }
}
