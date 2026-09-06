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
    val dormant: List<DormantApp>,
    val usageTotalMs: Long,
    val timeline: List<TimelineBucket>,
) {
    val userShare: Float get() = if (total == 0) 0f else userCount.toFloat() / total

    companion object {
        val Empty = Insights(
            total = 0, userCount = 0, systemCount = 0, disabledCount = 0, splitCount = 0,
            installedLastWeek = 0, installedLastMonth = 0, installedLastQuarter = 0,
            updatedLastWeek = 0, totalBytes = 0, userBytes = 0, systemBytes = 0,
            storageIsMeasured = false, recentlyInstalled = emptyList(), recentlyUpdated = emptyList(),
            largest = emptyList(), topUsed = emptyList(), dormant = emptyList(),
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
        .take(8)
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
        dormant = dormant,
        usageTotalMs = usageTotal,
        timeline = buildTimeline(apps, now),
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
