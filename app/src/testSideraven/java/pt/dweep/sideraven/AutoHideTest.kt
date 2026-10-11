/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 06: Auto-Hide Mode reveal and dismissal rules, against a real browser store. */
class AutoHideTest {
    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://one.example", id = "1"), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://two.example", id = "2")))
        }
    private val modeStore = InMemorySidebarModeStore(SidebarMode.AutoHide)
    private var revealed = false

    private fun state(tier: SidebarTier = SidebarTier.Tablet) =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = tier,
            screen = SidebarScreen.Browser,
            chosenMode = modeStore.chosenMode.value,
            isRevealed = revealed,
        )

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            closeTab = { store.dispatch(TabListAction.RemoveTabAction(it)) },
            setChosenMode = modeStore.chosenMode::set,
            setRevealed = { revealed = it },
        )

    @Test
    fun `GIVEN Auto-Hide Mode THEN the Sidebar starts hidden and takes no docked width`() {
        assertEquals(SidebarMode.AutoHide, state().mode)
        assertFalse(state().isRevealed)
    }

    @Test
    fun `WHEN revealed THEN selecting a tab switches to it and dismisses the Sidebar`() {
        handler.handle(SidebarIntent.Reveal, state())
        assertTrue(state().isRevealed)

        handler.handle(SidebarIntent.SelectTab("2"), state())

        assertEquals("2", store.state.selectedTabId)
        assertFalse(state().isRevealed)
    }

    @Test
    fun `WHEN revealed THEN New Tab dismisses the Sidebar`() {
        handler.handle(SidebarIntent.Reveal, state())
        handler.handle(SidebarIntent.NewTab, state())

        assertFalse(state().isRevealed)
    }

    @Test
    fun `WHEN revealed THEN closing a tab keeps the Sidebar open`() {
        handler.handle(SidebarIntent.Reveal, state())
        handler.handle(SidebarIntent.CloseTab("2"), state())

        assertTrue(state().isRevealed)
        assertEquals(listOf("1"), state().tabs.map { it.id })
    }

    @Test
    fun `WHEN the Scrim, back or a swipe dismisses THEN the Sidebar hides`() {
        handler.handle(SidebarIntent.Reveal, state())
        handler.handle(SidebarIntent.Dismiss, state())

        assertFalse(state().isRevealed)
    }

    @Test
    fun `GIVEN a docked mode THEN reveal does nothing`() {
        modeStore.chosenMode.set(SidebarMode.Persistent)

        handler.handle(SidebarIntent.Reveal, state())

        assertFalse(state().isRevealed)
    }

    @Test
    fun `THEN the toolbar shows the tab counter only without a Sidebar or as the Sidebar Button in Auto-Hide`() {
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.None, SidebarMode.Persistent, flapEnabled = true))
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.AutoHide, flapEnabled = false))
        assertTrue(SidebarGate.showsTabCounter(SidebarTier.LargePhone, SidebarMode.AutoHide, flapEnabled = false))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Persistent, flapEnabled = false))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.Tablet, SidebarMode.Collapsed, flapEnabled = false))
        assertFalse(SidebarGate.showsTabCounter(SidebarTier.LargePhone, SidebarMode.Persistent, flapEnabled = false))
    }

    @Test
    fun `GIVEN the Large Phone Tier THEN Auto-Hide is available`() {
        assertEquals(SidebarMode.AutoHide, state(SidebarTier.LargePhone).mode)
    }
}
