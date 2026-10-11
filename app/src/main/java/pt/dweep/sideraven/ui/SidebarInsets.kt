/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView

/**
 * How much of a system bar inset is still uncovered: the inset minus the gap already between the
 * Sidebar's edge and the window's edge. Fenix pads its content by the system bars on some screens
 * (web pages) but not others (home), and the Sidebar sits inside that content.
 */
fun remainingInset(
    insetPx: Float,
    gapToWindowEdgePx: Float,
): Float = (insetPx - gapToWindowEdgePx).coerceAtLeast(0f)

/**
 * How far (px, screen x) an ancestor is sliding the Sidebar right now. Insets are worked out from
 * where it rests, so a Sidebar part way out keeps its shape instead of padding its hidden part.
 */
internal val LocalSidebarSlide = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * Like `windowInsetsPadding(insets)`, but only for the part of each inset the Sidebar's position
 * doesn't already clear, so it never gets the status bar twice.
 */
@Composable
internal fun Modifier.remainingInsetsPadding(insets: WindowInsets): Modifier {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val root = LocalView.current.rootView
    val slide = LocalSidebarSlide.current
    var top by remember { mutableFloatStateOf(0f) }
    var bottomGap by remember { mutableFloatStateOf(0f) }
    var left by remember { mutableFloatStateOf(0f) }
    var rightGap by remember { mutableFloatStateOf(0f) }
    val padTop = remainingInset(insets.getTop(density).toFloat(), top)
    val padBottom = remainingInset(insets.getBottom(density).toFloat(), bottomGap)
    val padLeft = remainingInset(insets.getLeft(density, direction).toFloat(), left)
    val padRight = remainingInset(insets.getRight(density, direction).toFloat(), rightGap)
    return this
        .onGloballyPositioned {
            val position = it.positionInWindow()
            val restX = position.x - slide()
            top = position.y
            left = restX
            bottomGap = root.height - (position.y + it.size.height)
            rightGap = root.width - (restX + it.size.width)
        }
        .absolutePadding(
            top = with(density) { padTop.toDp() },
            bottom = with(density) { padBottom.toDp() },
            left = with(density) { padLeft.toDp() },
            right = with(density) { padRight.toDp() },
        )
}
