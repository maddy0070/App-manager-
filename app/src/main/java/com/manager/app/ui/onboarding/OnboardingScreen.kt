package com.manager.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.plotTint
import com.manager.app.design.components.CountingBytes
import com.manager.app.design.components.InverseGlyphButton
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerMark
import com.manager.app.design.components.ManagerTextAction
import com.manager.app.design.components.ReclaimBlock
import com.manager.app.design.components.Txt
import com.manager.app.design.components.VizSegment
import com.manager.app.design.components.driftOffset
import com.manager.app.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Onboarding as an object you are handed, rather than a tour you are taken on.
 *
 * One field of five apps, measured twice. Nothing is ever created or destroyed here — every beat
 * is the same objects being re-measured, opened, or gathered, which is what makes the continuity
 * structural rather than choreographed.
 *
 *  - **Discover.** The field arrives unclaimed: five objects, no order, meaning nothing. One
 *    invitation, one button.
 *  - **Understand.** Explore measures the field by screen time — position is rank, width is hours,
 *    the bar reads the total. Touch an app and it *becomes* the detail surface; drag that surface
 *    and the single figure it arrived with comes apart into app, data and cache.
 *  - **Interact.** Closing it re-measures the whole field by storage, and the order visibly
 *    disagrees with itself: the app used most is nearly the smallest, and the one barely opened in
 *    a fortnight is the largest thing here. That contradiction is the product's entire argument,
 *    made by moving five objects rather than by drawing a chart.
 *  - **Act.** Hold to select — the real gesture, with the real bar — and the weight accumulates.
 *    Review gathers the batch into a single measured rail.
 *
 * Nothing instructs. Two short lines of copy exist: the invitation at the start, and the sentence
 * at the end, which arrives after every claim in it has already happened under the reader's finger.
 *
 * Entirely self-contained: it runs on [StoryCast], so a fresh install with nothing granted and
 * nothing scanned tells the same story, deterministically.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = ManagerTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(Phase.Still) }
    var openedIndex by remember { mutableStateOf<Int?>(null) }
    var openedCentre by remember { mutableStateOf(Offset(0.5f, 0.3f)) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var touched by remember { mutableStateOf(false) }
    var closedOnce by remember { mutableStateOf(false) }
    var closeRequests by remember { mutableIntStateOf(0) }

    val morph = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val ledgerIn = remember { Animatable(0f) }
    val panelIn = remember { Animatable(0f) }
    val lift = remember { Animatable(1f) }
    val invite = remember { Animatable(0f) }
    val stillness = remember { Animatable(0f) }
    val doneIn = remember { Animatable(0f) }

    // Motion tokens read once: the staged animations below run in coroutine scope, not composition.
    val emphasized = ManagerTheme.motion.emphasized
    val exitEase = ManagerTheme.motion.exit
    val standardEase = ManagerTheme.motion.standardEase

    // Before Explore the field is present but unclaimed: visible enough to be intriguing, faint
    // enough that it is plainly not yet saying anything.
    val objects = remember {
        StoryCast.indices.map { ObjectState(StoryStaging.home(it, StoryStaging.Measure.Loose), startAlpha = 0.4f) }
    }
    val sizes = remember { mutableStateMapOf<Int, IntSize>() }
    val measure = phase.measure
    // Whatever currently sits at the top of the field is the object worth inviting a touch to.
    val inviteIndex = remember(measure) { StoryCast.indices.first { StoryStaging.rank(it, measure) == 0 } }
    val handoff = remember { Animatable(0f) }

    val selectedApps = StoryCast.filter { it.id in selection }
    val ledgerBytes = if (selection.isEmpty()) StoryCast.sumOf { it.totalBytes } else selectedApps.sumOf { it.totalBytes }
    val ledgerCount = if (selection.isEmpty()) StoryCast.size else selectedApps.size
    val fieldScreenTime = StoryCast.sumOf { it.screenTimeMs }

    // The field never stops breathing; each object traces its own slow path.
    val transition = rememberInfiniteTransition(label = "storyDrift")
    val phaseAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Restart),
        label = "storyPhase",
    )

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.canvas)) {
        val stageWidth = maxWidth
        val stageHeight = maxHeight
        val density = LocalDensity.current

        // Where the batch comes to rest: the track inside the panel that replaces the bar.
        // Measured from the bottom: the inset, the slot's own margin, the reserved way-out, the
        // panel's padding, and half the track.
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val railY = 1f - ((bottomInset + 14.dp + 68.dp + 20.dp + 40.dp) / stageHeight)
        val railInset = 38.dp / stageWidth
        val railSpan = 1f - railInset * 2f

        // The bar gets out of the way of the surface it made room for, completely: left at a few
        // percent it would sit over the sheet and still take the taps meant for it.
        val ledgerPresence = ledgerIn.value * (1f - morph.value)

        // ---- Gestures. The real ones: hold to start selecting, tap to open or toggle. ----------

        val onTap by rememberUpdatedState<(Int) -> Unit> { index ->
            if (!phase.isField) return@rememberUpdatedState
            touched = true
            val app = StoryCast[index]
            if (selection.isEmpty()) {
                openedCentre = objects[index].centre.value
                openedIndex = index
            } else {
                selection = if (app.id in selection) selection - app.id else selection + app.id
            }
        }
        val onHold by rememberUpdatedState<(Int) -> Unit> { index ->
            if (!phase.isField) return@rememberUpdatedState
            touched = true
            if (selection.isEmpty()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                // Selecting is about weight, so a hold settles the field into the dimension the
                // total is counted in. Anyone who reaches for selection first still gets there.
                if (phase == Phase.Usage) phase = Phase.Storage
                selection = setOf(StoryCast[index].id)
            }
        }

        // ---- The bar arrives a beat after the field, so the field is what is looked at first ---

        LaunchedEffect(phase) {
            if (phase == Phase.Still) return@LaunchedEffect
            delay(if (ledgerIn.value > 0f) 0 else 260)
            ledgerIn.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 300f))
        }

        /**
         * The measure changed, so every object moves and resizes at once.
         *
         * This is the moment the whole screen exists for. Nothing is created or destroyed: the
         * same five objects travel to their rank in the new dimension while their widths retune,
         * and the app that was at the top of one list visibly falls to the bottom of the other.
         */
        LaunchedEffect(measure) {
            if (measure == StoryStaging.Measure.Loose) return@LaunchedEffect
            objects.forEachIndexed { index, state ->
                launch {
                    delay(StoryStaging.rank(index, measure) * 45L)
                    state.centre.animateTo(StoryStaging.displaced(index, selection, measure), ReorderSpring)
                }
                launch { state.alpha.animateTo(1f, tween(360, easing = emphasized)) }
            }
        }

        // ---- The invitation. Shown, never written. ----------------------------------------------

        val invitation = when {
            !phase.isField || openedIndex != null -> Invitation.None
            !touched -> Invitation.Breathe
            closedOnce && selection.isEmpty() -> Invitation.Hold
            else -> Invitation.None
        }
        LaunchedEffect(invitation) {
            if (invitation == Invitation.None) {
                invite.animateTo(0f, tween(200))
                return@LaunchedEffect
            }
            var round = 0
            while (true) {
                delay(
                    when {
                        invitation == Invitation.Hold -> 3200L
                        round == 0 -> 2300L
                        else -> 5400L
                    },
                )
                round++
                if (invitation == Invitation.Breathe) {
                    // Something with mass, settling. Enough to say "this is alive and touchable".
                    invite.animateTo(1f, tween(560, easing = emphasized))
                    invite.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 220f))
                } else {
                    // The shape of a long press, performed rather than described: the object
                    // compresses, holds, and springs back.
                    invite.animateTo(-1f, tween(280, easing = standardEase))
                    delay(440)
                    invite.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f))
                }
            }
        }

        // ---- Selection displaces the field ------------------------------------------------------

        LaunchedEffect(selection, phase) {
            if (!phase.isField) return@LaunchedEffect
            objects.forEachIndexed { index, state ->
                val chosen = StoryCast[index].id in selection
                launch { state.centre.animateTo(StoryStaging.displaced(index, selection, measure), DisplaceSpring) }
                if (openedIndex == null) {
                    launch { state.scale.animateTo(if (chosen) 1.045f else 1f, spring(dampingRatio = 0.62f, stiffness = 420f)) }
                }
            }
        }

        // ---- Opening an object ------------------------------------------------------------------

        LaunchedEffect(openedIndex) {
            val index = openedIndex ?: return@LaunchedEffect
            reveal.snapTo(0f)
            objects.forEachIndexed { other, state ->
                if (other == index) {
                    launch { state.alpha.animateTo(0f, tween(90)) }
                } else {
                    launch { state.alpha.animateTo(0.10f, tween(420, easing = emphasized)) }
                    launch { state.scale.animateTo(0.94f, spring(dampingRatio = 1f, stiffness = 260f)) }
                }
            }
            morph.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        }

        // The surface shrinks back into the object it grew from; only then is it gone.
        LaunchedEffect(closeRequests) {
            if (closeRequests == 0) return@LaunchedEffect
            val index = openedIndex ?: return@LaunchedEffect
            reveal.animateTo(0f, tween(220, easing = exitEase))
            launch { objects[index].alpha.animateTo(1f, tween(200, delayMillis = 120)) }
            morph.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 420f))
            openedIndex = null
            closedOnce = true
            // Having just taken one object apart into app, data and cache, the field re-forms
            // around the fact the user was shown. The transition is caused, not scheduled.
            if (phase == Phase.Usage) phase = Phase.Storage
            objects.forEachIndexed { other, state ->
                launch { state.alpha.animateTo(1f, tween(260)) }
                launch {
                    val chosen = StoryCast[other].id in selection
                    state.scale.animateTo(if (chosen) 1.045f else 1f, spring(dampingRatio = 0.8f, stiffness = 300f))
                }
            }
        }

        // ---- Removal: the batch becomes a measurement, and then the measurement leaves ----------

        LaunchedEffect(phase) {
            if (phase != Phase.Review) return@LaunchedEffect
            launch { stillness.animateTo(1f, tween(700, easing = emphasized)) }
            launch { ledgerIn.animateTo(0f, tween(180, easing = exitEase)) }
            launch { panelIn.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }

            // Everything not chosen steps out of the story.
            objects.forEachIndexed { index, state ->
                if (StoryCast[index].id !in selection) {
                    launch { state.alpha.animateTo(0f, tween(340, easing = exitEase)) }
                }
            }

            // The chosen travel to their own slot in the rail and hand over to the block there,
            // which carries the same colour they did. That is what makes five objects becoming one
            // measurement legible without a caption.
            val total = selectedApps.sumOf { it.totalBytes }.coerceAtLeast(1L)
            var running = 0f
            val slots = selectedApps.map { app ->
                val share = app.totalBytes.toFloat() / total
                val centre = railInset + railSpan * (running + share / 2f)
                running += share
                app.id to centre
            }.toMap()

            delay(200)
            selectedApps.forEachIndexed { order, app ->
                val index = StoryCast.indexOfFirst { it.id == app.id }
                val x = slots[app.id] ?: 0.5f
                launch {
                    delay(order * 90L)
                    launch { objects[index].centre.animateTo(Offset(x, railY), ConvergeSpring) }
                    launch { objects[index].scale.animateTo(0.2f, tween(430, easing = emphasized)) }
                    launch { objects[index].alpha.animateTo(0f, tween(380, delayMillis = 90)) }
                }
            }

            // The rail fills as they land. Nothing then leaves it: this is a review, and nothing
            // has been removed, so drawing a departure would be the one lie on the screen.
            delay(320)
            lift.animateTo(0f, tween(760, easing = emphasized))
            phase = Phase.Done
        }

        // Deliberately its own effect. The sequence above is keyed on the phase and ends by
        // changing it, so anything it tried to run after that line would be cancelled with it.
        LaunchedEffect(phase) {
            if (phase == Phase.Done) doneIn.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 260f))
        }

        /**
         * The last frame of the demonstration is the first frame of the product.
         *
         * On the way out the review panel stretches to the full width of a list row and the whole
         * stack rises, so what dissolves into the real app is already the shape of the app list
         * rather than a card being replaced by a screen.
         */
        val leave: () -> Unit = {
            scope.launch {
                handoff.animateTo(1f, tween(380, easing = emphasized))
                onFinish()
            }
            Unit
        }

        // ---- The field --------------------------------------------------------------------------

        objects.forEachIndexed { index, state ->
            // An object that has handed its role to something else is gone, not merely invisible:
            // leaving it composed keeps it in the semantics tree and lets it swallow a touch meant
            // for something underneath.
            if (state.alpha.value < 0.02f) return@forEachIndexed
            val app = StoryCast[index]
            val selected = app.id in selection
            // Unmeasured or dimmed objects are scenery: inert, and not read out.
            val decorative = state.alpha.value < 0.35f || !phase.isField
            val width = stageWidth * StoryStaging.widthFraction(app, measure)
            val measured = sizes[index]
            val pxWidth = measured?.width ?: with(density) { width.roundToPx() }
            val pxHeight = measured?.height ?: with(density) { 57.dp.roundToPx() }

            // Drift is additive and dies away once the field stops being the subject.
            val drift = driftOffset(phaseAngle, index * 3 + 1, 5.5f) * (1f - stillness.value)
            val centre = state.centre.value
            val x = stageWidth.value * density.density * centre.x - pxWidth / 2f + drift.x
            val y = stageHeight.value * density.density * centre.y - pxHeight / 2f + drift.y
            val invited = if (index == inviteIndex) invite.value else 0f

            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .onSizeChanged { sizes[index] = it }
                    .graphicsLayer {
                        alpha = state.alpha.value
                        val s = state.scale.value * (1f + invited * 0.04f)
                        scaleX = s
                        scaleY = s
                        // Rising reads as something you could pick up; growing alone reads as a
                        // pulsing button. Negative values are the press being demonstrated.
                        translationY = -invited.coerceAtLeast(0f) * 5.dp.toPx()
                    }
                    .then(
                        if (decorative) {
                            Modifier.clearAndSetSemantics { }
                        } else {
                            Modifier.combinedClickable(
                                interactionSource = remember(index) { MutableInteractionSource() },
                                indication = null,
                                onClickLabel = when {
                                    selection.isEmpty() -> "Open details"
                                    selected -> "Deselect"
                                    else -> "Select"
                                },
                                onLongClickLabel = if (selection.isEmpty()) "Select" else null,
                                onLongClick = { onHold(index) },
                                onClick = { onTap(index) },
                            )
                        },
                    ),
            ) {
                StoryChip(app = app, selected = selected, width = width, measure = measure)
            }
        }

        // ---- The bar that always says what things weigh --------------------------------------------

        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (ledgerPresence > 0.01f) {
                StoryLedger(
                    count = ledgerCount,
                    bytes = ledgerBytes,
                    durationMs = fieldScreenTime,
                    measure = measure,
                    selecting = selection.isNotEmpty(),
                    presence = ledgerPresence,
                    onClear = { selection = emptySet() },
                    onRemove = { if (selection.isNotEmpty()) phase = Phase.Review },
                )
            }
        }

        // ---- What the field is currently ordered by ------------------------------------------------

        // One word, pinned to the object at the top of the order, and it changes when the order
        // does. It is the only label on the field, and it exists because "these are ranked" is the
        // one thing position alone cannot say.
        if (phase.isField && openedIndex == null) {
            val topCentre = objects[inviteIndex].centre.value
            val topHeight = sizes[inviteIndex]?.height ?: with(density) { 57.dp.roundToPx() }
            val markerY = stageHeight.value * density.density * topCentre.y - topHeight / 2f
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, (markerY - with(density) { 18.dp.toPx() }).roundToInt()) },
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = measure,
                    transitionSpec = {
                        (fadeIn(tween(320, delayMillis = 220)) + slideInVertically(
                            tween(360, delayMillis = 220, easing = emphasized),
                        ) { it / 2 }) togetherWith fadeOut(tween(160))
                    },
                    label = "fieldOrder",
                ) { current ->
                    Txt(
                        if (current == StoryStaging.Measure.Usage) "MOST USED" else "LARGEST",
                        style = ManagerTheme.type.eyebrow,
                        color = colors.inkTertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        // ---- The surface an object became ---------------------------------------------------------

        val opened = openedIndex?.let { StoryCast[it] }
        if (opened != null && morph.value > 0.001f) {
            // Anywhere outside the surface puts it back, which is how the real sheet behaves too.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = morph.value * 0.001f }
                    .clearAndSetSemantics { }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { closeRequests++ },
                    ),
            )
            StoryDetailSurface(
                app = opened,
                morph = morph.value,
                reveal = reveal.value,
                stageWidth = stageWidth,
                stageHeight = stageHeight,
                chipWidth = stageWidth * StoryStaging.widthFraction(opened, measure),
                chipCentre = openedCentre,
                measure = measure,
                onReveal = { value -> scope.launch { reveal.snapTo(value) } },
                onRevealSettled = { target ->
                    scope.launch { reveal.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 380f)) }
                },
                onDismiss = { closeRequests++ },
            )
        }


        if (panelIn.value > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = panelIn.value
                                val s = (0.94f + 0.06f * panelIn.value) * (1f + handoff.value * 0.04f)
                                scaleX = s
                                scaleY = s
                                // On the way out the panel rises and squares off, so the last frame
                                // of the demonstration already has the proportions of a list row.
                                translationY = -handoff.value * 54.dp.toPx()
                            }
                            .shadow(
                                elevation = 26.dp,
                                shape = ManagerTheme.shapes.lg,
                                clip = false,
                                ambientColor = colors.ink.copy(alpha = 0.34f),
                                spotColor = colors.ink.copy(alpha = 0.24f),
                            )
                            .clip(ManagerTheme.shapes.lg)
                            .background(colors.surface)
                            .padding(20.dp),
                    ) {
                        ReclaimBlock(
                            caption = "SELECTED",
                            bytes = selectedApps.sumOf { it.totalBytes },
                            blocks = selectedApps.map { VizSegment(it.label, it.totalBytes, plotTint(it.tint)) },
                            lift = lift.value,
                            departs = true,
                            note = "Removing these would give that space back. Nothing here is real, " +
                                "and nothing has been touched.",
                        )
                    }
                    // Reserved from the moment the panel appears, so nothing shifts when the way
                    // out arrives in it.
                    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.BottomCenter) {
                        if (doneIn.value > 0.01f) {
                            ManagerButton(
                                "Open Manager",
                                leave,
                                fillWidth = true,
                                modifier = Modifier.graphicsLayer {
                                    alpha = doneIn.value
                                    translationY = (1f - doneIn.value) * 22.dp.toPx()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ---- Chrome ---------------------------------------------------------------------------------

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
                Column {
                    Txt("Manager", style = ManagerTheme.type.titleM, color = colors.ink)
                    // Said once, plainly. Nothing on this screen came from the user's phone.
                    Txt("Sample data", style = ManagerTheme.type.metaS, color = colors.inkTertiary)
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = phase != Phase.Done,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200)),
                ) {
                    ManagerTextAction("Skip", onFinish, color = colors.inkTertiary)
                }
            }

            Spacer(Modifier.weight(1f))

            // An unmeasured field, and an invitation to measure it. This is the only screen state
            // that asks for anything, and it asks once.
            AnimatedVisibility(
                visible = phase == Phase.Still,
                enter = fadeIn(tween(420, delayMillis = 260, easing = emphasized)) + slideInVertically(
                    tween(520, delayMillis = 260, easing = emphasized),
                ) { it / 5 },
                exit = fadeOut(tween(200)) + slideOutVertically(tween(300, easing = exitEase)) { -it / 6 },
            ) {
                Column {
                    Txt("Your phone\nhas a story.", style = ManagerTheme.type.displayXl, color = colors.ink)
                    Spacer(Modifier.height(14.dp))
                    Txt(
                        "Let's uncover it.",
                        style = ManagerTheme.type.body,
                        color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(24.dp))
                    ManagerButton(
                        "Explore",
                        { phase = Phase.Usage },
                        icon = ManagerIcons.ArrowUpRight,
                    )
                    Spacer(Modifier.height(26.dp))
                }
            }

            // The only sentence in the whole screen, and it arrives after every claim in it has
            // already happened under the reader's own finger.
            AnimatedVisibility(
                visible = phase == Phase.Done,
                enter = fadeIn(tween(420, delayMillis = 120, easing = emphasized)) + slideInVertically(
                    tween(520, delayMillis = 120, easing = emphasized),
                ) { it / 4 },
            ) {
                Column {
                    Txt("Your phone,\nweighed.", style = ManagerTheme.type.displayXl, color = colors.ink)
                    Spacer(Modifier.height(16.dp))
                    Txt(
                        "Everything Android already knows about your apps, turned into something " +
                            "you can act on.",
                        style = ManagerTheme.type.body,
                        color = colors.inkSecondary,
                        modifier = Modifier.fillMaxWidth(0.94f),
                    )
                    Spacer(Modifier.height(26.dp))
                }
            }

            // The height the bottom slot occupies, so the sentence above never lands under it.
            Spacer(Modifier.height(if (phase.isField) 84.dp else if (phase == Phase.Still) 40.dp else 244.dp))
        }
    }
}

