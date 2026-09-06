package com.manager.app.domain

import com.manager.app.data.AppEntry
import com.manager.app.data.AppFilter
import com.manager.app.data.SortDirection
import com.manager.app.data.SortKey
import com.manager.app.data.UsageSnapshot
import com.manager.app.util.Format
import java.util.concurrent.TimeUnit

/**
 * Filtering, searching and sorting as pure functions over a snapshot.
 *
 * Kept out of the view model so the behaviour the user feels most directly — what typing two
 * letters produces — can be tested without a device in the loop.
 */
fun List<AppEntry>.applyFilter(
    filter: AppFilter,
    usage: UsageSnapshot,
    now: Long = System.currentTimeMillis(),
): List<AppEntry> = when (filter) {
    AppFilter.All -> this
    AppFilter.User -> filter { !it.isSystem }
    AppFilter.System -> filter { it.isSystem }
    AppFilter.RecentlyInstalled -> filter { it.installedAt >= now - TimeUnit.DAYS.toMillis(30) }
    AppFilter.RecentlyUpdated -> filter {
        it.updatedAt >= now - TimeUnit.DAYS.toMillis(14) &&
            it.updatedAt > it.installedAt + TimeUnit.MINUTES.toMillis(1)
    }
    // "Largest" is an ordering, not a subset — the sort that comes with it does the work.
    AppFilter.Largest -> this
    AppFilter.Dormant -> filter { entry ->
        if (entry.isSystem || !entry.hasLaunchIntent) return@filter false
        val last = usage.lastUsedByPackage[entry.packageName]
        last == null || Format.daysBetween(last, now) >= Insights.DORMANT_DAYS
    }
}

/**
 * Search ranks matches rather than merely filtering.
 *
 * A label that starts with the query beats one that merely contains it; a word inside the label
 * beats a package-name hit; and within a tier the shorter name wins, because a three-letter query
 * that fills a four-letter name is a better answer than one buried in a longer one. That ordering
 * is the whole reason typing "map" surfaces Maps before Mapper Studio, and both before something
 * whose package merely contains "map".
 */
fun List<AppEntry>.applySearch(rawQuery: String): List<AppEntry> {
    val query = rawQuery.trim().lowercase()
    if (query.isEmpty()) return this
    return asSequence()
        .mapNotNull { entry -> matchScore(entry, query)?.let { it to entry } }
        .sortedWith(compareBy({ it.first }, { it.second.label.length }, { it.second.label.lowercase() }))
        .map { it.second }
        .toList()
}

private fun matchScore(entry: AppEntry, query: String): Int? {
    val label = entry.label.lowercase()
    val pkg = entry.packageName.lowercase()
    return when {
        label == query -> 0
        label.startsWith(query) -> 1
        label.split(' ', '.', '-', '_').any { it.startsWith(query) } -> 2
        label.contains(query) -> 3
        pkg.startsWith(query) -> 4
        pkg.contains(query) -> 5
        else -> null
    }
}

fun List<AppEntry>.applySort(
    key: SortKey,
    direction: SortDirection,
    usage: UsageSnapshot,
): List<AppEntry> {
    val comparator: Comparator<AppEntry> = when (key) {
        SortKey.Name -> compareBy { it.label.lowercase() }
        SortKey.Size -> compareBy { it.totalBytes }
        SortKey.Installed -> compareBy { it.installedAt }
        SortKey.Updated -> compareBy { it.updatedAt }
        SortKey.Usage -> compareBy { usage.byPackage[it.packageName]?.foregroundMs ?: 0L }
    }
    // Ties fall back to name so the order is stable across rescans.
    val stable = comparator.thenBy { it.label.lowercase() }
    return sortedWith(if (direction == SortDirection.Ascending) stable else stable.reversed())
}
