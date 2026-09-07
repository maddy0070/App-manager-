package com.manager.app.ui.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.data.AppFilter
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ArcGauge
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.CompositionBar
import com.manager.app.design.components.CountingBytes
import com.manager.app.design.components.LegendSwatch
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.ManagerTextAction
import com.manager.app.design.components.Panel
import com.manager.app.design.components.RankBar
import com.manager.app.design.components.Skeleton
import com.manager.app.design.components.TimelineBars
import com.manager.app.design.components.Txt
import com.manager.app.design.components.VizSegment
import com.manager.app.design.components.pressResponse
import com.manager.app.domain.CacheReport
import com.manager.app.domain.Insights
import com.manager.app.ui.Destination
import com.manager.app.ui.DetailRequest
import com.manager.app.ui.ManagerViewModel
import com.manager.app.ui.common.PullToRefresh
import com.manager.app.ui.common.ScreenHeader
import com.manager.app.ui.common.SectionHeader
import com.manager.app.ui.common.StaggeredEntrance
import com.manager.app.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The dashboard.
 *
 * Not a wall of equal cards: a hero figure, then a sequence of blocks that each answer one
 * question a person actually has about their phone — what is on it, how big is it, what do I
 * actually use, when did it fill up, and what could go. Sections that have nothing honest to say
 * are absent rather than empty.
 */
