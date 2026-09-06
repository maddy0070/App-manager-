package com.manager.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.components.Txt

/**
 * The screen header.
 *
 * An eyebrow, a large display title and an optional trailing control. The title is the only
 * display-scale type on a screen, which is what gives each screen a single obvious entry point
 * for the eye.
 */
@Composable
fun ScreenHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Txt(
                eyebrow.uppercase(),
                style = ManagerTheme.type.eyebrow,
                color = ManagerTheme.colors.inkTertiary,
            )
            Spacer(Modifier.height(9.dp))
            Txt(title, style = ManagerTheme.type.displayS, color = ManagerTheme.colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(7.dp))
                Txt(subtitle, style = ManagerTheme.type.bodyS, color = ManagerTheme.colors.inkSecondary)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A section rule: eyebrow on the left, optional count or action on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Txt(
            title.uppercase(),
            style = ManagerTheme.type.eyebrow,
            color = ManagerTheme.colors.inkTertiary,
        )
        trailing?.invoke()
    }
}

/**
 * Staggered entrance for a screen's sections.
 *
 * Children rise a few dp and fade, each one a beat behind the last. The total is capped so a long
 * screen never becomes a slow screen — after six items everything arrives together.
 */
@Composable
fun StaggeredEntrance(
    index: Int,
    modifier: Modifier = Modifier,
    key: Any? = Unit,
    content: @Composable () -> Unit,
) {
    var visible by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) {
        kotlinx.coroutines.delay((index.coerceAtMost(6) * 42L))
        visible = true
    }
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 300f),
        label = "entrance",
    )
    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 22.dp.toPx()
        },
    ) {
        content()
    }
}

/** Vertical rhythm between sections, expressed once so screens cannot drift apart. */
@Composable
fun SectionSpacer(height: Dp = ManagerTheme.space.section) {
    Spacer(Modifier.height(height))
}

/** A hairline that fades out at both ends, used to separate content without boxing it in. */
@Composable
fun FadedRule(modifier: Modifier = Modifier, color: Color = ManagerTheme.colors.hairline) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.1f to color,
                    0.9f to color,
                    1f to Color.Transparent,
                ),
            ),
    )
}
