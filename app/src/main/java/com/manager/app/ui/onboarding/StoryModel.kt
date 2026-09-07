package com.manager.app.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The sample phone the demonstration runs on.
 *
 * Onboarding must play identically on a fresh install with nothing granted and nothing scanned, so
 * it never touches the real inventory. The names are plain category words rather than invented
 * brands: nobody mistakes them for something Manager found on their device.
 *
 * There are five apps and nothing else. The previous version of this screen also floated labelled
 * "Storage" and "Screen time" cards, which looked like a system but behaved like scenery — they
 * could not be opened, held or acted on, so every touch of one taught the user that touching does
 * nothing. A field where every object answers the same way is a smaller field and a better one.
 */
@Immutable
data class StoryApp(
    val id: String,
    val label: String,
    val appBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val screenTimeMs: Long,
    val version: String,
    val installed: String,
    /** Index into the product's chart ramp; also the colour its block keeps when it is removed. */
    val tint: Int,
) {
    val initial: String get() = label.first().uppercase()
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
    val packageName: String get() = "com.sample.$id"
}

private fun hours(h: Long, m: Long) = TimeUnit.HOURS.toMillis(h) + TimeUnit.MINUTES.toMillis(m)
private fun mb(v: Long) = v * 1024L * 1024L

/**
 * Deliberately uneven. The single silent lesson of the first two seconds is that these things have
 * different masses, which only lands if the spread is wide — a field of near-identical sizes would
 * teach nothing, and one runaway outlier would flatten everything else into the minimum.
 */
val StoryCast: List<StoryApp> = listOf(
    StoryApp(
        id = "photos", label = "Photos", tint = 0,
        appBytes = mb(284), dataBytes = mb(902), cacheBytes = mb(78),
        screenTimeMs = hours(3, 41), version = "8.42.1", installed = "14 Mar 2024",
    ),
    StoryApp(
        id = "weather", label = "Weather", tint = 1,
        appBytes = mb(41), dataBytes = mb(39), cacheBytes = mb(8),
        screenTimeMs = hours(0, 18), version = "3.1.4", installed = "9 Nov 2024",
    ),
    StoryApp(
        id = "radio", label = "Radio", tint = 2,
        appBytes = mb(180), dataBytes = mb(2210), cacheBytes = mb(102),
        screenTimeMs = hours(6, 5), version = "11.0.2", installed = "3 Feb 2021",
    ),
    StoryApp(
        id = "notes", label = "Notes", tint = 3,
        appBytes = mb(52), dataBytes = mb(138), cacheBytes = mb(6),
        screenTimeMs = hours(0, 47), version = "2.8.0", installed = "22 Jun 2022",
    ),
    StoryApp(
        id = "messages", label = "Messages", tint = 4,
        appBytes = mb(96), dataBytes = mb(298), cacheBytes = mb(18),
        screenTimeMs = hours(1, 12), version = "5.9.0", installed = "2 Jan 2023",
    ),
)

/**
 * Where the objects sit, and how large each one is.
 *
 * Size is the argument. An object's width is proportional to what it weighs, so the field states
 * "some of these are heavy" before a single word is read — and the heaviest one is, not by
 * accident, the one most likely to be touched first.
 *
 * Positions are fractions of the stage rather than dp, so the composition keeps its proportions on
 * any screen, and each object owns its own horizontal band. Bands cannot collide, which is what
 * lets the layout look scattered while being provably safe at every size.
 */
object StoryStaging {

    private const val NARROWEST = 0.34f
    private const val WIDEST = 0.66f

    private val lightest = sqrt(StoryCast.minOf { it.totalBytes }.toFloat())
    private val heaviest = sqrt(StoryCast.maxOf { it.totalBytes }.toFloat())

    /**
     * Square-rooted, not linear. Radio is twenty-seven times Weather; drawn linearly, Weather
     * would be a sliver and the field would read as one object and four crumbs. The root keeps the
     * ordering exact and the extremes legible, which is what the comparison actually needs.
     */
    fun widthFraction(app: StoryApp): Float {
        val span = (heaviest - lightest).coerceAtLeast(1f)
        val t = ((sqrt(app.totalBytes.toFloat()) - lightest) / span).coerceIn(0f, 1f)
        return NARROWEST + (WIDEST - NARROWEST) * t
    }

    /** One band per object, alternating sides, with a fixed wobble so the field is never a list. */
    fun home(index: Int): Offset {
        val x = if (index % 2 == 0) 0.44f else 0.56f
        val y = 0.19f + index * 0.125f
        val seed = StoryCast[index % StoryCast.size].id.hashCode()
        return Offset(x + wobble(seed, 0.02f), y + wobble(seed * 31, 0.012f))
    }

    /**
     * Where an object is pushed to when others are selected.
     *
     * A selected app has just been given weight, and weight displaces: its neighbours ease away
     * from it, nearest first. The effect is a few dp and most people will never consciously see
     * it — they will only notice that the field behaves like a physical thing.
     */
    fun displaced(index: Int, selected: Set<String>): Offset {
        val home = home(index)
        if (selected.isEmpty() || StoryCast[index].id in selected) return home
        var dx = 0f
        var dy = 0f
        StoryCast.forEachIndexed { other, app ->
            if (app.id !in selected) return@forEachIndexed
            val source = home(other)
            val vx = home.x - source.x
            val vy = home.y - source.y
            val distance = hypot(vx, vy).coerceAtLeast(0.05f)
            val push = PUSH / distance
            dx += vx / distance * push
            dy += vy / distance * push
        }
        return Offset(
            home.x + dx.coerceIn(-CAP, CAP),
            home.y + dy.coerceIn(-CAP, CAP),
        )
    }

    private const val PUSH = 0.0012f
    private const val CAP = 0.022f

    private fun wobble(seed: Int, amplitude: Float): Float {
        val normalised = ((seed % 1000) / 1000f) * 2f - 1f
        return normalised * amplitude
    }
}
