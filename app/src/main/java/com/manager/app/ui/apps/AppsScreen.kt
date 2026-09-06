package com.manager.app.ui.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppFilter
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.AppRowSkeleton
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.EmptyState
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.ManagerSearchField
import com.manager.app.design.components.ProgressRail
import com.manager.app.ui.DetailRequest
import com.manager.app.ui.ManagerViewModel
import com.manager.app.ui.common.PullToRefresh
import com.manager.app.ui.common.ScreenHeader
import com.manager.app.util.Format

/**
 * The complete inventory.
 *
 * Header, search, filter rail, list. The header collapses out of the way as you scroll so the
 * search field and filters stay reachable while four hundred rows go past — the controls you
 * need while scanning stay; the title you have already read leaves.
 */
@Composable
fun AppsScreen(viewModel: ManagerViewModel, graph: ManagerGraph) {
    val colors = ManagerTheme.colors
    val inventory by viewModel.inventory.collectAsState()
    val browse by viewModel.browse.collectAsState()
    val apps by viewModel.visibleApps.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val usageAccess by viewModel.usageAccess.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val preferences by viewModel.preferences.collectAsState()

    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val gutter = ManagerTheme.space.gutter
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // The title retires once the list is genuinely moving, not on the first pixel.
    val titleVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 60 }
    }

    val counts = remember(inventory.apps, usage) {
        mapOf(
            AppFilter.All to inventory.apps.size,
            AppFilter.User to inventory.apps.count { !it.isSystem },
            AppFilter.System to inventory.apps.count { it.isSystem },
        )
    }

    // Dismissing the keyboard on scroll keeps the list usable one-handed after a search.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) keyboard?.hide()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset + 22.dp)
                    .padding(horizontal = gutter),
            ) {
                AnimatedVisibility(
                    visible = titleVisible,
                    enter = fadeIn(tween(220)) + androidx.compose.animation.expandVertically(
                        tween(280, easing = ManagerTheme.motion.emphasized),
                    ),
                    exit = fadeOut(tween(140)) + androidx.compose.animation.shrinkVertically(
                        tween(220, easing = ManagerTheme.motion.exit),
                    ),
                ) {
                    Column {
                        ScreenHeader(
                            eyebrow = "Installed",
                            title = "Apps",
                            subtitle = subtitleFor(inventory.apps.size, apps.size, browse.filter, browse.query),
                            trailing = {
                                ActionRow {
                                    ManagerIconButton(
                                        icon = ManagerIcons.Refresh,
                                        contentDescription = "Rescan device",
                                        onClick = viewModel::refresh,
                                        container = colors.surface,
                                        pressedContainer = colors.canvasSunken,
                                    )
                                    ManagerIconButton(
                                        icon = ManagerIcons.Settings,
                                        contentDescription = "Settings",
                                        onClick = viewModel::openSettings,
                                        container = colors.surface,
                                        pressedContainer = colors.canvasSunken,
                                    )
                                }
                            },
                        )
                        Spacer(Modifier.height(22.dp))
                    }
                }

                ManagerSearchField(
                    query = browse.query,
                    onQueryChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))

                FilterBar(
                    filter = browse.filter,
                    sortKey = browse.sortKey,
                    sortDirection = browse.sortDirection,
                    counts = counts,
                    usageAvailable = usageAccess,
                    onFilter = viewModel::setFilter,
                    onSort = { viewModel.setSort(it, browse.sortDirection) },
                    onToggleDirection = viewModel::toggleSortDirection,
                )
                Spacer(Modifier.height(14.dp))

                // Storage measurement is a background pass; show it only while it is happening.
                AnimatedVisibility(
                    visible = inventory.detailProgress in 0.001f..0.999f && inventory.hasApps,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(300)),
                ) {
                    Column {
                        ProgressRail(inventory.detailProgress, height = 3.dp)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            PullToRefresh(
                refreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    inventory.loading && !inventory.hasApps -> LoadingList(gutter, bottomInset)

                    apps.isEmpty() -> AppsEmptyState(
                        query = browse.query,
                        filter = browse.filter,
                        usageAccess = usageAccess,
                        onClearSearch = { viewModel.setQuery("") },
                        onResetFilter = { viewModel.setFilter(AppFilter.All) },
                        onGrantUsage = viewModel::requestUsageAccess,
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = gutter - 12.dp,
                            end = gutter - 12.dp,
                            top = 2.dp,
                            bottom = bottomInset + 116.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(apps, key = { it.packageName }) { entry ->
                            AppRow(
                                entry = entry,
                                graph = graph,
                                selected = entry.packageName in browse.selection,
                                selectionMode = browse.selectionMode,
                                usageMs = usage.byPackage[entry.packageName]?.foregroundMs,
                                sortKey = browse.sortKey,
                                hapticsEnabled = preferences.hapticsEnabled,
                                onOpen = { bounds -> viewModel.openDetail(DetailRequest(entry, bounds)) },
                                onToggleSelect = { viewModel.toggleSelection(entry.packageName) },
                                onBeginSelection = { viewModel.beginSelection(entry.packageName) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun subtitleFor(total: Int, visible: Int, filter: AppFilter, query: String): String = when {
    query.isNotEmpty() -> "${Format.count(visible, "match", "matches")} for “$query”"
    filter != AppFilter.All -> "${Format.count(visible, "app")} · ${filter.label.lowercase()}"
    else -> "${Format.count(total, "package")} on this device"
}

@Composable
private fun LoadingList(gutter: androidx.compose.ui.unit.Dp, bottomInset: androidx.compose.ui.unit.Dp) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = gutter)
            .padding(bottom = bottomInset),
    ) {
        repeat(9) { AppRowSkeleton() }
    }
}

/**
 * Three different empty states, because there are three different reasons to be here — and each
 * one has a different way out.
 */
@Composable
private fun AppsEmptyState(
    query: String,
    filter: AppFilter,
    usageAccess: Boolean,
    onClearSearch: () -> Unit,
    onResetFilter: () -> Unit,
    onGrantUsage: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            query.isNotEmpty() -> EmptyState(
                title = "Nothing matches “$query”",
                body = "Manager searches app names and package names. Try a shorter fragment, or the " +
                    "developer's domain — most package names start with one.",
                icon = ManagerIcons.Search,
                actionLabel = "Clear search",
                onAction = onClearSearch,
            )

            filter == AppFilter.Dormant && !usageAccess -> EmptyState(
                title = "This filter needs usage access",
                body = "Android only reports when an app was last opened to apps you have granted " +
                    "Usage access. Without it, Manager cannot tell dormant apps from busy ones.",
                icon = ManagerIcons.Clock,
                actionLabel = "Open Usage access",
                onAction = onGrantUsage,
                tone = ButtonTone.Signal,
            )

            filter == AppFilter.Dormant -> EmptyState(
                title = "Nothing is gathering dust",
                body = "Every app you can open has been used in the last three weeks. That is a " +
                    "tidier phone than most.",
                icon = ManagerIcons.Check,
                actionLabel = "Show all apps",
                onAction = onResetFilter,
            )

            else -> EmptyState(
                title = "No apps in “${filter.label}”",
                body = "Nothing on this device fits that filter right now.",
                icon = ManagerIcons.Grid,
                actionLabel = "Show all apps",
                onAction = onResetFilter,
            )
        }
    }
}
