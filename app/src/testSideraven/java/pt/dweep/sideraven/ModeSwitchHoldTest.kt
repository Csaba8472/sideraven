/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Switching normal and private browsing: the Sidebar changes with the page, not before it. */
class ModeSwitchHoldTest {
    @After
    fun forgetScreen() {
        ModeSwitchHold.takeScreenAtSwitch()
    }

    @Test
    fun `GIVEN the screen's own mode THEN the Sidebar shows what is live`() {
        val hold = ModeSwitchHold<String>(drawnPrivate = false)
        assertEquals("normal tabs", hold.shown("normal tabs", isPrivate = false))
        assertEquals("more normal tabs", hold.shown("more normal tabs", isPrivate = false))
    }

    @Test
    fun `WHEN the mode switches THEN the Sidebar keeps what it last drew until the screen restarts`() {
        val hold = ModeSwitchHold<String>(drawnPrivate = false)
        hold.shown("normal tabs", isPrivate = false)
        assertEquals("normal tabs", hold.shown("private tabs", isPrivate = true))
    }

    @Test
    fun `GIVEN nothing drawn yet WHEN the modes disagree THEN the Sidebar shows what is live`() {
        // Fenix's store can catch up with the screen's mode a moment after the first frame.
        val hold = ModeSwitchHold<String>(drawnPrivate = true)
        assertEquals("normal tabs", hold.shown("normal tabs", isPrivate = false))
    }

    @Test
    fun `WHEN Fenix never restarts the screen THEN the Sidebar lets go and shows the new mode`() {
        val hold = ModeSwitchHold<String>(drawnPrivate = false)
        hold.shown("normal tabs", isPrivate = false)
        hold.giveUp()
        assertEquals("private tabs", hold.shown("private tabs", isPrivate = true))
    }

    @Test
    fun `THEN the restarted Sidebar starts on the switching screen, once, and fresh launches start nowhere`() {
        assertNull(ModeSwitchHold.takeScreenAtSwitch())
        ModeSwitchHold.rememberScreenAtSwitch(SidebarScreen.Browser)
        assertEquals(SidebarScreen.Browser, ModeSwitchHold.takeScreenAtSwitch())
        assertNull(ModeSwitchHold.takeScreenAtSwitch())
    }
}
