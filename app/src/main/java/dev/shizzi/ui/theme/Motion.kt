package dev.shizzi.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable

@Immutable
data class ShizziMotion(
    val fastMillis: Int,
    val standardMillis: Int,
    val slowMillis: Int,
    val fastStiffness: Float,
    val standardStiffness: Float,
    val emphasizedStiffness: Float,
    val damping: Float,
    val easing: Easing,
)

private val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Neobrutalism moves mechanically: quick, critically damped, no overshoot.
val BrutalMotion = ShizziMotion(
    fastMillis = 90,
    standardMillis = 160,
    slowMillis = 240,
    fastStiffness = Spring.StiffnessHigh,
    standardStiffness = Spring.StiffnessMedium,
    emphasizedStiffness = Spring.StiffnessMediumLow,
    damping = Spring.DampingRatioNoBouncy,
    easing = StandardEasing,
)

// Material Expressive settles with a visible bounce.
val ExpressiveMotion = ShizziMotion(
    fastMillis = 140,
    standardMillis = 260,
    slowMillis = 400,
    fastStiffness = Spring.StiffnessMedium,
    standardStiffness = Spring.StiffnessMediumLow,
    emphasizedStiffness = Spring.StiffnessLow * 2f,
    damping = Spring.DampingRatioMediumBouncy,
    easing = StandardEasing,
)

/** Snappy response for press and hover feedback. */
@Composable
@ReadOnlyComposable
fun <T> fastSpring(): FiniteAnimationSpec<T> = ShizziTheme.motion.let {
    spring(dampingRatio = it.damping, stiffness = it.fastStiffness)
}

/** Default for state changes: color, size, position. */
@Composable
@ReadOnlyComposable
fun <T> standardSpring(): FiniteAnimationSpec<T> = ShizziTheme.motion.let {
    spring(dampingRatio = it.damping, stiffness = it.standardStiffness)
}

/** Reserved for arrival moments that deserve to be noticed. */
@Composable
@ReadOnlyComposable
fun <T> emphasizedSpring(): FiniteAnimationSpec<T> = ShizziTheme.motion.let {
    spring(dampingRatio = it.damping, stiffness = it.emphasizedStiffness)
}

/** Duration-based counterpart for fades, where a spring reads as imprecise. */
@Composable
@ReadOnlyComposable
fun <T> standardTween(): FiniteAnimationSpec<T> = ShizziTheme.motion.let {
    tween(durationMillis = it.standardMillis, easing = it.easing)
}

@Composable
@ReadOnlyComposable
fun <T> fastTween(): FiniteAnimationSpec<T> = ShizziTheme.motion.let {
    tween(durationMillis = it.fastMillis, easing = it.easing)
}
