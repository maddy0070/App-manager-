package com.manager.app.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Uninstalls go through PackageInstaller, which means Android — not Manager — shows the
 * confirmation for every single package. That is not a limitation to work around; it is the
 * platform's guarantee to the user, so the UI is designed around a queue of system prompts
 * rather than pretending a silent bulk uninstall is possible.
 */
class UninstallCoordinator(private val context: Context) {

    sealed interface Event {
        /** Android is showing (or about to show) its own confirmation for this package. */
        data class AwaitingConfirmation(val packageName: String, val intent: Intent) : Event
        data class Removed(val packageName: String) : Event
        data class Declined(val packageName: String) : Event
        data class Failed(val packageName: String, val reason: String) : Event
    }

    private val channel = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = channel.receiveAsFlow()

    fun request(packageName: String) {
        val installer = context.packageManager.packageInstaller
        val intent = Intent(context, UninstallResultReceiver::class.java).apply {
            action = ACTION_RESULT
            putExtra(EXTRA_PACKAGE, packageName)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching { installer.uninstall(packageName, pending.intentSender) }
            .onFailure {
                channel.trySend(
                    Event.Failed(packageName, it.message ?: "Android refused the uninstall request."),
                )
            }
    }

    internal fun deliver(intent: Intent) {
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: return
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    channel.trySend(Event.AwaitingConfirmation(pkg, confirm))
                } else {
                    channel.trySend(Event.Failed(pkg, "Android did not provide a confirmation screen."))
                }
            }

            PackageInstaller.STATUS_SUCCESS -> channel.trySend(Event.Removed(pkg))

            PackageInstaller.STATUS_FAILURE_ABORTED -> channel.trySend(Event.Declined(pkg))

            PackageInstaller.STATUS_FAILURE_BLOCKED ->
                channel.trySend(Event.Failed(pkg, "Android blocked this uninstall. System packages and device-policy apps cannot be removed."))

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                channel.trySend(Event.Failed(pkg, message?.takeIf { it.isNotBlank() } ?: "The uninstall did not complete."))
            }
        }
    }

    /** True when the package is genuinely gone — used to confirm after the system screen closes. */
    fun stillInstalled(packageName: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        true
    }.getOrDefault(false)

    companion object {
        const val ACTION_RESULT = "com.manager.app.UNINSTALL_RESULT"
        const val EXTRA_PACKAGE = "com.manager.app.EXTRA_PACKAGE"
    }
}

/** Manifest-declared so the result survives the confirmation screen taking focus. */
class UninstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        com.manager.app.ManagerApplication.graphOrNull()?.uninstalls?.deliver(intent)
    }
}
