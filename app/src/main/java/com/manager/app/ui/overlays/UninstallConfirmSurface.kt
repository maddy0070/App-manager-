package com.manager.app.ui.overlays

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.Txt
import com.manager.app.util.Format

/**
 * The confirmation before Android's confirmation.
 *
 * Manager asks once, for the whole batch, showing exactly which apps and how much space comes
 * back — and states plainly that Android will still ask again for each one. That last sentence
 * matters: a bulk uninstall on Android is a queue of system prompts, and the interface should
 * set that expectation instead of letting it arrive as a surprise.
 */
@Composable
fun UninstallConfirmSurface(
    entries: List<AppEntry>,
    graph: ManagerGraph,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ManagerTheme.colors
    var rendered by remember { mutableStateOf(entries) }
    LaunchedEffect(entries) { if (entries.isNotEmpty()) rendered = entries }

    val visible = entries.isNotEmpty()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val removable = rendered.filter { it.isUninstallable }
    val blocked = rendered.size - removable.size
    val reclaim = removable.sumOf { it.totalBytes }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = if (colors.isLight) 0.38f else 0.6f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.84f, stiffness = 420f)) { it / 2 } +
                fadeIn(tween(180)) +
                scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f)),
            exit = slideOutVertically(tween(220, easing = ManagerTheme.motion.exit)) { it / 3 } + fadeOut(tween(160)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .shadow(
                        elevation = 32.dp,
                        shape = ManagerTheme.shapes.sheet,
                        clip = false,
                        ambientColor = colors.ink.copy(alpha = 0.5f),
                        spotColor = colors.ink.copy(alpha = 0.36f),
                    )
                    .clip(ManagerTheme.shapes.sheet)
                    .background(colors.surface),
            ) {
                Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(SquircleShape(15.dp, 0.8f))
                                .background(colors.emberSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            ManagerIcon(ManagerIcons.Trash, null, tint = colors.ember, size = 20.dp)
                        }
                        Spacer(Modifier.width(15.dp))
                        Column(Modifier.weight(1f)) {
                            Txt(
                                if (removable.size == 1) {
                                    "Uninstall ${removable.firstOrNull()?.label ?: "app"}?"
                                } else {
                                    "Uninstall ${removable.size} apps?"
                                },
                                style = ManagerTheme.type.titleL,
                                color = colors.ink,
                                maxLines = 2,
                            )
                            Spacer(Modifier.height(4.dp))
                            Txt(
                                "Frees about ${Format.bytes(reclaim)}",
                                style = ManagerTheme.type.metaS,
                                color = colors.inkTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Txt(
                        if (removable.size == 1) {
                            "Android will ask you to confirm this uninstall on its own screen. App data goes with it."
                        } else {
                            "Android confirms each uninstall on its own screen, so expect ${removable.size} prompts. " +
                                "App data goes with each one."
                        },
                        style = ManagerTheme.type.bodyS,
                        color = colors.inkSecondary,
                    )
                    if (blocked > 0) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ManagerIcon(ManagerIcons.Info, null, tint = colors.inkTertiary, size = 13.dp)
                            Spacer(Modifier.width(7.dp))
                            Txt(
                                "$blocked system ${if (blocked == 1) "package is" else "packages are"} skipped — " +
                                    "Android does not allow their removal.",
                                style = ManagerTheme.type.metaS,
                                color = colors.inkTertiary,
                            )
                        }
                    }
                }

                if (removable.size > 1) {
                    Spacer(Modifier.height(20.dp))
                    Hairline(Modifier.padding(horizontal = 26.dp))
                    Column(
                        Modifier
                            .heightIn(max = 236.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        removable.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 26.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(entry.packageName, entry.label, graph.icons, size = 30.dp)
                                Spacer(Modifier.width(12.dp))
                                Txt(
                                    entry.label,
                                    style = ManagerTheme.type.bodyS,
                                    color = colors.ink,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(Modifier.width(10.dp))
                                Txt(
                                    Format.bytes(entry.totalBytes),
                                    style = ManagerTheme.type.metaS,
                                    color = colors.inkTertiary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Hairline()
                ActionRow(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp + bottomInset),
                    spacing = 10.dp,
                ) {
                    ManagerButton(
                        "Keep",
                        onDismiss,
                        tone = ButtonTone.Outline,
                        modifier = Modifier.weight(1f),
                    )
                    ManagerButton(
                        if (removable.size == 1) "Uninstall" else "Uninstall ${removable.size}",
                        onConfirm,
                        tone = ButtonTone.Destructive,
                        icon = ManagerIcons.Trash,
                        enabled = removable.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
