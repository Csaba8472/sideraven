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
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ticket 14: the Inactive Tabs Section, against a real store. */
class InactiveTabsTest {
    private val now = 100_000_000L
    private val maxActive = 10_000L
    private val old = now - 50_000L

    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://fresh.example", id = "fresh", lastAccess = now), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://old1.example", id = "old1", lastAccess = old, createdAt = old)))
            dispatch(TabListAction.AddTabAction(createTab("https://old2.example", id = "old2", lastAccess = old, createdAt = old)))
            dispatch(TabListAction.AddTabAction(createTab("https://oldp.example", id = "oldp", private = true, lastAccess = old, createdAt = old)))
        }
    private val modeStore = InMemorySidebarModeStore()
    private val closed = mutableListOf<List<String>>()
    private var revealed = false
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            setRevealed = { revealed = it },
            setInactiveExpanded = modeStore.inactiveExpanded::set,
            closeTabs = { ids -> closed += ids; ids.forEach { store.dispatch(TabListAction.RemoveTabAction(it)) } },
        )

    private fun state(
        enabled: Boolean = true,
        isPrivate: Boolean = false,
        mode: SidebarMode = SidebarMode.Persistent,
    ) = store.state.toSidebarUiState(
        isPrivate = isPrivate,
        tier = SidebarTier.Tablet,
        screen = SidebarScreen.Browser,
        chosenMode = mode,
        isRevealed = revealed,
        inactiveTabsEnabled = enabled,
        inactiveExpanded = modeStore.inactiveExpanded.value,
        maxActiveTime = maxActive,
        now = now,
    )

    @Test
    fun `GIVEN old tabs THEN they leave the Tab List for the Inactive Tabs Section`() {
        assertEquals(listOf("fresh"), state().tabs.map { it.id })
        assertEquals(listOf("old2", "old1"), state().inactive!!.tabs.map { it.id })
        assertEquals(2, state().inactive!!.count)
    }

    @Test
    fun `THEN the section starts collapsed and remembers being opened`() {
        assertFalse(state().inactive!!.expanded)

        handler.handle(SidebarIntent.ToggleInactiveSection, state())

        assertTrue(state().inactive!!.expanded)
        assertTrue(modeStore.inactiveExpanded.value)
    }

    @Test
    fun `GIVEN the feature or the setting is off THEN there is no section and every tab is listed`() {
        assertNull(state(enabled = false).inactive)
        assertEquals(listOf("old2", "old1", "fresh"), state(enabled = false).tabs.map { it.id })
    }

    @Test
    fun `GIVEN Private Mode THEN no tab is treated as inactive`() {
        assertNull(state(isPrivate = true).inactive)
        assertEquals(listOf("oldp"), state(isPrivate = true).tabs.map { it.id })
    }

    @Test
    fun `WHEN Close all inactive is used THEN exactly the inactive tabs close`() {
        handler.handle(SidebarIntent.CloseAllInactive, state())

        assertEquals(listOf(listOf("old2", "old1")), closed)
        assertNull(state().inactive)
        assertEquals(listOf("fresh"), state().tabs.map { it.id })
    }

    @Test
    fun `GIVEN Collapsed Mode WHEN the inactive icon is tapped THEN the full Sidebar reveals with the section open`() {
        handler.handle(SidebarIntent.RevealInactiveSection, state(mode = SidebarMode.Collapsed))

        val revealedState = state(mode = SidebarMode.Collapsed)
        assertTrue(revealedState.isRevealed)
        assertTrue(revealedState.inactive!!.expanded)
    }

    @Test
    fun `GIVEN the selected tab is old THEN it stays in the Tab List`() {
        store.dispatch(TabListAction.SelectTabAction("old1"))

        assertTrue(state().tabs.any { it.id == "old1" })
    }
}
