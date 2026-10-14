/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Closing tabs from the Sidebar, against a real in-memory browser store. */
class CloseTabTest {
    private lateinit var store: BrowserStore
    private val closed = mutableListOf<Pair<Boolean, Boolean>>()
    private lateinit var handler: SidebarIntentHandler

    private fun state(isPrivate: Boolean = false) =
        store.state.toSidebarUiState(isPrivate = isPrivate, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser)

    @Before
    fun setUp() {
        store = BrowserStore()
        store.dispatch(TabListAction.AddTabAction(createTab("https://one.example", id = "1"), select = true))
        store.dispatch(TabListAction.AddTabAction(createTab("https://two.example", id = "2")))
        store.dispatch(TabListAction.AddTabAction(createTab("https://p.example", id = "p", private = true)))
        handler =
            SidebarIntentHandler(
                selectTab = {},
                openNewTab = {},
                closeTab = { store.dispatch(TabListAction.RemoveTabAction(it)) },
                onTabClosed = { private, wasLast -> closed += private to wasLast },
            )
    }

    @Test
    fun `WHEN a tab is closed THEN it leaves the Tab List and undo is offered`() {
        handler.handle(SidebarIntent.CloseTab("2"), state())

        assertEquals(listOf("1"), state().tabs.map { it.id })
        assertEquals(listOf(false to false), closed)
    }

    @Test
    fun `WHEN the only tab of a Browsing Mode is closed THEN the host is told it was the last`() {
        handler.handle(SidebarIntent.CloseTab("p"), state(isPrivate = true))

        assertEquals(emptyList<String>(), state(isPrivate = true).tabs.map { it.id })
        assertEquals(listOf(true to true), closed)
    }

    @Test
    fun `WHEN the selected tab is closed THEN another tab becomes selected`() {
        handler.handle(SidebarIntent.CloseTab("1"), state())

        assertEquals("2", store.state.selectedTabId)
        assertEquals(listOf(true), state().tabs.map { it.isSelected })
    }
}
