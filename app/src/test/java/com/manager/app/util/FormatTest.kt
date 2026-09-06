package com.manager.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Formatting is where a premium product quietly fails: one inconsistent unit or a stray "0 B" and
 * the whole surface reads as unfinished. These lock the rules down.
 */
class FormatTest {

    @Test
    fun `bytes stay under four significant characters`() {
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.0 KB", Format.bytes(1024))
        assertEquals("9.8 KB", Format.bytes(10_000))
        assertEquals("9.5 MB", Format.bytes(10_000_000))
        assertEquals("95 MB", Format.bytes(100_000_000))
        assertEquals("1.9 GB", Format.bytes(2_000_000_000))
    }

    @Test
    fun `unknown storage reads as an em dash, never as zero`() {
        assertEquals("—", Format.bytes(null))
        assertEquals("—", Format.bytes(-1))
        assertEquals("—" to "", Format.bytesParts(null))
    }

    @Test
    fun `byte parts split value from unit for mixed type styling`() {
        assertEquals("1.0" to "KB", Format.bytesParts(1024))
        assertEquals("512" to "B", Format.bytesParts(512))
        assertEquals("95" to "MB", Format.bytesParts(100_000_000))
    }

    @Test
    fun `durations never render a bare zero`() {
        assertEquals("—", Format.duration(0))
        assertEquals("<1m", Format.duration(30_000))
        assertEquals("42m", Format.duration(TimeUnit.MINUTES.toMillis(42)))
        assertEquals("1h", Format.duration(TimeUnit.HOURS.toMillis(1)))
        assertEquals("3h 12m", Format.duration(TimeUnit.HOURS.toMillis(3) + TimeUnit.MINUTES.toMillis(12)))
    }

    @Test
    fun `relative days read as language, not arithmetic`() {
        val now = System.currentTimeMillis()
        assertEquals("Today", Format.relativeDay(now, now))
        assertEquals("Yesterday", Format.relativeDay(now - TimeUnit.DAYS.toMillis(1), now))
        assertEquals("3 days ago", Format.relativeDay(now - TimeUnit.DAYS.toMillis(3), now))
        assertEquals("Unknown", Format.relativeDay(0, now))
    }

    @Test
    fun `ranks are zero padded so the list keeps a straight edge`() {
        assertEquals("01", Format.rank(0))
        assertEquals("10", Format.rank(9))
    }

    @Test
    fun `counts agree with their noun`() {
        assertEquals("1 app", Format.count(1, "app"))
        assertEquals("4 apps", Format.count(4, "app"))
        assertEquals("2 matches", Format.count(2, "match", "matches"))
    }

    @Test
    fun `percent clamps rather than overflowing its label`() {
        assertEquals("0%", Format.percent(-0.4f))
        assertEquals("50%", Format.percent(0.5f))
        assertEquals("100%", Format.percent(1.8f))
    }
}
