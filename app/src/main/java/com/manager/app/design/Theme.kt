package com.manager.app.design

import androidx.compose.foundation.isSystemInDarkTheme
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

/**
 * Every interactive surface here passes `indication = null` and supplies its own press response,
 * so no ripple is ever drawn and none is configured. The trade is that focus indication is not
 * provided either: this is a touch product, and a keyboard or switch user would get no focus ring.
 */
@Composable
fun ManagerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalManagerColors provides colors,
        LocalManagerTypography provides ManagerTypography(),
        LocalManagerShapes provides ManagerShapes(),
        LocalManagerSpacing provides ManagerSpacing(),
        LocalManagerMotion provides ManagerMotion(),
        LocalContentColor provides colors.ink,
        LocalTextStyle provides ManagerTypography().body,
        content = content,
    )
}
