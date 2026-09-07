package com.manager.app.ui.onboarding

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Onboarding as an object you are handed, rather than a tour you are taken on.
 *
 * There are no steps here and nothing to advance. The screen is a field of five apps drawn at the
 * size of what they weigh, and a bar along the bottom that always states the weight of whatever is
 * currently in play. Everything the product does is reachable from that one arrangement, in any
 * order, as many times as the user likes:
 *
 *  - touch an app and it *becomes* the detail surface, then drag that surface and the single figure
 *    it arrived with comes apart into app, data, cache and the rest of the record;
 *  - hold an app and it is selected, exactly as in the real list, and its weight lands in the bar;
 *  - select more and the figure accumulates, while the unchosen objects ease away from the ones
 *    that just gained mass;
 *  - remove the batch and the chosen objects collapse into a measured rail and then leave it,
 *    which is the same moment the real confirmation sheet shows.
 *
 * Nothing instructs. The only line of copy that is not a label is the one at the very end, after
 * every claim it makes has already happened under the reader's own finger.
 *
 * Entirely self-contained: it runs on [StoryCast], so a fresh install with nothing granted and
 * nothing scanned tells the same story, deterministically.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = ManagerTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(Phase.Field) }
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

    val objects = remember { StoryCast.indices.map { ObjectState(StoryStaging.home(it)) } }
    val sizes = remember { mutableStateMapOf<Int, IntSize>() }
    val inviteIndex = remember { StoryCast.indices.maxBy { StoryCast[it].totalBytes } }

    val selectedApps = StoryCast.filter { it.id in selection }
    val ledgerBytes = if (selection.isEmpty()) StoryCast.sumOf { it.totalBytes } else selectedApps.sumOf { it.totalBytes }
    val ledgerCount = if (selection.isEmpty()) StoryCast.size else selectedApps.size

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
            if (phase != Phase.Field) return@rememberUpdatedState
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
            if (phase != Phase.Field) return@rememberUpdatedState
            touched = true
            if (selection.isEmpty()) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                selection = setOf(StoryCast[index].id)
            }
        }

        // ---- The bar arrives a beat after the field, so the field is what is looked at first ---

        LaunchedEffect(Unit) {
            delay(560)
            ledgerIn.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 300f))
        }

        // ---- The invitation. Shown, never written. ----------------------------------------------

        val invitation = when {
            phase != Phase.Field || openedIndex != null -> Invitation.None
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
            if (phase != Phase.Field) return@LaunchedEffect
            objects.forEachIndexed { index, state ->
                val chosen = StoryCast[index].id in selection
                launch { state.centre.animateTo(StoryStaging.displaced(index, selection), DisplaceSpring) }
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
            if (phase != Phase.Reclaim) return@LaunchedEffect
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

            // The rail fills as they land, holds, and then empties. The gap it is left with is the
            // space that comes back — the figure above it never had to change to say so.
            delay(320)
            lift.animateTo(0f, tween(760, easing = emphasized))
            delay(640)
            lift.animateTo(1f, tween(980, easing = emphasized))
            phase = Phase.Done
        }

        // Deliberately its own effect. The sequence above is keyed on the phase and ends by
        // changing it, so anything it tried to run after that line would be cancelled with it.
        LaunchedEffect(phase) {
            if (phase == Phase.Done) doneIn.animateTo(1f, spring(dampingRatio = 0.84f, stiffness = 260f))
        }

        // ---- The field --------------------------------------------------------------------------

        objects.forEachIndexed { index, state ->
            // An object that has handed its role to something else is gone, not merely invisible:
            // leaving it composed keeps it in the semantics tree and lets it swallow a touch meant
            // for something underneath.
            if (state.alpha.value < 0.02f) return@forEachIndexed
            val app = StoryCast[index]
            val selected = app.id in selection
            val decorative = state.alpha.value < 0.35f
            val width = stageWidth * StoryStaging.widthFraction(app)
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
                StoryChip(app = app, selected = selected, width = width)
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
                    selecting = selection.isNotEmpty(),
                    presence = ledgerIn.value * (1f - morph.value * 0.94f),
                    onClear = { selection = emptySet() },
                    onRemove = { if (selection.isNotEmpty()) phase = Phase.Reclaim },
                )
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
                chipWidth = stageWidth * StoryStaging.widthFraction(opened),
                chipCentre = openedCentre,
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
                                val s = 0.94f + 0.06f * panelIn.value
                                scaleX = s
                                scaleY = s
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
                            caption = "COMES BACK",
                            bytes = selectedApps.sumOf { it.totalBytes },
                            blocks = selectedApps.map { VizSegment(it.label, it.totalBytes, plotTint(it.tint)) },
                            lift = lift.value,
                            departs = true,
                        )
                    }
                    // Reserved from the moment the panel appears, so nothing shifts when the way
                    // out arrives in it.
                    Box(Modifier.fillMaxWidth().height(68.dp), contentAlignment = Alignment.BottomCenter) {
                        if (doneIn.value > 0.01f) {
                            ManagerButton(
                                "Open Manager",
                                onFinish,
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
            Spacer(Modifier.height(if (phase == Phase.Field) 84.dp else 244.dp))
        }
    }
}

private enum class Phase { Field, Reclaim, Done }

private enum class Invitation { None, Breathe, Hold }

/** One object's live position in fraction space, plus how present it currently is. */
private class ObjectState(start: Offset) {
    val centre = Animatable(start, Offset.VectorConverter)
    val alpha = Animatable(1f)
    val scale = Animatable(1f)
}

private val DisplaceSpring = spring<Offset>(
    dampingRatio = 0.72f,
    stiffness = 260f,
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
 * surface, same travelling figure, same glyph buttons. It offers only Remove, because Remove is
 * the one action the demonstration can honestly carry through: extraction copies real files to
 * real Downloads, and a button here that pretended to do that would be the one dishonest thing on
 * the screen. The full bar is one tap away.
 */
@Composable
private fun StoryLedger(
    count: Int,
    bytes: Long,
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
            CountingBytes(
                bytes = bytes,
                valueStyle = ManagerTheme.type.titleM,
                unitStyle = ManagerTheme.type.metaS,
                valueColor = colors.onSurfaceInverse,
                unitColor = colors.onSurfaceInverse.copy(alpha = 0.62f),
                gap = 3.dp,
            )
        }
        if (selecting) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(colors.onSurfaceInverse.copy(alpha = 0.18f)),
            )
            Spacer(Modifier.width(1.dp))
            InverseGlyphButton(ManagerIcons.Trash, "Remove", onRemove, colors.emberOnInverse)
        }
    }
}
