package com.manager.app.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.data.UsageWindow
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.AppRowSkeleton
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.EmptyState
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.RankBar
import com.manager.app.design.components.SegmentedControl
import com.manager.app.design.components.Txt
import com.manager.app.design.components.pressResponse
import com.manager.app.ui.DetailRequest
import com.manager.app.ui.ManagerViewModel
import com.manager.app.ui.common.PullToRefresh
import com.manager.app.ui.common.ScreenHeader
import com.manager.app.util.Format

/**
 * Usage analytics.
 *
 * One ranked list, ordered by time in the foreground, over a range the user picks. The bar under
 * each name is scaled against the leader rather than the total, because what a person wants to
 * see is how the rest compare to their top app — not five slivers next to one wall.
 *
 * Ranges longer than a week are labelled approximate, because Android genuinely stops keeping
 * daily buckets after that and aggregates into coarser ones.
 */
@Composable
fun UsageScreen(viewModel: ManagerViewModel, graph: ManagerGraph) {
    val colors = ManagerTheme.colors
    val usage by viewModel.usage.collectAsState()
    val usageAccess by viewModel.usageAccess.collectAsState()
    val inventory by viewModel.inventory.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val listState = rememberLazyListState()

    val gutter = ManagerTheme.space.gutter
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val ranked = remember(usage, inventory.apps) {
        val byPackage = inventory.apps.associateBy { it.packageName }
        usage.byPackage.values
            .asSequence()
            .filter { it.foregroundMs > 0 }
            .sortedByDescending { it.foregroundMs }
            .mapNotNull { record -> byPackage[record.packageName]?.let { it to record } }
            .toList()
    }
    val totalMs = ranked.sumOf { it.second.foregroundMs }
    val leader = ranked.firstOrNull()?.second?.foregroundMs ?: 1L

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
                ScreenHeader(
                    eyebrow = "Screen time",
                    title = "Usage",
                    subtitle = if (usageAccess) {
                        "${Format.duration(totalMs)} across ${Format.count(ranked.size, "app")}"
                    } else {
                        "Needs usage access"
                    },
                    trailing = {
                        ActionRow {
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
                Spacer(Modifier.height(20.dp))

                SegmentedControl(
                    options = UsageWindow.entries,
                    selected = usage.window,
                    onSelect = viewModel::setUsageWindow,
                    label = { it.shortLabel },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Txt(
                        usage.window.label,
                        style = ManagerTheme.type.metaS,
                        color = colors.inkSecondary,
                    )
                    if (usage.approximate && usageAccess) {
                        Txt(
                            "Approximate — Android aggregates beyond a week",
                            style = ManagerTheme.type.metaS,
                            color = colors.inkTertiary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            PullToRefresh(
                refreshing = refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    !usageAccess -> UsagePermissionState(onGrant = viewModel::requestUsageAccess)

                    inventory.loading && !inventory.hasApps -> Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = gutter),
                    ) { repeat(8) { AppRowSkeleton() } }

                    ranked.isEmpty() -> EmptyState(
                        title = "Nothing recorded yet",
                        body = "Android has no foreground time for this range. Usage access was probably " +
                            "granted very recently — check back after using the phone for a while.",
                        icon = ManagerIcons.Clock,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = gutter - 10.dp,
                            end = gutter - 10.dp,
                            top = 2.dp,
                            bottom = bottomInset + 116.dp,
                        ),
                    ) {
                        itemsIndexed(ranked, key = { _, item -> item.first.packageName }) { index, (entry, record) ->
                            UsageRow(
                                rank = index,
                                entry = entry,
                                foregroundMs = record.foregroundMs,
                                lastUsed = record.lastUsed,
                                share = if (totalMs == 0L) 0f else record.foregroundMs.toFloat() / totalMs,
                                leaderMs = leader,
                                graph = graph,
                                onOpen = { bounds -> viewModel.openDetail(DetailRequest(entry, bounds)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageRow(
    rank: Int,
    entry: AppEntry,
    foregroundMs: Long,
    lastUsed: Long,
    share: Float,
    leaderMs: Long,
    graph: ManagerGraph,
    onOpen: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<Rect?>(null) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clickable(interactionSource = interaction, indication = null) { onOpen(bounds) }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            Format.rank(rank),
            style = ManagerTheme.type.numericS,
            color = if (rank < 3) colors.signal else colors.inkTertiary,
            modifier = Modifier.width(26.dp),
        )
        AppIcon(
            packageName = entry.packageName,
            label = entry.label,
            loader = graph.icons,
            size = 42.dp,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(entry.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            RankBar(
                fraction = foregroundMs.toFloat() / leaderMs,
                rank = rank,
                height = 5.dp,
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                "${Format.percent(share)} of tracked time · last opened ${Format.relativeShort(lastUsed)}",
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Txt(
            Format.duration(foregroundMs),
            style = ManagerTheme.type.titleM,
            color = colors.ink,
            maxLines = 1,
        )
    }
}

/**
 * The permission state, given the same care as the content it replaces — it explains what Android
 * withholds and why, then offers exactly one way forward.
 */
@Composable
private fun UsagePermissionState(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            title = "Usage access is off",
            body = "Screen time lives behind a permission that only you can grant, in Android's " +
                "Settings. Manager reads it, ranks your apps, and never sends it anywhere.",
            icon = ManagerIcons.Clock,
            actionLabel = "Open Usage access",
            onAction = onGrant,
            tone = ButtonTone.Signal,
        )
    }
}
