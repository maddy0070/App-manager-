package com.manager.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.design.ManagerTheme
import com.manager.app.ui.apps.AppsScreen
import com.manager.app.ui.common.NavigationRail
import com.manager.app.ui.common.NoticeHost
import com.manager.app.ui.common.SelectionBar
import com.manager.app.ui.dashboard.DashboardScreen
import com.manager.app.ui.detail.AppDetailSurface
import com.manager.app.ui.onboarding.OnboardingScreen
import com.manager.app.ui.overlays.ExtractionSurface
import com.manager.app.ui.overlays.UninstallConfirmSurface
import com.manager.app.ui.settings.SettingsSurface
import com.manager.app.ui.usage.UsageScreen

/**
 * The whole product in one composition.
 *
 * Screens are not routes: this app is three peers and a set of floating surfaces, so a navigation
 * graph would only get in the way of the motion. Overlays are stacked here in z-order, which is
 * what lets the detail surface morph out of a list row rather than replace the screen.
 */
@Composable
fun AppRoot(viewModel: ManagerViewModel, graph: ManagerGraph) {
    val colors = ManagerTheme.colors
    val preferences by viewModel.preferences.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val browse by viewModel.browse.collectAsState()
    val detail by viewModel.detail.collectAsState()
    val extraction by viewModel.extraction.collectAsState()
    val settingsOpen by viewModel.settingsOpen.collectAsState()

    val pendingUninstall by viewModel.uninstallConfirm.collectAsState()
    val notices = remember { mutableStateListOf<Notice>() }
    val emphasized = ManagerTheme.motion.emphasized

    LaunchedEffect(Unit) {
        viewModel.notices.collect { notices.add(it) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        // Until DataStore answers, "onboarded" is unknown rather than false — so the root paints
        // bare canvas, which is exactly what the splash is already showing. Nothing flashes.
        AnimatedContent(
            targetState = if (preferences.loaded) preferences.onboarded else null,
            transitionSpec = {
                // Onboarding does not slide away — it dissolves upward as the dashboard settles
                // in from slightly below, so the two never look like separate screens.
                (
                    fadeIn(tween(420, delayMillis = 90, easing = emphasized)) +
                        scaleIn(initialScale = 0.97f, animationSpec = tween(520, delayMillis = 90, easing = emphasized))
                    ) togetherWith (
                    fadeOut(tween(240)) + scaleOut(targetScale = 1.04f, animationSpec = tween(420, easing = emphasized))
                    )
            },
            label = "rootPhase",
        ) { onboarded ->
            when (onboarded) {
                null -> Box(Modifier.fillMaxSize().background(colors.canvas))
                // Onboarding runs on sample data alone — it needs nothing from the view model
                // but the one call that ends it.
                false -> OnboardingScreen(onFinish = { viewModel.completeOnboarding() })
                true -> MainShell(viewModel = viewModel, graph = graph)
            }
        }

        // ---- Floating surfaces, in z-order --------------------------------------------------

        AppDetailSurface(
            request = detail,
            viewModel = viewModel,
            graph = graph,
            onDismiss = viewModel::closeDetail,
            onExtract = {
                viewModel.closeDetail()
                viewModel.extract(listOf(it))
            },
            onUninstall = {
                viewModel.closeDetail()
                viewModel.requestUninstallConfirmation(listOf(it))
            },
        )

        SettingsSurface(
            visible = settingsOpen,
            viewModel = viewModel,
            onDismiss = viewModel::closeSettings,
        )

        UninstallConfirmSurface(
            entries = pendingUninstall,
            graph = graph,
            onConfirm = viewModel::confirmUninstall,
            onDismiss = viewModel::dismissUninstallConfirmation,
        )

        ExtractionSurface(
            state = extraction,
            onDismiss = viewModel::dismissExtraction,
            onCancel = viewModel::cancelExtraction,
            onShare = viewModel::share,
            onOpen = viewModel::openExtracted,
        )

        NoticeHost(
            notice = notices.firstOrNull(),
            onDismiss = { if (notices.isNotEmpty()) notices.removeAt(0) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 18.dp)
                .padding(bottom = 96.dp),
        )
    }

    // ---- Back handling ----------------------------------------------------------------------

    BackHandler(enabled = detail != null) { viewModel.closeDetail() }
    BackHandler(enabled = detail == null && settingsOpen) { viewModel.closeSettings() }
    BackHandler(enabled = detail == null && !settingsOpen && pendingUninstall.isNotEmpty()) {
        viewModel.dismissUninstallConfirmation()
    }
    BackHandler(enabled = detail == null && !settingsOpen && pendingUninstall.isEmpty() && extraction != null) {
        if (extraction?.finished == true) viewModel.dismissExtraction()
    }
    BackHandler(
        enabled = detail == null && !settingsOpen && pendingUninstall.isEmpty() &&
            extraction == null && browse.selectionMode,
    ) { viewModel.exitSelection() }
    BackHandler(
        enabled = detail == null && !settingsOpen && pendingUninstall.isEmpty() &&
            extraction == null && !browse.selectionMode && destination != Destination.Dashboard,
    ) { viewModel.navigate(Destination.Dashboard) }
}

/**
 * The three peer screens plus the bar that switches between them.
 *
 * Screen changes move laterally in the direction of travel and scale a hair, which is enough to
 * say "sideways, same level" without a full slide that would make three tabs feel like a stack.
 */
@Composable
private fun MainShell(viewModel: ManagerViewModel, graph: ManagerGraph) {
    val destination by viewModel.destination.collectAsState()
    val browse by viewModel.browse.collectAsState()
    val visible by viewModel.visibleApps.collectAsState()
    val density = LocalDensity.current
    val emphasized = ManagerTheme.motion.emphasized
    val exitEase = ManagerTheme.motion.exit
    val shift = with(density) { 26.dp.roundToPx() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (
                    fadeIn(tween(300, delayMillis = 60, easing = emphasized)) +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(420, delayMillis = 60, easing = emphasized)) +
                        androidx.compose.animation.slideInHorizontally(
                            animationSpec = tween(420, delayMillis = 60, easing = emphasized),
                        ) { if (forward) shift else -shift }
                    ) togetherWith (
                    fadeOut(tween(180, easing = exitEase)) +
                        scaleOut(targetScale = 0.99f, animationSpec = tween(240)) +
                        androidx.compose.animation.slideOutHorizontally(
                            animationSpec = tween(240, easing = exitEase),
                        ) { if (forward) -shift / 2 else shift / 2 }
                    )
            },
            label = "destination",
        ) { target ->
            when (target) {
                Destination.Dashboard -> DashboardScreen(viewModel, graph)
                Destination.Apps -> AppsScreen(viewModel, graph)
                Destination.Usage -> UsageScreen(viewModel, graph)
            }
        }

        // The bar and the selection bar share one slot and one silhouette.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = !browse.selectionMode,
                enter = fadeIn(tween(200, delayMillis = 60)) + scaleIn(
                    initialScale = 0.88f,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
                ) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 420f)) { it / 3 },
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(180)) +
                    slideOutVertically(tween(180)) { it / 4 },
            ) {
                NavigationRail(current = destination, onSelect = viewModel::navigate)
            }

            AnimatedVisibility(
                visible = browse.selectionMode,
                enter = fadeIn(tween(200, delayMillis = 60)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                ) + slideInVertically(spring(dampingRatio = 0.78f, stiffness = 400f)) { it / 3 },
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180)),
            ) {
                val selected = viewModel.selectedEntries()
                SelectionBar(
                    count = browse.selection.size,
                    allSelected = browse.selection.isNotEmpty() && browse.selection.size >= visible.size,
                    canExtract = selected.isNotEmpty(),
                    canUninstall = selected.any { it.isUninstallable },
                    onSelectAll = viewModel::toggleSelectAllVisible,
                    onExtract = { viewModel.extract(selected) },
                    onUninstall = { viewModel.requestUninstallConfirmation(selected) },
                    onDismiss = viewModel::exitSelection,
                )
            }
        }
    }
}
