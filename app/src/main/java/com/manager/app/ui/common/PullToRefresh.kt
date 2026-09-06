package com.manager.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import kotlin.math.pow
import kotlinx.coroutines.launch

/**
 * Refresh, in the product's own language.
 *
 * There is no spinner. Pulling down assembles the Manager mark tile by tile — the further you
 * pull, the more of the mark exists — and the whole page travels with your finger against a
 * softening resistance curve. Releasing past the threshold completes the mark, holds it while the
 * scan runs, then lets the page fall back into place. It reads as the app reconstituting itself,
 * which is exactly what a rescan is.
 */
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { THRESHOLD.toPx() }
    val maxPx = with(density) { MAX_TRAVEL.toPx() }

    val offset = remember { Animatable(0f) }
    var pull by remember { mutableFloatStateOf(0f) }
    val currentRefreshing by rememberUpdatedState(refreshing)

    // While a refresh is running the cradle parks at the threshold; when it finishes it retracts.
    LaunchedEffect(refreshing) {
        if (refreshing) {
            offset.animateTo(thresholdPx, spring(dampingRatio = 0.8f, stiffness = 500f))
        } else {
            pull = 0f
            offset.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 380f))
        }
    }

    val connection = remember(thresholdPx, maxPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Upward drags first pay back whatever pull is outstanding.
                if (available.y < 0 && pull > 0f) {
                    val consumed = kotlin.math.min(-available.y, pull)
                    pull -= consumed
                    scope.launch { offset.snapTo(resist(pull, maxPx)) }
                    return Offset(0f, -consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (currentRefreshing) return Offset.Zero
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    pull += available.y
                    scope.launch { offset.snapTo(resist(pull, maxPx)) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (currentRefreshing) return Velocity.Zero
                val travelled = offset.value
                pull = 0f
                return if (travelled >= thresholdPx) {
                    onRefresh()
                    Velocity(0f, available.y.coerceAtMost(0f))
                } else if (travelled > 0f) {
                    offset.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = 520f))
                    Velocity.Zero
                } else {
                    Velocity.Zero
                }
            }
        }
    }

    Box(modifier.nestedScroll(connection)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = offset.value },
            content = content,
        )
        RefreshCradle(
            travel = offset.value,
            threshold = thresholdPx,
            active = refreshing,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(WindowInsets.statusBars.asPaddingValues()),
        )
    }
}

/** Pull travel with diminishing returns, so the page can never be dragged arbitrarily far. */
private fun resist(pull: Float, max: Float): Float {
    if (pull <= 0f) return 0f
    val normalised = (pull / max).coerceAtMost(4f)
    return max * (1f - (1f - normalised.coerceAtMost(1f)).pow(2.2f)) * 0.92f
}

/**
 * The mark, assembling. Each of the four tiles crosses its own threshold as the pull deepens, and
 * the whole thing turns a few degrees so the completed state is unmistakable without a label.
 */
@Composable
private fun RefreshCradle(
    travel: Float,
    threshold: Float,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    if (travel <= 0.5f) return
    val colors = ManagerTheme.colors
    val progress = (travel / threshold).coerceIn(0f, 1.35f)
    val armed = progress >= 1f

    val spin = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            // A slow, continuous quarter-turn cadence while the scan runs — never a spinner.
            while (true) {
                spin.animateTo(spin.value + 90f, spring(dampingRatio = 0.65f, stiffness = 130f))
            }
        } else {
            spin.snapTo(0f)
        }
    }

    Box(
        modifier
            .padding(top = 14.dp)
            .graphicsLayer {
                alpha = (progress * 1.6f).coerceAtMost(1f)
                translationY = -(1f - progress.coerceAtMost(1f)) * 10f
                rotationZ = spin.value + (if (active) 0f else progress * 12f)
                val s = 0.7f + 0.3f * progress.coerceAtMost(1f)
                scaleX = s
                scaleY = s
            },
    ) {
        Box(Modifier.size(34.dp)) {
            val tiles = listOf(
                Triple(Alignment.TopStart, 18.dp, 0.12f),
                Triple(Alignment.TopEnd, 11.dp, 0.42f),
                Triple(Alignment.BottomStart, 11.dp, 0.66f),
                Triple(Alignment.BottomEnd, 11.dp, 0.88f),
            )
            tiles.forEachIndexed { index, (alignment, size, appearAt) ->
                val local = ((progress - appearAt) / 0.24f).coerceIn(0f, 1f)
                if (local <= 0.01f) return@forEachIndexed
                Box(
                    Modifier
                        .align(alignment)
                        .size(size)
                        .graphicsLayer {
                            alpha = local
                            scaleX = 0.5f + 0.5f * local
                            scaleY = 0.5f + 0.5f * local
                        }
                        .clip(SquircleShape(size * 0.34f, 0.8f))
                        .background(
                            when {
                                index == 3 -> if (armed || active) colors.ember else colors.ember.copy(alpha = 0.35f)
                                index == 0 -> colors.signal
                                else -> colors.signal.copy(alpha = 0.3f)
                            },
                        ),
                )
            }
        }
    }
}

private val THRESHOLD = 78.dp
private val MAX_TRAVEL = 150.dp
