package com.manager.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.ActionRow
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.CompositionBar
import com.manager.app.design.components.Hairline
import com.manager.app.design.components.LegendSwatch
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.Txt
import com.manager.app.design.components.VizSegment
import com.manager.app.design.components.pressResponse
import com.manager.app.ui.apps.SystemBadge
import com.manager.app.util.Format

/**
 * What the panel actually says about an app.
 *
 * Ordered by what a person asks in sequence: what is this, how much room does it take, when did
 * it arrive, and what can I do about it. Everything a developer might want — SDK levels, install
 * source, APK paths — is real and available, but folded away behind one disclosure so it never
 * competes with the four facts most people came for.
 */
@Composable
internal fun ColumnScope.DetailContent(
    entry: AppEntry,
    graph: ManagerGraph,
    usageMs: Long?,
    usageWindowLabel: String,
    lastUsed: Long?,
    open: Float,
    headerIconAlpha: Float,
    onHeaderIconBounds: (Rect) -> Unit,
    onDismiss: () -> Unit,
    onExtract: () -> Unit,
    onUninstall: () -> Unit,
    maxHeight: Dp,
) {
    val colors = ManagerTheme.colors
    val scrollState = rememberScrollState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content arrives a beat behind the panel so the surface lands before the words appear.
    val contentReveal by animateFloatAsState(
        targetValue = if (open > 0.55f) 1f else 0f,
        animationSpec = tween(280, easing = ManagerTheme.motion.emphasized),
        label = "detailReveal",
    )

    // ---- Handle ---------------------------------------------------------------------------
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(ManagerTheme.shapes.capsule)
                .background(colors.hairlineStrong),
        )
    }

    // ---- Header ---------------------------------------------------------------------------
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 14.dp, top = 14.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .onGloballyPositioned { onHeaderIconBounds(it.boundsInRoot()) }
                .graphicsLayer { alpha = headerIconAlpha },
        ) {
            AppIcon(
                packageName = entry.packageName,
                label = entry.label,
                loader = graph.icons,
                size = 62.dp,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    alpha = contentReveal
                    translationX = (1f - contentReveal) * 14.dp.toPx()
                },
        ) {
            Txt(entry.label, style = ManagerTheme.type.titleL, color = colors.ink, maxLines = 2)
            Spacer(Modifier.height(5.dp))
            Txt(
                entry.packageName,
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ManagerIconButton(
            icon = ManagerIcons.Close,
            contentDescription = "Close",
            onClick = onDismiss,
            container = colors.canvasSunken,
            pressedContainer = colors.hairlineStrong,
            tint = colors.inkSecondary,
            size = 40.dp,
            iconSize = 16.dp,
        )
    }

    // ---- Scrolling body ---------------------------------------------------------------------
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(scrollState)
            .graphicsLayer {
                alpha = contentReveal
                translationY = (1f - contentReveal) * 18.dp.toPx()
            },
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entry.isSystem) SystemBadge() else TypeBadge("YOUR APP", colors.signalSoft, colors.signal)
            if (entry.isSplit) TypeBadge("${entry.splitCount + 1} APKs", colors.canvasSunken, colors.inkSecondary)
            if (!entry.isEnabled) TypeBadge("DISABLED", colors.emberSoft, colors.ember)
            if (entry.isDebuggable) TypeBadge("DEBUGGABLE", colors.emberSoft, colors.ember)
        }

        Spacer(Modifier.height(22.dp))
        StorageBlock(entry)

        Spacer(Modifier.height(24.dp))
        Hairline(Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(18.dp))

        FactRow("Version", entry.versionName ?: "Not reported", "Build ${entry.versionCode}")
        FactRow("Installed", Format.relativeDay(entry.installedAt), Format.fullDate(entry.installedAt))
        FactRow(
            "Last updated",
            if (entry.updatedAt > entry.installedAt) Format.relativeDay(entry.updatedAt) else "Never updated",
            if (entry.updatedAt > entry.installedAt) Format.fullDate(entry.updatedAt) else null,
        )
        if (usageMs != null && usageMs > 0) {
            FactRow(
                "Screen time",
                Format.duration(usageMs),
                usageWindowLabel.lowercase().replaceFirstChar { it.uppercase() },
            )
        }
        if (lastUsed != null && lastUsed > 0) {
            FactRow("Last opened", Format.relativeDay(lastUsed), Format.fullDate(lastUsed))
        }

        TechnicalDisclosure(entry)

        Spacer(Modifier.height(20.dp))
    }

    // ---- Actions ------------------------------------------------------------------------------
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = contentReveal },
    ) {
        Hairline()
        Column(
            Modifier.padding(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 16.dp + bottomInset,
            ),
        ) {
            ActionRow(Modifier.fillMaxWidth()) {
                ManagerButton(
                    label = if (entry.isSplit) "Extract ${entry.splitCount + 1} APKs" else "Extract APK",
                    onClick = onExtract,
                    icon = ManagerIcons.Extract,
                    tone = ButtonTone.Signal,
                    modifier = Modifier.weight(1f),
                )
                ManagerButton(
                    label = if (entry.isUpdatedSystemApp && entry.isSystem) "Remove updates" else "Uninstall",
                    onClick = onUninstall,
                    icon = ManagerIcons.Trash,
                    tone = ButtonTone.Destructive,
                    enabled = entry.isUninstallable,
                    modifier = Modifier.weight(1f),
                )
            }
            if (!entry.isUninstallable) {
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ManagerIcon(ManagerIcons.Info, null, tint = colors.inkTertiary, size = 13.dp)
                    Spacer(Modifier.width(7.dp))
                    Txt(
                        "Android does not allow this system package to be removed.",
                        style = ManagerTheme.type.metaS,
                        color = colors.inkTertiary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

// ---- Blocks --------------------------------------------------------------------------------

/**
 * Storage, showing only what Android actually reported.
 *
 * With usage access this is app code, data and cache from StorageStatsManager. Without it, the
 * only honest number is the size of the APK files on disk, and the block says so rather than
 * quietly presenting a smaller figure as the whole truth.
 */
@Composable
private fun StorageBlock(entry: AppEntry) {
    val colors = ManagerTheme.colors
    val storage = entry.storage
    val obb = entry.obbBytes?.takeIf { it > 0 }

    val segments = buildList {
        if (storage != null) {
            add(VizSegment("App", storage.appBytes, colors.plot1))
            add(VizSegment("Data", storage.dataBytes, colors.plot3))
            add(VizSegment("Cache", storage.cacheBytes, colors.plot5))
        } else {
            add(VizSegment("APK", entry.apkBytes, colors.plot1))
        }
        if (obb != null) add(VizSegment("OBB", obb, colors.ember))
    }
    val total = segments.sumOf { it.value }
    val (value, unit) = Format.bytesParts(total)

    Column(Modifier.padding(horizontal = 24.dp)) {
        Txt("STORAGE", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Txt(value, style = ManagerTheme.type.displayM, color = colors.ink, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Txt(unit, style = ManagerTheme.type.titleM, color = colors.inkTertiary, maxLines = 1)
        }
        Spacer(Modifier.height(16.dp))
        CompositionBar(segments = segments, height = 11.dp)
        Spacer(Modifier.height(16.dp))
        segments.forEachIndexed { index, segment ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            LegendSwatch(segment.color, segment.label, Format.bytes(segment.value))
        }
        if (storage == null) {
            Spacer(Modifier.height(14.dp))
            Txt(
                "APK size on disk. App data and cache need usage access to measure.",
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
            )
        }
        if (entry.obbBytes == null) {
            Spacer(Modifier.height(if (storage == null) 6.dp else 14.dp))
            Txt(
                "OBB folders are not readable on this Android version.",
                style = ManagerTheme.type.metaS,
                color = colors.inkTertiary,
            )
        } else if (obb == null) {
            Spacer(Modifier.height(if (storage == null) 6.dp else 14.dp))
            Txt("No OBB expansion files.", style = ManagerTheme.type.metaS, color = colors.inkTertiary)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, detail: String? = null) {
    val colors = ManagerTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Txt(
            label,
            style = ManagerTheme.type.meta,
            color = colors.inkTertiary,
            modifier = Modifier.width(112.dp),
        )
        Column(Modifier.weight(1f)) {
            Txt(value, style = ManagerTheme.type.strong, color = colors.ink, maxLines = 2)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Txt(detail, style = ManagerTheme.type.metaS, color = colors.inkTertiary, maxLines = 2)
            }
        }
    }
}

/**
 * Everything a developer would want, one tap away and out of everyone else's way.
 */
@Composable
private fun TechnicalDisclosure(entry: AppEntry) {
    val colors = ManagerTheme.colors
    var expanded by remember(entry.packageName) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
        label = "disclosureChevron",
    )

    Column {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .pressResponse(interaction, pressedScale = 0.99f)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                ) { expanded = !expanded }
                .padding(horizontal = 24.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Txt("Technical detail", style = ManagerTheme.type.labelS, color = colors.signal)
            Spacer(Modifier.weight(1f))
            ManagerIcon(
                ManagerIcons.ChevronDown,
                null,
                tint = colors.signal,
                size = 14.dp,
                modifier = Modifier.rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(tween(200, delayMillis = 50)),
            exit = shrinkVertically(tween(200, easing = ManagerTheme.motion.exit)) + fadeOut(tween(110)),
        ) {
            Column {
                FactRow("Target SDK", "API ${entry.targetSdk}", androidRelease(entry.targetSdk))
                FactRow("Minimum SDK", "API ${entry.minSdk}", androidRelease(entry.minSdk))
                FactRow(
                    "Installed by",
                    entry.installerPackage?.let { friendlyInstaller(it) } ?: "Sideloaded or preinstalled",
                    entry.installerPackage,
                )
                FactRow(
                    "Package layout",
                    if (entry.isSplit) "Base + ${entry.splitCount} splits" else "Single APK",
                    entry.sourceDir,
                )
                FactRow("Launchable", if (entry.hasLaunchIntent) "Yes" else "No launcher entry")
            }
        }
    }
}

@Composable
private fun TypeBadge(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier
            .clip(ManagerTheme.shapes.capsule)
            .background(container)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Txt(label.uppercase(), style = ManagerTheme.type.eyebrow, color = content, maxLines = 1)
    }
}

private fun friendlyInstaller(packageName: String): String = when (packageName) {
    "com.android.vending" -> "Google Play"
    "com.google.android.packageinstaller",
    "com.android.packageinstaller",
    -> "Package installer"

    "com.amazon.venezia" -> "Amazon Appstore"
    "org.fdroid.fdroid" -> "F-Droid"
    "com.aurora.store" -> "Aurora Store"
    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

/** API level to the Android version people actually recognise. */
private fun androidRelease(sdk: Int): String? = when (sdk) {
    36 -> "Android 16"
    35 -> "Android 15"
    34 -> "Android 14"
    33 -> "Android 13"
    32, 31 -> "Android 12"
    30 -> "Android 11"
    29 -> "Android 10"
    28 -> "Android 9"
    27, 26 -> "Android 8"
    25, 24 -> "Android 7"
    23 -> "Android 6"
    21, 22 -> "Android 5"
    else -> null
}
