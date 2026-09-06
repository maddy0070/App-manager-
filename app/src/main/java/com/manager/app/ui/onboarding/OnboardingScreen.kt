package com.manager.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.manager.app.ManagerGraph
import com.manager.app.design.ManagerIcons
import com.manager.app.design.ManagerTheme
import com.manager.app.design.SquircleShape
import com.manager.app.design.components.ButtonTone
import com.manager.app.design.components.ManagerButton
import com.manager.app.design.components.ManagerIcon
import com.manager.app.design.components.ManagerMark
import com.manager.app.design.components.ManagerTextAction
import com.manager.app.design.components.Txt
import com.manager.app.design.components.driftOffset
import com.manager.app.ui.ManagerViewModel
import kotlin.math.roundToInt

/**
 * Onboarding.
 *
 * Three beats, not a carousel: what this is, what it needs, and permission to begin. The page
 * never scrolls and never fills — the whole point of the first screen is to show restraint, so
 * the type is large, the margins are wide, and one CTA sits where the thumb already is.
 *
 * The floating chips behind the headline are the product's own vocabulary — user apps, system
 * apps, storage, usage, APKs — drifting on independent slow paths. They say what the app is about
 * without an illustration doing the talking.
 */
@Composable
fun OnboardingScreen(
    viewModel: ManagerViewModel,
    graph: ManagerGraph,
    onFinish: () -> Unit,
) {
    val colors = ManagerTheme.colors
    val usageGranted by viewModel.usageAccess.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    val emphasized = ManagerTheme.motion.emphasized
    val exitEase = ManagerTheme.motion.exit

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to colors.canvas,
                    0.55f to colors.canvas,
                    1f to if (colors.isLight) colors.signalSoft.copy(alpha = 0.55f) else colors.canvasSunken,
                ),
            ),
    ) {
        FloatingField(step = step)

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 30.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ManagerMark(size = 30.dp, animated = true)
                Spacer(Modifier.width(12.dp))
                Txt("Manager", style = ManagerTheme.type.titleM, color = colors.ink)
                Spacer(Modifier.weight(1f))
                StepIndicator(step = step, total = 3)
            }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (
                        fadeIn(tween(420, delayMillis = 110, easing = emphasized)) +
                            slideInVertically(
                                tween(560, delayMillis = 110, easing = emphasized),
                            ) { if (forward) it / 5 else -it / 5 }
                        ) togetherWith (
                        fadeOut(tween(190, easing = exitEase)) +
                            slideOutVertically(tween(260, easing = exitEase)) {
                                if (forward) -it / 8 else it / 8
                            }
                        )
                },
                label = "onboardingStep",
            ) { current ->
                when (current) {
                    0 -> IntroBeat()
                    1 -> PermissionBeat(granted = usageGranted)
                    else -> ReadyBeat(granted = usageGranted)
                }
            }

            Spacer(Modifier.height(38.dp))

            OnboardingActions(
                step = step,
                usageGranted = usageGranted,
                onNext = { step += 1 },
                onGrant = { viewModel.requestUsageAccess() },
                onSkip = { step = 2 },
                onFinish = onFinish,
            )

            Spacer(Modifier.height(30.dp))
        }
    }
}

// ---- Beats -----------------------------------------------------------------------------------

@Composable
private fun IntroBeat() {
    val colors = ManagerTheme.colors
    Column {
        Txt(
            "Every app\non this phone,\nunderstood.",
            style = ManagerTheme.type.displayXl,
            color = colors.ink,
        )
        Spacer(Modifier.height(22.dp))
        Txt(
            "Manager reads what Android already knows about your device — sizes, install dates, " +
                "split packages, time spent — and lays it out so you can actually act on it.",
            style = ManagerTheme.type.body,
            color = colors.inkSecondary,
            modifier = Modifier.fillMaxWidth(0.92f),
        )
    }
}

@Composable
private fun PermissionBeat(granted: Boolean) {
    val colors = ManagerTheme.colors
    Column {
        Txt(
            if (granted) "Usage access\nis on." else "One optional\npermission.",
            style = ManagerTheme.type.displayL,
            color = colors.ink,
        )
        Spacer(Modifier.height(20.dp))
        Txt(
            if (granted) {
                "Manager can read screen time and measure real storage. Everything below is live."
            } else {
                "Android keeps screen time and precise storage figures behind Usage access. " +
                    "It is granted in Settings, and it is the only thing Manager asks for."
            },
            style = ManagerTheme.type.body,
            color = colors.inkSecondary,
            modifier = Modifier.fillMaxWidth(0.92f),
        )
        Spacer(Modifier.height(26.dp))
        PermissionLedger(granted)
    }
}

@Composable
private fun ReadyBeat(granted: Boolean) {
    val colors = ManagerTheme.colors
    Column {
        Txt(
            "Ready when\nyou are.",
            style = ManagerTheme.type.displayXl,
            color = colors.ink,
        )
        Spacer(Modifier.height(20.dp))
        Txt(
            if (granted) {
                "Scanning starts now. Long-press any app to select several, then extract or remove them together."
            } else {
                "Manager works without usage access — sizes fall back to APK size on disk, and the " +
                    "usage screen will show you how to turn it on later."
            },
            style = ManagerTheme.type.body,
            color = colors.inkSecondary,
            modifier = Modifier.fillMaxWidth(0.92f),
        )
    }
}

/**
 * What the permission actually buys, item by item. Nothing here overstates: the two lines Manager
 * can do without usage access are marked as available either way.
 */
