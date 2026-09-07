package com.manager.app.domain

import androidx.compose.runtime.Immutable
import com.manager.app.data.AppEntry
import com.manager.app.data.UsageSnapshot
import com.manager.app.util.Format
import java.util.Calendar
import java.util.concurrent.TimeUnit

@Immutable
data class UsageRank(
    val entry: AppEntry,
    val foregroundMs: Long,
    val share: Float,
    val lastUsed: Long,
)

@Immutable
data class DormantApp(
    val entry: AppEntry,
    val lastUsed: Long?,
    val daysIdle: Int?,
)

/**
 * What Android was willing to say about cache, and how much of the device it actually covers.
 *
 * [unmeasured] is the honest half of this: `StorageStatsManager` refuses some packages even with
 * usage access granted, so a total that quietly summed only the answers it got would understate
 * the device without ever saying so. Every figure here is paired with its own coverage.
 */
@Immutable
data class CacheReport(
    val bytes: Long,
    /** Apps carrying a cache Android reported as larger than zero. */
    val holders: Int,
    /** Apps whose cache Android reported — including the ones reporting zero. */
    val measured: Int,
    /** Apps Android would not measure at all. Their cache is unknown, not absent. */
    val unmeasured: Int,
    val largest: List<CacheHolder>,
) {
    val isKnown: Boolean get() = measured > 0
    val isComplete: Boolean get() = measured > 0 && unmeasured == 0

    companion object {
        val Empty = CacheReport(0, 0, 0, 0, emptyList())
    }
}

@Immutable
data class CacheHolder(
    val entry: AppEntry,
    val cacheBytes: Long,
    val lastUsed: Long?,
) {
    /** Cache as a fraction of everything the app occupies — how much of it is disposable. */
    val shareOfApp: Float
        get() = entry.totalBytes.takeIf { it > 0 }?.let { cacheBytes.toFloat() / it } ?: 0f
}

@Immutable
data class TimelineBucket(val label: String, val count: Int, val isCurrent: Boolean)

/**
 * Everything the dashboard states about the device, derived in one pass.
 *
 * This is deliberately a pure function over a snapshot: the dashboard never queries anything
 * itself, so every figure on screen comes from the same moment in time and cannot disagree with
 * the list a tap away.
 */
@Immutable
data class Insights(
    val total: Int,
    val userCount: Int,
    val systemCount: Int,
    val disabledCount: Int,
    val splitCount: Int,
    val installedLastWeek: Int,
    val installedLastMonth: Int,
    val installedLastQuarter: Int,
    val updatedLastWeek: Int,
    val totalBytes: Long,
    val userBytes: Long,
    val systemBytes: Long,
    val storageIsMeasured: Boolean,
    val recentlyInstalled: List<AppEntry>,
    val recentlyUpdated: List<AppEntry>,
    val largest: List<AppEntry>,
    val topUsed: List<UsageRank>,
    /** The worst offenders, capped for display. [dormantCount] is how many there really are. */
    val dormant: List<DormantApp>,
    val dormantCount: Int,
    /** What all of them are holding — not just the ones shown. */
    val dormantBytes: Long,
    val dormantIdleDays: Int,
    val cache: CacheReport,
    val usageTotalMs: Long,
    val timeline: List<TimelineBucket>,
) {
    val userShare: Float get() = if (total == 0) 0f else userCount.toFloat() / total

    /**
     * Space Manager can point at without guessing: cache Android reported, plus everything held by
     * apps the user has not opened in three weeks. Deliberately not called "junk" — the dormant
     * half is only reclaimable if the user agrees it is, which is why it is never acted on
     * automatically.
     */
    val reclaimable: Long get() = cache.bytes + dormantBytes

    companion object {
        val Empty = Insights(
            total = 0, userCount = 0, systemCount = 0, disabledCount = 0, splitCount = 0,
            installedLastWeek = 0, installedLastMonth = 0, installedLastQuarter = 0,
            updatedLastWeek = 0, totalBytes = 0, userBytes = 0, systemBytes = 0,
            storageIsMeasured = false, recentlyInstalled = emptyList(), recentlyUpdated = emptyList(),
            largest = emptyList(), topUsed = emptyList(), dormant = emptyList(),
            dormantCount = 0, dormantBytes = 0, dormantIdleDays = 0, cache = CacheReport.Empty,
            usageTotalMs = 0, timeline = emptyList(),
        )

        /** Apps untouched for this long are surfaced as candidates for removal. */
        const val DORMANT_DAYS = 21
    }
}

