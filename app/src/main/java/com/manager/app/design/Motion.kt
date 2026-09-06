package com.manager.app.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Motion is a token, not a per-call-site decision.
 *
 * The product has exactly two motion personalities: springs for anything the finger caused, and
 * eased tweens for anything the system caused. Nothing in the app animates on a default spec.
 */
@Immutable
data class ManagerMotion(
    /** Surfaces arriving or leaving — settles with a hint of overshoot, never a bounce. */
    val surface: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 340f),
    /** Direct manipulation feedback: press, selection, chip toggles. */
    val snap: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 1100f),
    /** Layout reflow — slower, so the eye can follow items relocating. */
    val settle: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 260f),
    /** The morph from a list row into the detail surface. */
    val morph: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 420f),
    val quick: Int = 140,
    val standard: Int = 260,
    val expressive: Int = 440,
    /** Content entering: fast out of the gate, long tail. */
    val emphasized: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f),
    val standardEase: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val exit: Easing = CubicBezierEasing(0.4f, 0f, 0.9f, 0.45f),
    /** Delay between staggered children. Small enough to read as one gesture. */
    val stagger: Int = 34,
)

val LocalManagerMotion = staticCompositionLocalOf { ManagerMotion() }

fun ManagerMotion.fadeIn(delayMillis: Int = 0): FiniteAnimationSpec<Float> =
    tween(durationMillis = expressive, delayMillis = delayMillis, easing = emphasized)

fun ManagerMotion.offsetSpring(): SpringSpec<Offset> = spring(dampingRatio = 0.88f, stiffness = 420f, visibilityThreshold = Offset(0.5f, 0.5f))

fun ManagerMotion.sizeSpring(): SpringSpec<Size> = spring(dampingRatio = 0.88f, stiffness = 420f, visibilityThreshold = Size(0.5f, 0.5f))

fun ManagerMotion.intOffsetSpring(): SpringSpec<IntOffset> =
    spring(dampingRatio = 0.85f, stiffness = 380f, visibilityThreshold = IntOffset(1, 1))

fun ManagerMotion.intSizeSpring(): SpringSpec<IntSize> =
    spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntSize(1, 1))
