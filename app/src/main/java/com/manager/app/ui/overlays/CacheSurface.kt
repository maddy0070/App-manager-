package com.manager.app.ui.overlays

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.CountingBytes
import com.manager.app.design.components.EmptyState
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerChip
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.RankBar
import com.manager.app.design.components.Txt
import com.manager.app.domain.CacheHolder
import com.manager.app.domain.CacheOrder
import com.manager.app.domain.applyCacheOrder
import com.manager.app.domain.buildCacheReport
import com.manager.app.ui.ManagerViewModel
import com.manager.app.util.Format

/**
 * Everything Android was willing to say about cache, and the one door it leaves open.
 *
 * The honest shape of this feature is a measurement tool with a hand-off, not a cleaner. Manager
 * ranks what is there and can put the user in front of the exact system screen that acts on it —
 * per app, or for the whole device — but it never performs the deletion and never claims to. The
 * footer says so in the product's own voice rather than hiding it in a disclaimer.
 */
@Composable
fun CacheSurface(
    visible: Boolean,
    viewModel: ManagerViewModel,
    graph: ManagerGraph,
    onDismiss: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val sheetGutter = ManagerTheme.space.sheetGutter
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val inventory by viewModel.inventory.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val usageAccess by viewModel.usageAccess.collectAsState()
    val order by viewModel.cacheOrder.collectAsState()
    val cleanup by viewModel.cacheCleanup.collectAsState()

    // Built here rather than read from the dashboard's flow so the list and the total on top of it
    // are the same measurement, taken at the same instant.
    val report = remember(inventory.apps, usage) { buildCacheReport(inventory.apps, usage) }
    val holders = remember(inventory.apps, usage, order) {
        inventory.apps
            .mapNotNull { app ->
                app.storage?.cacheBytes?.takeIf { it > 0 }?.let {
                    CacheHolder(app, it, usage.lastUsedByPackage[app.packageName])
                }
            }
            .applyCacheOrder(order)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(180))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim.copy(alpha = if (colors.isLight) 0.34f else 0.56f))
                    .clearAndSetSemantics { }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sheetHeight = maxHeight * 0.88f
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
                        .height(sheetHeight)
                        .padding(10.dp)
                        .shadow(
                            elevation = 32.dp,
                            shape = ManagerTheme.shapes.sheet,
                            clip = false,
                            ambientColor = colors.ink.copy(alpha = 0.48f),
                            spotColor = colors.ink.copy(alpha = 0.34f),
                        )
                        .clip(ManagerTheme.shapes.sheet)
                        .background(colors.surface),
                ) {
                    // ---- The figure, and how much of the device it covers --------------------
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = sheetGutter, end = 14.dp, top = 24.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Txt("APP CACHE", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
                            Spacer(Modifier.height(10.dp))
                            CountingBytes(
                                bytes = report.bytes,
                                valueStyle = ManagerTheme.type.displayM,
                                unitStyle = ManagerTheme.type.titleM,
                                valueColor = colors.ink,
                                unitColor = colors.inkTertiary,
                                gap = 5.dp,
                                modifier = Modifier.clearAndSetSemantics {
                                    contentDescription = "${Format.bytes(report.bytes)} of app cache"
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Txt(
                                coverageLine(report.holders, report.unmeasured),
                                style = ManagerTheme.type.metaS,
                                color = colors.inkTertiary,
                            )
                        }
                        ManagerIconButton(
                            icon = ManagerIcons.Close,
                            contentDescription = "Close cache",
                            onClick = onDismiss,
                            container = colors.canvasSunken,
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    if (holders.isNotEmpty()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = sheetGutter),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CacheOrder.entries.forEach { candidate ->
                                ManagerChip(
                                    label = candidate.label,
                                    selected = candidate == order,
                                    onClick = { viewModel.setCacheOrder(candidate) },
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    Hairline()

                    Box(Modifier.weight(1f)) {
                        when {
                            !usageAccess -> CacheUnavailable(
                                title = "Cache needs usage access",
                                body = "Android only reports how much space an app's cache takes to apps you " +
                                    "have granted Usage access. Without it Manager cannot measure it, and " +
                                    "will not guess.",
                                actionLabel = "Open Usage access",
                                onAction = viewModel::requestUsageAccess,
                            )

                            !report.isKnown -> CacheUnavailable(
                                title = "Nothing measured yet",
                                body = "The storage pass has not finished, or Android declined every package " +
                                    "on this device. Pull down on the overview to measure again.",
                                actionLabel = null,
                                onAction = {},
                            )

                            holders.isEmpty() -> CacheUnavailable(
                                title = "No cache to reclaim",
                                body = "Every app Android would report is holding nothing. That is unusual, " +
                                    "and it means there is nothing here worth clearing.",
                                actionLabel = null,
                                onAction = {},
                            )

                            else -> LazyColumn(
                                Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = sheetGutter - 10.dp,
                                    end = sheetGutter - 10.dp,
                                    top = 6.dp,
                                    bottom = 10.dp,
                                ),
                            ) {
                                val leader = holders.maxOf { it.cacheBytes }.coerceAtLeast(1L)
                                itemsIndexed(holders, key = { _, it -> it.entry.packageName }) { index, holder ->
                                    CacheRow(
                                        holder = holder,
                                        rank = index,
                                        leader = leader,
                                        graph = graph,
                                        onOpen = { viewModel.requestAppStorage(holder.entry) },
                                    )
                                }
                            }
                        }
                    }

                    // ---- Who actually does the clearing --------------------------------------
                    Hairline()
                    Column(
                        Modifier.padding(
                            start = sheetGutter,
                            end = sheetGutter,
                            top = 16.dp,
                            bottom = 16.dp + bottomInset,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            ManagerIcon(ManagerIcons.Info, null, tint = colors.inkTertiary, size = 13.dp)
                            Spacer(Modifier.width(8.dp))
                            Txt(
                                "Android clears caches, not Manager — no app can delete another app's " +
                                    "files. Manager measures before and after, and tells you what actually " +
                                    "came back.",
                                style = ManagerTheme.type.metaS,
                                color = colors.inkTertiary,
                            )
                        }
                        if (report.isKnown && holders.isNotEmpty()) {
                            Spacer(Modifier.height(14.dp))
                            ManagerButton(
                                label = if (cleanup != null) "Waiting for Android…" else "Clear cache",
                                onClick = viewModel::requestCacheCleanup,
                                icon = ManagerIcons.ArrowUpRight,
                                enabled = cleanup == null,
                                fillWidth = true,
                                tone = ButtonTone.Signal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Coverage stated up front rather than in a footnote.
 *
 * "842 MB across 37 apps" and "842 MB across 37 apps, 12 more would not say" are different claims,
 * and the second one is usually the true one.
 */
private fun coverageLine(holders: Int, unmeasured: Int): String = buildString {
    append(Format.count(holders, "app"))
    append(if (holders == 1) " is holding it" else " are holding it")
    if (unmeasured > 0) {
        append(" · ")
        append(unmeasured)
        append(" would not report")
    }
}

/**
 * One cache holder, in the app list's own language: the real icon, the name, the figure, and a
 * bar for how it compares. The secondary line says what share of the app the cache *is*, which is
 * the number that decides whether clearing it is worth the reload.
 */
@Composable
private fun CacheRow(
    holder: CacheHolder,
    rank: Int,
    leader: Long,
    graph: ManagerGraph,
    onOpen: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val entry = holder.entry
    val share = (holder.shareOfApp * 100).toInt()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(ManagerTheme.shapes.sm)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Open storage settings",
                onClick = onOpen,
            )
            .padding(horizontal = 10.dp, vertical = 11.dp)
            .semantics {
                contentDescription = buildString {
                    append(entry.label)
                    append(", ")
                    append(Format.bytes(holder.cacheBytes))
                    append(" of cache")
                    if (share >= 1) append(", $share percent of the app")
                    holder.lastUsed?.let { append(", last opened ${Format.relativeShort(it)}") }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.packageName, entry.label, graph.icons, size = 36.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(
                    entry.label,
                    style = ManagerTheme.type.strong,
                    color = colors.ink,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(10.dp))
                Txt(
                    Format.bytes(holder.cacheBytes),
                    style = ManagerTheme.type.numericS,
                    color = colors.ink,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            RankBar(
                fraction = (holder.cacheBytes.toFloat() / leader).coerceIn(0f, 1f),
                rank = rank,
                height = 4.dp,
                color = colors.plot3,
            )
            Spacer(Modifier.height(6.dp))
            Txt(
                buildString {
                    if (share >= 1) append("$share% of the app")
                    holder.lastUsed?.let {
                        if (isNotEmpty()) append(" · ")
                        append("opened ${Format.relativeShort(it)}")
                    }
                },
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CacheUnavailable(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize().heightIn(min = 200.dp), contentAlignment = Alignment.TopCenter) {
        EmptyState(
            title = title,
            body = body,
            icon = ManagerIcons.Storage,
            actionLabel = actionLabel,
            onAction = onAction,
            tone = ButtonTone.Signal,
        )
    }
}
