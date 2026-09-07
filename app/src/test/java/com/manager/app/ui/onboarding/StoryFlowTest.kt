package com.manager.app.ui.onboarding

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.manager.app.design.ManagerTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Plays the onboarding the way a finger would.
 *
 * The premise of this screen is that gestures carry the meaning and copy does not, so the only
 * test worth having performs the gestures: it explores the field, measures the objects against
 * each other in both dimensions, opens one, drags its figure apart, watches the field re-order
 * itself around what it just learned, holds to select, and reviews the batch.
 *
 * The clock is driven by hand. The field drifts forever by design, so a test that waited for idle
 * would never find a moment the runtime considered idle.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default is a 320x470 mdpi screen with stubbed text measurement that lays every
// string out one character wide. This is the shape of the real phone, with real metrics.
@Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private companion object {
        // The field states its own reading, so the accessible name changes with the dimension.
        const val PHOTOS_TIME = "Photos, 3h 41m"
        const val RADIO_TIME = "Radio, 12m"
        const val MESSAGES_TIME = "Messages, 4h 5m"
        const val WEATHER_TIME = "Weather, 1h 30m"
        const val NOTES_TIME = "Notes, 47m"

        const val PHOTOS = "Photos, 1.2 GB"
        const val RADIO = "Radio, 2.4 GB"
        const val WEATHER = "Weather, 88 MB"
        const val MESSAGES = "Messages, 412 MB"
    }

    /** Opens the screen and takes the one step the opening scene asks for. */
    private fun explore(onFinish: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ManagerTheme { OnboardingScreen(onFinish = onFinish) } }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("Explore").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(2_400)
    }

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(900)
    }

    /**
     * A long press that actually presses long.
     *
     * `longClick()` advances injected event time only; with the clock driven by hand the gesture
     * detector's timeout never elapses and the press lands as a tap. Holding the pointer down
     * across an explicit clock advance is what a finger really does.
     */
    private fun hold(description: String) {
        // Injected by position, not by name: a long press re-measures the field, so by the time
        // the finger lifts the object is announcing a different figure and would not be found.
        val bounds = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        compose.onRoot().performTouchInput {
            down(Offset((bounds.left + bounds.right).toPx() / 2f, (bounds.top + bounds.bottom).toPx() / 2f))
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_400)
    }

    /** Opens an app, pulls its figure apart, and throws it back — which re-measures the field. */
    private fun openAndClose(description: String) {
        tap(description)
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.86f, endY = height * 0.22f, durationMillis = 220)
        }
        compose.mainClock.advanceTimeBy(1_200)
        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.40f, endY = height * 0.94f, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(2_400)
    }

    // ---- Discover -----------------------------------------------------------------------------

    @Test
    fun `the opening invites rather than instructs, and the field is not yet claiming anything`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { ManagerTheme { OnboardingScreen(onFinish = {}) } }
        compose.mainClock.advanceTimeBy(1_200)

        compose.onNodeWithText("Your phone\nhas a story.").assertIsDisplayed()
        compose.onNodeWithText("Let's uncover it.").assertIsDisplayed()
        compose.onNodeWithText("Explore").assertIsDisplayed()
        // Nothing is ranked until the user asks for it to be.
        assertTrue(compose.onAllNodesWithText("MOST USED").fetchSemanticsNodes().isEmpty())
    }

    // ---- Understand: the field measured by time ------------------------------------------------

    @Test
    fun `exploring measures the field by screen time and says so`() {
        explore()
        listOf(PHOTOS_TIME, RADIO_TIME, MESSAGES_TIME, WEATHER_TIME, NOTES_TIME).forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        compose.onNodeWithText("MOST USED").assertIsDisplayed()
        // The bar reads the same dimension the field is drawn in.
        compose.onNodeWithText("5 apps").assertIsDisplayed()
        compose.onNodeWithText("10h 15m").assertIsDisplayed()
    }

    @Test
    fun `in the usage dimension the most used app is the widest and the highest`() {
        explore()
        val messages = compose.onNodeWithContentDescription(MESSAGES_TIME).getBoundsInRoot()
        val radio = compose.onNodeWithContentDescription(RADIO_TIME).getBoundsInRoot()

        assertTrue(
            "4h 5m should be wider than 12m",
            (messages.right - messages.left) > (radio.right - radio.left),
        )
        assertTrue("the most used app sits at the top", messages.top < radio.top)
    }

    // ---- Interact: the same objects, re-measured ------------------------------------------------

    @Test
    fun `opening an object shows what is inside it, and closing it re-measures the whole field`() {
        explore()
        tap(PHOTOS_TIME)
        compose.onNodeWithText("com.sample.photos").assertIsDisplayed()

        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.86f, endY = height * 0.22f, durationMillis = 220)
        }
        compose.mainClock.advanceTimeBy(1_200)
        // The single figure came apart into the three parts Android actually reports.
        compose.onNodeWithText("App").assertIsDisplayed()
        compose.onNodeWithText("Data").assertIsDisplayed()
        compose.onNodeWithText("Cache").assertIsDisplayed()

        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.40f, endY = height * 0.94f, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(2_400)

        // Having been shown what is inside one, the field re-forms around that fact.
        compose.onNodeWithText("LARGEST").assertIsDisplayed()
        compose.onNodeWithContentDescription(RADIO).assertIsDisplayed()
        compose.onNodeWithText("4.3").assertIsDisplayed()
    }

    @Test
    fun `the two dimensions genuinely disagree, which is the whole argument`() {
        explore()
        val messagesByTime = compose.onNodeWithContentDescription(MESSAGES_TIME).getBoundsInRoot()
        val radioByTime = compose.onNodeWithContentDescription(RADIO_TIME).getBoundsInRoot()
        assertTrue("most used is above least used", messagesByTime.top < radioByTime.top)

        openAndClose(PHOTOS_TIME)

        val messagesBySize = compose.onNodeWithContentDescription(MESSAGES).getBoundsInRoot()
        val radioBySize = compose.onNodeWithContentDescription(RADIO).getBoundsInRoot()
        // The order inverts for this pair. If it ever stops inverting, the demonstration has
        // nothing left to demonstrate.
        assertTrue("the largest app is now above the most used one", radioBySize.top < messagesBySize.top)
        assertTrue(
            "and it is now the wider of the two",
            (radioBySize.right - radioBySize.left) > (messagesBySize.right - messagesBySize.left),
        )
    }

    // ---- Act ------------------------------------------------------------------------------------

    @Test
    fun `a hold selects and a tap opens, which is what the real list does`() {
        explore()
        openAndClose(PHOTOS_TIME)
        assertTrue(compose.onAllNodesWithText("1 selected").fetchSemanticsNodes().isEmpty())

        hold(PHOTOS)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("1.2").assertIsDisplayed()
    }

    @Test
    fun `weight accumulates as the batch grows and is given back when it shrinks`() {
        explore()
        hold(RADIO_TIME)
        compose.mainClock.advanceTimeBy(1_400)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        // Holding in the usage dimension settles the field into the one the total is counted in.
        compose.onNodeWithText("LARGEST").assertIsDisplayed()
        compose.onNodeWithText("2.4").assertIsDisplayed()

        tap(PHOTOS)
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("3.7").assertIsDisplayed()

        tap(PHOTOS)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("2.4").assertIsDisplayed()
    }

    @Test
    fun `reviewing gathers the batch, weighs it, and says plainly that nothing happened`() {
        var finished = false
        explore { finished = true }
        hold(RADIO_TIME)
        compose.mainClock.advanceTimeBy(1_400)
        tap(PHOTOS)
        compose.onNodeWithText("3.7").assertIsDisplayed()

        compose.onNodeWithContentDescription("Review selection").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(5_000)

        compose.onNodeWithContentDescription("Selected, 3.7 GB", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Your phone,\nweighed.").assertIsDisplayed()

        assertFalse(finished)
        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(800)
        assertTrue(finished)
    }

    // ---- The rules the screen has to keep --------------------------------------------------------

    @Test
    fun `nothing is ever instructed`() {
        explore()
        compose.mainClock.advanceTimeBy(6_000)
        // An earlier version of this screen told the user what to do next in as many words.
        // Whatever it does to invite a touch, it must never be that again.
        listOf("Touch one.", "Hold one.", "Two more.", "One more.", "Tap an app", "Long press").forEach {
            assertTrue(
                "onboarding should not instruct: \"$it\"",
                compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun `the screen says once that none of this is the user's phone`() {
        explore()
        compose.onNodeWithText("Sample data").assertIsDisplayed()
    }

    @Test
    fun `no two objects overlap in either measured dimension`() {
        explore()
        assertNoOverlap("measured by usage")
        openAndClose(PHOTOS_TIME)
        assertNoOverlap("measured by storage")
    }

    /**
     * The field is positioned absolutely, so nothing in the layout system prevents two objects
     * from landing on each other. The staging is arranged so they cannot — this is what says so.
     *
     * Only the measured layouts are checked, because only they combine variable widths with
     * per-object jitter. The opening scatter draws every object at one uniform width in two
     * columns that cannot meet, and its objects are inert scenery with no semantics to find.
     */
    private fun assertNoOverlap(where: String) {
        // Only the field's own objects: every one of them carries a selection state, and
        // nothing else on the screen does.
        val boxes = compose.onAllNodes(hasStateDescription("Not selected")).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.width > 0 && it.height > 0 }
        assertTrue("the field should have objects in it", boxes.size >= 5)
        boxes.forEachIndexed { i, a ->
            boxes.drop(i + 1).forEach { b ->
                val overlaps = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                assertFalse("two objects overlap when $where", overlaps)
            }
        }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
    fun `and they do not overlap on the narrowest phone either`() {
        explore()
        assertNoOverlap("measured by usage, small phone")
        openAndClose(PHOTOS_TIME)
        assertNoOverlap("measured by storage, small phone")
    }

    @Test
    fun `skip is available from the start and ends it immediately`() {
        var finished = false
        compose.mainClock.autoAdvance = false
        compose.setContent { ManagerTheme { OnboardingScreen(onFinish = { finished = true }) } }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Skip").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
    fun `the whole thing plays on a small phone`() {
        var finished = false
        explore { finished = true }
        assertTrue(compose.onAllNodesWithContentDescription(RADIO_TIME).fetchSemanticsNodes().isNotEmpty())

        hold(RADIO_TIME)
        compose.mainClock.advanceTimeBy(1_400)
        tap(WEATHER)
        compose.onNodeWithText("2 selected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Review selection").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(5_000)

        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(800)
        assertTrue(finished)
    }
}
