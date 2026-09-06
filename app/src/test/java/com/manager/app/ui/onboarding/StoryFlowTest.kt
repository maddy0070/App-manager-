package com.manager.app.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
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
 * Plays the onboarding story end to end.
 *
 * The whole point of this onboarding is that gestures, not copy, carry the meaning — so the only
 * test worth having is one that performs the gestures. It taps an object open, drags the surface
 * to fill it in, long-presses to select, picks two more, and checks the story actually arrives at
 * its ending with a working way out. Nothing here inspects internals; it drives the screen the way
 * a finger would.
 *
 * The clock is driven by hand. The field animates forever by design, so letting the test advance
 * time automatically would never find a moment it considered idle.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric's default is a 320x470 mdpi screen, which is not a device this product targets;
// composing a 52sp headline into it proves nothing. This is the shape of the real phone.
@Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoryFlowTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * A long press that actually presses long.
     *
     * `longClick()` advances injected event time only; with the clock driven by hand the gesture
     * detector's timeout never elapses and the press lands as a tap. Holding the pointer down
     * across an explicit clock advance is what a finger really does.
     */
    private fun longPress(text: String) {
        compose.onNodeWithText(text).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithText(text).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(150)
    }

    private fun start(onFinish: () -> Unit = {}) {
        compose.mainClock.autoAdvance = false
        compose.setContent { ManagerTheme { OnboardingScreen(onFinish = onFinish) } }
        compose.mainClock.advanceTimeBy(120)
    }

    @Test
    fun `the story opens on the field, with the cue held back until it has settled`() {
        start()
        compose.onNodeWithText("There's a lot\ngoing on.").assertIsDisplayed()
        // The cue must not be there on arrival — the field gets a beat to be looked at first.
        assertTrue(compose.onAllNodesWithText("Touch one.").fetchSemanticsNodes().isEmpty())

        compose.mainClock.advanceTimeBy(2_200)
        compose.onNodeWithText("Touch one.").assertIsDisplayed()
    }

    @Test
    fun `tapping an app grows it into a detail surface carrying that app's own data`() {
        start()
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText("PHOTOS").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_600)

        // The surface belongs to the object that was touched, not to a generic sample.
        compose.onNodeWithText("com.sample.photos").assertIsDisplayed()
        compose.onNodeWithText("See where\nyour phone goes.").assertIsDisplayed()
    }

    @Test
    fun `a fact object declines the touch instead of opening`() {
        start()
        compose.mainClock.advanceTimeBy(2_000)

        compose.onNodeWithText("STORAGE").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_200)

        // Still on the first beat: facts are context, not subjects.
        compose.onNodeWithText("There's a lot\ngoing on.").assertIsDisplayed()
    }

    @Test
    fun `dragging the surface up fills in the numbers behind it`() {
        start()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("PHOTOS").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_600)

        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.86f, endY = height * 0.25f, durationMillis = 220)
        }
        // Long enough for the settle to finish, short enough to still be on this beat: the story
        // hands itself over about a second after the reveal completes.
        compose.mainClock.advanceTimeBy(700)

        // Facts that only exist once the drag has produced them.
        compose.onNodeWithText("Version").assertIsDisplayed()
        compose.onNodeWithText("8.42.1").assertIsDisplayed()
        compose.onNodeWithText("Screen time").assertIsDisplayed()
    }

    @Test
    fun `a completed drag hands over to the selection beat`() {
        start()
        advanceToGather()
        compose.onNodeWithText("Now pick a few.").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithText("Hold one.").assertIsDisplayed()
    }

    @Test
    fun `selection needs a hold, and a plain tap does not start it`() {
        start()
        advanceToGather()
        compose.mainClock.advanceTimeBy(1_200)

        compose.onNodeWithText("PHOTOS").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(700)
        // Nothing selected: the cue is still asking for a hold.
        compose.onNodeWithText("Hold one.").assertIsDisplayed()

        longPress("PHOTOS")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("selected").assertIsDisplayed()
        compose.onNodeWithText("Two more.").assertIsDisplayed()
    }

    @Test
    fun `three selected apps converge into a capsule that offers the real actions`() {
        start()
        advanceToGather()
        compose.mainClock.advanceTimeBy(1_200)

        longPress("PHOTOS")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("MESSAGES").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("WEATHER").performTouchInput { click() }
        // Long enough for the actions to finish expanding, short of the hand-off to the last beat.
        compose.mainClock.advanceTimeBy(1_100)

        compose.onNodeWithText("selected").assertIsDisplayed()
        compose.onNodeWithText("Extract").assertIsDisplayed()
        compose.onNodeWithText("Remove").assertIsDisplayed()
    }

    @Test
    fun `the story resolves, and the way in works`() {
        var finished = false
        start { finished = true }
        advanceToGather()
        compose.mainClock.advanceTimeBy(1_200)

        longPress("PHOTOS")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("MESSAGES").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("WEATHER").performTouchInput { click() }

        // Convergence, then the transformation.
        compose.mainClock.advanceTimeBy(6_000)

        compose.onNodeWithText("Your phone,\nunderstood.").assertIsDisplayed()
        compose.onNodeWithText("YOUR PHONE").assertIsDisplayed()

        assertFalse(finished)
        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    @Test
    fun `skip is available throughout and ends the story immediately`() {
        var finished = false
        start { finished = true }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Skip intro").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h640dp-xhdpi")
    fun `the whole story still plays through on a small phone`() {
        var finished = false
        start { finished = true }
        advanceToGather()
        compose.mainClock.advanceTimeBy(1_200)
        longPress("PHOTOS")
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("MESSAGES").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("WEATHER").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(6_000)

        compose.onNodeWithText("Open Manager").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(finished)
    }

    /** Opens an app, completes the reveal drag, and waits for the hand-off to the third beat. */
    private fun advanceToGather() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("PHOTOS").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onRoot().performTouchInput {
            swipeUp(startY = height * 0.86f, endY = height * 0.2f, durationMillis = 200)
        }
        // Settle, dwell, then the surface shrinks back and the field rearranges.
        compose.mainClock.advanceTimeBy(4_500)
    }
}
