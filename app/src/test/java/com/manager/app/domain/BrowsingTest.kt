package com.manager.app.domain

import com.manager.app.data.AppEntry
import com.manager.app.data.AppFilter
import com.manager.app.data.SortDirection
import com.manager.app.data.SortKey
import com.manager.app.data.UsageRecord
import com.manager.app.data.UsageSnapshot
import com.manager.app.data.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BrowsingTest {

    private val now = System.currentTimeMillis()

    private fun entry(
        pkg: String,
        label: String,
        system: Boolean = false,
        installedDaysAgo: Long = 200,
        updatedDaysAgo: Long = installedDaysAgo,
        apkBytes: Long = 1_000_000,
        launchable: Boolean = true,
    ) = AppEntry(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        versionCode = 1,
        installedAt = now - TimeUnit.DAYS.toMillis(installedDaysAgo),
        updatedAt = now - TimeUnit.DAYS.toMillis(updatedDaysAgo),
        isSystem = system,
        isUpdatedSystemApp = false,
        isEnabled = true,
        isDebuggable = false,
        minSdk = 26,
        targetSdk = 34,
        sourceDir = "/data/app/$pkg/base.apk",
        splitSourceDirs = emptyList(),
        installerPackage = null,
        hasLaunchIntent = launchable,
        apkBytes = apkBytes,
    )

    private val catalogue = listOf(
        entry("com.google.android.apps.maps", "Maps"),
        entry("com.spotify.music", "Spotify"),
        entry("com.example.mapper", "Mapper Studio"),
        entry("com.mailmap.client", "Post Office"),
        entry("com.android.settings", "Settings", system = true),
    )

    // ---- Search --------------------------------------------------------------------------

    @Test
    fun `an exact label match wins outright`() {
        assertEquals("Maps", catalogue.applySearch("maps").first().label)
    }

    @Test
    fun `within a tier the shorter name wins, and label hits beat package-only hits`() {
        // Both "Maps" and "Mapper Studio" are prefix matches; "Post Office" only matches on its
        // package name, so it must come last however alphabetical order would have it.
        val results = catalogue.applySearch("map").map { it.label }
        assertEquals(listOf("Maps", "Mapper Studio", "Post Office"), results)
    }

    @Test
    fun `search matches a word inside a multi-word name`() {
        assertTrue(catalogue.applySearch("studio").map { it.label }.contains("Mapper Studio"))
    }

    @Test
    fun `package names are searchable, which is how you find an app you cannot name`() {
        assertEquals(listOf("Spotify"), catalogue.applySearch("com.spotify").map { it.label })
    }

    @Test
    fun `search is case and whitespace insensitive`() {
        assertEquals(catalogue.applySearch("maps"), catalogue.applySearch("  MAPS "))
    }

    @Test
    fun `an empty query returns the list untouched, not a copy in another order`() {
        assertEquals(catalogue, catalogue.applySearch("   "))
    }

    @Test
    fun `a query that matches nothing returns nothing rather than everything`() {
        assertTrue(catalogue.applySearch("zzzz").isEmpty())
    }

    // ---- Filter --------------------------------------------------------------------------

    @Test
    fun `system and user filters partition the catalogue exactly`() {
        val user = catalogue.applyFilter(AppFilter.User, UsageSnapshot.Empty, now)
        val system = catalogue.applyFilter(AppFilter.System, UsageSnapshot.Empty, now)
        assertEquals(catalogue.size, user.size + system.size)
        assertTrue(user.none { it.isSystem })
        assertTrue(system.all { it.isSystem })
    }

    @Test
    fun `recently updated ignores apps whose update time is just their install time`() {
        val apps = listOf(
            entry("a", "Never updated", installedDaysAgo = 3, updatedDaysAgo = 3),
            entry("b", "Updated", installedDaysAgo = 300, updatedDaysAgo = 2),
        )
        val result = apps.applyFilter(AppFilter.RecentlyUpdated, UsageSnapshot.Empty, now)
        assertEquals(listOf("Updated"), result.map { it.label })
    }

    @Test
    fun `largest is an ordering, so as a filter it removes nothing`() {
        assertEquals(catalogue, catalogue.applyFilter(AppFilter.Largest, UsageSnapshot.Empty, now))
    }

    @Test
    fun `dormant excludes system packages and apps with no launcher entry`() {
        val apps = listOf(
            entry("user.idle", "Idle"),
            entry("sys.idle", "System idle", system = true),
            entry("headless", "Headless", launchable = false),
        )
        val usage = UsageSnapshot(UsageWindow.Week, emptyMap(), emptyMap(), false, now)
        assertEquals(listOf("Idle"), apps.applyFilter(AppFilter.Dormant, usage, now).map { it.label })
    }

    // ---- Sort ----------------------------------------------------------------------------

    @Test
    fun `sorting by name is case insensitive and reversible`() {
        val ascending = catalogue.applySort(SortKey.Name, SortDirection.Ascending, UsageSnapshot.Empty)
        val descending = catalogue.applySort(SortKey.Name, SortDirection.Descending, UsageSnapshot.Empty)
        assertEquals(ascending.map { it.label }, descending.map { it.label }.reversed())
        assertEquals("Mapper Studio", ascending.first().label)
    }

    @Test
    fun `sorting by size uses the best available total`() {
        val apps = listOf(
            entry("small", "Small", apkBytes = 1),
            entry("big", "Big", apkBytes = 900_000_000),
        )
        val result = apps.applySort(SortKey.Size, SortDirection.Descending, UsageSnapshot.Empty)
        assertEquals(listOf("Big", "Small"), result.map { it.label })
    }

    @Test
    fun `apps with no usage record sort as zero rather than being dropped`() {
        val usage = UsageSnapshot(
            window = UsageWindow.Week,
            byPackage = mapOf("com.spotify.music" to UsageRecord("com.spotify.music", 5_000, now)),
            lastUsedByPackage = emptyMap(),
            approximate = false,
            generatedAt = now,
        )
        val result = catalogue.applySort(SortKey.Usage, SortDirection.Descending, usage)
        assertEquals(catalogue.size, result.size)
        assertEquals("Spotify", result.first().label)
    }

    @Test
    fun `ties fall back to name so the order does not shuffle between rescans`() {
        val apps = listOf(
            entry("z", "Zulu", apkBytes = 100),
            entry("a", "Alpha", apkBytes = 100),
        )
        val result = apps.applySort(SortKey.Size, SortDirection.Ascending, UsageSnapshot.Empty)
        assertEquals(listOf("Alpha", "Zulu"), result.map { it.label })
    }
}
