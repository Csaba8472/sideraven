/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Test

/** Review fix: a layout switch made during full screen isn't lost when full screen ends. */
class ToolbarFollowerTest {
    private val calls = mutableListOf<String>()
    private var fullScreen = false
    private val follower = SidebarToolbar.ToolbarFollower({ fullScreen }, { calls += "hide" }, { calls += "show" })

    @Test
    fun `WHEN the layout changes during full screen THEN the next change after it still applies`() {
        // Switch to the Sidebar layout while a video is full screen: Fenix's own collapse keeps the toolbar hidden on exit.
        fullScreen = true
        follower.onChange(true)
        fullScreen = false
        // Back to Top: Fenix's toolbar must come back.
        follower.onChange(false)

        assertEquals(listOf("show"), calls)
    }

    @Test
    fun `THEN only changes reach Fenix's toolbar`() {
        follower.onChange(false)
        follower.onChange(true)
        follower.onChange(true)
        follower.onChange(false)

        assertEquals(listOf("hide", "show"), calls)
    }
}
