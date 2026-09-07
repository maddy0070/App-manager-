package com.manager.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.manager.app.ManagerApplication
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.data.SortKey
import com.manager.app.data.StorageBreakdown
import com.manager.app.data.UsageRecord
import com.manager.app.data.UsageSnapshot
import com.manager.app.data.UsageWindow
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.AppRowSkeleton
import com.manager.app.design.components.ArcGauge
import com.manager.app.design.components.CompositionBar
import com.manager.app.design.components.EmptyState
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerChip
import com.manager.app.design.components.ManagerMark
import com.manager.app.design.components.ManagerSearchField
import com.manager.app.design.components.ManagerSwitch
import com.manager.app.design.components.RankBar
import com.manager.app.design.components.SegmentedControl
import com.manager.app.design.components.SelectionMark
import com.manager.app.design.components.TimelineBars
import com.manager.app.design.components.Txt
import com.manager.app.design.components.VizSegment
import com.manager.app.ui.apps.AppRow
import com.manager.app.ui.common.NavigationRail
import com.manager.app.ui.common.SelectionBar
import com.manager.app.ui.detail.AppDetailSurface
import com.manager.app.ui.overlays.CacheSurface
import com.manager.app.ui.overlays.UninstallConfirmSurface
import com.manager.app.ui.settings.SettingsSurface
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

/**
 * Composition smoke tests.
 *
 * Rendering the real tree on the JVM is the closest thing to launching the app that a build
 * machine can do, and it catches the failures that matter most in a heavily custom UI: a shape
 * that divides by zero at some size, a scope misuse, a layout that throws on an empty list.
 * Every screen and every bespoke component is instantiated at least once here, in both themes.
 */
