package com.manager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.manager.app.data.ThemeMode
import com.manager.app.design.ManagerTheme
import com.manager.app.ui.ActivityRequest
import com.manager.app.ui.ActivityRequestKind
import com.manager.app.ui.AppRoot
import com.manager.app.ui.ManagerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var graph: ManagerGraph
    private var viewModelRef: ManagerViewModel? = null

    /**
     * The system uninstall screen. Its result code is unreliable across OEMs, so the outcome is
     * confirmed against the package list rather than trusted.
     */
    private val uninstallLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModelRef?.onUninstallScreenClosed() }

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModelRef?.onReturnFromSettings() }

    /**
     * The system storage screens. Their result code says nothing about whether anything was
     * cleared — Android does not report that — so the return simply triggers a re-measure.
     */
    private val cacheLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModelRef?.onReturnFromCacheCleanup() }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        graph = ManagerApplication.graph()

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Hold the splash until the app genuinely has something to show: the stored preferences
        // (so onboarding never flashes) and a first package scan. A hard ceiling guarantees the
        // splash can never become the experience.
        var packagesReady = false
        var preferencesReady = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                graph.packages.state.collectLatest { state ->
                    if (!state.loading || state.hasApps) packagesReady = true
                }
            }
        }
        lifecycleScope.launch {
            graph.preferences.preferences.first()
            preferencesReady = true
        }
        lifecycleScope.launch {
            delay(SPLASH_CEILING_MS)
            packagesReady = true
            preferencesReady = true
        }
        splash.setKeepOnScreenCondition { !(packagesReady && preferencesReady) }

        // Hand off rather than cut: the splash mark lifts and dissolves into the app canvas.
        splash.setOnExitAnimationListener { provider ->
            val view = provider.iconView
            val fade = android.animation.ObjectAnimator.ofFloat(view, android.view.View.ALPHA, 1f, 0f)
            val lift = android.animation.ObjectAnimator.ofFloat(view, android.view.View.TRANSLATION_Y, 0f, -48f)
            val grow = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                view,
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 1f, 1.14f),
                android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 1f, 1.14f),
            )
            android.animation.AnimatorSet().apply {
                playTogether(fade, lift, grow)
                duration = 420
                interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
                doOnEnd { provider.remove() }
                start()
            }
        }

        setContent {
            val viewModel: ManagerViewModel = viewModel(factory = ManagerViewModel.Factory(graph))
            viewModelRef = viewModel
            val preferences by viewModel.preferences.collectAsState()

            val dark = when (preferences.themeMode) {
                ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            androidx.compose.runtime.LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                viewModel.activityIntents.collect(::dispatch)
            }

            // Driven from the composition rather than Activity.onStart: on a cold launch the
            // activity is already started before setContent has composed anything, so an
            // activity-side call would be delivered to a view model that does not exist yet.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) viewModel.onStart()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            ManagerTheme(dark = dark) {
                AppRoot(viewModel = viewModel, graph = graph)
            }
        }
    }

    /**
     * Tries each candidate in order and stops at the first that opens.
     *
     * System screens are not uniform across OEM builds — a device may have no direct cache dialog,
     * or no per-app details page reachable by that action. Falling through the list is what turns
     * "this vendor removed that screen" from a dead end into a slightly less direct route.
     */
    private fun dispatch(request: ActivityRequest) {
        val opened = request.intents.any { intent ->
            runCatching {
                when (request.kind) {
                    ActivityRequestKind.UsageSettings -> settingsLauncher.launch(intent)
                    // The confirmation must return here so the uninstall queue can advance.
                    ActivityRequestKind.Uninstall -> uninstallLauncher.launch(intent)
                    // Returns so the cache can be re-measured and the real difference reported.
                    ActivityRequestKind.CacheCleanup -> cacheLauncher.launch(intent)
                    ActivityRequestKind.General -> startActivity(intent)
                }
            }.isSuccess
        }
        if (opened) return

        // Nothing resolved. Anything waiting on a return will never get one, so release it.
        if (request.kind == ActivityRequestKind.CacheCleanup) viewModelRef?.abandonCacheCleanup()
        viewModelRef?.notify(
            com.manager.app.ui.Notice(
                id = System.nanoTime(),
                title = "Nothing could handle that",
                body = "No screen on this device responded to the request.",
                tone = com.manager.app.ui.NoticeTone.Warning,
            ),
        )
    }

    private companion object {
        const val TRANSPARENT = 0x00000000
        const val SPLASH_CEILING_MS = 2_200L
    }
}
