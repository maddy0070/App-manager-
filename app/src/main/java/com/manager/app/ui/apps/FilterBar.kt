package com.manager.app.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manager.app.data.AppFilter
import com.manager.app.data.SortDirection
import com.manager.app.data.SortKey
import com.manager.app.design.CapsuleShape
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.ManagerChip
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.Panel
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse

/**
 * Filtering and sorting, without a dropdown anywhere.
 *
 * Filters live on a single scrolling rail so the common ones are one tap away and none of them
 * costs vertical space. Sorting hides behind one control that expands the rail into a panel in
 * place — the row does not jump, a menu does not fly in over the content, and the current sort is
 * always legible on the closed control.
 */
@Composable
fun FilterBar(
    filter: AppFilter,
    sortKey: SortKey,
    sortDirection: SortDirection,
    counts: Map<AppFilter, Int>,
    usageAvailable: Boolean,
    onFilter: (AppFilter) -> Unit,
    onSort: (SortKey) -> Unit,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = ManagerTheme.colors

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppFilter.entries.forEach { candidate ->
                    val enabled = candidate != AppFilter.Dormant || usageAvailable
                    ManagerChip(
                        label = candidate.label,
                        selected = filter == candidate,
                        enabled = enabled,
                        trailing = counts[candidate]?.takeIf { filter == candidate }?.toString(),
                        onClick = { onFilter(candidate) },
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(10.dp))
            SortToggle(
                expanded = expanded,
                sortKey = sortKey,
                onClick = { expanded = !expanded },
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) +
                fadeIn(tween(200, delayMillis = 60)),
            exit = shrinkVertically(tween(220, easing = ManagerTheme.motion.exit)) + fadeOut(tween(120)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.fillMaxWidth(), shape = ManagerTheme.shapes.md) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Txt("SORT BY", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
                            Spacer(Modifier.weight(1f))
                            DirectionToggle(sortDirection, onToggleDirection)
                        }
                        Hairline(Modifier.padding(horizontal = 18.dp))
                        SortKey.entries.forEach { key ->
                            val enabled = key != SortKey.Usage || usageAvailable
                            SortOption(
                                key = key,
                                selected = sortKey == key,
                                enabled = enabled,
                                direction = sortDirection,
                                onClick = { onSort(key) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortToggle(expanded: Boolean, sortKey: SortKey, onClick: () -> Unit) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "sortChevron",
    )
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (expanded) colors.signalSoft else colors.surface,
        animationSpec = tween(200),
        label = "sortContainer",
    )
    Row(
        modifier = Modifier
            .pressResponse(interaction, pressedScale = 0.94f)
            .clip(ManagerTheme.shapes.capsule)
            .background(container)
            .outlined(colors.hairline, expanded)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ManagerIcon(ManagerIcons.Sort, null, tint = if (expanded) colors.signal else colors.inkSecondary, size = 15.dp)
        Txt(
            sortKey.label,
            style = ManagerTheme.type.labelS,
            color = if (expanded) colors.signal else colors.inkSecondary,
            maxLines = 1,
        )
        ManagerIcon(
            ManagerIcons.ChevronDown,
            null,
            tint = if (expanded) colors.signal else colors.inkTertiary,
            size = 13.dp,
            modifier = Modifier.rotate(rotation),
        )
    }
}

private fun Modifier.outlined(color: Color, expanded: Boolean) =
    if (expanded) this else border(1.dp, color, CapsuleShape)

@Composable
private fun DirectionToggle(direction: SortDirection, onToggle: () -> Unit) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val ascending = direction == SortDirection.Ascending
    val rotation by animateFloatAsState(
        targetValue = if (ascending) 0f else 180f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 400f),
        label = "directionFlip",
    )
    Row(
        modifier = Modifier
            .pressResponse(interaction, pressedScale = 0.92f)
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.canvasSunken)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onToggle)
            .defaultMinSize(minHeight = 44.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ManagerIcon(
            ManagerIcons.ChevronDown,
            null,
            tint = colors.inkSecondary,
            size = 12.dp,
            modifier = Modifier.rotate(rotation + 180f),
        )
        Txt(
            if (ascending) "Ascending" else "Descending",
            style = ManagerTheme.type.metaS,
            color = colors.inkSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun SortOption(
    key: SortKey,
    selected: Boolean,
    enabled: Boolean,
    direction: SortDirection,
    onClick: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val markScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 800f),
        label = "sortMark",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressResponse(interaction, enabled, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            key.label,
            style = ManagerTheme.type.body,
            color = when {
                !enabled -> colors.inkDisabled
                selected -> colors.ink
                else -> colors.inkSecondary
            },
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
        if (!enabled) {
            Txt("Needs usage access", style = ManagerTheme.type.metaS, color = colors.inkDisabled, maxLines = 1)
        } else if (selected) {
            Txt(
                if (direction == SortDirection.Ascending) hintAscending(key) else hintDescending(key),
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (markScale > 0.02f) {
                ManagerIcon(
                    ManagerIcons.Check,
                    null,
                    tint = colors.signal,
                    size = 16.dp,
                    modifier = Modifier.scale(markScale),
                )
            }
        }
    }
}

/** Plain-language hints, so nobody has to guess what "ascending" means for a date. */
private fun hintAscending(key: SortKey) = when (key) {
    SortKey.Name -> "A to Z"
    SortKey.Size -> "Smallest first"
    SortKey.Installed -> "Oldest first"
    SortKey.Updated -> "Oldest first"
    SortKey.Usage -> "Least used first"
}

private fun hintDescending(key: SortKey) = when (key) {
    SortKey.Name -> "Z to A"
    SortKey.Size -> "Largest first"
    SortKey.Installed -> "Newest first"
    SortKey.Updated -> "Newest first"
    SortKey.Usage -> "Most used first"
}
