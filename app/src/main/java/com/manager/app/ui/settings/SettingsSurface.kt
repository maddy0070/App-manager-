package com.manager.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.manager.app.data.ThemeMode
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.ManagerMark
import com.manager.app.design.components.ManagerSwitch
import com.manager.app.design.components.SegmentedControl
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse
import com.manager.app.ui.ManagerViewModel

/**
 * Settings.
 *
 * Deliberately short. This product has almost nothing to configure, and a long preference screen
 * would be a confession that the defaults are wrong. Appearance, one interaction toggle, the
 * permission state, and an honest note about what the app can and cannot do.
 */
@Composable
fun SettingsSurface(
    visible: Boolean,
    viewModel: ManagerViewModel,
    onDismiss: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val preferences by viewModel.preferences.collectAsState()
    val usageAccess by viewModel.usageAccess.collectAsState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = if (colors.isLight) 0.34f else 0.56f))
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
            enter = slideInVertically(spring(dampingRatio = 0.85f, stiffness = 400f)) { it / 2 } +
                fadeIn(tween(200)) +
                scaleIn(initialScale = 0.95f, animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)),
            exit = slideOutVertically(tween(230, easing = ManagerTheme.motion.exit)) { it / 3 } +
                fadeOut(tween(170)) + scaleOut(targetScale = 0.96f),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .shadow(
                        elevation = 32.dp,
                        shape = ManagerTheme.shapes.sheet,
                        clip = false,
                        ambientColor = colors.ink.copy(alpha = 0.48f),
                        spotColor = colors.ink.copy(alpha = 0.34f),
                    )
                    .clip(ManagerTheme.shapes.sheet)
                    .background(colors.surface)
                    .padding(bottom = bottomInset),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 14.dp, top = 24.dp, bottom = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ManagerMark(size = 26.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Txt("Manager", style = ManagerTheme.type.titleL, color = colors.ink)
                        Spacer(Modifier.height(3.dp))
                        Txt(
                            "Personal build · v1.0",
                            style = ManagerTheme.type.metaS,
                            color = colors.inkTertiary,
                        )
                    }
                    ManagerIconButton(
                        icon = ManagerIcons.Close,
                        contentDescription = "Close settings",
                        onClick = onDismiss,
                        container = colors.canvasSunken,
                        pressedContainer = colors.hairlineStrong,
                        tint = colors.inkSecondary,
                        size = 40.dp,
                        iconSize = 16.dp,
                    )
                }

                Hairline()

                Column(Modifier.padding(horizontal = 26.dp, vertical = 20.dp)) {
                    Txt("APPEARANCE", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
                    Spacer(Modifier.height(14.dp))
                    SegmentedControl(
                        options = ThemeMode.entries,
                        selected = preferences.themeMode,
                        onSelect = viewModel::setThemeMode,
                        label = { it.label },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(9.dp))
                    Txt(
                        "Manager is designed for light. Dark mode is fully supported but secondary.",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                    )

                    Spacer(Modifier.height(26.dp))
                    Hairline()
                    Spacer(Modifier.height(6.dp))

                    ToggleRow(
                        title = "Haptics on selection",
                        detail = "A short tick when a long press enters selection mode.",
                        checked = preferences.hapticsEnabled,
                        onChange = viewModel::setHaptics,
                    )

                    Spacer(Modifier.height(6.dp))
                    Hairline()
                    Spacer(Modifier.height(20.dp))

                    Txt("PERMISSIONS", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
                    Spacer(Modifier.height(14.dp))
                    PermissionRow(
                        granted = usageAccess,
                        onOpen = viewModel::requestUsageAccess,
                    )

                    Spacer(Modifier.height(24.dp))
                    Txt(
                        "Manager reads what Android exposes and never sends anything off the device. " +
                            "Uninstalls always go through Android's own confirmation, and extraction only " +
                            "copies APK files the system already lets apps read.",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.99f)
            .clip(ManagerTheme.shapes.sm)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(title, style = ManagerTheme.type.strong, color = colors.ink)
            Spacer(Modifier.height(3.dp))
            Txt(detail, style = ManagerTheme.type.metaS, color = colors.inkTertiary)
        }
        Spacer(Modifier.width(16.dp))
        ManagerSwitch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PermissionRow(granted: Boolean, onOpen: () -> Unit) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clip(ManagerTheme.shapes.sm)
            .background(if (granted) colors.signalSoft else colors.canvasSunken)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerIcon(
            if (granted) ManagerIcons.Check else ManagerIcons.Alert,
            null,
            tint = if (granted) colors.signal else colors.inkSecondary,
            size = 17.dp,
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Txt("Usage access", style = ManagerTheme.type.strong, color = colors.ink)
            Spacer(Modifier.height(3.dp))
            Txt(
                if (granted) {
                    "Granted — screen time and exact sizes are live."
                } else {
                    "Off — sizes fall back to APK size and usage is unavailable."
                },
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
            )
        }
        Spacer(Modifier.width(10.dp))
        ManagerIcon(ManagerIcons.ArrowUpRight, null, tint = colors.inkTertiary, size = 15.dp)
    }
}
