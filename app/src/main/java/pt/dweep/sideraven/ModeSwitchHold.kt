/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Switching normal and private browsing restarts Fenix's screen a moment after the mode changes.
 * Until then the Sidebar keeps what it last drew for the screen's own mode ([drawnPrivate]), so it
 * changes with the page, not before it.
 */
internal class ModeSwitchHold<T>(
    val drawnPrivate: Boolean,
) {
    private var lastDrawn: T? = null
    private var gaveUp by mutableStateOf(false)

    /** What the Sidebar shows: [live], or what it last drew while a switch waits for the restart. */
    fun shown(
        live: T,
        isPrivate: Boolean,
    ): T {
        if (isPrivate == drawnPrivate || gaveUp) {
            lastDrawn = live
            return live
        }
        return lastDrawn ?: live
    }

    /** Fenix didn't restart the screen (it was closing, say): show the new mode after all. */
    fun giveUp() {
        gaveUp = true
    }

    companion object {
        /** The longest a switch waits for Fenix's restart; it takes well under a second. */
        const val MAX_HOLD_MS = 2_000L

        private var screenAtSwitch: SidebarScreen? = null

        /** The screen a switch left; the restarted Sidebar starts there, so it shows in its first frame. */
        fun rememberScreenAtSwitch(screen: SidebarScreen) {
            screenAtSwitch = screen
        }

        /** The screen the last switch left, once; null for a launch that isn't a switch's restart. */
        fun takeScreenAtSwitch(): SidebarScreen? = screenAtSwitch.also { screenAtSwitch = null }
    }
}
