/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import org.mozilla.fenix.tabstray.browser.compose.ListReorderState

/** Where a Tab Drag would drop if released now. */
enum class DropTarget {
    None,

    /** Over the Essentials Section, and pinning is allowed: accent outline. */
    PinAccepted,

    /** Over the Essentials Section, but pinning is blocked (cap / private): disabled outline. */
    PinRejected,
}

/**
 * Long-press then drag in the Tab List, like Fenix's `detectListPressAndDrag`, but also reporting
 * the pointer's vertical position (in list coordinates) so drops can land outside the list:
 * above it (the Essentials Section) or above the Inactive Tabs Section header.
 *
 * @param onDragMove Called with the pointer's position (list coordinates) while dragging.
 * @param onDragFinished Called on release with the dragged item's key and the final position
 * (key is null when the drag was cancelled).
 */
fun Modifier.sidebarListPressAndDrag(
    listState: LazyListState,
    reorderState: ListReorderState,
    onDragMove: (position: Offset) -> Unit,
    onDragFinished: (key: String?, position: Offset) -> Unit,
): Modifier =
    composed {
        // The gesture outlives recompositions; it must call the latest callbacks, which see the
        // latest tabs.
        val currentOnDragMove by rememberUpdatedState(onDragMove)
        val currentOnDragFinished by rememberUpdatedState(onDragFinished)
        this then dragGesture(listState, reorderState, { currentOnDragMove(it) }, { key, position -> currentOnDragFinished(key, position) })
    }

private fun dragGesture(
    listState: LazyListState,
    reorderState: ListReorderState,
    onDragMove: (position: Offset) -> Unit,
    onDragFinished: (key: String?, position: Offset) -> Unit,
): Modifier =
        Modifier.pointerInput(listState) {
            var position = Offset.Zero
            var key: String? = null
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    position = offset
                    reorderState.onTouchSlopPassed(offset.y, true)
                    key = reorderState.draggingItemKey as? String
                    onDragMove(position)
                },
                onDrag = { change, amount ->
                    change.consume()
                    position += amount
                    reorderState.onDrag(amount.y)
                    onDragMove(position)
                },
                onDragEnd = {
                    val dropped = key
                    reorderState.onDragInterrupted()
                    key = null
                    onDragFinished(dropped, position)
                },
                onDragCancel = {
                    reorderState.onDragInterrupted()
                    key = null
                    onDragFinished(null, position)
                },
            )
        }
