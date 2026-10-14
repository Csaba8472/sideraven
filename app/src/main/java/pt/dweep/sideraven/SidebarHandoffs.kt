/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import android.graphics.PointF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.fenix.browser.SwipeGestureListener

/**
 * Fenix's toolbar gestures beside a Sidebar. "Swipe toolbar vertically to see open tabs" opens the Tab
 * Tray from the browser screen's toolbar only (Fenix's home toolbar has no such gesture). Where a
 * Sidebar shows, the tabs are already in it, so the gesture is off on both screens; without a Sidebar
 * Fenix keeps it. The browser screen wraps the gesture from one `SIDERAVEN-HOOK`.
 */
object SidebarGestures {
    /** [listener], acting only while the window has no Sidebar. */
    fun onlyWithoutSidebar(
        context: Context,
        listener: SwipeGestureListener,
    ): SwipeGestureListener = onlyWithoutSidebar({ SidebarGate.showsSidebar(context) }, listener)

    internal fun onlyWithoutSidebar(
        showsSidebar: () -> Boolean,
        listener: SwipeGestureListener,
    ): SwipeGestureListener =
        object : SwipeGestureListener {
            override fun onSwipeStarted(
                start: PointF,
                next: PointF,
            ): Boolean = !showsSidebar() && listener.onSwipeStarted(start, next)

            override fun onSwipeUpdate(
                distanceX: Float,
                distanceY: Float,
            ) = listener.onSwipeUpdate(distanceX, distanceY)

            override fun onSwipeFinished(
                velocityX: Float,
                velocityY: Float,
            ) = listener.onSwipeFinished(velocityX, velocityY)
        }
}

/**
 * A new tab that Fenix opens by focusing its address bar (switching to a Browsing Mode with no tabs, or
 * New tab opens: Home page). In the Sidebar layout Fenix's toolbar is hidden, so focusing it would only
 * raise the keyboard: the Sidebar's Search Pop-up opens instead. A Browsing Mode switch restarts the
 * screen, so the request waits here for the Sidebar of that mode.
 */
object SearchHandoff {
    private val waiting = MutableStateFlow<Boolean?>(null)

    /** The Browsing Mode (true = private) whose Sidebar should open the Search Pop-up, or null. */
    val pending: StateFlow<Boolean?> = waiting.asStateFlow()

    /**
     * Whether Fenix may focus its own address bar for a new tab it [wants] to focus, in a window whose
     * toolbar is hidden or not. When it may not, the Search Pop-up is asked for instead, in [private] mode.
     */
    fun focusFenixAddressBar(
        wants: Boolean,
        toolbarHidden: Boolean,
        private: Boolean,
    ): Boolean {
        if (wants && toolbarHidden) {
            waiting.value = private
            return false
        }
        return wants
    }

    /** Takes the request if it's for a Sidebar in [private] mode: true once, then it's gone. */
    fun take(private: Boolean): Boolean = waiting.compareAndSet(private, null)
}
