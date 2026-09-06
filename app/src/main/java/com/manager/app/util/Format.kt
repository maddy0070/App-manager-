package com.manager.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * One place for every number the user reads. Storage, time and dates are formatted identically
 * wherever they appear, which is most of why the screens feel like one product.
 */
object Format {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    /** `512 B` / `9.4 MB` / `124 MB` / `2.3 GB` — one decimal below ten, none above. */
    fun bytes(value: Long?): String {
        if (value == null) return "—"
        if (value < 0) return "—"
        if (value < KB) return "$value B"
        val (amount, unit) = when {
            value < MB -> value / KB to "KB"
            value < GB -> value / MB to "MB"
            else -> value / GB to "GB"
        }
        return if (amount < 10) String.format(Locale.US, "%.1f %s", amount, unit)
        else String.format(Locale.US, "%.0f %s", amount, unit)
    }

    /** Splits the value from its unit so the two can carry different type styles. */
    fun bytesParts(value: Long?): Pair<String, String> {
        if (value == null || value < 0) return "—" to ""
        if (value < KB) return "$value" to "B"
        val (amount, unit) = when {
            value < MB -> value / KB to "KB"
            value < GB -> value / MB to "MB"
            else -> value / GB to "GB"
        }
        val text = if (amount < 10) String.format(Locale.US, "%.1f", amount)
        else String.format(Locale.US, "%.0f", amount)
        return text to unit
    }

    /** `4m` / `1h 12m` / `14h` — never a bare "0". */
    fun duration(millis: Long): String {
        if (millis <= 0) return "—"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        if (minutes < 1) return "<1m"
        val hours = minutes / 60
        val remainder = minutes % 60
        return when {
            hours == 0L -> "${minutes}m"
            remainder == 0L -> "${hours}h"
            hours < 100 -> "${hours}h ${remainder}m"
            else -> "${hours}h"
        }
    }

    /** `Today` / `Yesterday` / `4 days ago` / `3 weeks ago` / `12 Mar 2024`. */
    fun relativeDay(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0) return "Unknown"
        val delta = now - timestamp
        if (delta < 0) return dayMonth(timestamp)
        val days = daysBetween(timestamp, now)
        return when {
            days == 0 -> "Today"
            days == 1 -> "Yesterday"
            days < 7 -> "$days days ago"
            days < 14 -> "Last week"
            days < 31 -> "${days / 7} weeks ago"
            days < 62 -> "Last month"
            days < 365 -> "${days / 30} months ago"
            days < 730 -> "Last year"
            else -> "${days / 365} years ago"
        }
    }

    /** Compact form for dense metadata lines. */
    fun relativeShort(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0) return "—"
        val days = daysBetween(timestamp, now)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(abs(now - timestamp))
        return when {
            minutes < 60 -> "${minutes.coerceAtLeast(1)}m ago"
            days == 0 -> "${minutes / 60}h ago"
            days == 1 -> "Yesterday"
            days < 30 -> "${days}d ago"
            days < 365 -> "${days / 30}mo ago"
            else -> "${days / 365}y ago"
        }
    }

    fun dayMonth(timestamp: Long): String {
        if (timestamp <= 0) return "Unknown"
        val sameYear = Calendar.getInstance().get(Calendar.YEAR) ==
            Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)
        val pattern = if (sameYear) "d MMM" else "d MMM yyyy"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }

    fun fullDate(timestamp: Long): String {
        if (timestamp <= 0) return "Not reported"
        return SimpleDateFormat("d MMM yyyy 'at' HH:mm", Locale.getDefault()).format(Date(timestamp))
    }

    fun daysBetween(from: Long, to: Long): Int {
        val start = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = to
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(end - start).toInt()
    }

    fun percent(fraction: Float): String = "${(fraction * 100).toInt().coerceIn(0, 100)}%"

    /** `1` → `01`, so ranked lists keep a straight left edge. */
    fun rank(index: Int): String = (index + 1).toString().padStart(2, '0')

    fun count(value: Int, singular: String, plural: String = "${singular}s"): String =
        "$value ${if (value == 1) singular else plural}"
}
