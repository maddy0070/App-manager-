package com.manager.app.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape

/**
 * The Manager mark, in code.
 *
 * A bento of squircle tiles: one hero and three companions, one of them lit in ember. It is the
 * app's own geometry at its simplest — the same corner smoothing as every card in the product,
 * which is why the icon and the interface read as one object.
 *
 * When [animated] the tiles arrive in reading order on a soft spring, which is what the splash
 * hands off into.
 */
@Composable
fun ManagerMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    heroColor: Color = ManagerTheme.colors.signal,
    companionColor: Color = ManagerTheme.colors.signal.copy(alpha = 0.26f),
    accentColor: Color = ManagerTheme.colors.ember,
    animated: Boolean = false,
) {
    val unit = size / 100f
    val hero = unit * 52
    val small = unit * 30
    val gap = unit * 6

    val tiles = listOf(
        Triple(Alignment.TopStart, hero, heroColor),
        Triple(Alignment.TopEnd, small, companionColor),
        Triple(Alignment.BottomStart, small, companionColor),
        Triple(Alignment.BottomEnd, small, accentColor),
    )

    val entrances = tiles.indices.map { remember { Animatable(if (animated) 0f else 1f) } }
    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        entrances.forEachIndexed { index, animatable ->
            kotlinx.coroutines.delay(index * 62L)
            animatable.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 480f))
        }
    }

    Box(modifier.size(size + gap)) {
        tiles.forEachIndexed { index, (alignment, tileSize, color) ->
            val progress = entrances[index].value
            Box(
                Modifier
                    .align(alignment)
                    .size(tileSize)
                    .graphicsLayer {
                        scaleX = 0.55f + 0.45f * progress
                        scaleY = 0.55f + 0.45f * progress
                        alpha = progress
                    }
                    .clip(SquircleShape(tileSize * 0.35f, 0.8f))
                    .background(color),
            )
        }
    }
}
