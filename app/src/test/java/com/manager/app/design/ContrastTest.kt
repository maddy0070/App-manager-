package com.manager.app.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The palette, checked against WCAG rather than against taste.
 *
 * Contrast is the one part of a visual system that is not a matter of opinion, and it is exactly
 * the part that drifts when colours get nudged. Encoding the thresholds here means a future
 * "slightly lighter grey" cannot quietly take the metadata below the readable line — which is how
 * it went below it the first time.
 */
class ContrastTest {

    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun ratio(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertReadable(
        name: String,
        foreground: Color,
        background: Color,
        minimum: Double = AA_NORMAL,
    ) {
        val actual = ratio(foreground, background)
        assertTrue(
            "$name is %.2f:1, below the %.1f:1 it needs".format(actual, minimum),
            actual >= minimum,
        )
    }

    // ---- Light mode, which carries the product's identity ------------------------------------

    @Test
    fun `every ink level is readable on both grounds`() {
        listOf("canvas" to LightColors.canvas, "surface" to LightColors.surface).forEach { (bg, color) ->
            assertReadable("ink on $bg", LightColors.ink, color)
            assertReadable("inkSecondary on $bg", LightColors.inkSecondary, color)
            // Tertiary carries every eyebrow, caption and placeholder in the product at 11-12sp,
            // so it is held to the normal-text threshold rather than the large-text one.
            assertReadable("inkTertiary on $bg", LightColors.inkTertiary, color)
        }
    }

    @Test
    fun `the tinted grounds carry the ink level that is actually placed on them`() {
        // Sunken and soft grounds carry secondary ink in the product, never tertiary — the note
        // box on the dashboard, the system badge, the sort controls. This asserts what is drawn.
        assertReadable("inkSecondary on canvasSunken", LightColors.inkSecondary, LightColors.canvasSunken)
        assertReadable("inkSecondary on signalSoft", LightColors.inkSecondary, LightColors.signalSoft)
        assertReadable("ink on canvasSunken", LightColors.ink, LightColors.canvasSunken)
    }

    @Test
    fun `the accents are readable wherever they carry text`() {
        assertReadable("signal on canvas", LightColors.signal, LightColors.canvas)
        assertReadable("signal on surface", LightColors.signal, LightColors.surface)
        assertReadable("signal on signalSoft", LightColors.signal, LightColors.signalSoft)
        assertReadable("ember on canvas", LightColors.ember, LightColors.canvas)
        assertReadable("ember on emberSoft", LightColors.ember, LightColors.emberSoft)
    }

    @Test
    fun `inverse surfaces carry both their content and their destructive action`() {
        // The selection bar and the notice capsule are inverse; "Remove" lives there.
        assertReadable("onSurfaceInverse", LightColors.onSurfaceInverse, LightColors.surfaceInverse)
        assertReadable("emberOnInverse", LightColors.emberOnInverse, LightColors.surfaceInverse)
        assertReadable("onSignal on signal", LightColors.onSignal, LightColors.signal)
    }

    @Test
    fun `the ink levels stay distinguishable from one another`() {
        // Hierarchy must survive as steps, not just as three colours that all happen to pass.
        val primary = ratio(LightColors.ink, LightColors.canvas)
        val secondary = ratio(LightColors.inkSecondary, LightColors.canvas)
        val tertiary = ratio(LightColors.inkTertiary, LightColors.canvas)
        assertTrue("primary should outrank secondary", primary > secondary * 1.5)
        assertTrue("secondary should outrank tertiary", secondary > tertiary * 1.2)
    }

    // ---- Dark mode, architected rather than primary --------------------------------------------

    @Test
    fun `dark mode holds the same line for text`() {
        listOf("surface" to DarkColors.surface, "canvas" to DarkColors.canvas).forEach { (bg, color) ->
            assertReadable("dark ink on $bg", DarkColors.ink, color)
            assertReadable("dark inkSecondary on $bg", DarkColors.inkSecondary, color)
            assertReadable("dark signal on $bg", DarkColors.signal, color)
            assertReadable("dark ember on $bg", DarkColors.ember, color)
        }
        assertReadable("dark onSurfaceInverse", DarkColors.onSurfaceInverse, DarkColors.surfaceInverse)
        assertReadable("dark emberOnInverse", DarkColors.emberOnInverse, DarkColors.surfaceInverse)
    }

    @Test
    fun `dark tertiary ink clears the large-text line at minimum`() {
        // Dark mode is explicitly the secondary experience; tertiary there is held to the
        // large-text threshold rather than the normal-text one, and this records that choice.
        assertReadable("dark inkTertiary", DarkColors.inkTertiary, DarkColors.surface, AA_LARGE)
    }

    @Test
    fun `chart bands stay separable from their track and from each other`() {
        listOf(
            "light" to LightColors,
            "dark" to DarkColors,
        ).forEach { (name, palette) ->
            val bands = listOf(palette.plot1, palette.plot2, palette.plot3, palette.plot4, palette.plot5)
            bands.zipWithNext().forEachIndexed { index, (a, b) ->
                val step = ratio(a, b)
                assertTrue(
                    "$name plot${index + 1} and plot${index + 2} are %.2f:1 apart".format(step),
                    step >= 1.2,
                )
            }
        }
    }

    private companion object {
        const val AA_NORMAL = 4.5
        const val AA_LARGE = 3.0
    }
}
