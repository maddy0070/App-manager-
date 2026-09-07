package com.manager.app.ui.apps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.data.SortKey
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.SelectionMark
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse
import com.manager.app.util.Format

/**
 * One app in the list.
 *
 * Three lines of information, no more: what it is, its version and age, and its size. Everything
 * else waits in the detail surface. The row carries only what you need to decide whether to look
 * closer, which is what lets a list of four hundred apps stay scannable.
 *
 * Selection is not a checkbox appearing in a gap. The whole row inverts to a tinted surface, the
 * icon steps aside for a filled mark, and the size column holds its position — so a selected row
 * is obvious peripherally, and the list never reflows as selections change.
 */
@Composable
fun AppRow(
    entry: AppEntry,
    graph: ManagerGraph,
    selected: Boolean,
    selectionMode: Boolean,
    usageMs: Long?,
    sortKey: SortKey,
    onOpen: (Rect?) -> Unit,
    onToggleSelect: () -> Unit,
    onBeginSelection: () -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    var iconBounds by remember { mutableStateOf<Rect?>(null) }

    val container by animateColorAsState(
        targetValue = if (selected) colors.signalSoft else Color.Transparent,
        animationSpec = tween(220, easing = ManagerTheme.motion.standardEase),
        label = "rowContainer",
    )
    val iconShift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 520f),
        label = "rowIconShift",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clip(ManagerTheme.shapes.md)
            .background(container)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = when {
                    !selectionMode -> "Open details"
                    selected -> "Deselect"
                    else -> "Select"
                },
                onLongClickLabel = if (selectionMode) null else "Select",
                onLongClick = {
                    if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (selectionMode) onToggleSelect() else onBeginSelection()
                },
                onClick = {
                    if (selectionMode) onToggleSelect() else onOpen(iconBounds)
                },
            )
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .semantics {
                contentDescription = buildString {
                    append(entry.label)
                    append(", ")
                    append(if (entry.isSystem) "system app" else "user app")
                    if (!entry.isEnabled) append(", disabled")
                    append(", ")
                    append(Format.bytes(entry.totalBytes))
                    if (entry.isSplit) append(", ${entry.splitCount + 1} APK parts")
                }
                // Selection is state, not part of the name. Announced this way it is also
                // re-announced when it changes, which a name never is.
                if (selectionMode) {
                    this.selected = selected
                    stateDescription = if (selected) "Selected" else "Not selected"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            AppIcon(
                packageName = entry.packageName,
                label = entry.label,
                loader = graph.icons,
                size = 46.dp,
                modifier = Modifier
                    .onGloballyPositioned { iconBounds = it.boundsInRoot() }
                    .graphicsLayer {
                        val fade = 1f - iconShift
                        alpha = fade
                        scaleX = 0.84f + 0.16f * fade
                        scaleY = 0.84f + 0.16f * fade
                    },
            )
            if (iconShift > 0.01f) {
                SelectionMark(
                    selected = selected,
                    size = 26.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = iconShift
                        scaleX = 0.7f + 0.3f * iconShift
                        scaleY = 0.7f + 0.3f * iconShift
                    },
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    entry.label,
                    style = ManagerTheme.type.strong,
                    color = colors.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.isSystem) {
                    Spacer(Modifier.width(7.dp))
                    SystemBadge()
                }
                if (!entry.isEnabled) {
                    Spacer(Modifier.width(6.dp))
                    Txt("DISABLED", style = ManagerTheme.type.eyebrow, color = colors.ember, maxLines = 1)
                }
            }
            Spacer(Modifier.height(4.dp))
            Txt(
                secondaryLine(entry, usageMs, sortKey),
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            val (value, unit) = Format.bytesParts(entry.totalBytes)
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(value, style = ManagerTheme.type.numeric, color = colors.ink, maxLines = 1)
                Spacer(Modifier.width(2.dp))
                Txt(unit, style = ManagerTheme.type.metaS, color = colors.inkTertiary, maxLines = 1)
            }
            if (entry.isSplit) {
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManagerIcon(ManagerIcons.Split, null, tint = colors.inkTertiary, size = 11.dp)
                    Spacer(Modifier.width(3.dp))
                    Txt(
                        "${entry.splitCount + 1}",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * The metadata line follows the sort.
 *
 * If you have just sorted by install date, the date you sorted by is the one worth reading; if you
 * sorted by usage, the time is. Showing a fixed field would force you to open every row to check
 * the very thing you asked the list to order itself by.
 */
private fun secondaryLine(entry: AppEntry, usageMs: Long?, sortKey: SortKey): String {
    val version = entry.versionName ?: "v${entry.versionCode}"
    val detail = when (sortKey) {
        SortKey.Installed -> "Installed ${Format.relativeShort(entry.installedAt)}"
        SortKey.Usage -> if (usageMs != null && usageMs > 0) {
            "${Format.duration(usageMs)} used"
        } else {
            "No recorded use"
        }
        SortKey.Size -> if (entry.storage != null) "Measured" else "APK size on disk"
        else -> "Updated ${Format.relativeShort(entry.updatedAt)}"
    }
    return "$version · $detail"
}

/** System apps are marked with a glyph as well as a tint, never colour alone. */
@Composable
fun SystemBadge(modifier: Modifier = Modifier) {
    val colors = ManagerTheme.colors
    Row(
        modifier = modifier
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.canvasSunken)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ManagerIcon(ManagerIcons.System, null, tint = colors.inkSecondary, size = 10.dp)
        Txt("SYSTEM", style = ManagerTheme.type.eyebrow, color = colors.inkSecondary, maxLines = 1)
    }
}
