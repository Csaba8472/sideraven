/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarTierTest {
    @Test
    fun `GIVEN a window narrower than 600dp THEN there is no Sidebar tier`() {
        assertEquals(SidebarTier.None, SidebarTier.resolve(widthDp = 411, smallestWidthDp = 411, phoneSidebar = false))
        assertEquals(SidebarTier.None, SidebarTier.resolve(widthDp = 599, smallestWidthDp = 599, phoneSidebar = false))
    }

    @Test
    fun `GIVEN a tablet-sized window THEN it is the Tablet Tier in both orientations`() {
        assertEquals(SidebarTier.Tablet, SidebarTier.resolve(widthDp = 1280, smallestWidthDp = 800, phoneSidebar = false))
        assertEquals(SidebarTier.Tablet, SidebarTier.resolve(widthDp = 800, smallestWidthDp = 800, phoneSidebar = false))
        assertEquals(SidebarTier.Tablet, SidebarTier.resolve(widthDp = 600, smallestWidthDp = 600, phoneSidebar = false))
    }

    @Test
    fun `GIVEN a large phone in landscape THEN it is the Large Phone Tier`() {
        assertEquals(SidebarTier.LargePhone, SidebarTier.resolve(widthDp = 915, smallestWidthDp = 412, phoneSidebar = false))
    }

    @Test
    fun `GIVEN a tablet in narrow split-screen THEN there is no Sidebar`() {
        assertEquals(SidebarTier.None, SidebarTier.resolve(widthDp = 480, smallestWidthDp = 480, phoneSidebar = false))
    }

    @Test
    fun `WHEN gating THEN both Qualifying Device tiers show the Sidebar`() {
        assertTrue(SidebarGate.showsSidebar(SidebarTier.Tablet))
        assertTrue(SidebarGate.showsSidebar(SidebarTier.LargePhone))
        assertFalse(SidebarGate.showsSidebar(SidebarTier.None))
    }
}
