package com.manager.app.design.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.data.IconLoader
import com.manager.app.design.ManagerTheme
import com.manager.app.design.iconTileShape

/**
 * The real icon of a real installed app, loaded off the main thread and faded in.
 *
 * Icons already in the cache appear instantly with no animation — otherwise a scroll would
 * flicker with hundreds of fades. Only a genuinely new decode gets the entrance.
 */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    loader: IconLoader,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
) {
    val cached = remember(packageName) { loader.peek(packageName) }
    var bitmap by remember(packageName) { mutableStateOf(cached) }
    val inspection = LocalInspectionMode.current

    LaunchedEffect(packageName) {
        if (bitmap == null && !inspection) {
            bitmap = loader.load(packageName)
        }
    }

    val appeared = bitmap != null
    val alpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(dampingRatio = 1f, stiffness = 700f),
        label = "iconFade",
    )
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f),
        label = "iconScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        IconPlaceholder(size = size, visible = !appeared, seed = packageName)
        val image: ImageBitmap? = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        this.alpha = if (cached != null) 1f else alpha
                        val s = if (cached != null) 1f else scale
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

/**
 * The waiting state for an icon: a tinted squircle whose hue is derived from the package name,
 * so the layout never jumps and the placeholder still carries a hint of identity.
 */
@Composable
fun IconPlaceholder(size: Dp, visible: Boolean, seed: String, modifier: Modifier = Modifier) {
    if (!visible) return
    val colors = ManagerTheme.colors
    val tint = remember(seed, colors.isLight) {
        val palette = listOf(colors.plot2, colors.plot3, colors.plot4, colors.plot5)
        palette[(seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }) % palette.size]
    }
    Box(
        modifier
            .size(size)
            .clip(iconTileShape(size, ManagerTheme.shapes.iconSmoothing))
            .background(tint.copy(alpha = if (colors.isLight) 0.16f else 0.22f)),
    )
}
