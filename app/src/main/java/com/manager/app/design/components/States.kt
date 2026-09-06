package com.manager.app.design.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.iconTileShape

/**
 * Skeletons rather than spinners.
 *
 * A sweep of slightly lighter tone travels across the placeholder every 1.5s. It is deliberately
 * low-contrast: the point is to say "this shape is coming", not to draw the eye to the wait.
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    shape: Shape = ManagerTheme.shapes.xs,
) {
    val colors = ManagerTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val phase by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonPhase",
    )
    val base = if (colors.isLight) colors.canvasSunken else colors.surfaceMuted
    val highlight = if (colors.isLight) colors.surface else colors.surfaceRaised

    Box(
        modifier
            .clip(shape)
            .background(base)
            .drawWithCache {
                val width = size.width.coerceAtLeast(1f)
                val start = phase * width
                val brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to highlight.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                    startX = start - width * 0.45f,
                    endX = start + width * 0.45f,
                )
                onDrawWithContent {
                    drawContent()
                    drawRect(brush)
                }
            },
    )
}

/** The list's loading state: the shape of the rows that are coming, at the right rhythm. */
@Composable
fun AppRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(
            Modifier.size(46.dp),
            shape = iconTileShape(46.dp, ManagerTheme.shapes.iconSmoothing),
        )
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Skeleton(Modifier.width(148.dp).height(13.dp), ManagerTheme.shapes.capsule)
            Skeleton(Modifier.width(96.dp).height(10.dp), ManagerTheme.shapes.capsule)
        }
        Spacer(Modifier.weight(1f))
        Skeleton(Modifier.width(46.dp).height(11.dp), ManagerTheme.shapes.capsule)
    }
}

/**
 * Every empty state in the product says three things: what shape the missing content has, why it
 * is missing, and what to do next. The mark is drawn from the app's own geometry, never a stock
 * glyph on grey.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    tone: ButtonTone = ButtonTone.Quiet,
    contentPadding: PaddingValues = PaddingValues(horizontal = 34.dp, vertical = 44.dp),
) {
    val colors = ManagerTheme.colors
    Column(
        modifier = modifier.padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyStateMark(icon)
        Spacer(Modifier.height(26.dp))
        Txt(
            title,
            style = ManagerTheme.type.titleM,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Txt(
            body,
            style = ManagerTheme.type.body,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            ManagerButton(actionLabel, onAction, tone = tone, compact = true)
        }
    }
}

/**
 * The empty-state mark: three offset squircles from the brand geometry, the front one carrying
 * the message's icon. It is the same motif as the launcher, at rest.
 */
@Composable
private fun EmptyStateMark(icon: ImageVector?) {
    val colors = ManagerTheme.colors
    Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .offset(x = (-19).dp, y = (-15).dp)
                .size(32.dp)
                .clip(SquircleShape(11.dp, 0.8f))
                .background(colors.signalSoft),
        )
        Box(
            Modifier
                .offset(x = 19.dp, y = 15.dp)
                .size(32.dp)
                .clip(SquircleShape(11.dp, 0.8f))
                .background(colors.signalSoftStrong),
        )
        Box(
            Modifier
                .size(46.dp)
                .clip(SquircleShape(15.dp, 0.8f))
                .background(colors.signal),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                ManagerIcon(icon, null, tint = colors.onSignal, size = 21.dp)
            }
        }
    }
}

/**
 * A determinate bar used for extraction and for the storage-detail scan. It never resets to
 * zero mid-operation, so the user can trust its direction.
 */
@Composable
fun ProgressRail(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    track: Color = ManagerTheme.colors.canvasSunken,
    fill: Color = ManagerTheme.colors.signal,
) {
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 240f),
        label = "progressRail",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(ManagerTheme.shapes.capsule)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(ManagerTheme.shapes.capsule)
                .background(fill),
        )
    }
}