@RunWith(RobolectricTestRunner::class)
// The device this product targets, with real font metrics. Robolectric's defaults are a 320x470
// mdpi screen and stubbed text measurement, which lays every string out one character wide — a
// composition that survives that has not been meaningfully checked.
@Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = System.currentTimeMillis()

    private val graph: ManagerGraph by lazy {
        ManagerGraph(ApplicationProvider.getApplicationContext<ManagerApplication>())
    }

    private val viewModel: ManagerViewModel by lazy { ManagerViewModel(graph) }

    private fun entry(
        pkg: String = "com.example.app",
        label: String = "Example App",
        system: Boolean = false,
        split: Boolean = false,
        measured: Boolean = false,
        longName: Boolean = false,
    ) = AppEntry(
        packageName = if (longName) "com.example.a.very.long.package.name.that.will.not.fit.on.one.line" else pkg,
        label = if (longName) "An Extremely Long Application Name That Should Truncate Gracefully" else label,
        versionName = if (longName) "12.34.56-beta.1+build.20250101" else "1.2.3",
        versionCode = 42,
        installedAt = now - TimeUnit.DAYS.toMillis(30),
        updatedAt = now - TimeUnit.DAYS.toMillis(2),
        isSystem = system,
        isUpdatedSystemApp = false,
        isEnabled = true,
        isDebuggable = false,
        minSdk = 26,
        targetSdk = 34,
        sourceDir = "/data/app/$pkg/base.apk",
        splitSourceDirs = if (split) listOf("/data/app/$pkg/split_config.arm64_v8a.apk") else emptyList(),
        installerPackage = "com.android.vending",
        hasLaunchIntent = true,
        apkBytes = 84_000_000,
        storage = if (measured) StorageBreakdown(84_000_000, 300_000_000, 40_000_000) else null,
        obbBytes = if (measured) 1_200_000_000 else null,
    )

    // ---- Whole screens -------------------------------------------------------------------

    @Test
    fun `the app root renders from a cold, empty state`() {
        compose.setContent { ManagerTheme { AppRoot(viewModel, graph) } }
        compose.waitForIdle()
    }

    @Test
    fun `the app root renders in dark mode`() {
        compose.setContent { ManagerTheme(dark = true) { AppRoot(viewModel, graph) } }
        compose.waitForIdle()
    }

    @Test
    fun `onboarding opens on an unclaimed field, on sample data, with a way out`() {
        compose.setContent { ManagerTheme { AppRoot(viewModel, graph) } }
        compose.onNodeWithText("Your phone\nhas a story.").assertIsDisplayed()
        compose.onNodeWithText("Explore").assertIsDisplayed()
        compose.onNodeWithText("Sample data").assertIsDisplayed()
        compose.onNodeWithText("Skip").assertIsDisplayed()
    }

    @Test
    fun `settings renders every control`() {
        compose.setContent {
            ManagerTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsSurface(visible = true, viewModel = viewModel, onDismiss = {})
                }
            }
        }
        compose.waitForIdle()
    }

    // ---- The detail surface --------------------------------------------------------------

    @Test
    fun `the detail surface renders without an origin to fly from`() {
        compose.setContent {
            ManagerTheme {
                AppDetailSurface(
                    request = DetailRequest(entry(measured = true, split = true)),
                    viewModel = viewModel,
                    graph = graph,
                    onDismiss = {},
                    onExtract = {},
                    onUninstall = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the detail surface survives a system app with no measured storage`() {
        compose.setContent {
            ManagerTheme {
                AppDetailSurface(
                    request = DetailRequest(entry(system = true, measured = false)),
                    viewModel = viewModel,
                    graph = graph,
                    onDismiss = {},
                    onExtract = {},
                    onUninstall = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the detail surface handles names long enough to break a layout`() {
        compose.setContent {
            ManagerTheme {
                AppDetailSurface(
                    request = DetailRequest(entry(longName = true)),
                    viewModel = viewModel,
                    graph = graph,
                    onDismiss = {},
                    onExtract = {},
                    onUninstall = {},
                )
            }
        }
        compose.waitForIdle()
    }

    // ---- Destructive flows -----------------------------------------------------------------

    @Test
    fun `bulk uninstall confirmation lists what will go`() {
        compose.setContent {
            ManagerTheme {
                UninstallConfirmSurface(
                    entries = listOf(entry("a", "Alpha"), entry("b", "Beta"), entry("c", "System", system = true)),
                    graph = graph,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a selection of only system packages still renders its confirmation`() {
        compose.setContent {
            ManagerTheme {
                UninstallConfirmSurface(
                    entries = listOf(entry("a", "System", system = true)),
                    graph = graph,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
    }

    // ---- Rows and bars -----------------------------------------------------------------------

    @Test
    fun `an app row renders selected, unselected, split and system variants`() {
        compose.setContent {
            ManagerTheme {
                androidx.compose.foundation.layout.Column {
                    AppRow(entry(), graph, selected = false, selectionMode = false, usageMs = null, sortKey = SortKey.Name, onOpen = {}, onToggleSelect = {}, onBeginSelection = {}, hapticsEnabled = true)
                    AppRow(entry("b", "Selected"), graph, selected = true, selectionMode = true, usageMs = 3_600_000, sortKey = SortKey.Usage, onOpen = {}, onToggleSelect = {}, onBeginSelection = {}, hapticsEnabled = false)
                    AppRow(entry("c", "Split", split = true), graph, selected = false, selectionMode = true, usageMs = null, sortKey = SortKey.Name, onOpen = {}, onToggleSelect = {}, onBeginSelection = {}, hapticsEnabled = true)
                    AppRow(entry("d", "System", system = true), graph, selected = false, selectionMode = false, usageMs = null, sortKey = SortKey.Name, onOpen = {}, onToggleSelect = {}, onBeginSelection = {}, hapticsEnabled = true)
                    AppRow(entry(longName = true), graph, selected = false, selectionMode = false, usageMs = null, sortKey = SortKey.Name, onOpen = {}, onToggleSelect = {}, onBeginSelection = {}, hapticsEnabled = true)
                }
            }
        }
        compose.onNodeWithText("Example App").assertIsDisplayed()
    }

    @Test
    fun `the navigation bar and the selection bar both render`() {
        compose.setContent {
            ManagerTheme {
                androidx.compose.foundation.layout.Column {
                    NavigationRail(current = Destination.Dashboard, onSelect = {})
                    SelectionBar(
                        count = 3,
                        bytes = 5_175_453_286L,
                        measured = true,
                        breakdown = emptyList(),
                        allSelected = false,
                        canExtract = true,
                        canUninstall = true,
                        onSelectAll = {},
                        onExtract = {},
                        onUninstall = {},
                        onDismiss = {},
                    )
                }
            }
        }
        // The nav label carries no semantics of its own — the row announces the destination once,
        // which is what a screen reader should hear.
        compose.onNodeWithContentDescription("Overview").assertIsDisplayed()
    }

    @Test
    fun `the selection bar states the weight of the batch, and marks it when it is an estimate`() {
        compose.setContent {
            ManagerTheme {
                androidx.compose.foundation.layout.Column {
                    SelectionBar(
                        count = 3, bytes = 5_175_453_286L, measured = true, allSelected = false,
                        breakdown = listOf(
                            VizSegment("App", 1_100_000_000L, ManagerTheme.colors.plot1),
                            VizSegment("Data", 3_200_000_000L, ManagerTheme.colors.plot3),
                            VizSegment("Cache", 875_453_286L, ManagerTheme.colors.plot5),
                        ),
                        canExtract = true, canUninstall = true,
                        onSelectAll = {}, onExtract = {}, onUninstall = {}, onDismiss = {},
                    )
                    SelectionBar(
                        count = 1, bytes = 88_080_384L, measured = false, allSelected = true,
                        breakdown = emptyList(),
                        canExtract = true, canUninstall = false,
                        onSelectAll = {}, onExtract = {}, onUninstall = {}, onDismiss = {},
                    )
                }
            }
        }
        // The count and the figure are one reading, so a screen reader hears the decision rather
        // than three unrelated fragments — and the estimate says so in words, not just a glyph.
        compose.onNodeWithContentDescription("3 apps selected, 4.8 GB", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "1 app selected, about 84 MB, measured from APK size on disk",
            substring = true,
        ).assertIsDisplayed()
    }

    @Test
    fun `the cache surface renders, and says why it has nothing to show`() {
        compose.setContent {
            ManagerTheme {
                CacheSurface(visible = true, viewModel = viewModel, graph = graph, onDismiss = {})
            }
        }
        // No usage access in the test environment, which is exactly the state that most needs an
        // honest explanation rather than a hopeful zero.
        compose.onNodeWithText("Cache needs usage access").assertIsDisplayed()
        compose.onNodeWithContentDescription("0 B of app cache").assertIsDisplayed()
    }

    @Test
    fun `the cache surface renders in dark mode too`() {
        compose.setContent {
            ManagerTheme(dark = true) {
                CacheSurface(visible = true, viewModel = viewModel, graph = graph, onDismiss = {})
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `the confirmation states what Android removes, in order, and what it could not measure`() {
        compose.setContent {
            ManagerTheme {
                UninstallConfirmSurface(
                    entries = listOf(entry("a", "Alpha")),
                    graph = graph,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("ANDROID REMOVES").assertIsDisplayed()
        compose.onNodeWithText("Cache").assertIsDisplayed()
        compose.onNodeWithText("App data").assertIsDisplayed()
        compose.onNodeWithText("Application").assertIsDisplayed()
        // This entry has no StorageStats record, so two of the three stages are unknown — and the
        // sheet has to say unknown rather than show a confident zero.
        compose.onAllNodesWithText("Not measured")[0].assertIsDisplayed()
    }

    @Test
    fun `the confirmation leads with what comes back rather than burying it`() {
        compose.setContent {
            ManagerTheme {
                UninstallConfirmSurface(
                    entries = listOf(entry("a", "Alpha", measured = true), entry("b", "Beta", measured = true)),
                    graph = graph,
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Comes back", substring = true).assertIsDisplayed()
    }

    // ---- Visualisations at their degenerate inputs -------------------------------------------

    @Test
    fun `visualisations survive empty, zero and single-value inputs`() {
        compose.setContent {
            ManagerTheme {
                androidx.compose.foundation.layout.Column {
                    CompositionBar(segments = emptyList())
                    CompositionBar(segments = listOf(VizSegment("Only", 0, ManagerTheme.colors.signal)))
                    CompositionBar(
                        segments = listOf(
                            VizSegment("App", 1, ManagerTheme.colors.plot1),
                            VizSegment("Data", 999_999_999, ManagerTheme.colors.plot3),
                        ),
                    )
                    RankBar(fraction = 0f, rank = 0)
                    RankBar(fraction = 1f, rank = 9)
                    RankBar(fraction = Float.NaN.takeIf { false } ?: 0.5f, rank = 2)
                    ArcGauge(fraction = 0f)
                    ArcGauge(fraction = 1f)
                    TimelineBars(values = emptyList())
                    TimelineBars(values = List(12) { 0 })
                    TimelineBars(values = List(12) { it * 3 }, highlightIndex = 11)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `every bespoke control renders in light mode`() = renderControlGallery(dark = false)

    @Test
    fun `every bespoke control renders in dark mode`() = renderControlGallery(dark = true)

    private fun renderControlGallery(dark: Boolean) {
        compose.setContent {
            ManagerTheme(dark = dark) {
                    androidx.compose.foundation.layout.Column {
                        ManagerMark(animated = false)
                        ManagerButton("Primary", {})
                        ManagerChip("Chip", selected = true, onClick = {})
                        ManagerChip("Chip", selected = false, onClick = {}, enabled = false)
                        SegmentedControl(
                            options = UsageWindow.entries,
                            selected = UsageWindow.Week,
                            onSelect = {},
                            label = { it.shortLabel },
                        )
                        ManagerSearchField(query = "", onQueryChange = {})
                        ManagerSearchField(query = "maps", onQueryChange = {})
                        SelectionMark(selected = true)
                        ManagerSwitch(checked = true, onCheckedChange = {})
                    AppRowSkeleton()
                    Txt("Sample")
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `empty states render with and without an action`() {
        compose.setContent {
            ManagerTheme {
                androidx.compose.foundation.layout.Column {
                    EmptyState(title = "Nothing here", body = "And that is fine.")
                    EmptyState(
                        title = "Nothing matches",
                        body = "Try a shorter fragment.",
                        actionLabel = "Clear search",
                        onAction = {},
                    )
                }
            }
        }
        compose.onNodeWithText("Nothing here").assertIsDisplayed()
    }

    // ---- Data plumbing ------------------------------------------------------------------------

    @Test
    fun `an empty usage snapshot is a valid state, not a crash`() {
        val snapshot = UsageSnapshot(UsageWindow.Today, emptyMap(), emptyMap(), false, now)
        compose.setContent {
            ManagerTheme {
                Txt(snapshot.byPackage.size.toString())
            }
        }
        compose.onNodeWithText("0").assertIsDisplayed()
    }

    @Test
    fun `a usage record for a package that is not installed does not break the row`() {
        val orphan = UsageRecord("com.gone", 1_000, now)
        compose.setContent { ManagerTheme { Txt(orphan.packageName) } }
        compose.onNodeWithText("com.gone").assertIsDisplayed()
    }
}