@Composable
private fun PermissionLedger(granted: Boolean) {
    val colors = ManagerTheme.colors
    val rows = listOf(
        Triple(ManagerIcons.Clock, "Time spent per app", true),
        Triple(ManagerIcons.Storage, "App, data and cache sizes", true),
        Triple(ManagerIcons.Package, "Inventory, versions, dates", false),
        Triple(ManagerIcons.Extract, "APK extraction and uninstall", false),
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        rows.forEach { (icon, label, needsPermission) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 7.dp),
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(SquircleShape(10.dp, 0.75f))
                        .background(
                            if (!needsPermission || granted) colors.signalSoft else colors.canvasSunken,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ManagerIcon(
                        icon,
                        null,
                        tint = if (!needsPermission || granted) colors.signal else colors.inkTertiary,
                        size = 15.dp,
                    )
                }
                Spacer(Modifier.width(13.dp))
                Txt(
                    label,
                    style = ManagerTheme.type.bodyS,
                    color = if (!needsPermission || granted) colors.ink else colors.inkSecondary,
                )
                Spacer(Modifier.weight(1f))
                Txt(
                    when {
                        !needsPermission -> "Always"
                        granted -> "On"
                        else -> "Needs access"
                    },
                    style = ManagerTheme.type.metaS,
                    color = if (!needsPermission || granted) colors.signal else colors.inkTertiary,
                )
            }
        }
    }
}

// ---- Actions ---------------------------------------------------------------------------------

@Composable
private fun OnboardingActions(
    step: Int,
    usageGranted: Boolean,
    onNext: () -> Unit,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (step) {
            0 -> ManagerButton("Get started", onNext, fillWidth = true, icon = null)
            1 -> if (usageGranted) {
                ManagerButton("Continue", onNext, fillWidth = true)
            } else {
                ManagerButton("Open Usage access", onGrant, fillWidth = true, icon = ManagerIcons.ArrowUpRight)
            }

            else -> ManagerButton("Open Manager", onFinish, fillWidth = true)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.height(38.dp), contentAlignment = Alignment.Center) {
            when {
                step == 1 && !usageGranted ->
                    ManagerTextAction("Continue without it", onSkip, color = ManagerTheme.colors.inkTertiary)

                step == 1 && usageGranted -> Unit
                else -> Unit
            }
        }
    }
}

@Composable
private fun StepIndicator(step: Int, total: Int) {
    val colors = ManagerTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { index ->
            val active = index == step
            val width by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
                label = "stepWidth",
            )
            val color by androidx.compose.animation.animateColorAsState(
                targetValue = if (active) colors.signal else colors.hairlineStrong,
                animationSpec = tween(240),
                label = "stepColor",
            )
            Box(
                Modifier
                    .width(width)
                    .height(6.dp)
                    .clip(ManagerTheme.shapes.capsule)
                    .background(color),
            )
        }
    }
}

// ---- The drifting field ----------------------------------------------------------------------

private data class Mote(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val xFraction: Float,
    val yFraction: Float,
    val scale: Float,
    val accent: Boolean,
)

/**
 * A composed constellation, not a random scatter.
 *
 * Positions are hand-placed as fractions of the viewport so the arrangement holds on any screen
 * width, and each chip drifts on its own slow Lissajous path with an amplitude of a few dp. The
 * motion exists to make the surface feel alive while the user reads; it must never compete with
 * the headline, which is why nothing here moves more than about six dp.
 */
@Composable
private fun FloatingField(step: Int) {
    val colors = ManagerTheme.colors
    val transition = rememberInfiniteTransition(label = "drift")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Restart),
        label = "driftPhase",
    )

    val motes = remember {
        listOf(
            Mote("User apps", "142", ManagerIcons.Person, 0.06f, 0.10f, 1f, false),
            Mote("System", "218", ManagerIcons.System, 0.62f, 0.05f, 0.92f, false),
            Mote("Storage", "48.2 GB", ManagerIcons.Storage, 0.70f, 0.20f, 1f, true),
            Mote("Split APK", "3 parts", ManagerIcons.Split, 0.04f, 0.26f, 0.9f, false),
            Mote("Screen time", "4h 12m", ManagerIcons.Clock, 0.46f, 0.33f, 0.95f, false),
        )
    }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        motes.forEachIndexed { index, mote ->
            val drift = driftOffset(phase, index * 3 + 1, 6f)
            val entrance by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 240f),
                label = "moteEntrance",
            )
            // Beats two and three quieten the field so the copy owns the screen.
            val recede by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (step == 0) 1f else 0.32f,
                animationSpec = tween(520, easing = ManagerTheme.motion.emphasized),
                label = "moteRecede",
            )
            MoteChip(
                mote = mote,
                modifier = Modifier
                    .offset(x = width * mote.xFraction, y = height * mote.yFraction)
                    .graphicsLayer {
                        translationX = drift.x + (1f - entrance) * 26f * (if (index % 2 == 0) -1f else 1f)
                        translationY = drift.y + (1f - entrance) * 34f
                        alpha = entrance * recede
                        val s = mote.scale * (0.86f + 0.14f * entrance)
                        scaleX = s
                        scaleY = s
                    },
                accentColor = if (mote.accent) colors.ember else colors.signal,
            )
        }
    }
}

@Composable
private fun MoteChip(mote: Mote, modifier: Modifier, accentColor: Color) {
    val colors = ManagerTheme.colors
    val shape = SquircleShape(18.dp, 0.72f)
    Row(
        modifier = modifier
            .shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.ink.copy(alpha = 0.22f),
                spotColor = colors.ink.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(colors.surface)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(SquircleShape(9.dp, 0.8f))
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            ManagerIcon(mote.icon, null, tint = accentColor, size = 13.dp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Txt(mote.label.uppercase(), style = ManagerTheme.type.eyebrow, color = colors.inkTertiary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Txt(mote.value, style = ManagerTheme.type.numericS, color = colors.ink, maxLines = 1)
        }
    }
}
