package com.manager.app.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
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
 * The whole premise of this screen is that gestures carry the meaning and copy does not, so the
 * only test worth having is one that performs the gestures: it measures the objects against each
 * other, opens one, drags its figure apart, throws it back, holds to select, watches the weight
 * accumulate and release, and removes the batch. Nothing here inspects internals.
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
        const val PHOTOS = "Photos, 1.2 GB"
        const val RADIO = "Radio, 2.4 GB"
        const val WEATHER = "Weather, 88 MB"
        const val NOTES = "Notes, 196 MB"
        const val MESSAGES = "Messages, 412 MB"
    }

    private fun start(onFinish: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ManagerTheme { OnboardingScreen(onFinish = onFinish) } }
        compose.mainClock.advanceTimeBy(1_000)
    }

    private fun tap(description: String) {
        compose.onNodeWithContentDescription(description).performTouchInput { click() }
        compose.mainClock.advanceTimeBy(700)
    }

    /**
     * A long press that actually presses long.
     *
     * `longClick()` advances injected event time only; with the clock driven by hand the gesture
     * detector's timeout never elapses and the press lands as a tap. Holding the pointer down
     * across an explicit clock advance is what a finger really does.
     */
    private fun hold(description: String) {
        compose.onNodeWithContentDescription(description).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithContentDescription(description).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(900)
    }

    // ---- The field ----------------------------------------------------------------------------

    @Test
    fun `the field arrives already weighed, and the bar states what it is looking at`() {
        start()
        listOf(PHOTOS, RADIO, WEATHER, NOTES, MESSAGES).forEach {
            compose.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        // Idle, the bar weighs the whole field: five apps, 4.3 GB between them.
        compose.onNodeWithText("5 apps").assertIsDisplayed()
        compose.onNodeWithText("4.3").assertIsDisplayed()
    }

    @Test
    fun `an object is as wide as it is heavy`() {
        start()
        val radio = compose.onNodeWithContentDescription(RADIO).getBoundsInRoot().let { it.right - it.left }
        val photos = compose.onNodeWithContentDescription(PHOTOS).getBoundsInRoot().let { it.right - it.left }
        val messages = compose.onNodeWithContentDescription(MESSAGES).getBoundsInRoot().let { it.right - it.left }
        val weather = compose.onNodeWithContentDescription(WEATHER).getBoundsInRoot().let { it.right - it.left }

        // This is the one thing the screen says before anybody reads anything, so it is the one
        // thing worth asserting about the layout: size is the datum, not a styling choice.
        assertTrue("2.4 GB should be wider than 1.2 GB", radio > photos)
        assertTrue("1.2 GB should be wider than 412 MB", photos > messages)
        assertTrue("412 MB should be wider than 88 MB", messages > weather)
    }

    // ---- Opening ------------------------------------------------------------------------------

    @Test
    fun `touching an object grows it into the surface carrying that object's own record`() {
        start()
        tap(PHOTOS)
        compose.mainClock.advanceTimeBy(900)

        // The surface belongs to the object that was touched, not to a generic sample.
        compose.onNodeWithText("com.sample.photos").assertIsDisplayed()
    }

    @Test
    fun `dragging the surface takes the single figure apart`() {
        start()
        tap(PHOTOS)
        compose.mainClock.advanceTimeBy(900)

        // Before the drag the sheet carries one number and nothing has been decomposed.
        assertTrue(compose.onAllNodesWithText("Cache").fetchSemanticsNodes().isEmpty())

        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.86f, endY = height * 0.22f, durationMillis = 220)
        }
        compose.mainClock.advanceTimeBy(1_200)

        // The parts the total came apart into, then the rest of the record underneath.
        compose.onNodeWithText("App").assertIsDisplayed()
        compose.onNodeWithText("Data").assertIsDisplayed()
        compose.onNodeWithText("Cache").assertIsDisplayed()
        compose.onNodeWithText("Version").assertIsDisplayed()
        compose.onNodeWithText("8.42.1").assertIsDisplayed()
    }

    @Test
    fun `throwing the surface back down puts the object back in the field`() {
        start()
        tap(PHOTOS)
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("com.sample.photos").assertIsDisplayed()

        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.60f, endY = height * 0.94f, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(1_400)

        assertTrue(
            "the surface should be gone",
            compose.onAllNodesWithText("com.sample.photos").fetchSemanticsNodes().isEmpty(),
        )
        compose.onNodeWithContentDescription(PHOTOS).assertIsDisplayed()
    }

    // ---- Selecting ----------------------------------------------------------------------------

    @Test
    fun `a hold selects and a tap opens, which is what the real list does`() {
        start()
        tap(PHOTOS)
        compose.mainClock.advanceTimeBy(900)
        // A tap opened the object rather than selecting it.
        compose.onNodeWithText("com.sample.photos").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("1 selected").fetchSemanticsNodes().isEmpty())

        compose.onRoot().performTouchInput {
            swipeDown(startY = height * 0.60f, endY = height * 0.94f, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(1_400)

        hold(PHOTOS)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun `weight accumulates as the batch grows and is given back when it shrinks`() {
        start()
        hold(PHOTOS)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("1.2").assertIsDisplayed()

        // In selection mode a plain tap toggles — again, the real list's behaviour.
        tap(RADIO)
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("3.7").assertIsDisplayed()

        tap(RADIO)
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithText("1.2").assertIsDisplayed()
    }

    // ---- Removing -----------------------------------------------------------------------------

    @Test
    fun `removing the batch measures it, empties the rail, and offers the way in`() {
        var finished = false
        start { finished = true }
        hold(PHOTOS)
        tap(RADIO)
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("3.7").assertIsDisplayed()

        compose.onNodeWithContentDescription("Remove").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(6_000)

        compose.onNodeWithContentDescription("Comes back, 3.7 GB").assertIsDisplayed()
        compose.onNodeWithText("Your phone,\nweighed.").assertIsDisplayed()

        assertFalse(finished)
        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    @Test
    fun `nothing is ever instructed`() {
        start()
        compose.mainClock.advanceTimeBy(6_000)
        // The previous onboarding told the user what to do next in as many words. Whatever this
        // screen does to invite a touch, it must not be that.
        listOf("Touch one.", "Hold one.", "Two more.", "One more.", "Tap an app").forEach {
            assertTrue(
                "onboarding should not instruct: \"$it\"",
                compose.onAllNodesWithText(it).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun `skip is available from the start and ends it immediately`() {
        var finished = false
        start { finished = true }
        compose.onNodeWithText("Skip").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
    fun `the whole thing plays on a small phone`() {
        var finished = false
        start { finished = true }
        assertTrue(compose.onAllNodesWithContentDescription(RADIO).fetchSemanticsNodes().isNotEmpty())

        hold(PHOTOS)
        tap(WEATHER)
        compose.mainClock.advanceTimeBy(900)
        compose.onNodeWithText("2 selected").assertIsDisplayed()

        compose.onNodeWithContentDescription("Remove").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(6_000)

        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }
}
