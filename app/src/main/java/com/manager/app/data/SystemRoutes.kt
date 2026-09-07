package com.manager.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings

/**
 * The doors Android actually leaves open.
 *
 * Manager cannot clear another application's cache, and no amount of design makes that untrue.
 * `PackageManager.deleteApplicationCacheFiles` is a system API; `freeStorageAndNotify` needs
 * `CLEAR_APP_CACHE`, which has been signature-or-privileged since API 26. A normal, sideloaded,
 * consumer install holds neither and never will.
 *
 * What a normal app *can* do is everything either side of the privileged step: measure the cache
 * precisely, rank it, put the user in front of the exact system screen that can act on it, and —
 * this is the part nobody bothers with — measure again afterwards and report what actually came
 * back. That is the product here. The privileged operation stays Android's; the intelligence and
 * the accounting are Manager's.
 *
 * Each route is a list of candidates in descending order of directness, because OEM builds vary in
 * which system screens they expose. The caller tries them in order and only fails if none resolve.
 */
object SystemRoutes {

    /**
     * The device-wide cache screen.
     *
     * On API 31+ Android has a purpose-built dialog for exactly this, which is as close to
     * "clear all caches" as a consumer app is ever allowed to get. It is listed first and simply
     * falls through on the builds where it is absent or refuses.
     */
    fun clearCache(): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Intent(StorageManager.ACTION_CLEAR_APP_CACHE))
        }
        add(Intent(StorageManager.ACTION_MANAGE_STORAGE))
        add(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    /** One app's own storage page, where its cache can be cleared individually. */
    fun appStorage(packageName: String): List<Intent> = listOf(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
        Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
    )

    /**
     * Whether the direct cache dialog exists on this build. Used to word the UI accurately rather
     * than to decide what to launch — the fallbacks handle that.
     */
    fun hasDirectCacheDialog(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
                .resolveActivity(context.packageManager) != null
        }.getOrDefault(false)
    }
}
