/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Test

/** Sidebar fixes 01: menus and sheets float over the screen; the Sidebar stays. */
class SidebarScreenTest {
    @Test
    fun `WHEN the main menu (a dialog) opens over the browser THEN the Sidebar's screen stays Browser`() {
        assertEquals(
            SidebarScreen.Browser,
            SidebarScreen.after(SidebarScreen.Browser, isHome = false, isBrowser = false, isDialog = true),
        )
    }

    @Test
    fun `WHEN a full-screen page opens THEN the Sidebar hides, and comes back on the browser`() {
        assertEquals(SidebarScreen.Other, SidebarScreen.after(SidebarScreen.Browser, isHome = false, isBrowser = false, isDialog = false))
        assertEquals(SidebarScreen.Browser, SidebarScreen.after(SidebarScreen.Other, isHome = false, isBrowser = true, isDialog = false))
        assertEquals(SidebarScreen.Home, SidebarScreen.after(SidebarScreen.Other, isHome = true, isBrowser = false, isDialog = false))
    }
}
