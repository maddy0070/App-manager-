package com.manager.app.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import com.manager.app.design.ManagerIcons
import java.util.concurrent.TimeUnit

/**
 * The onboarding story's cast and staging.
 *
 * The whole experience is one set of objects that never gets created or destroyed — the four
 * beats only change where each object wants to be and how it is dressed. That is what makes the
 * continuity structural: an object cannot fail to carry through a transition, because there is no
 * transition, only a new target.
 *
 * Everything here is sample data. Onboarding must run identically on a fresh install with no
 * permissions granted and no packages scanned, so it never touches the real inventory.
 */

/** The four beats. Order matters: the story only ever moves forward. */
enum class Scene { Discovery, Explore, Gather, Resolved }

enum class FactUnit { Bytes, Duration, Text }

/** One line inside the detail surface, revealed as the user drags. */
@Immutable
data class RevealFact(
    val label: String,
    val unit: FactUnit,
    val amount: Long = 0L,
    val text: String = "",
)

/**
 * An object suspended in the field.
 *
 * Apps are the subjects of the story — they can be opened, held and selected. Facts are the
 * context around them, and in the last beat they collapse into a single summary, which is the
 * whole product's argument in one gesture.
 */
@Immutable
data class StoryObject(
    val id: String,
    val isApp: Boolean,
    val label: String,
    val summary: String,
    /** Facts carry a glyph; apps carry their initial, because apps have identity and facts have meaning. */
    val icon: ImageVector? = null,
    val tint: Int = 0,
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
    val screenTimeMs: Long = 0L,
    val version: String = "",
    val installed: String = "",
) {
    val initial: String get() = label.first().uppercase()
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes

    /** The order matters: size first, because that is the question people actually arrive with. */
    fun reveal(): List<RevealFact> = listOf(
        RevealFact("App", FactUnit.Bytes, appBytes),
        RevealFact("Data", FactUnit.Bytes, dataBytes),
        RevealFact("Cache", FactUnit.Bytes, cacheBytes),
        RevealFact("Screen time", FactUnit.Duration, screenTimeMs),
        RevealFact("Version", FactUnit.Text, text = version),
        RevealFact("Installed", FactUnit.Text, text = installed),
    )
}

private fun hours(h: Long, m: Long) = TimeUnit.HOURS.toMillis(h) + TimeUnit.MINUTES.toMillis(m)
private fun mb(v: Long) = v * 1024L * 1024L

/**
 * Five apps and five facts.
 *
 * The names are plain category words rather than invented brands: they read instantly as sample
 * data, and nobody mistakes them for something Manager found on their phone. Initials are all
 * distinct so the tiles are individually identifiable at a glance.
 */
val StoryCast: List<StoryObject> = listOf(
    StoryObject(
        id = "photos", isApp = true, label = "Photos", summary = "1.2 GB", tint = 0,
        appBytes = mb(284), dataBytes = mb(902), cacheBytes = mb(78),
        screenTimeMs = hours(3, 41), version = "8.42.1", installed = "14 Mar 2024",
    ),
    StoryObject(
        id = "apps", isApp = false, label = "Installed", summary = "142",
        icon = ManagerIcons.Grid,
    ),
    StoryObject(
        id = "storage", isApp = false, label = "Storage", summary = "48.2 GB",
        icon = ManagerIcons.Storage,
    ),
    StoryObject(
        id = "messages", isApp = true, label = "Messages", summary = "412 MB", tint = 1,
        appBytes = mb(96), dataBytes = mb(298), cacheBytes = mb(18),
        screenTimeMs = hours(1, 12), version = "5.9.0", installed = "2 Jan 2023",
    ),
    StoryObject(
        id = "weather", isApp = true, label = "Weather", summary = "88 MB", tint = 2,
        appBytes = mb(41), dataBytes = mb(39), cacheBytes = mb(8),
        screenTimeMs = hours(0, 18), version = "3.1.4", installed = "9 Nov 2024",
    ),
    StoryObject(
        id = "system", isApp = false, label = "System", summary = "218",
        icon = ManagerIcons.System,
    ),
    StoryObject(
        id = "screentime", isApp = false, label = "Screen time", summary = "4h 12m",
        icon = ManagerIcons.Clock,
    ),
    StoryObject(
        id = "notes", isApp = true, label = "Notes", summary = "196 MB", tint = 3,
        appBytes = mb(52), dataBytes = mb(138), cacheBytes = mb(6),
        screenTimeMs = hours(0, 47), version = "2.8.0", installed = "22 Jun 2022",
    ),
    StoryObject(
        id = "radio", isApp = true, label = "Radio", summary = "2.4 GB", tint = 4,
        appBytes = mb(180), dataBytes = mb(2210), cacheBytes = mb(102),
        screenTimeMs = hours(6, 5), version = "11.0.2", installed = "3 Feb 2021",
    ),
    StoryObject(
        id = "split", isApp = false, label = "Split APK", summary = "3 parts",
        icon = ManagerIcons.Split,
    ),
)

val StoryApps: List<StoryObject> = StoryCast.filter { it.isApp }

/**
 * Where every object wants to be, per beat, in fractions of the stage.
 *
 * Fractions rather than dp so the composition holds its proportions on any screen, and centres
 * rather than corners so a chip's own measured width never pushes it out of its lane. The
 * discovery lattice is deterministic — two columns whose gutter is wider than any jitter can
 * close — which is how a field that reads as scattered is guaranteed never to overlap.
 *
 * The cast is ordered so that apps and facts do not alternate. Alternating would put every app in
 * one column and every fact in the other, and a field sorted into two tidy columns is the exact
 * opposite of the impression the first beat needs to make.
 */
object StoryStaging {

    /** Chips are capped at this fraction of the stage, which fixes the column gutter at 0.04. */
    const val CHIP_MAX_WIDTH_FRACTION = 0.40f

    private const val COL_LEFT = 0.28f
    private const val COL_RIGHT = 0.72f

    fun discovery(index: Int, id: String): Offset {
        val column = index % 2
        val row = index / 2
        val x = if (column == 0) COL_LEFT else COL_RIGHT
        val y = 0.125f + row * 0.100f
        // Deterministic per-object jitter, small enough that the gutter always survives it.
        val seed = id.hashCode()
        return Offset(
            x = x + wobble(seed, 0.012f),
            y = y + wobble(seed * 31, 0.016f),
        )
    }

    /** Beat three: the apps step forward into their own arrangement; the facts stay put and dim. */
    fun gather(appIndex: Int): Offset = when (appIndex) {
        0 -> Offset(0.29f, 0.17f)
        1 -> Offset(0.71f, 0.21f)
        2 -> Offset(0.27f, 0.33f)
        3 -> Offset(0.73f, 0.37f)
        else -> Offset(0.50f, 0.49f)
    }

    /** Beat four: apps become a list, facts collapse into the one summary above it. */
    fun resolvedApp(appIndex: Int): Offset = Offset(0.5f, 0.375f + appIndex * 0.058f)

    val SummaryCentre = Offset(0.5f, 0.215f)

    private fun wobble(seed: Int, amplitude: Float): Float {
        val normalised = ((seed % 1000) / 1000f) * 2f - 1f
        return normalised * amplitude
    }
}
