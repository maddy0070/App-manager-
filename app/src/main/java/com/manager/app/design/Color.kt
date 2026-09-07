package com.manager.app.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Bone & Evergreen" — the single palette for Manager.
 *
 * Foundation: a warm paper neutral rather than clinical white, so surfaces read as printed
 * matter instead of chrome. Signal: a deep evergreen used only for identity, selection and
 * primary action. Ember: reserved exclusively for destructive intent and for the single
 * highlighted value in a visualisation. No third accent exists on purpose.
 */
@Immutable
data class ManagerColors(
    val canvas: Color,
    val canvasSunken: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val surfaceInverse: Color,
    val onSurfaceInverse: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val inkDisabled: Color,
    val signal: Color,
    val signalPressed: Color,
    val signalSoft: Color,
    val signalSoftStrong: Color,
    val onSignal: Color,
    val ember: Color,
    val emberSoft: Color,
    val onEmber: Color,
    /**
     * Ember as it must appear on an inverse surface. The same hue at the lightness that surface
     * demands — a destructive action nobody can read is worse than one that is slightly brighter.
     */
    val emberOnInverse: Color,
    val scrim: Color,
    val plot1: Color,
    val plot2: Color,
    val plot3: Color,
    val plot4: Color,
    val plot5: Color,
    val isLight: Boolean,
)

val LightColors = ManagerColors(
    canvas = Color(0xFFF6F3EE),
    canvasSunken = Color(0xFFEDE9E1),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFFBF9F5),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceInverse = Color(0xFF16453C),
    onSurfaceInverse = Color(0xFFF1F5F2),
    hairline = Color(0xFFE7E2D9),
    hairlineStrong = Color(0xFFD5CFC2),
    ink = Color(0xFF1A1814),
    inkSecondary = Color(0xFF5E5950),
    inkTertiary = Color(0xFF726C64),
    inkDisabled = Color(0xFFB9B3A7),
    signal = Color(0xFF16453C),
    signalPressed = Color(0xFF0E332C),
    signalSoft = Color(0xFFE6EDE9),
    signalSoftStrong = Color(0xFFCFDDD6),
    onSignal = Color(0xFFF3F7F4),
    ember = Color(0xFFB84019),
    emberSoft = Color(0xFFFBEEE8),
    onEmber = Color(0xFFFFF7F4),
    emberOnInverse = Color(0xFFF09A72),
    scrim = Color(0xFF1A1814),
    plot1 = Color(0xFF16453C),
    plot2 = Color(0xFF356A5C),
    plot3 = Color(0xFF5E8F81),
    plot4 = Color(0xFF95B4A9),
    plot5 = Color(0xFFC9D8D1),
    isLight = true,
)

/**
 * Dark mode is architected but deliberately secondary: same structure, same tokens, tuned for
 * a dim room. Light mode carries the product's identity.
 */
val DarkColors = ManagerColors(
    canvas = Color(0xFF121311),
    canvasSunken = Color(0xFF0B0C0B),
    surface = Color(0xFF1B1D1A),
    surfaceMuted = Color(0xFF212420),
    surfaceRaised = Color(0xFF232622),
    surfaceInverse = Color(0xFFE8EDE9),
    onSurfaceInverse = Color(0xFF12211D),
    hairline = Color(0xFF2C2F2B),
    hairlineStrong = Color(0xFF3D423C),
    ink = Color(0xFFF0EEE8),
    inkSecondary = Color(0xFFA8A499),
    inkTertiary = Color(0xFF77746C),
    inkDisabled = Color(0xFF565550),
    signal = Color(0xFF74C3A9),
    signalPressed = Color(0xFF5FAF95),
    signalSoft = Color(0xFF16302A),
    signalSoftStrong = Color(0xFF1F433A),
    onSignal = Color(0xFF08211B),
    ember = Color(0xFFE07A54),
    emberSoft = Color(0xFF3A211A),
    onEmber = Color(0xFF23100A),
    // Dark mode's inverse surface is light, so this one goes the other way.
    emberOnInverse = Color(0xFFB23C17),
    scrim = Color(0xFF000000),
    plot1 = Color(0xFF74C3A9),
    plot2 = Color(0xFF57A38C),
    plot3 = Color(0xFF41806D),
    plot4 = Color(0xFF3A6E5F),
    plot5 = Color(0xFF4C8574),
    isLight = false,
)

val LocalManagerColors = staticCompositionLocalOf { LightColors }

/**
 * The chart ramp, indexed.
 *
 * Wherever several things have to be told apart by colour alone — the apps in a batch about to be
 * removed, the objects in the onboarding field — they take consecutive bands from this one ramp
 * rather than inventing a palette. It is the only place in the product where more than one hue is
 * on screen at once, and the contrast suite holds its bands separable from each other.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun plotTint(index: Int): Color {
    val colors = ManagerTheme.colors
    return when (((index % 5) + 5) % 5) {
        0 -> colors.plot1
        1 -> colors.plot3
        2 -> colors.plot5
        3 -> colors.plot2
        else -> colors.plot4
    }
}
