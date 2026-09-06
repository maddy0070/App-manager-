package com.manager.app.data

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Usage access is the one permission that changes what the product can honestly show, so its
 * state is observed rather than assumed. Nothing here ever reports a permission as granted that
 * the system has not actually granted.
 */
class PermissionMonitor(private val context: Context) {

    private val _usageAccess = MutableStateFlow(false)
    val usageAccess: StateFlow<Boolean> = _usageAccess.asStateFlow()

    fun refresh(): Boolean {
        val granted = hasUsageAccess()
        _usageAccess.value = granted
        return granted
    }

    // Deprecated on paper, but still the only way to ask whether this app holds usage access
    // without triggering an op note. Every alternative reports the caller's own permission, which
    // is not the same question.
    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> context.checkCallingOrSelfPermission(
                android.Manifest.permission.PACKAGE_USAGE_STATS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }.getOrDefault(false)

    /**
     * Deep-links straight to this app's row in Usage access when the OEM supports it, and falls
     * back to the plain list rather than failing.
     */
    fun usageAccessIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        },
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
}
