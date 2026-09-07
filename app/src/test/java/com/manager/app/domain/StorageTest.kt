package com.manager.app.domain

import com.manager.app.data.AppEntry
import com.manager.app.data.StorageBreakdown
import com.manager.app.data.UsageRecord
import com.manager.app.data.UsageSnapshot
import com.manager.app.data.UsageWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The arithmetic behind every storage figure the product states.
 *
 * All of it is pure, and all of it has one rule in common: a number Android did not give us is
 * *absent*, never zero. These tests exist mostly to hold that line — it is the single easiest
 * thing to lose in a refactor, and losing it turns an honest product into a plausible one.
 */
class StorageTest {

    private val now = System.currentTimeMillis()

    private fun entry(
        pkg: String,
        label: String = pkg,
        apk: Long = 100_000_000,
        app: Long? = null,
        data: Long = 0,
        cache: Long = 0,
        system: Boolean = false,
        launchable: Boolean = true,
        installedDaysAgo: Int = 200,
    ) = AppEntry(
        packageName = pkg,
        label = label,
        versionName = "1.0",
        versionCode = 1,
        installedAt = now - TimeUnit.DAYS.toMillis(installedDaysAgo.toLong()),
        updatedAt = now - TimeUnit.DAYS.toMillis(installedDaysAgo.toLong()),
        isSystem = system,
        isUpdatedSystemApp = false,
        isEnabled = true,
        isDebuggable = false,
        minSdk = 29,
        targetSdk = 36,
        sourceDir = "/data/app/$pkg/base.apk",
        splitSourceDirs = emptyList(),
        installerPackage = null,
        hasLaunchIntent = launchable,
        apkBytes = apk,
        storage = app?.let { StorageBreakdown(it, data, cache) },
        obbBytes = 0L,
    )

    private fun usage(vararg lastUsed: Pair<String, Long>) = UsageSnapshot(
        window = UsageWindow.Week,
        byPackage = lastUsed.associate { (pkg, at) -> pkg to UsageRecord(pkg, 0, at) },
        lastUsedByPackage = lastUsed.toMap(),
        approximate = false,
        generatedAt = now,
    )

    // ---- Cache aggregation ---------------------------------------------------------------------

    @Test
    fun `cache sums only what Android reported, and counts the refusals separately`() {
        val report = buildCacheReport(
            listOf(
                entry("a", app = 10, cache = 200_000_000),
                entry("b", app = 10, cache = 50_000_000),
                // Reported, and genuinely holding nothing.
                entry("c", app = 10, cache = 0),
                // Not reported at all. Its cache is unknown, not zero.
                entry("d"),
                entry("e"),
            ),
            UsageSnapshot.Empty,
        )

        assertEquals(250_000_000L, report.bytes)
        assertEquals("only apps actually holding cache", 2, report.holders)
        assertEquals("everything Android answered for", 3, report.measured)
        assertEquals("everything it declined", 2, report.unmeasured)
        assertTrue(report.isKnown)
        assertFalse("coverage is incomplete and must say so", report.isComplete)
    }

    @Test
    fun `a device Android will not measure at all reports nothing rather than zero`() {
        val report = buildCacheReport(listOf(entry("a"), entry("b")), UsageSnapshot.Empty)
        assertFalse("nothing is known, which is not the same as nothing being there", report.isKnown)
        assertEquals(0, report.holders)
        assertEquals(2, report.unmeasured)
        assertTrue(report.largest.isEmpty())
    }

    @Test
    fun `an empty device produces an empty report rather than throwing`() {
        val report = buildCacheReport(emptyList(), UsageSnapshot.Empty)
        assertEquals(CacheReport.Empty, report.copy(largest = emptyList()))
    }

    @Test
    fun `cache share of an app is a share of everything that app occupies`() {
        // 100 app + 100 data + 200 cache = 400, of which the cache is half. An app that is mostly
        // cache is the one worth clearing, so this ratio is the whole reason the column exists.
        val holder = CacheHolder(entry("a", apk = 0, app = 100, data = 100, cache = 200), 200, null)
        assertEquals(0.5f, holder.shareOfApp, 0.001f)
    }

