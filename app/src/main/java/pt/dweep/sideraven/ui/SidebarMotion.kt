/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.unit.IntOffset
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.theme.SideravenMotion

/**
 * The Sidebar's motion at the chosen [AnimationLevel] (Sideraven Settings). Full is the motion
 * plan as designed; Reduced keeps short fades and colour changes but drops movement (slides,
 * size changes, placement, morphs); Off makes every change instant.
 */
data class SidebarMotion(
    val level: AnimationLevel,
) {
    /** Whether things may move on screen. */
    val moves: Boolean get() = level == AnimationLevel.Full

    /** For movement: as designed at Full, instant otherwise. */
    fun <T> move(
        durationMs: Int,
        easing: Easing = SideravenMotion.EaseOutStrong,
    ): FiniteAnimationSpec<T> = if (moves) tween(durationMs, easing = easing) else snap()

    /** Gesture settles and list placement: a no-bounce spring at Full, instant otherwise. */
    fun <T> settle(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (moves) spring(Spring.DampingRatioNoBouncy, SideravenMotion.SETTLE_STIFFNESS, visibilityThreshold) else snap()

    /**
     * A gesture let go: carried to where it's going (back where it was, or open) by a spring with a
     * little bounce at Full, instant otherwise. Against a hard stop it simply stops there.
     */
    fun <T> springSettle(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        if (moves) spring(SideravenMotion.SPRING_SETTLE_DAMPING, SideravenMotion.SETTLE_STIFFNESS, visibilityThreshold) else snap()

    /** For fades, colour and rotation feedback: shortened when Reduced, instant when Off. */
    fun <T> fade(
        durationMs: Int,
        easing: Easing = SideravenMotion.EaseOutStrong,
    ): FiniteAnimationSpec<T> =
        when (level) {
            AnimationLevel.Full -> tween(durationMs, easing = easing)
            AnimationLevel.Reduced -> tween(minOf(durationMs, REDUCED_FADE_MS), easing = easing)
            AnimationLevel.Off -> snap()
        }

    companion object {
        /** Longest fade at the Reduced level. */
        const val REDUCED_FADE_MS = 150
    }
}

internal val LocalSidebarMotion = staticCompositionLocalOf { SidebarMotion(AnimationLevel.Full) }

/**
 * Animates height changes only; width follows the constraints at once, so it is safe inside the
 * Sidebar's own width animation (Persistent and Collapsed Mode swap whole regions mid-change).
 */
internal fun Modifier.animateHeight(spec: FiniteAnimationSpec<Float>): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val height = remember { Animatable(-1f) }
        // The last height asked for, so a measure pass starts at most one animation per change.
        val requested = remember { floatArrayOf(-1f) }
        clipToBounds().layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val target = placeable.height.toFloat()
            if (requested[0] != target) {
                val first = requested[0] < 0f
                requested[0] = target
                scope.launch { if (first) height.snapTo(target) else height.animateTo(target, spec) }
            }
            val shown = if (height.value < 0f) placeable.height else height.value.roundToInt()
            layout(placeable.width, shown) { placeable.place(0, 0) }
        }
    }

/** [target] opacity, crossfading when it changes (Ghost, sleeping); nothing animates at launch. */
@Composable
internal fun fadedAlpha(target: Float): Float =
    androidx.compose.animation.core.animateFloatAsState(target, LocalSidebarMotion.current.fade(SideravenMotion.SELECTION_FADE_MS), label = "fadedAlpha").value

/** Springs an in-place drag's offset back home: a no-bounce settle at Full motion, at once otherwise. */
internal suspend fun springHome(
    from: Offset,
    motion: SidebarMotion,
    onFrame: (Offset) -> Unit,
) {
    if (!motion.moves || from == Offset.Zero) {
        onFrame(Offset.Zero)
        return
    }
    Animatable(from, Offset.VectorConverter).animateTo(Offset.Zero, motion.springSettle(Offset.VisibilityThreshold)) { onFrame(value) }
}

/**
 * Springs an item's in-place drag offset home after a drop that left it where it was; a new drag
 * stops a settle still running, so it never fights the finger.
 */
internal class SpringHome(
    private val scope: CoroutineScope,
    private val motion: SidebarMotion,
) {
    private var job: Job? = null

    fun stop() {
        job?.cancel()
        job = null
    }

    fun offset(
        from: Offset,
        onFrame: (Offset) -> Unit,
    ) {
        stop()
        job = scope.launch { springHome(from, motion, onFrame) }
    }

    fun axis(
        from: Float,
        onFrame: (Float) -> Unit,
    ) {
        stop()
        job = scope.launch { springHome(from, motion, onFrame) }
    }
}

@Composable
internal fun rememberSpringHome(): SpringHome {
    val scope = rememberCoroutineScope()
    val motion = LocalSidebarMotion.current
    return remember(scope, motion) { SpringHome(scope, motion) }
}

/** [springHome] along one axis (the Space row). */
internal suspend fun springHome(
    from: Float,
    motion: SidebarMotion,
    onFrame: (Float) -> Unit,
) {
    if (!motion.moves || from == 0f) {
        onFrame(0f)
        return
    }
    Animatable(from).animateTo(0f, motion.springSettle()) { onFrame(value) }
}

/** Fades its content in when [animate] (structure swapped in by a mode change); otherwise shows it at once. */
@Composable
internal fun FadeInOnEnter(
    animate: Boolean,
    content: @Composable () -> Unit,
) {
    val motion = LocalSidebarMotion.current
    val alpha = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (alpha.value < 1f) alpha.animateTo(1f, motion.fade(SideravenMotion.SELECTION_FADE_MS)) }
    androidx.compose.foundation.layout.Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

/**
 * Keys that appeared since the previous composition, for "newly added" entrances; empty on the
 * first composition, so nothing animates at launch or when a section is rebuilt.
 */
@Composable
internal fun rememberNewKeys(keys: List<String>): Set<String> {
    val seen = remember { keys.toMutableSet() }
    val fresh = keys.filterTo(mutableSetOf()) { it !in seen }
    SideEffect {
        seen.clear()
        seen.addAll(keys)
    }
    return fresh
}

/** A lazy list item's insert, remove and move animation at the Sidebar's motion level, from the tokens. */
@Composable
internal fun LazyItemScope.sidebarItem(): Modifier {
    val motion = LocalSidebarMotion.current
    return when (motion.level) {
        AnimationLevel.Off -> Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
        else ->
            Modifier.animateItem(
                fadeInSpec = motion.fade(SideravenMotion.ITEM_ENTER_MS),
                placementSpec = if (motion.moves) motion.settle(IntOffset.VisibilityThreshold) else null,
                fadeOutSpec = motion.fade(SideravenMotion.SELECTION_FADE_MS),
            )
    }
}
