package com.manager.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerMark
import com.manager.app.design.components.ManagerTextAction
import com.manager.app.design.components.Txt
import com.manager.app.design.components.driftOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Onboarding as a short interactive demonstration rather than a slideshow.
 *
 * Four beats, roughly forty seconds, driven by the user's own gestures: a field of suspended
 * objects, one of which they open; a surface they drag to fill in; a handful of apps they select;
 * and the whole scattered field resolving into the list the product actually is. Every claim the
 * copy makes has already happened under the reader's finger by the time they read it.
 *
 * Entirely self-contained. It runs on [StoryCast] — sample data — so a fresh install with no
 * permissions and no completed package scan tells the same story, deterministically.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = ManagerTheme.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var scene by remember { mutableStateOf(Scene.Discovery) }
    var openedId by remember { mutableStateOf<String?>(null) }
    var openedCentre by remember { mutableStateOf(Offset(0.5f, 0.3f)) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var converged by remember { mutableStateOf(false) }
    var cueVisible by remember { mutableStateOf(false) }
    var hasDragged by remember { mutableStateOf(false) }

    val morph = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val rowness = remember { Animatable(0f) }
    val summaryIn = remember { Animatable(0f) }
    val capsuleIn = remember { Animatable(0f) }

    // Motion tokens read once here: the staged animations below run inside effects, which are
    // coroutine scope rather than composition scope.
    val emphasized = ManagerTheme.motion.emphasized
    val exitEase = ManagerTheme.motion.exit

    val objects = remember {
        StoryCast.mapIndexed { index, obj ->
            ObjectState(obj, StoryStaging.discovery(index, obj.id))
        }
    }
    val byId = remember { objects.associateBy { it.obj.id } }
    val sizes = remember { mutableStateMapOf<String, IntSize>() }

    // The field never stops breathing; each object traces its own slow path.
    val transition = rememberInfiniteTransition(label = "storyDrift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Restart),
        label = "storyPhase",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.canvas,
                    0.6f to colors.canvas,
                    1f to if (colors.isLight) colors.signalSoft.copy(alpha = 0.5f) else colors.canvasSunken,
                ),
            ),
    ) {
        val stageWidth = maxWidth
        val stageHeight = maxHeight
        val density = LocalDensity.current
        val chipWidth = lerpDp(
            stageWidth * StoryStaging.CHIP_MAX_WIDTH_FRACTION,
            stageWidth * 0.84f,
            rowness.value,
        )
        val capsuleCentre = Offset(0.5f, 0.63f)

        // Gesture handling is hoisted so the pointer inputs below can be keyed on identity alone.
        // A handler re-installed mid-gesture drops the touch that caused it.
        val handleTap by rememberUpdatedState<(StoryObject, ObjectState) -> Unit> { obj, state ->
            when {
                scene == Scene.Discovery && obj.isApp -> {
                    openedId = obj.id
                    openedCentre = state.centre.value
                    scene = Scene.Explore
                }
                // A fact is context, not a subject: it acknowledges the touch and stays put,
                // which is itself the lesson.
                scene == Scene.Discovery -> scope.nudge(state)
                scene == Scene.Gather && obj.isApp && selection.isNotEmpty() && !converged ->
                    selection = if (obj.id in selection) selection - obj.id else selection + obj.id
                scene == Scene.Gather && obj.isApp -> scope.nudge(state)
                else -> Unit
            }
        }
        val handleLongPress by rememberUpdatedState<(StoryObject) -> Unit> { obj ->
            if (scene == Scene.Gather && obj.isApp && !converged) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (obj.id !in selection) selection = selection + obj.id
            }
        }

        // ---- Beat one: the cue arrives only once the field has been allowed to settle. -------
        LaunchedEffect(scene) {
            cueVisible = false
            when (scene) {
                Scene.Discovery -> { delay(1500); cueVisible = true }
                Scene.Gather -> { delay(800); cueVisible = true }
                else -> Unit
            }
        }

        // ---- Beat two: if the surface is not pulled, it offers itself once in a while. -------
        LaunchedEffect(scene, hasDragged) {
            if (scene != Scene.Explore || hasDragged) return@LaunchedEffect
            while (true) {
                delay(2600)
                if (hasDragged) return@LaunchedEffect
                reveal.animateTo(0.085f, spring(dampingRatio = 0.62f, stiffness = 380f))
                reveal.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 300f))
            }
        }

        // ---- The story only ever moves forward, and each move is one staged animation. -------
        LaunchedEffect(scene) {
            when (scene) {
                Scene.Discovery -> Unit

                Scene.Explore -> {
                    objects.forEach { state ->
                        if (state.obj.id == openedId) {
                            scope.launch { state.alpha.animateTo(0f, tween(90)) }
                        } else {
                            scope.launch { state.alpha.animateTo(0.10f, tween(420, easing = emphasized)) }
                            scope.launch { state.scale.animateTo(0.94f, spring(dampingRatio = 1f, stiffness = 260f)) }
                        }
                    }
                    morph.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
                }

                Scene.Gather -> {
                    // The surface shrinks back into the object it grew from before the field
                    // rearranges, so nothing is ever replaced off-screen.
                    reveal.animateTo(0f, tween(260, easing = exitEase))
                    morph.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
                    openedId?.let { byId[it]?.alpha?.animateTo(1f, tween(180)) }
                    openedId = null

                    var appIndex = 0
                    objects.forEach { state ->
                        if (state.obj.isApp) {
                            val target = StoryStaging.gather(appIndex++)
                            scope.launch { state.centre.animateTo(target, CentreSpring) }
                            scope.launch { state.alpha.animateTo(1f, tween(300)) }
                            scope.launch { state.scale.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                        } else {
                            // Facts stay where they are and step back into the background.
                            scope.launch { state.alpha.animateTo(0.10f, tween(500)) }
                            scope.launch { state.scale.animateTo(0.86f, spring(dampingRatio = 1f, stiffness = 220f)) }
                        }
                    }
                }

                Scene.Resolved -> {
                    capsuleIn.animateTo(0f, tween(220, easing = exitEase))
                    var appIndex = 0
                    objects.forEach { state ->
                        if (state.obj.isApp) {
                            val target = StoryStaging.resolvedApp(appIndex++)
                            scope.launch { state.centre.animateTo(target, ResolveSpring) }
                            scope.launch { state.alpha.animateTo(1f, tween(320)) }
                            scope.launch { state.scale.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 240f)) }
                        } else {
                            // The five facts collapse into the one summary that replaces them.
                            scope.launch { state.centre.animateTo(StoryStaging.SummaryCentre, ResolveSpring) }
                            scope.launch { state.scale.animateTo(0.45f, tween(520, easing = emphasized)) }
                            scope.launch { state.alpha.animateTo(0f, tween(520, easing = emphasized)) }
                        }
                    }
                    scope.launch { rowness.animateTo(1f, tween(620, easing = emphasized)) }
                    delay(280)
                    summaryIn.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 240f))
                }
            }
        }

        // ---- The field ----------------------------------------------------------------------

        // Facts are drawn first and apps second, always. In the beats where the two overlap the
        // apps are the subject, and a 10%-opacity chip veiling a crisp one would be backwards.
        // A fixed order also means the z-stack never pops mid-story.
        val drawOrder = remember(objects) {
            objects.withIndex().sortedBy { (_, state) -> if (state.obj.isApp) 1 else 0 }
        }

        drawOrder.forEach { (index, state) ->
            val obj = state.obj
            // An object that has handed its role to something else is gone, not merely invisible:
            // leaving it composed would keep it in the semantics tree for a screen reader to
            // announce, and would let two things claim the same identity at once.
            if (state.alpha.value < 0.02f) return@forEach
            val selected = obj.id in selection
            // Objects dimmed into the background are scenery; they should not be read out.
            val decorative = state.alpha.value < 0.35f
            val measured = sizes[obj.id]
            val fallbackWidth = with(density) { chipWidth.roundToPx() }
            val width = measured?.width ?: fallbackWidth
            val height = measured?.height ?: with(density) { 52.dp.roundToPx() }

            // Drift is additive and dies away as the field resolves, so the final list is still.
            val drift = driftOffset(phase, index * 3 + 1, 5.5f) * (1f - rowness.value)
            val centre = state.centre.value
            val x = stageWidth.value * density.density * centre.x - width / 2f + drift.x
            val y = stageHeight.value * density.density * centre.y - height / 2f + drift.y

            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .onSizeChanged { sizes[obj.id] = it }
                    .graphicsLayer {
                        alpha = state.alpha.value
                        scaleX = state.scale.value
                        scaleY = state.scale.value
                    }
                    // Scenery is inert. A dimmed object left listening would sit invisibly over a
                    // live one and swallow the tap meant for it — and would be announced by a
                    // screen reader as though it were still part of the story.
                    .then(
                        if (decorative) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier.pointerInput(obj.id) {
                                detectTapGestures(
                                    onLongPress = { handleLongPress(obj) },
                                    onTap = { handleTap(obj, state) },
                                )
                            }
                        },
                    ),
            ) {
                StoryChip(
                    obj = obj,
                    selected = selected,
                    rowness = rowness.value,
                    width = chipWidth,
                )
            }
        }

        // ---- The summary the facts became -----------------------------------------------------

        if (summaryIn.value > 0.01f) {
            SummaryCard(
                progress = summaryIn.value,
                centre = StoryStaging.SummaryCentre,
                stageWidth = stageWidth,
                stageHeight = stageHeight,
            )
        }

        // ---- The surface the object became -----------------------------------------------------

        val opened = openedId?.let { byId[it]?.obj }
        if (opened != null && morph.value > 0.001f) {
            StoryDetailSurface(
                obj = opened,
                morph = morph.value,
                reveal = reveal.value,
                stageWidth = stageWidth,
                stageHeight = stageHeight,
                chipWidth = chipWidth,
                chipCentre = openedCentre,
                onReveal = { value ->
                    hasDragged = true
                    scope.launch { reveal.snapTo(value) }
                },
                onRevealSettled = { target ->
                    scope.launch {
                        reveal.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 380f))
                        if (target >= 1f) {
                            delay(1100)
                            if (scene == Scene.Explore) scene = Scene.Gather
                        }
                    }
                },
            )
        }

        // ---- Selection, and what selection unlocks ---------------------------------------------

        LaunchedEffect(selection.size) {
            if (scene != Scene.Gather) return@LaunchedEffect
            if (selection.isNotEmpty() && capsuleIn.value < 1f) {
                capsuleIn.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 400f))
            }
            if (selection.isEmpty()) capsuleIn.animateTo(0f, tween(200))

            if (selection.size == 3 && !converged) {
                converged = true
                delay(420)
                // The chosen objects travel into the capsule: the batch is a thing you can see.
                selection.forEachIndexed { i, id ->
                    val state = byId[id] ?: return@forEachIndexed
                    scope.launch {
                        delay(i * 70L)
                        launch { state.centre.animateTo(capsuleCentre, ConvergeSpring) }
                        launch { state.scale.animateTo(0.22f, tween(420, easing = emphasized)) }
                        launch { state.alpha.animateTo(0f, tween(400, delayMillis = 90)) }
                    }
                }
                delay(1250)
                if (scene == Scene.Gather) scene = Scene.Resolved
            }
        }

        if (capsuleIn.value > 0.01f) {
            SelectionCapsule(
                count = selection.size,
                progress = capsuleIn.value,
                centre = capsuleCentre,
                stageWidth = stageWidth,
                stageHeight = stageHeight,
            )
        }

        // ---- Chrome -----------------------------------------------------------------------------

        StoryChrome(
            scene = scene,
            cueVisible = cueVisible,
            selectionCount = selection.size,
            converged = converged,
            onSkip = onFinish,
            onFinish = onFinish,
        )
    }
}

