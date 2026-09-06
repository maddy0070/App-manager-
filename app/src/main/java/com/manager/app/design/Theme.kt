package com.manager.app.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection

/**
 * The product's only theme entry point. Nothing in the UI reads a Material colour scheme; every
 * surface pulls from [ManagerTheme] so the visual system stays in one place.
 */
object ManagerTheme {
    val colors: ManagerColors
        @Composable @ReadOnlyComposable get() = LocalManagerColors.current
    val type: ManagerTypography
        @Composable @ReadOnlyComposable get() = LocalManagerTypography.current
    val shapes: ManagerShapes
        @Composable @ReadOnlyComposable get() = LocalManagerShapes.current
    val space: ManagerSpacing
        @Composable @ReadOnlyComposable get() = LocalManagerSpacing.current
    val motion: ManagerMotion
        @Composable @ReadOnlyComposable get() = LocalManagerMotion.current
}

val LocalContentColor = staticCompositionLocalOf { Color.Black }
val LocalTextStyle = staticCompositionLocalOf { TextStyle(textDirection = TextDirection.Content) }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ManagerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    // Ripple exists only as an accessibility affordance for talkback/keyboard; every interactive
    // surface in this product carries its own designed press response on top.
    val ripple = RippleConfiguration(
        color = colors.ink,
        rippleAlpha = RippleAlpha(
            draggedAlpha = 0.04f,
            focusedAlpha = 0.06f,
            hoveredAlpha = 0.03f,
            pressedAlpha = 0.035f,
        ),
    )
    CompositionLocalProvider(
        LocalManagerColors provides colors,
        LocalManagerTypography provides ManagerTypography(),
        LocalManagerShapes provides ManagerShapes(),
        LocalManagerSpacing provides ManagerSpacing(),
        LocalManagerMotion provides ManagerMotion(),
        LocalContentColor provides colors.ink,
        LocalTextStyle provides ManagerTypography().body,
        LocalRippleConfiguration provides ripple,
        content = content,
    )
}
