package com.manager.app.data

import androidx.compose.runtime.Immutable

/** What Android could actually tell us about a package's storage. Absent fields stay absent. */
@Immutable
data class StorageBreakdown(
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
) {
    val total: Long get() = appBytes + dataBytes
}

@Immutable
data class AppEntry(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val installedAt: Long,
    val updatedAt: Long,
    val isSystem: Boolean,
    /** A system app that has since received an update — its updates can be removed. */
    val isUpdatedSystemApp: Boolean,
    val isEnabled: Boolean,
    val isDebuggable: Boolean,
    val minSdk: Int,
    val targetSdk: Int,
    val sourceDir: String?,
    val splitSourceDirs: List<String>,
    val installerPackage: String?,
    val hasLaunchIntent: Boolean,
    /** Sum of every APK file on disk. Always known, needs no permission. */
    val apkBytes: Long,
    /** Null until StorageStatsManager has answered, or if it refused. */
    val storage: StorageBreakdown? = null,
    /** Null when Android will not let us read the OBB directory; 0L when genuinely absent. */
    val obbBytes: Long? = null,
) {
    val isSplit: Boolean get() = splitSourceDirs.isNotEmpty()
    val splitCount: Int get() = splitSourceDirs.size
    /** Best available total: real stats when granted, APK size otherwise. */
    val totalBytes: Long get() = (storage?.total ?: apkBytes) + (obbBytes ?: 0L)
    val isUninstallable: Boolean get() = !isSystem || isUpdatedSystemApp
}

@Immutable
data class UsageRecord(
    val packageName: String,
    val foregroundMs: Long,
    val lastUsed: Long,
)

/** Snapshot of usage for one window, plus the honest truth about where it came from. */
@Immutable
data class UsageSnapshot(
    val window: UsageWindow,
    val byPackage: Map<String, UsageRecord>,
    val lastUsedByPackage: Map<String, Long>,
    val approximate: Boolean,
    val generatedAt: Long,
) {
    companion object {
        val Empty = UsageSnapshot(UsageWindow.Week, emptyMap(), emptyMap(), false, 0L)
    }
}

enum class UsageWindow(val label: String, val shortLabel: String, val days: Int, val approximate: Boolean) {
    Today("Today", "1D", 1, false),
    ThreeDays("Last 3 days", "3D", 3, false),
    Week("Last 7 days", "7D", 7, false),
    Month("Last 30 days", "30D", 30, true),
}

enum class AppFilter(val label: String) {
    All("All apps"),
    User("User apps"),
    System("System apps"),
    RecentlyInstalled("Recently installed"),
    RecentlyUpdated("Recently updated"),
    Largest("Largest"),
    Dormant("Least used"),
}

enum class SortKey(val label: String) {
    Name("Name"),
    Size("Size"),
    Installed("Install date"),
    Updated("Last updated"),
    Usage("Usage time"),
}

enum class SortDirection { Ascending, Descending }

/** Whether a scan is still filling in the expensive parts. */
@Immutable
data class InventoryState(
    val apps: List<AppEntry> = emptyList(),
    val loading: Boolean = true,
    /** Storage detail arrives after the first paint; this is how far along it is. */
    val detailProgress: Float = 0f,
    val scannedAt: Long = 0L,
    val error: String? = null,
) {
    val hasApps: Boolean get() = apps.isNotEmpty()
}
