package com.manager.app.domain

import com.manager.app.data.AppEntry
import com.manager.app.data.StorageBreakdown
import com.manager.app.data.UsageRecord
import com.manager.app.data.UsageSnapshot
import com.manager.app.data.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class InsightsTest {

    private val now = System.currentTimeMillis()

    private fun entry(
        pkg: String,
        label: String = pkg,
        system: Boolean = false,
        installedDaysAgo: Long = 100,
        updatedDaysAgo: Long = installedDaysAgo,
        apkBytes: Long = 10_000_000,
        storage: StorageBreakdown? = null,
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
        storage = storage,
    )

    @Test
    fun `empty inventory produces the empty insight rather than a divide by zero`() {
        val result = buildInsights(emptyList(), UsageSnapshot.Empty, false, now)
        assertEquals(Insights.Empty, result)
        assertEquals(0f, result.userShare, 0.0001f)
    }

    @Test
    fun `user and system are counted from flags, never from names`() {
        val apps = listOf(
            entry("com.example.one"),
            entry("com.android.systemui", system = true),
            entry("com.example.two"),
        )
        val insights = buildInsights(apps, UsageSnapshot.Empty, false, now)
        assertEquals(3, insights.total)
        assertEquals(2, insights.userCount)
        assertEquals(1, insights.systemCount)
        assertEquals(2f / 3f, insights.userShare, 0.001f)
    }

    @Test
    fun `install windows nest correctly`() {
        val apps = listOf(
            entry("a", installedDaysAgo = 2),
            entry("b", installedDaysAgo = 20),
            entry("c", installedDaysAgo = 60),
            entry("d", installedDaysAgo = 400),
        )
        val insights = buildInsights(apps, UsageSnapshot.Empty, false, now)
        assertEquals(1, insights.installedLastWeek)
        assertEquals(2, insights.installedLastMonth)
        assertEquals(3, insights.installedLastQuarter)
    }

    @Test
    fun `an app that was never updated does not count as recently updated`() {
        // installedAt == updatedAt is how Android reports "never updated".
        val apps = listOf(entry("a", installedDaysAgo = 1, updatedDaysAgo = 1))
        assertEquals(0, buildInsights(apps, UsageSnapshot.Empty, false, now).updatedLastWeek)
    }

    @Test
    fun `storage totals prefer measured stats over apk size on disk`() {
        val measured = entry(
            "a",
            apkBytes = 10_000_000,
            storage = StorageBreakdown(appBytes = 12_000_000, dataBytes = 30_000_000, cacheBytes = 1_000_000),
        )
        val unmeasured = entry("b", apkBytes = 5_000_000)
        val insights = buildInsights(listOf(measured, unmeasured), UsageSnapshot.Empty, false, now)
        assertEquals(42_000_000L + 5_000_000L, insights.userBytes)
        assertTrue(insights.storageIsMeasured)
    }

    @Test
    fun `top used is ranked descending and shares sum to one`() {
        val apps = listOf(entry("a"), entry("b"), entry("c"))
        val usage = UsageSnapshot(
            window = UsageWindow.Week,
            byPackage = mapOf(
                "a" to UsageRecord("a", TimeUnit.HOURS.toMillis(1), now),
                "b" to UsageRecord("b", TimeUnit.HOURS.toMillis(3), now),
                "c" to UsageRecord("c", TimeUnit.HOURS.toMillis(2), now),
            ),
            lastUsedByPackage = emptyMap(),
            approximate = false,
            generatedAt = now,
        )
        val insights = buildInsights(apps, usage, true, now)
        assertEquals(listOf("b", "c", "a"), insights.topUsed.map { it.entry.packageName })
        assertEquals(1f, insights.topUsed.sumOf { it.share.toDouble() }.toFloat(), 0.001f)
    }

    @Test
    fun `usage records for packages that are gone are dropped, not rendered as blanks`() {
        val usage = UsageSnapshot(
            window = UsageWindow.Week,
            byPackage = mapOf("ghost" to UsageRecord("ghost", 5_000, now)),
            lastUsedByPackage = emptyMap(),
            approximate = false,
            generatedAt = now,
        )
        val insights = buildInsights(listOf(entry("a")), usage, true, now)
        assertTrue(insights.topUsed.isEmpty())
    }

    @Test
    fun `dormant needs usage access and never includes system or unlaunchable apps`() {
        val apps = listOf(
            entry("idle", installedDaysAgo = 90),
            entry("system.idle", system = true, installedDaysAgo = 90),
            entry("headless", installedDaysAgo = 90, launchable = false),
            entry("busy", installedDaysAgo = 90),
        )
        val usage = UsageSnapshot(
            window = UsageWindow.Week,
            byPackage = emptyMap(),
            lastUsedByPackage = mapOf(
                "idle" to now - TimeUnit.DAYS.toMillis(40),
                "busy" to now - TimeUnit.DAYS.toMillis(2),
            ),
            approximate = false,
            generatedAt = now,
        )
        assertTrue(buildInsights(apps, usage, false, now).dormant.isEmpty())

        val dormant = buildInsights(apps, usage, true, now).dormant
        assertEquals(listOf("idle"), dormant.map { it.entry.packageName })
        assertEquals(40, dormant.first().daysIdle)
    }

    @Test
    fun `an app with no recorded use at all still counts as dormant`() {
        val apps = listOf(entry("never", installedDaysAgo = 90))
        val usage = UsageSnapshot(UsageWindow.Week, emptyMap(), emptyMap(), false, now)
        val dormant = buildInsights(apps, usage, true, now).dormant
        assertEquals(1, dormant.size)
        assertEquals(null, dormant.first().daysIdle)
    }

    @Test
    fun `a freshly installed app is never called dormant`() {
        val apps = listOf(entry("fresh", installedDaysAgo = 2))
        val usage = UsageSnapshot(UsageWindow.Week, emptyMap(), emptyMap(), false, now)
        assertTrue(buildInsights(apps, usage, true, now).dormant.isEmpty())
    }

    @Test
    fun `the timeline always has twelve buckets and loses no installs`() {
        val apps = listOf(
            entry("a", installedDaysAgo = 1),
            entry("b", installedDaysAgo = 40),
            entry("c", installedDaysAgo = 700),
        )
        val timeline = buildInsights(apps, UsageSnapshot.Empty, false, now).timeline
        assertEquals(12, timeline.size)
        assertEquals(3, timeline.sumOf { it.count })
        assertTrue(timeline.last().isCurrent)
    }
}
