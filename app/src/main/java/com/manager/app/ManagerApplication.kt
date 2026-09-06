package com.manager.app

import android.app.Application
import android.content.Context
import com.manager.app.data.ApkExtractor
import com.manager.app.data.IconLoader
import com.manager.app.data.PackageRepository
import com.manager.app.data.PermissionMonitor
import com.manager.app.data.PreferencesStore
import com.manager.app.data.UninstallCoordinator
import com.manager.app.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A hand-written object graph. The app has one process, one activity and a handful of
 * long-lived collaborators, so a DI framework would add build time and indirection without
 * buying anything. Everything is constructed once and shared.
 */
class ManagerGraph(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val permissions = PermissionMonitor(app)
    val preferences = PreferencesStore(app)
    val icons = IconLoader(app)
    val usage = UsageRepository(app, permissions)
    val packages = PackageRepository(app, permissions, scope)
    val extractor = ApkExtractor(app)
    val uninstalls = UninstallCoordinator(app)
}

class ManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        graph = ManagerGraph(this)
    }

    companion object {
        private var graph: ManagerGraph? = null

        fun graph(): ManagerGraph = graph ?: error("ManagerApplication has not been created")

        /** Broadcast receivers can outlive the graph on cold restore; they must tolerate null. */
        fun graphOrNull(): ManagerGraph? = graph
    }
}
