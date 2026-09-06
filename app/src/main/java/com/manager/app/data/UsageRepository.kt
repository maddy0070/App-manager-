package com.manager.app.data

import android.app.usage.UsageStatsManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Usage analytics, straight from UsageStatsManager.
 *
 * Android does not keep unlimited history: daily buckets survive about a week, after which the
 * framework aggregates into coarser buckets. Windows longer than a week are therefore flagged
 * approximate and the UI says so, rather than presenting a precise-looking number that isn't.
 */
class UsageRepository(
    private val context: Context,
    private val permissions: PermissionMonitor,
) {
    private val _snapshot = MutableStateFlow(UsageSnapshot.Empty)
    val snapshot: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()

    private val _window = MutableStateFlow(UsageWindow.Week)
    val window: StateFlow<UsageWindow> = _window.asStateFlow()

    suspend fun load(window: UsageWindow = _window.value): UsageSnapshot {
        _window.value = window
        if (!permissions.hasUsageAccess()) {
            val empty = UsageSnapshot(window, emptyMap(), emptyMap(), false, System.currentTimeMillis())
            _snapshot.value = empty
            return empty
        }
        val result = withContext(Dispatchers.IO) { query(window) }
        _snapshot.value = result
        return result
    }

    private fun query(window: UsageWindow): UsageSnapshot {
        val manager = context.getSystemService(UsageStatsManager::class.java)
            ?: return UsageSnapshot(window, emptyMap(), emptyMap(), false, System.currentTimeMillis())

        val now = System.currentTimeMillis()
        val begin = when (window) {
            UsageWindow.Today -> startOfToday()
            else -> now - TimeUnit.DAYS.toMillis(window.days.toLong())
        }

        val aggregated = runCatching { manager.queryAndAggregateUsageStats(begin, now) }
            .getOrDefault(emptyMap())

        val byPackage = aggregated.mapNotNull { (pkg, stats) ->
            val fg = stats.totalTimeInForeground
            if (fg <= 0L) null else pkg to UsageRecord(pkg, fg, stats.lastTimeUsed)
        }.toMap()

        // "Not opened in a month" needs a much wider lens than the selected window.
        val lastUsedWindowStart = now - TimeUnit.DAYS.toMillis(LAST_USED_LOOKBACK_DAYS)
        val lastUsed = runCatching { manager.queryAndAggregateUsageStats(lastUsedWindowStart, now) }
            .getOrDefault(emptyMap())
            .mapNotNull { (pkg, stats) ->
                val stamp = maxOf(stats.lastTimeUsed, stats.lastTimeStamp)
                if (stamp <= 0L) null else pkg to stamp
            }.toMap()

        return UsageSnapshot(
            window = window,
            byPackage = byPackage,
            lastUsedByPackage = lastUsed,
            approximate = window.approximate,
            generatedAt = now,
        )
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val LAST_USED_LOOKBACK_DAYS = 180L
    }
}