    @Test
    fun `an app's size includes its cache, so the list and the detail sheet cannot disagree`() {
        val app = entry("a", app = 1_000, data = 500, cache = 250)
        // The detail sheet draws app, data and cache as three segments and sums them for its
        // headline. Anything the list quotes has to be that same sum.
        assertEquals(1_750L, app.totalBytes)
        assertEquals(app.storage!!.appBytes + app.storage!!.dataBytes + app.storage!!.cacheBytes, app.totalBytes)
    }

    // ---- Cache ordering ------------------------------------------------------------------------

    @Test
    fun `each cache order answers a different question about the same list`() {
        val big = CacheHolder(entry("big", app = 5_000_000_000, cache = 300), 300, now)
        val proportional = CacheHolder(entry("small", apk = 0, app = 100, cache = 100), 100, now)
        val forgotten = CacheHolder(entry("old", app = 1_000, cache = 200), 200, null)

        val list = listOf(proportional, forgotten, big)

        assertEquals("largest cache first", "big", list.applyCacheOrder(CacheOrder.Largest).first().entry.packageName)
        assertEquals(
            "the app that is mostly cache first",
            "small",
            list.applyCacheOrder(CacheOrder.ShareOfApp).first().entry.packageName,
        )
        assertEquals(
            "never-opened outranks long-ago",
            "old",
            list.applyCacheOrder(CacheOrder.LeastUsed).first().entry.packageName,
        )
    }

    // ---- Selection totals ------------------------------------------------------------------------

    @Test
    fun `selecting and deselecting moves the total by exactly the app's own size`() {
        val a = entry("a", app = 1_000_000_000, data = 200_000_000, cache = 50_000_000)
        val b = entry("b", app = 400_000_000, data = 100_000_000, cache = 20_000_000)

        assertEquals(0L, emptyList<AppEntry>().selectionTotals().bytes)

        val one = listOf(a).selectionTotals()
        assertEquals(1, one.count)
        assertEquals(1_250_000_000L, one.bytes)

        val two = listOf(a, b).selectionTotals()
        assertEquals(2, two.count)
        assertEquals(1_770_000_000L, two.bytes)
        assertEquals("deselecting returns exactly what selecting added", one.bytes, two.bytes - 520_000_000L)
    }

    @Test
    fun `select-all totals every visible app`() {
        val all = (1..40).map { entry("p$it", app = 10_000_000, data = 5_000_000) }
        val totals = all.selectionTotals()
        assertEquals(40, totals.count)
        assertEquals(40 * 15_000_000L, totals.bytes)
        assertTrue(totals.hasBreakdown)
        assertEquals(40 * 10_000_000L, totals.appBytes)
    }

    @Test
    fun `one unmeasured app makes the whole total an estimate and withdraws the breakdown`() {
        val measured = entry("a", app = 1_000_000_000, data = 200_000_000)
        val unmeasured = entry("b", apk = 90_000_000)

        val clean = listOf(measured).selectionTotals()
        assertTrue(clean.measured)
        assertTrue(clean.hasBreakdown)

        val mixed = listOf(measured, unmeasured).selectionTotals()
        assertFalse("a sum containing a fallback is not a measurement", mixed.measured)
        assertFalse("and it must not be drawn as a composition", mixed.hasBreakdown)
        // The figure is still the best available one — it just stops claiming to be exact.
        assertEquals(1_200_000_000L + 90_000_000L, mixed.bytes)
        assertEquals("and the withdrawn breakdown is zero, not a partial shape", 0L, mixed.appBytes)
    }

    // ---- Dormant storage -------------------------------------------------------------------------