@Composable
fun DashboardScreen(viewModel: ManagerViewModel, graph: ManagerGraph) {
    val colors = ManagerTheme.colors
    val inventory by viewModel.inventory.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val usageAccess by viewModel.usageAccess.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val cleanup by viewModel.cacheCleanup.collectAsState()
    val listState = rememberLazyListState()

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gutter = ManagerTheme.space.gutter

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = gutter,
                end = gutter,
                top = topInset + 22.dp,
                bottom = bottomInset + 116.dp,
            ),
        ) {
            item(key = "header") {
                ScreenHeader(
                    eyebrow = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) },
                    title = "Overview",
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
                Spacer(Modifier.height(30.dp))
            }

            if (inventory.loading && !inventory.hasApps) {
                item(key = "loading") { DashboardSkeleton() }
                return@LazyColumn
            }

            if (inventory.error != null) {
                item(key = "error") {
                    Panel(Modifier.fillMaxWidth(), color = colors.emberSoft, border = null) {
                        Column(Modifier.padding(22.dp)) {
                            Txt("Scan failed", style = ManagerTheme.type.titleM, color = colors.ink)
                            Spacer(Modifier.height(7.dp))
                            Txt(inventory.error!!, style = ManagerTheme.type.bodyS, color = colors.inkSecondary)
                            Spacer(Modifier.height(16.dp))
                            ManagerButton("Try again", viewModel::refresh, tone = ButtonTone.Quiet, compact = true)
                        }
                    }
                }
                return@LazyColumn
            }

            item(key = "hero") {
                StaggeredEntrance(0) { InventoryHero(insights) }
                Spacer(Modifier.height(ManagerTheme.space.section))
            }

            if (!usageAccess) {
                item(key = "permission") {
                    StaggeredEntrance(1) {
                        UsageAccessPanel(onGrant = viewModel::requestUsageAccess)
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            if (insights.topUsed.isNotEmpty()) {
                item(key = "topUsedHeader") {
                    StaggeredEntrance(2) {
                        SectionHeader(
                            title = "Most used · ${usage.window.label}",
                            trailing = {
                                ManagerTextAction("All usage", { viewModel.navigate(Destination.Usage) })
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        Panel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                insights.topUsed.forEachIndexed { index, rank ->
                                    TopUsedRow(
                                        rank = index,
                                        entry = rank.entry,
                                        durationMs = rank.foregroundMs,
                                        share = rank.share,
                                        leader = insights.topUsed.first().foregroundMs,
                                        graph = graph,
                                        onOpen = { bounds ->
                                            viewModel.openDetail(DetailRequest(rank.entry, bounds))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            item(key = "storage") {
                StaggeredEntrance(3) {
                    StoragePanel(
                        insights = insights,
                        detailProgress = inventory.detailProgress,
                        measured = insights.storageIsMeasured,
                        onEnable = viewModel::requestUsageAccess,
                        usageAccess = usageAccess,
                    )
                }
                Spacer(Modifier.height(ManagerTheme.space.section))
            }

            if (insights.cache.isKnown || usageAccess) {
                item(key = "cache") {
                    StaggeredEntrance(4) {
                        CachePanel(
                            report = insights.cache,
                            busy = cleanup != null,
                            onClear = viewModel::requestCacheCleanup,
                            onOpenAll = viewModel::openCache,
                            graph = graph,
                        )
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            if (insights.recentlyInstalled.isNotEmpty()) {
                item(key = "recent") {
                    StaggeredEntrance(4) {
                        SectionHeader(
                            title = "Recently installed",
                            trailing = {
                                ManagerTextAction(
                                    label = "See all",
                                    onClick = {
                                        viewModel.setFilter(AppFilter.RecentlyInstalled)
                                        viewModel.navigate(Destination.Apps)
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                    }
                }
                item(key = "recentRow") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(insights.recentlyInstalled, key = { it.packageName }) { entry ->
                            RecentTile(entry, graph) { bounds ->
                                viewModel.openDetail(DetailRequest(entry, bounds))
                            }
                        }
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            item(key = "timeline") {
                StaggeredEntrance(5) { InstallTimelinePanel(insights) }
                Spacer(Modifier.height(ManagerTheme.space.section))
            }

            if (insights.largest.isNotEmpty()) {
                item(key = "largest") {
                    StaggeredEntrance(6) {
                        SectionHeader(
                            title = "Largest apps",
                            trailing = {
                                ManagerTextAction(
                                    label = "See all",
                                    onClick = {
                                        viewModel.setFilter(AppFilter.Largest)
                                        viewModel.navigate(Destination.Apps)
                                    },
                                )
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        Panel(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                val leader = insights.largest.first().totalBytes.coerceAtLeast(1L)
                                insights.largest.take(5).forEach { entry ->
                                    LargestRow(entry, leader, graph) { bounds ->
                                        viewModel.openDetail(DetailRequest(entry, bounds))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            if (usageAccess && insights.dormant.isNotEmpty()) {
                item(key = "dormant") {
                    StaggeredEntrance(6) {
                        DormantPanel(
                            insights = insights,
                            graph = graph,
                            onOpen = { entry, bounds -> viewModel.openDetail(DetailRequest(entry, bounds)) },
                            onReviewAll = {
                                viewModel.setFilter(AppFilter.Dormant)
                                viewModel.navigate(Destination.Apps)
                            },
                        )
                    }
                    Spacer(Modifier.height(ManagerTheme.space.section))
                }
            }

            item(key = "footer") {
                StaggeredEntrance(6) {
                    ScanFooter(
                        scannedAt = inventory.scannedAt,
                        total = insights.total,
                        detailProgress = inventory.detailProgress,
                        measuring = insights.storageIsMeasured && inventory.detailProgress < 1f,
                    )
                }
            }
        }
    }
}

// ---- Hero --------------------------------------------------------------------------------------

/**
 * The one figure the screen is built around. The arc carries the user/system split so the number
 * does not need a second line of explanation, and the composition bar underneath restates it as
 * proportion — the same fact, twice, in two registers.
 */
@Composable
private fun InventoryHero(insights: Insights) {
    val colors = ManagerTheme.colors
    Panel(
        modifier = Modifier.fillMaxWidth(),
        shape = ManagerTheme.shapes.lg,
        elevation = 2.dp,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt(
                        "APPS INSTALLED",
                        style = ManagerTheme.type.eyebrow,
                        color = colors.inkTertiary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(
                            "${insights.total}",
                            style = ManagerTheme.type.displayXl,
                            color = colors.ink,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Txt(
                        "${insights.userCount} yours · ${insights.systemCount} system",
                        style = ManagerTheme.type.meta,
                        color = colors.inkSecondary,
                        maxLines = 1,
                    )
                }
                ArcGauge(
                    fraction = insights.userShare,
                    size = 92.dp,
                    thickness = 10.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt(
                            Format.percent(insights.userShare),
                            style = ManagerTheme.type.titleM,
                            color = colors.ink,
                        )
                        Txt("yours", style = ManagerTheme.type.metaS, color = colors.inkTertiary)
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            CompositionBar(
                segments = listOf(
                    VizSegment("Yours", insights.userCount.toLong(), colors.signal),
                    VizSegment("System", insights.systemCount.toLong(), colors.plot4),
                ),
                height = 10.dp,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                HeroStat("This week", "${insights.installedLastWeek}", Modifier.weight(1f))
                HeroStat("30 days", "${insights.installedLastMonth}", Modifier.weight(1f))
                HeroStat("90 days", "${insights.installedLastQuarter}", Modifier.weight(1f))
                HeroStat("Updated", "${insights.updatedLastWeek}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(value, style = ManagerTheme.type.titleL, color = ManagerTheme.colors.ink, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Txt(label, style = ManagerTheme.type.metaS, color = ManagerTheme.colors.inkTertiary, maxLines = 1)
    }
}

// ---- Permission --------------------------------------------------------------------------------

/**
 * The one place the app asks for something. It states plainly what is missing, what turning it on
 * changes, and does not pretend the product is broken without it.
 */
@Composable
private fun UsageAccessPanel(onGrant: () -> Unit) {
    val colors = ManagerTheme.colors
    Panel(
        modifier = Modifier.fillMaxWidth(),
        shape = ManagerTheme.shapes.lg,
        color = colors.signalSoft,
        border = null,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(SquircleShape(12.dp, 0.78f))
                        .background(colors.signal),
                    contentAlignment = Alignment.Center,
                ) {
                    ManagerIcon(ManagerIcons.Clock, null, tint = colors.onSignal, size = 17.dp)
                }
                Spacer(Modifier.width(13.dp))
                Txt("Two things are missing", style = ManagerTheme.type.titleM, color = colors.ink)
            }
            Spacer(Modifier.height(14.dp))
            Txt(
                "Android keeps screen time and exact app sizes behind Usage access. Without it, " +
                    "Manager shows APK size on disk and cannot rank apps by time spent.",
                style = ManagerTheme.type.bodyS,
                color = colors.inkSecondary,
            )
            Spacer(Modifier.height(20.dp))
            ManagerButton(
                "Grant usage access",
                onGrant,
                tone = ButtonTone.Signal,
                compact = true,
                icon = ManagerIcons.ArrowUpRight,
            )
        }
    }
}

// ---- Rows --------------------------------------------------------------------------------------

@Composable
private fun TopUsedRow(
    rank: Int,
    entry: AppEntry,
    durationMs: Long,
    share: Float,
    leader: Long,
    graph: ManagerGraph,
    onOpen: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open details",
            ) { onOpen(bounds) }
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .semantics {
                contentDescription =
                    "${entry.label}, ranked ${rank + 1}, ${Format.duration(durationMs)}, " +
                        "${Format.percent(share)} of tracked time"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt(
            Format.rank(rank),
            style = ManagerTheme.type.numericS,
            color = if (rank == 0) colors.signal else colors.inkTertiary,
            modifier = Modifier.width(24.dp),
        )
        AppIcon(
            packageName = entry.packageName,
            label = entry.label,
            loader = graph.icons,
            size = 38.dp,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Txt(entry.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            RankBar(
                fraction = if (leader <= 0) 0f else durationMs.toFloat() / leader,
                rank = rank,
                height = 5.dp,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Txt(Format.duration(durationMs), style = ManagerTheme.type.numeric, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Txt(Format.percent(share), style = ManagerTheme.type.metaS, color = colors.inkTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun LargestRow(
    entry: AppEntry,
    leader: Long,
    graph: ManagerGraph,
    onOpen: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val (value, unit) = Format.bytesParts(entry.totalBytes)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open details",
            ) { onOpen(bounds) }
            .padding(horizontal = 18.dp, vertical = 13.dp)
            .semantics {
                contentDescription = "${entry.label}, ${Format.bytes(entry.totalBytes)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = entry.packageName,
            label = entry.label,
            loader = graph.icons,
            size = 38.dp,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Txt(entry.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(7.dp))
            RankBar(
                fraction = entry.totalBytes.toFloat() / leader,
                rank = 0,
                height = 5.dp,
                color = colors.plot3,
            )
        }
        Spacer(Modifier.width(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, style = ManagerTheme.type.numeric, color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Txt(unit, style = ManagerTheme.type.metaS, color = colors.inkTertiary, maxLines = 1)
        }
    }
}

@Composable
private fun RecentTile(
    entry: AppEntry,
    graph: ManagerGraph,
    onOpen: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Panel(
        modifier = Modifier
            .width(124.dp)
            .pressResponse(interaction, pressedScale = 0.955f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open details",
            ) { onOpen(bounds) }
            .semantics {
                contentDescription =
                    "${entry.label}, installed ${Format.relativeDay(entry.installedAt)}"
            },
        shape = ManagerTheme.shapes.md,
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            AppIcon(
                packageName = entry.packageName,
                label = entry.label,
                loader = graph.icons,
                size = 42.dp,
                modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
            )
            Spacer(Modifier.height(13.dp))
            Txt(entry.label, style = ManagerTheme.type.labelS, color = colors.ink, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Txt(
                Format.relativeShort(entry.installedAt),
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

// ---- Panels ------------------------------------------------------------------------------------

@Composable
private fun StoragePanel(
    insights: Insights,
    detailProgress: Float,
    measured: Boolean,
    usageAccess: Boolean,
    onEnable: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val (value, unit) = Format.bytesParts(insights.totalBytes)

    Panel(Modifier.fillMaxWidth(), shape = ManagerTheme.shapes.lg) {
        Column(Modifier.padding(24.dp)) {
            Txt("STORAGE USED BY APPS", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(value, style = ManagerTheme.type.displayM, color = colors.ink, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Txt(unit, style = ManagerTheme.type.titleM, color = colors.inkTertiary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (measured && detailProgress < 1f) {
                    Txt(
                        "measuring ${Format.percent(detailProgress)}",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            CompositionBar(
                segments = listOf(
                    VizSegment("Your apps", insights.userBytes, colors.signal),
                    VizSegment("System", insights.systemBytes, colors.plot4),
                ),
            )
            Spacer(Modifier.height(18.dp))
            LegendSwatch(colors.signal, "Your apps", Format.bytes(insights.userBytes))
            Spacer(Modifier.height(11.dp))
            LegendSwatch(colors.plot4, "System apps", Format.bytes(insights.systemBytes))

            if (!measured) {
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(ManagerTheme.shapes.sm)
                        .background(colors.canvasSunken)
                        .padding(15.dp),
                ) {
                    Column {
                        Txt(
                            "Measured from APK files on disk",
                            style = ManagerTheme.type.labelS,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(5.dp))
                        Txt(
                            "App data and cache are not included. Usage access unlocks the real figures.",
                            style = ManagerTheme.type.metaS,
                            color = colors.inkSecondary,
                        )
                        if (!usageAccess) {
                            Spacer(Modifier.height(10.dp))
                            ManagerTextAction("Turn on usage access", onEnable)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Cache, and the only honest thing Manager can do about it.
 *
 * The figure is real — `StorageStatsManager`, per package, summed. The action is a hand-off:
 * Android alone can delete another app's files, so the button opens the system screen that can and
 * Manager measures again on the way back. Presenting a "Clean" button that quietly did nothing
 * would be the single most dishonest thing this product could ship, so it does not exist.
 *
 * The panel is absent entirely when nothing was measured, rather than showing a hopeful zero.
 */
@Composable
private fun CachePanel(
    report: CacheReport,
    busy: Boolean,
    onClear: () -> Unit,
    onOpenAll: () -> Unit,
    graph: ManagerGraph,
) {
    val colors = ManagerTheme.colors

    Column {
        SectionHeader(
            title = "App cache",
            trailing = {
                if (report.holders > report.largest.size) {
                    ManagerTextAction("All ${report.holders}", onOpenAll)
                }
            },
        )
        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth(), shape = ManagerTheme.shapes.lg) {
            Column(Modifier.padding(24.dp)) {
                if (!report.isKnown) {
                    Txt(
                        "Not measured yet",
                        style = ManagerTheme.type.titleM,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(7.dp))
                    Txt(
                        "Android reports cache sizes only to apps with usage access, and only once " +
                            "the storage pass has run. Nothing here is guessed in the meantime.",
                        style = ManagerTheme.type.bodyS,
                        color = colors.inkSecondary,
                    )
                    return@Column
                }

                CountingBytes(
                    bytes = report.bytes,
                    valueStyle = ManagerTheme.type.displayM,
                    unitStyle = ManagerTheme.type.titleM,
                    valueColor = colors.ink,
                    unitColor = colors.inkTertiary,
                    gap = 6.dp,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "${Format.bytes(report.bytes)} of app cache, " +
                            "across ${Format.count(report.holders, "app")}"
                    },
                )
                Spacer(Modifier.height(6.dp))
                Txt(
                    buildString {
                        append("across ${Format.count(report.holders, "app")}")
                        if (report.unmeasured > 0) append(" · ${report.unmeasured} would not report")
                    },
                    style = ManagerTheme.type.metaS,
                    color = colors.inkTertiary,
                )

                if (report.largest.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    val leader = report.largest.first().cacheBytes.coerceAtLeast(1L)
                    report.largest.take(3).forEachIndexed { index, holder ->
                        if (index > 0) Spacer(Modifier.height(13.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(holder.entry.packageName, holder.entry.label, graph.icons, size = 26.dp)
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Txt(
                                        holder.entry.label,
                                        style = ManagerTheme.type.meta,
                                        color = colors.ink,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Txt(
                                        Format.bytes(holder.cacheBytes),
                                        style = ManagerTheme.type.numericS,
                                        color = colors.inkSecondary,
                                        maxLines = 1,
                                    )
                                }
                                Spacer(Modifier.height(5.dp))
                                RankBar(
                                    fraction = (holder.cacheBytes.toFloat() / leader).coerceIn(0f, 1f),
                                    rank = index,
                                    height = 4.dp,
                                    color = colors.plot3,
                                )
                            }
                        }
                    }
                }

                if (report.holders > 0) {
                    Spacer(Modifier.height(22.dp))
                    ActionRow {
                        ManagerButton(
                            label = if (busy) "Waiting for Android…" else "Clear cache",
                            onClick = onClear,
                            enabled = !busy,
                            compact = true,
                        )
                        ManagerTextAction("Break it down", onOpenAll)
                    }
                    Spacer(Modifier.height(12.dp))
                    Txt(
                        "Android does the clearing — Manager measures before and after and reports " +
                            "what actually came back.",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallTimelinePanel(insights: Insights) {
    val colors = ManagerTheme.colors
    Panel(Modifier.fillMaxWidth(), shape = ManagerTheme.shapes.lg) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Txt("WHEN THIS PHONE FILLED UP", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
                    Spacer(Modifier.height(8.dp))
                    Txt("Installs by month", style = ManagerTheme.type.titleM, color = colors.ink)
                }
                Txt(
                    "12 months",
                    style = ManagerTheme.type.metaS,
                    color = colors.inkTertiary,
                )
            }
            Spacer(Modifier.height(22.dp))
            TimelineBars(
                values = insights.timeline.map { it.count },
                highlightIndex = insights.timeline.indexOfLast { it.isCurrent },
                height = 56.dp,
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                insights.timeline.forEachIndexed { index, bucket ->
                    // Label every third month: twelve labels at this width would be unreadable.
                    Txt(
                        if (index % 3 == 0 || index == insights.timeline.lastIndex) bucket.label else "",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DormantPanel(
    insights: Insights,
    graph: ManagerGraph,
    onOpen: (AppEntry, androidx.compose.ui.geometry.Rect?) -> Unit,
    onReviewAll: () -> Unit,
) {
    val colors = ManagerTheme.colors

    Column {
        SectionHeader(
            title = "Sitting idle",
            trailing = { ManagerTextAction("Review all", onReviewAll) },
        )
        Spacer(Modifier.height(14.dp))
        Panel(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The figure first and the sentence under it: "3.8 GB" is the fact, and
                    // "tied up in twelve apps you have not opened in 45 days" is the reason it
                    // is worth a second of anyone's attention.
                    Column(Modifier.weight(1f)) {
                        Txt(
                            Format.bytes(insights.dormantBytes),
                            style = ManagerTheme.type.displayS,
                            color = colors.ink,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(4.dp))
                        Txt(
                            "tied up in ${Format.count(insights.dormantCount, "app")} you haven't " +
                                "opened in ${insights.dormantIdleDays} days",
                            style = ManagerTheme.type.metaS,
                            color = colors.inkTertiary,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                insights.dormant.take(4).forEach { dormant ->
                    DormantRow(dormant.entry, dormant.daysIdle, graph) { bounds ->
                        onOpen(dormant.entry, bounds)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DormantRow(
    entry: AppEntry,
    daysIdle: Int?,
    graph: ManagerGraph,
    onOpen: (androidx.compose.ui.geometry.Rect?) -> Unit,
) {
    val colors = ManagerTheme.colors
    val interaction = remember { MutableInteractionSource() }
    var bounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressResponse(interaction, pressedScale = 0.985f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open details",
            ) { onOpen(bounds) }
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .semantics {
                contentDescription = buildString {
                    append(entry.label)
                    append(", unopened for ")
                    append(if (daysIdle == null) "as long as Android has recorded" else "$daysIdle days")
                    append(", ")
                    append(Format.bytes(entry.totalBytes))
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = entry.packageName,
            label = entry.label,
            loader = graph.icons,
            size = 34.dp,
            modifier = Modifier.onGloballyPositioned { bounds = it.boundsInRoot() },
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Txt(entry.label, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Txt(
                when {
                    daysIdle == null -> "No recorded use"
                    daysIdle >= 365 -> "Over a year"
                    daysIdle >= 30 -> "${daysIdle / 30} months"
                    else -> "$daysIdle days"
                },
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
        Txt(Format.bytes(entry.totalBytes), style = ManagerTheme.type.numericS, color = colors.inkSecondary, maxLines = 1)
    }
}

@Composable
private fun ScanFooter(scannedAt: Long, total: Int, detailProgress: Float, measuring: Boolean) {
    val colors = ManagerTheme.colors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        Txt(
            if (measuring) {
                "Measuring storage · ${Format.percent(detailProgress)}"
            } else {
                "$total packages · scanned ${Format.relativeShort(scannedAt)}"
            },
            style = ManagerTheme.type.metaS,
            color = colors.inkTertiary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Txt(
            "Pull down to rescan",
            style = ManagerTheme.type.metaS,
            color = colors.inkDisabled,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Loading -----------------------------------------------------------------------------------

@Composable
private fun DashboardSkeleton() {
    Column {
        Skeleton(Modifier.fillMaxWidth().height(212.dp), ManagerTheme.shapes.lg)
        Spacer(Modifier.height(ManagerTheme.space.section))
        Skeleton(Modifier.fillMaxWidth().height(150.dp), ManagerTheme.shapes.lg)
        Spacer(Modifier.height(ManagerTheme.space.section))
        Skeleton(Modifier.fillMaxWidth().height(230.dp), ManagerTheme.shapes.lg)
    }
}
