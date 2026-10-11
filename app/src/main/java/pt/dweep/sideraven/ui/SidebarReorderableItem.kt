/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import org.mozilla.fenix.tabstray.browser.compose.ListReorderState

/**
 * A Tab List row that can be dragged to reorder: Sideraven's copy of Fenix's
 * `ReorderableDragItemContainer` (same module, so Fenix's file stays untouched), with the list's
 * insert, remove and move motion following the Animations setting. Below Full motion the dropped
 * row lands at once instead of settling.
 */
@Composable
internal fun LazyItemScope.SidebarReorderableItem(
    state: ListReorderState,
    key: Any,
    position: Int,
    content: @Composable () -> Unit,
) {
    val moves = LocalSidebarMotion.current.moves
    val modifier =
        when (key) {
            state.draggingItemKey ->
                Modifier.zIndex(1f).graphicsLayer {
                    when (state.orientation) {
                        Orientation.Vertical -> translationY = state.computeItemOffset(position)
                        Orientation.Horizontal -> translationX = state.computeItemOffset(position)
                    }
                }
            state.previousKeyOfDraggedItem ->
                Modifier.zIndex(1f).graphicsLayer {
                    if (moves) {
                        when (state.orientation) {
                            Orientation.Vertical -> translationY = state.previousItemOffset.value
                            Orientation.Horizontal -> translationX = state.previousItemOffset.value
                        }
                    }
                }
            else -> Modifier.zIndex(0f).then(sidebarItem())
        }
    Box(modifier = modifier, propagateMinConstraints = true) {
        content()
    }
}