/**
 * Discover, understand, interact, act.
 *
 * [Still] is a field that has not been measured — objects present, meaning nothing. [Usage] and
 * [Storage] are the same five objects measured against facts that disagree, and moving between
 * them is the product's whole argument made physically. [Review] is the batch gathered and
 * weighed; [Done] flattens it into the shape of the list the user is about to meet.
 */
private enum class Phase { Still, Usage, Storage, Review, Done }

private val Phase.measure: StoryStaging.Measure
    get() = when (this) {
        Phase.Still -> StoryStaging.Measure.Loose
        Phase.Usage -> StoryStaging.Measure.Usage
        else -> StoryStaging.Measure.Storage
    }

/** The field is live — touchable, selectable, re-orderable — in exactly these two phases. */
private val Phase.isField: Boolean get() = this == Phase.Usage || this == Phase.Storage

private enum class Invitation { None, Breathe, Hold }

/** One object's live position in fraction space, plus how present it currently is. */
private class ObjectState(start: Offset, startAlpha: Float = 1f) {
    val centre = Animatable(start, Offset.VectorConverter)
    val alpha = Animatable(startAlpha)
    val scale = Animatable(1f)
}

private val DisplaceSpring = spring<Offset>(
    dampingRatio = 0.72f,
    stiffness = 260f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

/** The re-measure. Slow and heavy on purpose: five objects changing places must be followable. */
private val ReorderSpring = spring<Offset>(
    dampingRatio = 0.84f,
    stiffness = 130f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

private val ConvergeSpring = spring<Offset>(
    dampingRatio = 0.9f,
    stiffness = 300f,
    visibilityThreshold = Offset(0.0004f, 0.0004f),
)

/**
 * The bar, which is the real selection bar wearing the demonstration's data.
 *
 * Idle it weighs the field; selecting, it weighs the selection — same capsule, same inverse
 * surface, same travelling figure, same glyph buttons. It offers one action, and that action
 * reviews rather than removes: nothing on this screen is real, so nothing is destroyed, and the
 * glyph opens the same kind of weighed confirmation the real trash button does. Extraction is
 * absent because it copies real files to real Downloads, and a button here that pretended to do
 * that would be the one dishonest thing on the screen. The full bar is one tap away.
 */
@Composable
private fun StoryLedger(
    count: Int,
    bytes: Long,
    durationMs: Long,
    measure: StoryStaging.Measure,
    selecting: Boolean,
    presence: Float,
    onClear: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = ManagerTheme.colors
    Row(
        modifier = Modifier
            .graphicsLayer {
                alpha = presence
                val s = 0.88f + 0.12f * presence
                scaleX = s
                scaleY = s
            }
            .shadow(
                elevation = 24.dp,
                shape = ManagerTheme.shapes.capsule,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.45f),
                spotColor = colors.ink.copy(alpha = 0.32f),
            )
            .clip(ManagerTheme.shapes.capsule)
            .background(colors.surfaceInverse)
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 380f))
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (selecting) {
            InverseGlyphButton(ManagerIcons.Close, "Clear selection", onClear, colors.onSurfaceInverse)
        }
        Column(Modifier.padding(start = if (selecting) 2.dp else 14.dp, end = 12.dp)) {
            Txt(
                if (selecting) "$count selected" else "$count apps",
                style = ManagerTheme.type.metaS,
                color = colors.onSurfaceInverse.copy(alpha = 0.62f),
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            // Whatever the field is currently measured by, the bar reads the same thing. Time
            // does not travel between values the way bytes do — the total only changes when the
            // selection does, and there is no selecting in the usage dimension.
            if (measure == StoryStaging.Measure.Usage) {
                Txt(
                    Format.duration(durationMs),
                    style = ManagerTheme.type.titleM,
                    color = colors.onSurfaceInverse,
                    maxLines = 1,
                )
            } else {
                CountingBytes(
                    bytes = bytes,
                    valueStyle = ManagerTheme.type.titleM,
                    unitStyle = ManagerTheme.type.metaS,
                    valueColor = colors.onSurfaceInverse,
                    unitColor = colors.onSurfaceInverse.copy(alpha = 0.62f),
                    gap = 3.dp,
                )
            }
        }
        if (selecting) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(colors.onSurfaceInverse.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.width(1.dp))
            InverseGlyphButton(ManagerIcons.Trash, "Review selection", onRemove, colors.emberOnInverse)
        }
    }
}
