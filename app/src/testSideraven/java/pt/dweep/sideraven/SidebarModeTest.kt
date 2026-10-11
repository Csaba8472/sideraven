/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Ticket 05: Sidebar Modes, the Toggle and tier fallback. */
class SidebarModeTest {
    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://one.example", id = "1"), select = true))
        }
    private val modeStore = InMemorySidebarModeStore()

    private fun state(tier: SidebarTier) =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = tier,
            screen = SidebarScreen.Browser,
            chosenMode = modeStore.chosenMode.value,
        )

    private val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, setChosenMode = modeStore.chosenMode::set)

    @Test
    fun `GIVEN no choice yet THEN tablets start Persistent and large phones start Collapsed`() {
        assertEquals(SidebarMode.Persistent, state(SidebarTier.Tablet).mode)
        assertEquals(SidebarMode.Collapsed, state(SidebarTier.LargePhone).mode)
    }

    @Test
    fun `WHEN the Toggle is used on a tablet THEN it cycles Persistent, Collapsed, Auto-Hide and stores the choice`() {
        assertEquals(SidebarMode.Collapsed, state(SidebarTier.Tablet).nextMode)

        handler.handle(SidebarIntent.ToggleMode, state(SidebarTier.Tablet))
        assertEquals(SidebarMode.Collapsed, modeStore.chosenMode.value)
        assertEquals(SidebarMode.Collapsed, state(SidebarTier.Tablet).mode)
        assertEquals(SidebarMode.AutoHide, state(SidebarTier.Tablet).nextMode)

        handler.handle(SidebarIntent.ToggleMode, state(SidebarTier.Tablet))
        assertEquals(SidebarMode.AutoHide, state(SidebarTier.Tablet).mode)

        handler.handle(SidebarIntent.ToggleMode, state(SidebarTier.Tablet))
        assertEquals(SidebarMode.Persistent, state(SidebarTier.Tablet).mode)
    }

    @Test
    fun `GIVEN Persistent is chosen WHEN the window drops to the Large Phone Tier THEN Collapsed shows and the choice is kept`() {
        modeStore.chosenMode.set(SidebarMode.Persistent)

        assertEquals(SidebarMode.Collapsed, state(SidebarTier.LargePhone).mode)
        assertEquals(SidebarMode.Persistent, modeStore.chosenMode.value)
        assertEquals(SidebarMode.Persistent, state(SidebarTier.Tablet).mode)
    }

    @Test
    fun `GIVEN the Large Phone Tier THEN the Toggle cycles Collapsed and Auto-Hide, never Persistent`() {
        assertEquals(SidebarMode.AutoHide, state(SidebarTier.LargePhone).nextMode)
        assertEquals(listOf(SidebarMode.Collapsed, SidebarMode.AutoHide), SidebarMode.available(SidebarTier.LargePhone))
    }

    @Test
    fun `GIVEN a window under 600dp THEN no mode applies and the Sidebar is hidden`() {
        modeStore.chosenMode.set(SidebarMode.Persistent)

        assertNull(SidebarMode.effective(modeStore.chosenMode.value, SidebarTier.None))
        assertFalse(state(SidebarTier.None).isVisible)
    }

    @Test
    fun `WHEN the window grows and shrinks THEN the tier follows the window, not the device`() {
        // A tablet going into narrow split-screen, then back.
        assertEquals(SidebarTier.Tablet, SidebarTier.resolve(widthDp = 1280, smallestWidthDp = 800, phoneSidebar = false))
        assertEquals(SidebarTier.None, SidebarTier.resolve(widthDp = 500, smallestWidthDp = 500, phoneSidebar = false))
        assertEquals(SidebarTier.LargePhone, SidebarTier.resolve(widthDp = 700, smallestWidthDp = 500, phoneSidebar = false))
    }
}
