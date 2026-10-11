/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Polish round 2 ticket 08: phones get the Auto-Hide Sidebar, on by default, with a way back to Fenix's tab tray. */
class PhoneSidebarTest {
    private val store = InMemorySidebarModeStore()

    @Test
    fun `GIVEN a phone-width window THEN it's the Phone tier while the phone Sidebar is on, and plain Fenix when off`() {
        assertEquals(SidebarTier.Phone, SidebarTier.resolve(widthDp = 411, smallestWidthDp = 411, phoneSidebar = true))
        assertEquals(SidebarTier.None, SidebarTier.resolve(widthDp = 411, smallestWidthDp = 411, phoneSidebar = false))
        // Wider windows are unaffected.
        assertEquals(SidebarTier.Tablet, SidebarTier.resolve(widthDp = 1280, smallestWidthDp = 800, phoneSidebar = false))
        assertEquals(SidebarTier.LargePhone, SidebarTier.resolve(widthDp = 900, smallestWidthDp = 411, phoneSidebar = true))
    }

    @Test
    fun `THEN the phone Sidebar is on by default, and its edge swipe off`() {
        assertTrue(store.phoneSidebar.value)
        assertFalse(store.phoneSwipeZone.value)
        assertTrue("tablets keep theirs", store.swipeZoneEnabled.value)
    }

    @Test
    fun `GIVEN the Phone tier THEN Auto-Hide is the only mode, whatever was chosen, and there's nothing to toggle`() {
        assertEquals(listOf(SidebarMode.AutoHide), SidebarMode.available(SidebarTier.Phone))
        assertEquals(SidebarMode.AutoHide, SidebarMode.effective(null, SidebarTier.Phone))
        assertEquals(SidebarMode.AutoHide, SidebarMode.effective(SidebarMode.Persistent, SidebarTier.Phone))
        assertEquals(SidebarMode.AutoHide, SidebarMode.effective(SidebarMode.Collapsed, SidebarTier.Phone))
        assertNull(SidebarMode.next(SidebarMode.AutoHide, SidebarTier.Phone))
        // A large phone still falls back to Collapsed.
        assertEquals(SidebarMode.Collapsed, SidebarMode.effective(SidebarMode.Persistent, SidebarTier.LargePhone))
    }

    @Test
    fun `GIVEN the Phone tier THEN the Sidebar shows, hidden behind its handle, and Fenix's tab counter gives way`() {
        val ui =
            BrowserState(tabs = listOf(createTab("https://a.example", id = "a")), selectedTabId = "a")
                .toSidebarUiState(isPrivate = false, tier = SidebarTier.Phone, screen = SidebarScreen.Browser)

        assertTrue(ui.isVisible)
        assertEquals(SidebarMode.AutoHide, ui.mode)
        assertTrue(ui.showsFlap)
        assertNull(ui.nextMode)
        assertTrue(SidebarGate.showsSidebar(SidebarTier.Phone))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Phone, null, flapEnabled = true))
    }
}