// ---- State ------------------------------------------------------------------------------------

/** One object's live position in fraction space, plus how present it currently is. */
private class ObjectState(val obj: StoryObject, start: Offset) {
    val centre = Animatable(start, Offset.VectorConverter)
    val alpha = Animatable(1f)
    val scale = Animatable(1f)
}

private val CentreSpring = spring<Offset>(
    dampingRatio = 0.78f,
    stiffness = 210f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

/** Slower and heavier: the final settle should read as weight coming to rest. */
private val ResolveSpring = spring<Offset>(
    dampingRatio = 0.86f,
    stiffness = 150f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

private val ConvergeSpring = spring<Offset>(
    dampingRatio = 0.9f,
    stiffness = 320f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

/** A touch that is acknowledged but declined — the object gives, then returns. */
private fun CoroutineScope.nudge(state: ObjectState) {
    launch {
        state.scale.animateTo(0.93f, spring(dampingRatio = 1f, stiffness = 2200f))
        state.scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f))
    }
}

// ---- Pieces -----------------------------------------------------------------------------------

/**
 * The capsule the selection converges into — the same silhouette and the same inverse surface the
 * real app uses, so the thing learned here is the thing that ships.
 */
@Composable
private fun SelectionCapsule(
    count: Int,
    progress: Float,
    centre: Offset,
    stageWidth: Dp,
    stageHeight: Dp,
) {
    val colors = ManagerTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = stageHeight * centre.y - 26.dp, start = 10.dp, end = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.86f + 0.14f * progress
                    scaleY = 0.86f + 0.14f * progress
                }
                .shadow(
                    elevation = 22.dp,
                    shape = ManagerTheme.shapes.capsule,
                    clip = false,
                    ambientColor = colors.ink.copy(alpha = 0.42f),
                    spotColor = colors.ink.copy(alpha = 0.3f),
                )
                .clip(ManagerTheme.shapes.capsule)
                .background(colors.surfaceInverse)
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(110)) + slideOutVertically { -it / 2 })
                },
                label = "storyCount",
            ) { value ->
                Txt("$value", style = ManagerTheme.type.titleM, color = colors.onSurfaceInverse, maxLines = 1)
            }
            Spacer(Modifier.width(6.dp))
            Txt(
                "selected",
                style = ManagerTheme.type.meta,
                color = colors.onSurfaceInverse.copy(alpha = 0.62f),
                maxLines = 1,
            )

            AnimatedVisibility(
                visible = count >= 3,
                enter = fadeIn(tween(240, delayMillis = 120)) +
                    androidx.compose.animation.expandHorizontally(
                        spring(dampingRatio = 0.85f, stiffness = 320f),
                    ),
                exit = fadeOut(tween(120)) + androidx.compose.animation.shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(20.dp)
                            .background(colors.onSurfaceInverse.copy(alpha = 0.2f)),
                    )
                    Spacer(Modifier.width(12.dp))
                    CapsuleAction(ManagerIcons.Extract, "Extract", colors.onSurfaceInverse)
                    Spacer(Modifier.width(13.dp))
                    CapsuleAction(ManagerIcons.Trash, "Remove", colors.ember)
                }
            }
        }
    }
}