fun buildInsights(
    apps: List<AppEntry>,
    usage: UsageSnapshot,
    hasUsageAccess: Boolean,
    now: Long = System.currentTimeMillis(),
): Insights {
    if (apps.isEmpty()) return Insights.Empty

    val week = now - TimeUnit.DAYS.toMillis(7)
    val month = now - TimeUnit.DAYS.toMillis(30)
    val quarter = now - TimeUnit.DAYS.toMillis(90)

    var userCount = 0
    var systemCount = 0
    var disabled = 0
    var splits = 0
    var installedWeek = 0
    var installedMonth = 0
    var installedQuarter = 0
    var updatedWeek = 0
    var userBytes = 0L
    var systemBytes = 0L

    apps.forEach { app ->
        if (app.isSystem) systemCount++ else userCount++
        if (!app.isEnabled) disabled++
        if (app.isSplit) splits++
        if (app.installedAt >= week) installedWeek++
        if (app.installedAt >= month) installedMonth++
        if (app.installedAt >= quarter) installedQuarter++
        // An update only counts when it genuinely post-dates the install.
        if (app.updatedAt >= week && app.updatedAt > app.installedAt + TimeUnit.MINUTES.toMillis(1)) updatedWeek++
        if (app.isSystem) systemBytes += app.totalBytes else userBytes += app.totalBytes
    }

    val recentlyInstalled = apps
        .filter { it.installedAt > 0 }
        .sortedByDescending { it.installedAt }
        .take(6)

    val recentlyUpdated = apps
        .filter { it.updatedAt > it.installedAt + TimeUnit.MINUTES.toMillis(1) }
        .sortedByDescending { it.updatedAt }
        .take(6)

    val largest = apps.sortedByDescending { it.totalBytes }.take(6)

    val byPackage = apps.associateBy { it.packageName }
    val usageTotal = usage.byPackage.values.sumOf { it.foregroundMs }

    val topUsed = usage.byPackage.values
        .asSequence()
        .filter { it.foregroundMs > 0 }
        .sortedByDescending { it.foregroundMs }
        .mapNotNull { record ->
            byPackage[record.packageName]?.let { entry ->
                UsageRank(
                    entry = entry,
                    foregroundMs = record.foregroundMs,
                    share = if (usageTotal == 0L) 0f else record.foregroundMs.toFloat() / usageTotal,
                    lastUsed = record.lastUsed,
                )
            }
        }
        .take(5)
        .toList()

    val dormant = if (!hasUsageAccess) emptyList() else apps
        .asSequence()
        // Only apps the user could actually open are meaningful here.
        .filter { !it.isSystem && it.hasLaunchIntent }
        .mapNotNull { entry ->
            val last = usage.lastUsedByPackage[entry.packageName]
            val idle = last?.let { Format.daysBetween(it, now) }
            when {
                last == null && entry.installedAt < now - TimeUnit.DAYS.toMillis(Insights.DORMANT_DAYS.toLong()) ->
                    DormantApp(entry, null, null)

                idle != null && idle >= Insights.DORMANT_DAYS -> DormantApp(entry, last, idle)
                else -> null
            }
        }
        .sortedWith(compareByDescending<DormantApp> { it.daysIdle ?: Int.MAX_VALUE }.thenByDescending { it.entry.totalBytes })
        .toList()

    return Insights(
        total = apps.size,
        userCount = userCount,
        systemCount = systemCount,
        disabledCount = disabled,
        splitCount = splits,
        installedLastWeek = installedWeek,
        installedLastMonth = installedMonth,
        installedLastQuarter = installedQuarter,
        updatedLastWeek = updatedWeek,
        totalBytes = userBytes + systemBytes,
        userBytes = userBytes,
        systemBytes = systemBytes,
        storageIsMeasured = apps.any { it.storage != null },
        recentlyInstalled = recentlyInstalled,
        recentlyUpdated = recentlyUpdated,
        largest = largest,
        topUsed = topUsed,
        dormant = dormant.take(8),
        dormantCount = dormant.size,
        dormantBytes = dormant.sumOf { it.entry.totalBytes },
        // The dormancy the headline claims is the shortest one every listed app actually clears,
        // so "untouched for N days" is true of all of them rather than only the worst.
        dormantIdleDays = dormant.minOfOrNull { it.daysIdle ?: Int.MAX_VALUE }
            ?.takeIf { it != Int.MAX_VALUE } ?: Insights.DORMANT_DAYS,
        cache = buildCacheReport(apps, usage),
        usageTotalMs = usageTotal,
        timeline = buildTimeline(apps, now),
    )
}

/**
 * Cache, counted only where Android answered.
 *
 * `StorageStatsManager` needs usage access and still declines some packages, so an app with no
 * storage record has an *unknown* cache rather than an empty one. Those are counted separately and
 * never folded into the total — the difference between "37 apps hold 842 MB" and "37 apps hold at
 * least 842 MB, and 12 more would not say" is the whole reason to trust the figure.
 */
