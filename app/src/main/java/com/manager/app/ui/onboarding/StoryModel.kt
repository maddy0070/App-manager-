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
 * Deliberately uneven, and deliberately contradictory.
 *
 * Two rules govern this cast. The spread of each dimension is wide, because a field of
 * near-identical sizes teaches nothing and one runaway outlier flattens everything else into the
 * minimum. And the two dimensions *disagree*: the app used most is nearly the smallest, and the
 * one barely opened in a fortnight is the largest thing on the phone. That disagreement is the
 * entire argument of the product, and the onboarding makes it by re-measuring the same five
 * objects rather than by drawing a chart of them.
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
        screenTimeMs = hours(1, 30), version = "3.1.4", installed = "9 Nov 2024",
    ),
    StoryApp(
        id = "radio", label = "Radio", tint = 2,
        appBytes = mb(180), dataBytes = mb(2210), cacheBytes = mb(102),
        screenTimeMs = hours(0, 12), version = "11.0.2", installed = "3 Feb 2021",
    ),
    StoryApp(
        id = "notes", label = "Notes", tint = 3,
        appBytes = mb(52), dataBytes = mb(138), cacheBytes = mb(6),
        screenTimeMs = hours(0, 47), version = "2.8.0", installed = "22 Jun 2022",
    ),
    StoryApp(
        id = "messages", label = "Messages", tint = 4,
        appBytes = mb(96), dataBytes = mb(298), cacheBytes = mb(18),
        screenTimeMs = hours(4, 5), version = "5.9.0", installed = "2 Jan 2023",
    ),
)

/**
 * Where the objects sit, and how large each one is.
 *
 * Size is the argument. Once the field has been measured, an object's width is proportional to
 * what it weighs in the current dimension, so the field states "some of these are heavy" before a
 * single word is read — and the heaviest one is, not by accident, the one most likely to be
 * touched first.
 *
 * Positions are fractions of the stage rather than dp, so the composition keeps its proportions on
 * any screen. In the measured layouts each object owns its own horizontal band; in the opening
 * scatter the columns are disjoint because nothing is sized yet. Either way no two objects can
 * collide, which is what lets the layout look scattered while being provably safe at every size —
 * and `StoryFlowTest` asserts it at both ends of the supported range.
 */
object StoryStaging {

    private const val NARROWEST = 0.34f
    private const val WIDEST = 0.66f
    private const val UNCLAIMED = 0.40f

    /**
     * The dimension the field is currently measured in.
     *
     * [Loose] is before anything has been claimed — the objects are simply present, in no order,
     * meaning nothing. The other two are the same five objects measured against different facts,
     * and the whole point of the onboarding is that they disagree.
     */
    enum class Measure { Loose, Usage, Storage }

    private fun weight(app: StoryApp, measure: Measure): Float = when (measure) {
        Measure.Usage -> app.screenTimeMs.toFloat()
        else -> app.totalBytes.toFloat()
    }

    /**
     * Square-rooted, not linear. Radio is twenty-seven times Weather by size; drawn linearly,
     * Weather would be a sliver and the field would read as one object and four crumbs. The root
     * keeps the ordering exact and the extremes legible, which is what a comparison needs.
     */
    fun widthFraction(app: StoryApp, measure: Measure = Measure.Storage): Float {
        // An unmeasured field encodes nothing, so every object is the same size. Explore is then
        // the moment they *gain* their proportions, which is a much larger event than a reshuffle.
        if (measure == Measure.Loose) return UNCLAIMED
        val values = StoryCast.map { sqrt(weight(it, measure)) }
        val low = values.min()
        val range = (values.max() - low).coerceAtLeast(1f)
        val t = ((sqrt(weight(app, measure)) - low) / range).coerceIn(0f, 1f)
        return NARROWEST + (WIDEST - NARROWEST) * t
    }

    /** Rank in the current dimension, heaviest first. Vertical position *is* the ordering. */
    fun rank(index: Int, measure: Measure): Int {
        if (measure == Measure.Loose) return index
        return StoryCast.indices
            .sortedByDescending { weight(StoryCast[it], measure) }
            .indexOf(index)
    }

    /**
     * One band per object, alternating sides, with a fixed wobble so the field is never a list.
     *
     * Before the field has been measured the arrangement is a scatter in declaration order and
     * claims nothing. Once a measure is chosen the band becomes the rank, so the same five objects
     * physically re-order when the dimension changes underneath them.
     */
    fun home(index: Int, measure: Measure = Measure.Storage): Offset {
        val seed = StoryCast[index % StoryCast.size].id.hashCode()
        if (measure == Measure.Loose) {
            // A scatter across two axes rather than a ladder: the opening has to read as objects
            // present in a space, and it has to leave the bottom third of the screen to the one
            // question the screen asks. Uniform widths make the two columns provably disjoint.
            val (x, y) = SCATTER[index % SCATTER.size]
            return Offset(x + wobble(seed, 0.012f), y + wobble(seed * 31, 0.006f))
        }
        val position = rank(index, measure)
        val x = if (position % 2 == 0) 0.44f else 0.56f
        val y = 0.225f + position * 0.125f
        return Offset(x + wobble(seed, 0.01f), y + wobble(seed * 31, 0.006f))
    }

    /** Two disjoint columns plus one below them. Verified against the narrowest supported screen. */
    private val SCATTER = listOf(
        0.30f to 0.15f,
        0.70f to 0.19f,
        0.26f to 0.28f,
        0.72f to 0.33f,
        0.48f to 0.44f,
    )

    /**
     * Where an object is pushed to when others are selected.
     *
     * A selected app has just been given weight, and weight displaces: its neighbours ease away
     * from it, nearest first. The effect is a few dp and most people will never consciously see
     * it — they will only notice that the field behaves like a physical thing.
     */
    fun displaced(index: Int, selected: Set<String>, measure: Measure = Measure.Storage): Offset {
        val home = home(index, measure)
        if (selected.isEmpty() || StoryCast[index].id in selected) return home
        var dx = 0f
        var dy = 0f
        StoryCast.forEachIndexed { other, app ->
            if (app.id !in selected) return@forEachIndexed
            val source = home(other, measure)
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