    @Test
    fun `dormant storage counts every idle app, not just the ones on screen`() {
        val idle = (1..12).map {
            entry("idle$it", app = 100_000_000, installedDaysAgo = 300)
        }
        val fresh = entry("fresh", app = 100_000_000)
        val snapshot = usage(
            *idle.map { it.packageName to now - TimeUnit.DAYS.toMillis(60) }.toTypedArray(),
            "fresh" to now,
        )

        val insights = buildInsights(idle + fresh, snapshot, hasUsageAccess = true, now = now)

        assertEquals("the list is capped for display", 8, insights.dormant.size)
        assertEquals("the count is not", 12, insights.dormantCount)
        assertEquals("and neither is the figure", 12 * 100_000_000L, insights.dormantBytes)
        assertEquals("the claim holds for every app in it", 60, insights.dormantIdleDays)
    }

    @Test
    fun `without usage access nothing is called dormant, because nothing is known`() {
        val apps = (1..5).map { entry("p$it", app = 100_000_000, installedDaysAgo = 300) }
        val insights = buildInsights(apps, UsageSnapshot.Empty, hasUsageAccess = false, now = now)
        assertTrue(insights.dormant.isEmpty())
        assertEquals(0L, insights.dormantBytes)
        assertEquals(0, insights.dormantCount)
    }

    @Test
    fun `system packages and apps with no launcher entry are never called dormant`() {
        val apps = listOf(
            entry("sys", system = true, app = 500_000_000, installedDaysAgo = 300),
            entry("headless", launchable = false, app = 500_000_000, installedDaysAgo = 300),
            entry("real", app = 100_000_000, installedDaysAgo = 300),
        )
        val snapshot = usage(
            "sys" to now - TimeUnit.DAYS.toMillis(90),
            "headless" to now - TimeUnit.DAYS.toMillis(90),
            "real" to now - TimeUnit.DAYS.toMillis(90),
        )
        val insights = buildInsights(apps, snapshot, hasUsageAccess = true, now = now)

        assertEquals(1, insights.dormantCount)
        assertEquals("real", insights.dormant.single().entry.packageName)
    }

    @Test
    fun `reclaimable is cache plus dormant and nothing invented`() {
        val apps = listOf(
            entry("a", app = 100_000_000, cache = 30_000_000, installedDaysAgo = 300),
            entry("b", app = 200_000_000, cache = 20_000_000),
        )
        val snapshot = usage("a" to now - TimeUnit.DAYS.toMillis(60), "b" to now)
        val insights = buildInsights(apps, snapshot, hasUsageAccess = true, now = now)

        assertEquals(50_000_000L, insights.cache.bytes)
        assertEquals(130_000_000L, insights.dormantBytes)
        assertEquals(180_000_000L, insights.reclaimable)
    }

    // ---- What the return from the system screen is allowed to claim -------------------------------

    @Test
    fun `a reclaim is only reported when the measurement actually moved`() {
        val mb = 1024L * 1024L

        assertEquals(
            "half a gigabyte gone is a reclaim",
            ReclaimOutcome.Freed,
            reclaimOutcome(before = 900 * mb, after = 400 * mb, noiseFloor = mb),
        )
        assertEquals(
            "an untouched device is not a success",
            ReclaimOutcome.Unchanged,
            reclaimOutcome(before = 900 * mb, after = 900 * mb, noiseFloor = mb),
        )
        assertEquals(
            "a few hundred kilobytes either way is apps rewriting their own caches",
            ReclaimOutcome.Unchanged,
            reclaimOutcome(before = 900 * mb, after = 900 * mb - 400_000, noiseFloor = mb),
        )
        assertEquals(
            "cache can grow while Settings is open, and saying so beats claiming a win",
            ReclaimOutcome.Grew,
            reclaimOutcome(before = 400 * mb, after = 900 * mb, noiseFloor = mb),
        )
    }

    @Test
    fun `a cleanup that cleared everything reports the whole baseline`() {
        assertEquals(ReclaimOutcome.Freed, reclaimOutcome(before = 842_000_000, after = 0, noiseFloor = 1024))
        // And a device that had nothing to begin with cannot have freed anything.
        assertEquals(ReclaimOutcome.Unchanged, reclaimOutcome(before = 0, after = 0, noiseFloor = 1024))
    }
}