@Composable
private fun CapsuleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ManagerIcon(icon, null, tint = tint, size = 16.dp)
        Txt(label, style = ManagerTheme.type.labelS, color = tint, maxLines = 1)
    }
}

/**
 * What the five facts turn into.
 *
 * It is the dashboard's own hero, in miniature — which is the point: the last thing the story
 * shows is the first thing the product will.
 */
@Composable
private fun SummaryCard(
    progress: Float,
    centre: Offset,
    stageWidth: Dp,
    stageHeight: Dp,
) {
    val colors = ManagerTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = stageHeight * centre.y - 52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(stageWidth * 0.84f)
                .graphicsLayer {
                    alpha = progress
                    scaleX = 0.9f + 0.1f * progress
                    scaleY = 0.9f + 0.1f * progress
                    translationY = (1f - progress) * 16.dp.toPx()
                }
                .shadow(
                    elevation = 4.dp,
                    shape = SquircleShape(24.dp, 0.7f),
                    clip = false,
                    ambientColor = colors.ink.copy(alpha = 0.26f),
                    spotColor = colors.ink.copy(alpha = 0.18f),
                )
                .clip(SquircleShape(24.dp, 0.7f))
                .background(colors.surface)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Txt("YOUR PHONE", style = ManagerTheme.type.eyebrow, color = colors.inkTertiary)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryStat("360", "apps")
                SummaryStat("48.2", "GB")
                SummaryStat("4h 12m", "today")
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String) {
    Column {
        Txt(value, style = ManagerTheme.type.displayS, color = ManagerTheme.colors.ink, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Txt(label, style = ManagerTheme.type.metaS, color = ManagerTheme.colors.inkTertiary, maxLines = 1)
    }
}

/**
 * Wordmark, escape hatch, and the one line of copy the current beat is entitled to.
 *
 * The copy sits at the bottom for the three beats where the field is the subject, and moves to
 * the top for the one where the surface is — because the surface needs the bottom of the screen
 * and the words should never be the thing the user has to work around.
 */
@Composable
private fun StoryChrome(
    scene: Scene,
    cueVisible: Boolean,
    selectionCount: Int,
    converged: Boolean,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val emphasized = ManagerTheme.motion.emphasized

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ManagerMark(size = 26.dp, animated = true)
            Spacer(Modifier.width(11.dp))
            Txt("Manager", style = ManagerTheme.type.titleM, color = colors.ink)
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(
                visible = scene != Scene.Resolved,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200)),
            ) {
                ManagerTextAction("Skip intro", onSkip, color = colors.inkTertiary)
            }
        }

        // Beat two hands the bottom of the screen to the surface, so the line moves up here.
        AnimatedVisibility(
            visible = scene == Scene.Explore,
            enter = fadeIn(tween(320, delayMillis = 220)) + slideInVertically(
                tween(420, delayMillis = 220, easing = emphasized),
            ) { -it / 3 },
            exit = fadeOut(tween(160)),
        ) {
            Column {
                Spacer(Modifier.height(34.dp))
                Txt("See where\nyour phone goes.", style = ManagerTheme.type.displayL, color = colors.ink)
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = scene != Scene.Explore,
            enter = fadeIn(tween(300, delayMillis = 140)),
            exit = fadeOut(tween(140)),
        ) {
            Column {
                AnimatedContent(
                    targetState = scene,
                    transitionSpec = {
                        (
                            fadeIn(tween(360, delayMillis = 120, easing = emphasized)) +
                                slideInVertically(
                                    tween(460, delayMillis = 120, easing = emphasized),
                                ) { it / 4 }
                            ) togetherWith (
                            fadeOut(tween(180)) + slideOutVertically(tween(240)) { -it / 6 }
                            )
                    },
                    label = "storyHeadline",
                ) { current ->
                    Column {
                        when (current) {
                            Scene.Resolved -> {
                                Txt("Your phone,\nunderstood.", style = ManagerTheme.type.displayXl, color = colors.ink)
                                Spacer(Modifier.height(18.dp))
                                Txt(
                                    "Manager turns everything Android already knows about your apps " +
                                        "into something you can actually act on.",
                                    style = ManagerTheme.type.body,
                                    color = colors.inkSecondary,
                                    modifier = Modifier.fillMaxWidth(0.94f),
                                )
                            }

                            Scene.Gather -> Txt(
                                "Now pick a few.",
                                style = ManagerTheme.type.displayXl,
                                color = colors.ink,
                            )

                            else -> Txt(
                                "There's a lot\ngoing on.",
                                style = ManagerTheme.type.displayXl,
                                color = colors.ink,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // The cue is a whisper, not an instruction panel: one line, low contrast, and it
                // rewrites itself as the user makes progress instead of stacking up steps.
                Box(Modifier.height(26.dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = cueVisible && scene != Scene.Resolved && !converged,
                        enter = fadeIn(tween(420)) + slideInVertically(tween(460)) { it / 3 },
                        exit = fadeOut(tween(160)),
                    ) {
                        CuePulse(
                            text = when {
                                scene == Scene.Discovery -> "Touch one."
                                selectionCount == 0 -> "Hold one."
                                selectionCount == 1 -> "Two more."
                                selectionCount == 2 -> "One more."
                                else -> ""
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = scene == Scene.Resolved,
                    enter = fadeIn(tween(420, delayMillis = 620)) + slideInVertically(
                        spring(dampingRatio = 0.82f, stiffness = 260f),
                    ) { it / 2 },
                    exit = fadeOut(tween(120)),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        ManagerButton("Open Manager", onFinish, fillWidth = true)
                    }
                }

                Spacer(Modifier.height(34.dp))
            }
        }
    }
}

/** The cue breathes rather than blinks — presence without pestering. */
@Composable
private fun CuePulse(text: String) {
    val transition = rememberInfiniteTransition(label = "cue")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = ManagerTheme.motion.standardEase),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cuePulse",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(5.dp)
                .graphicsLayer { alpha = pulse }
                .clip(ManagerTheme.shapes.capsule)
                .background(ManagerTheme.colors.signal),
        )
        Spacer(Modifier.width(9.dp))
        Txt(
            text,
            style = ManagerTheme.type.labelS,
            color = ManagerTheme.colors.inkSecondary,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = 0.55f + 0.45f * pulse },
        )
    }
}