fun buildCacheReport(apps: List<AppEntry>, usage: UsageSnapshot): CacheReport {
    var bytes = 0L
    var holders = 0
    var measured = 0
    var unmeasured = 0
    val candidates = ArrayList<CacheHolder>()

    apps.forEach { app ->
        val cache = app.storage?.cacheBytes
        if (cache == null) {
            unmeasured++
            return@forEach
        }
        measured++
        if (cache <= 0) return@forEach
        holders++
        bytes += cache
        candidates.add(CacheHolder(app, cache, usage.lastUsedByPackage[app.packageName]))
    }

    return CacheReport(
        bytes = bytes,
        holders = holders,
        measured = measured,
        unmeasured = unmeasured,
        largest = candidates.sortedByDescending { it.cacheBytes }.take(6),
    )
}

/** How the focused cache view is ordered. Each answers a different question about the same list. */
enum class CacheOrder(val label: String) {
    Largest("Largest"),
    ShareOfApp("Most of the app"),
    LeastUsed("Least used"),
}

fun List<CacheHolder>.applyCacheOrder(order: CacheOrder): List<CacheHolder> = when (order) {
    CacheOrder.Largest -> sortedByDescending { it.cacheBytes }
    CacheOrder.ShareOfApp -> sortedByDescending { it.shareOfApp }
    // Never-opened first, then longest-ago. A null last-used is the strongest signal there is.
    CacheOrder.LeastUsed -> sortedWith(
        compareBy<CacheHolder> { it.lastUsed ?: Long.MIN_VALUE }.thenByDescending { it.cacheBytes },
    )
}

/**
 * Twelve monthly buckets of install activity. Older installs all land in the first bucket rather
 * than being dropped, so the total always reconciles with the inventory count.
 */
private fun buildTimeline(apps: List<AppEntry>, now: Long): List<TimelineBucket> {
    val buckets = IntArray(12)
    val labels = arrayOfNulls<String>(12)

    val cursor = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val monthStarts = LongArray(12)
    for (i in 11 downTo 0) {
        monthStarts[i] = cursor.timeInMillis
        labels[i] = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(cursor.time)
        cursor.add(Calendar.MONTH, -1)
    }

    apps.forEach { app ->
        if (app.installedAt <= 0) return@forEach
        val index = monthStarts.indexOfLast { app.installedAt >= it }
        if (index >= 0) buckets[index]++ else buckets[0]++
    }

    return buckets.mapIndexed { index, count ->
        TimelineBucket(labels[index] ?: "", count, index == 11)
    }
}

/**
 * What a selection adds up to.
 *
 * Pulled out of the UI because it is arithmetic with rules, not layout: the total is only a
 * *measurement* when Android measured every app in it, and the breakdown only exists at all in
 * that case. A composition drawn from one app's real figures and another's absence would be a
 * shape made partly of missing data, which is worse than no shape.
 */
@Immutable
data class SelectionTotals(
    val count: Int,
    val bytes: Long,
    val measured: Boolean,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val hasBreakdown: Boolean get() = measured && (appBytes + dataBytes + cacheBytes) > 0

    companion object {
        val Empty = SelectionTotals(0, 0, false, 0, 0, 0)
    }
}

fun List<AppEntry>.selectionTotals(): SelectionTotals {
    if (isEmpty()) return SelectionTotals.Empty
    val measured = all { it.storage != null }
    return SelectionTotals(
        count = size,
        bytes = sumOf { it.totalBytes },
        measured = measured,
        appBytes = if (measured) sumOf { it.storage!!.appBytes } else 0L,
        dataBytes = if (measured) sumOf { it.storage!!.dataBytes } else 0L,
        cacheBytes = if (measured) sumOf { it.storage!!.cacheBytes } else 0L,
    )
}

/**
 * What a re-measurement after the system flow actually shows.
 *
 * Android never reports whether the user cleared anything, so this is the only evidence there is:
 * the difference between two of Manager's own measurements. It is deliberately three-valued —
 * "nothing changed" is a real, common and honest answer, and a product that only knows how to
 * congratulate would report it as success.
 */
enum class ReclaimOutcome { Freed, Grew, Unchanged }

/**
 * @param noiseFloor below which a change is apps rewriting their own caches while Settings was
 * open, not anything the user did.
 */
fun reclaimOutcome(before: Long, after: Long, noiseFloor: Long): ReclaimOutcome {
    val freed = before - after
    return when {
        freed >= noiseFloor -> ReclaimOutcome.Freed
        freed <= -noiseFloor -> ReclaimOutcome.Grew
        else -> ReclaimOutcome.Unchanged
    }
}
