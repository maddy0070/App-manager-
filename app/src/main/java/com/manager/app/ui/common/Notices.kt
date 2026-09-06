package com.manager.app.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerTextAction
import com.manager.app.design.components.Txt
import com.manager.app.ui.Notice
import com.manager.app.ui.NoticeTone

/**
 * The product's own transient message.
 *
 * Not a Snackbar: an inverse capsule that rises from the same place the navigation bar lives,
 * carries a tone glyph, and leaves the way it came. It never covers the bar it replaces because
 * it is stacked above it.
 */
@Composable
fun NoticeHost(
    notice: Notice?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shown by remember(notice?.id) { mutableStateOf(false) }

    LaunchedEffect(notice?.id) {
        if (notice == null) return@LaunchedEffect
        shown = true
        kotlinx.coroutines.delay(if (notice.action != null) 6_500 else 4_200)
        shown = false
        kotlinx.coroutines.delay(340)
        onDismiss()
    }

    Box(modifier) {
        AnimatedVisibility(
            visible = shown && notice != null,
            enter = slideInVertically(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                initialOffsetY = { it / 2 },
            ) + fadeIn(spring(stiffness = 600f)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
            ),
            exit = slideOutVertically(
                animationSpec = spring(dampingRatio = 1f, stiffness = 700f),
                targetOffsetY = { it / 3 },
            ) + fadeOut(spring(stiffness = 900f)) + scaleOut(targetScale = 0.94f),
        ) {
            if (notice != null) NoticeCard(notice, onDismiss = { shown = false })
        }
    }
}

@Composable
private fun NoticeCard(notice: Notice, onDismiss: () -> Unit) {
    val colors = ManagerTheme.colors
    val accent = when (notice.tone) {
        NoticeTone.Neutral -> colors.onSurfaceInverse
        NoticeTone.Positive -> if (colors.isLight) colors.plot4 else colors.signal
        NoticeTone.Warning -> colors.ember
    }
    val glyph = when (notice.tone) {
        NoticeTone.Neutral -> ManagerIcons.Info
        NoticeTone.Positive -> ManagerIcons.Check
        NoticeTone.Warning -> ManagerIcons.Alert
    }

    Row(
        modifier = Modifier
            .shadow(
                elevation = 22.dp,
                shape = ManagerTheme.shapes.lg,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.45f),
                spotColor = colors.ink.copy(alpha = 0.3f),
            )
            .clip(ManagerTheme.shapes.lg)
            .background(colors.surfaceInverse)
            .padding(start = 18.dp, end = 10.dp, top = 15.dp, bottom = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ManagerIcon(glyph, null, tint = accent, size = 18.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Txt(notice.title, style = ManagerTheme.type.labelS, color = colors.onSurfaceInverse, maxLines = 2)
            if (notice.body != null) {
                Txt(
                    notice.body,
                    style = ManagerTheme.type.metaS,
                    color = colors.onSurfaceInverse.copy(alpha = 0.66f),
                    maxLines = 3,
                )
            }
        }
        if (notice.actionLabel != null && notice.action != null) {
            Spacer(Modifier.width(8.dp))
            ManagerTextAction(
                label = notice.actionLabel,
                onClick = { notice.action.invoke(); onDismiss() },
                color = accent,
            )
        } else {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(8.dp))
        }
    }
}
