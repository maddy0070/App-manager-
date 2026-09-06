package com.manager.app.design

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The shape system is load-bearing: every card, sheet, chip and icon tile is cut from it, so a
 * degenerate outline at an awkward size would be visible everywhere at once. These check the
 * cases real layouts actually produce — tiny chips, extreme aspect ratios, zero sizes.
 */
@RunWith(RobolectricTestRunner::class)
class SquircleTest {

    private val density = Density(density = 3f, fontScale = 1f)

    private fun outlineOf(shape: SquircleShape, width: Float, height: Float) =
        shape.createOutline(Size(width, height), LayoutDirection.Ltr, density)

    @Test
    fun `a normal card produces a closed generic path, not a rectangle fallback`() {
        val outline = outlineOf(SquircleShape(22.dp), 1000f, 500f)
        assertTrue(outline is Outline.Generic)
        assertTrue((outline as Outline.Generic).path.getBounds().width > 0f)
    }

    @Test
    fun `the outline fills its box exactly at every smoothing value`() {
        listOf(0f, 0.3f, 0.6f, 1f).forEach { smoothing ->
            val outline = outlineOf(SquircleShape(20.dp, smoothing), 600f, 400f) as Outline.Generic
            val bounds = outline.path.getBounds()
            assertEquals("left at smoothing $smoothing", 0f, bounds.left, 0.6f)
            assertEquals("top at smoothing $smoothing", 0f, bounds.top, 0.6f)
            assertEquals("right at smoothing $smoothing", 600f, bounds.right, 0.6f)
            assertEquals("bottom at smoothing $smoothing", 400f, bounds.bottom, 0.6f)
        }
    }

    @Test
    fun `a radius larger than the box degrades to a capsule instead of turning inside out`() {
        // A 40dp radius on a 30px-tall chip: the budget must clamp rather than produce loops.
        val outline = outlineOf(SquircleShape(40.dp), 200f, 30f) as Outline.Generic
        val bounds = outline.path.getBounds()
        assertEquals(0f, bounds.top, 0.6f)
        assertEquals(30f, bounds.bottom, 0.6f)
        assertEquals(200f, bounds.right, 0.6f)
    }

    @Test
    fun `zero and negative sizes fall back to a rectangle rather than throwing`() {
        assertTrue(outlineOf(SquircleShape(16.dp), 0f, 0f) is Outline.Rectangle)
        assertTrue(outlineOf(SquircleShape(16.dp), 100f, 0f) is Outline.Rectangle)
    }

    @Test
    fun `a zero radius is an honest rectangle outline`() {
        val outline = outlineOf(SquircleShape(0.dp), 100f, 60f) as Outline.Generic
        val bounds = outline.path.getBounds()
        assertEquals(100f, bounds.width, 0.1f)
        assertEquals(60f, bounds.height, 0.1f)
    }

    @Test
    fun `per-corner radii are honoured independently`() {
        val shape = SquircleShape(topStart = 30.dp, topEnd = 0.dp, bottomEnd = 30.dp, bottomStart = 0.dp)
        val outline = outlineOf(shape, 400f, 400f) as Outline.Generic
        val bounds = outline.path.getBounds()
        assertEquals(0f, bounds.left, 0.6f)
        assertEquals(400f, bounds.right, 0.6f)
    }

    @Test
    fun `layout direction mirrors the corners`() {
        val shape = SquircleShape(topStart = 40.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
        val ltr = shape.createOutline(Size(300f, 300f), LayoutDirection.Ltr, density) as Outline.Generic
        val rtl = shape.createOutline(Size(300f, 300f), LayoutDirection.Rtl, density) as Outline.Generic
        // Both stay in the same box; the rounded corner simply swaps sides.
        assertEquals(ltr.path.getBounds().width, rtl.path.getBounds().width, 0.6f)
        assertTrue(ltr.path.getBounds() == rtl.path.getBounds())
    }

    @Test
    fun `the capsule tracks its own height`() {
        val outline = CapsuleShape.createOutline(Size(240f, 48f), LayoutDirection.Ltr, density) as Outline.Generic
        val bounds = outline.path.getBounds()
        assertEquals(240f, bounds.width, 0.6f)
        assertEquals(48f, bounds.height, 0.6f)
    }

    @Test
    fun `equal shapes compare equal so recomposition can skip them`() {
        assertEquals(SquircleShape(20.dp, 0.6f), SquircleShape(20.dp, 0.6f))
        assertEquals(SquircleShape(20.dp, 0.6f).hashCode(), SquircleShape(20.dp, 0.6f).hashCode())
        assertTrue(SquircleShape(20.dp, 0.6f) != SquircleShape(20.dp, 0.8f))
    }

    @Test
    fun `icon tiles scale their radius with their size`() {
        val small = iconTileShape(24.dp).createOutline(Size(72f, 72f), LayoutDirection.Ltr, density)
        val large = iconTileShape(64.dp).createOutline(Size(192f, 192f), LayoutDirection.Ltr, density)
        assertTrue(small is Outline.Generic)
        assertTrue(large is Outline.Generic)
    }
}
