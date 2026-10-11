/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 08: when the Swipe Zone reclaims part of the left edge. */
class SwipeZoneTest {
    private fun state(
        mode: SidebarMode,
        enabled: Boolean = true,
        revealed: Boolean = false,
        screen: SidebarScreen = SidebarScreen.Browser,
    ) = BrowserState().toSidebarUiState(
        isPrivate = false,
        tier = SidebarTier.Tablet,
        screen = screen,
        chosenMode = mode,
        isRevealed = revealed,
        swipeZoneEnabled = enabled,
    )

    @Test
    fun `GIVEN Auto-Hide and a hidden Sidebar THEN the Swipe Zone is active`() {
        assertTrue(state(SidebarMode.AutoHide).showsSwipeZone)
    }

    @Test
    fun `GIVEN a docked mode THEN the whole left edge stays system back`() {
        assertFalse(state(SidebarMode.Persistent).showsSwipeZone)
        assertFalse(state(SidebarMode.Collapsed).showsSwipeZone)
    }

    @Test
    fun `GIVEN the Sidebar is already revealed THEN the Swipe Zone is released`() {
        assertFalse(state(SidebarMode.AutoHide, revealed = true).showsSwipeZone)
    }

    @Test
    fun `GIVEN the setting is off THEN there is no Swipe Zone`() {
        assertFalse(state(SidebarMode.AutoHide, enabled = false).showsSwipeZone)
    }

    @Test
    fun `GIVEN a screen without the Sidebar THEN there is no Swipe Zone`() {
        assertFalse(state(SidebarMode.AutoHide, screen = SidebarScreen.Other).showsSwipeZone)
    }

    @Test
    fun `THEN the setting defaults to on`() {
        assertTrue(InMemorySidebarModeStore().swipeZoneEnabled.value)
    }
}
