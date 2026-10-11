/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import kotlinx.coroutines.CoroutineStart
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.theme.SideravenMotion
import kotlin.math.roundToInt
import mozilla.components.ui.icons.R as iconsR

/** How far (of the row's width) a swipe must go before letting go closes the tab. */
private const val CLOSE_THRESHOLD_FRACTION = 0.4f

/** Resistance when dragging the wrong way. */
private const val RUBBER_BAND = 0.55f

/** A fling toward the closing edge faster than this (px/s) closes even short of the threshold. */
private const val CLOSE_FLING_VELOCITY = 1000f

private fun rubberBand(
    overshoot: Float,
    dimension: Float,
): Float = (1f - 1f / (overshoot * RUBBER_BAND / dimension + 1f)) * dimension

/**
 * Tab Close Gesture (ADR 0003, amended in spec 0008): swipe toward the nearest screen edge, like
 * Apple Mail. The row follows the finger; the wrong way rubber-bands; a × grows in behind it and a
 * haptic tick marks the point where letting go closes. A committed swipe throws the row off at the
 * finger's speed, folds its height shut so the rows below rise with it, and only then [onClose].
 * Reduced motion fades the row out instead; Off closes at once. [content] is told while the row is
 * being swiped, so it can show it's the one being touched.
 */
@Composable
internal fun SwipeToClose(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (swiping: Boolean) -> Unit,
) {
    val motion = LocalSidebarMotion.current
    val haptics = LocalHapticFeedback.current
    // 1 docked left (close toward the left edge), -1 docked right (toward the right edge).
    val dir = LocalSidebarSide.current.dir
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    // Offsets are toward-close negative; [dir] turns them into screen directions.
    val offset = remember { Animatable(0f) }
    val height = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }
    var raw by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var armed by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var swiping by remember { mutableStateOf(false) }
    val threshold = widthPx * CLOSE_THRESHOLD_FRACTION

    suspend fun commit(velocity: Float) {
        closing = true
        when {
            motion.moves -> {
                offset.animateTo(-widthPx, spring(Spring.DampingRatioNoBouncy, SideravenMotion.THROW_STIFFNESS), velocity)
                height.animateTo(0f, tween(SideravenMotion.ITEM_PLACEMENT_MS, easing = SideravenMotion.EaseInOutStrong))
            }
            motion.level == AnimationLevel.Reduced -> fade.animateTo(0f, motion.fade(SideravenMotion.DISMISS_MS))
            else -> Unit
        }
        currentOnClose()
    }

    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, (placeable.height * height.value).roundToInt()) { placeable.place(0, 0) }
            }
            .graphicsLayer { alpha = fade.value }
            .draggable(
                orientation = Orientation.Horizontal,
                enabled = !closing,
                state =
                    rememberDraggableState { delta ->
                        raw = (raw + delta * dir).coerceAtLeast(-widthPx)
                        val shown = if (raw >= 0f) rubberBand(raw, widthPx) else raw
                        // Applied at once, not queued: a queued move left over when the finger lifts
                        // would cancel the spring back and leave the row where it was.
                        scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(shown) }
                        val nowArmed = raw <= -threshold
                        if (nowArmed && !armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        armed = nowArmed
                    },
                onDragStarted = { swiping = true },
                onDragStopped = { velocity ->
                    swiping = false
                    val towardClose = velocity * dir
                    val flung = towardClose < -CLOSE_FLING_VELOCITY && raw < 0f
                    val commits = armed || flung
                    raw = 0f
                    armed = false
                    // Its own scope: closing disables the drag, which would cancel this one.
                    if (commits) scope.launch { commit(towardClose) } else offset.animateTo(0f, motion.springSettle(), towardClose)
                },
            )
            // A closing row takes no more taps.
            .pointerInput(closing) {
                if (!closing) return@pointerInput
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
    ) {
        if (offset.value < 0f) CloseAffordance(progress = (-offset.value / threshold).coerceIn(0f, 1f), armed = armed)
        Box(Modifier.graphicsLayer { translationX = offset.value * dir }) { content(swiping || closing) }
    }
}

/** The × revealed behind a swiped row: it grows in with the swipe and brightens once armed. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.CloseAffordance(
    progress: Float,
    armed: Boolean,
) {
    val motion = LocalSidebarMotion.current
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (armed) colors.surfaceContainerHighest else colors.surfaceContainer,
        motion.fade(SideravenMotion.SELECTION_FADE_MS),
        label = "closeAffordance",
    )
    val tint by animateColorAsState(
        if (armed) colors.onSurface else colors.onSurfaceVariant,
        motion.fade(SideravenMotion.SELECTION_FADE_MS),
        label = "closeAffordanceIcon",
    )
    Box(
        modifier =
            Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(background),
        contentAlignment = if (LocalSidebarSide.current.dir > 0) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Icon(
            painter = painterResource(iconsR.drawable.mozac_ic_cross_24),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(horizontal = 12.dp)
                    .size(20.dp)
                    .graphicsLayer {
                        alpha = progress
                        scaleX = 0.8f + 0.2f * progress
                        scaleY = scaleX
                    },
            tint = tint,
        )
    }
}
