package com.manager.app.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.data.AppEntry
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.AppIcon
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIconButton
import com.manager.app.design.components.Txt
import com.manager.app.ui.DetailRequest
import com.manager.app.ui.ManagerViewModel
import kotlinx.coroutines.launch

/**
 * The detail surface — the product's signature interaction.
 *
 * Tapping an app does not push a screen or raise a dialog. The row's own icon flies from where
 * your finger was into the header of a floating panel, which rises from below while the page
 * behind it dims and settles back a fraction. When the flight lands, the flying icon and the
 * header icon crossfade at identical size and position, so there is no moment where the app you
 * touched is not the app you are looking at.
 *
 * Closing runs the same path in reverse, and a downward drag on the panel scrubs it directly —
 * dismissal is a gesture with weight, not a button that makes something vanish.
 */
@Composable
fun AppDetailSurface(
    request: DetailRequest?,
    viewModel: ManagerViewModel,
    graph: ManagerGraph,
    onDismiss: () -> Unit,
    onExtract: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
) {
    // Keep the last request alive through the exit animation so content does not blank out.
    var rendered by remember { mutableStateOf(request) }
    LaunchedEffect(request) { if (request != null) rendered = request }

    val progress = remember { Animatable(0f) }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val exitEase = ManagerTheme.motion.exit

    LaunchedEffect(request) {
        if (request != null) {
            drag.snapTo(0f)
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
        } else {
            progress.animateTo(0f, tween(240, easing = exitEase))
            drag.snapTo(0f)
            rendered = null
        }
    }

    val entry = rendered?.entry ?: return
    val open = progress.value
    if (open <= 0.001f && request == null) return

    val colors = ManagerTheme.colors
    var headerIconBounds by remember(entry.packageName) { mutableStateOf<Rect?>(null) }

    val usage by viewModel.usage.collectAsState()
    val usageRecord = usage.byPackage[entry.packageName]
    val lastUsed = usage.lastUsedByPackage[entry.packageName]

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.82f
        val dragPx = drag.value
        val dismissThreshold = with(density) { 120.dp.toPx() }
        val dragFraction = (dragPx / (dismissThreshold * 2.2f)).coerceIn(0f, 1f)

        // ---- Scrim ------------------------------------------------------------------------
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = open * (1f - dragFraction * 0.6f) }
                .background(colors.scrim.copy(alpha = if (colors.isLight) 0.34f else 0.56f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .clearAndSetSemantics { },
        )

        // ---- The panel --------------------------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .graphicsLayer {
                    val rise = (1f - open) * 90.dp.toPx()
                    translationY = rise + dragPx
                    alpha = (open * 1.4f).coerceAtMost(1f)
                    val scale = 0.94f + 0.06f * open - dragFraction * 0.03f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp)
                .shadow(
                    elevation = 34.dp,
                    shape = ManagerTheme.shapes.sheet,
                    clip = false,
                    ambientColor = colors.ink.copy(alpha = 0.5f),
                    spotColor = colors.ink.copy(alpha = 0.36f),
                )
                .clip(ManagerTheme.shapes.sheet)
                .background(colors.surface)
                .pointerInput(entry.packageName) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (drag.value > dismissThreshold) {
                                onDismiss()
                            } else {
                                scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
                            }
                        },
                        onDragCancel = {
                            scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f)) }
                        },
                    ) { _, delta ->
                        scope.launch {
                            // Upward drag is resisted heavily: this sheet does not expand.
                            val next = if (delta > 0) drag.value + delta else drag.value + delta * 0.22f
                            drag.snapTo(next.coerceAtLeast(0f))
                        }
                    }
                },
        ) {
            DetailContent(
                entry = entry,
                graph = graph,
                usageMs = usageRecord?.foregroundMs,
                usageWindowLabel = usage.window.label,
                lastUsed = lastUsed,
                open = open,
                // The header icon only takes over once the flight has essentially landed, so the
                // two are never visible as two.
                headerIconAlpha = if (rendered?.originBounds == null) 1f
                else ((open - 0.8f) / 0.2f).coerceIn(0f, 1f),
                onHeaderIconBounds = { headerIconBounds = it },
                onDismiss = onDismiss,
                onExtract = { onExtract(entry) },
                onUninstall = { onUninstall(entry) },
                maxHeight = maxSheetHeight,
            )
        }

        // ---- The flying icon ----------------------------------------------------------------
        // Drawn above everything so it can cross from the list into the panel without being
        // clipped by either.
        FlyingIcon(
            entry = entry,
            graph = graph,
            origin = rendered?.originBounds,
            target = headerIconBounds,
            progress = open,
            dragOffset = dragPx,
        )
    }
}

/**
 * The shared element.
 *
 * Position and size are interpolated between the row's icon and the panel's header icon on an
 * eased curve, while opacity hands over in the last quarter of the flight. Interpolating the
 * rectangle rather than cross-dissolving two images is what makes it read as one object moving.
 */
@Composable
private fun FlyingIcon(
    entry: AppEntry,
    graph: ManagerGraph,
    origin: Rect?,
    target: Rect?,
    progress: Float,
    dragOffset: Float,
) {
    if (origin == null || target == null) return
    if (progress >= 0.985f || progress <= 0.001f) return

    val density = LocalDensity.current
    val eased = ManagerTheme.motion.emphasized.transform(progress.coerceIn(0f, 1f))

    val left = origin.left + (target.left - origin.left) * eased
    val top = origin.top + (target.top - origin.top) * eased
    val size = origin.width + (target.width - origin.width) * eased

    Box(
        Modifier
            .graphicsLayer {
                translationX = left
                translationY = top + dragOffset * eased
                alpha = 1f
            }
            .size(with(density) { size.toDp() })
            .clearAndSetSemantics { },
    ) {
        AppIcon(
            packageName = entry.packageName,
            label = entry.label,
            loader = graph.icons,
            size = with(density) { size.toDp() },
        )
    }
}
