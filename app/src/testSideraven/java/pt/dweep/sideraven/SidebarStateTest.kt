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
import org.junit.Before
import org.junit.Test

/**
 * Seam 1: the Sidebar state against a real in-memory browser store. Asserts only what the
 * Sidebar would render and what the store ends up containing.
 */
class SidebarStateTest {
    private lateinit var store: BrowserStore

    @Before
    fun setUp() {
        store = BrowserStore()
        store.dispatch(TabListAction.AddTabAction(createTab("https://one.example", title = "One", id = "1")))
        store.dispatch(TabListAction.AddTabAction(createTab("https://two.example", title = "Two", id = "2")))
        store.dispatch(TabListAction.AddTabAction(createTab("https://secret.example", id = "p1", private = true)))
        store.dispatch(TabListAction.AddTabAction(createTab("https://three.example", title = "Three", id = "3")))
        store.dispatch(TabListAction.SelectTabAction("2"))
    }

    private fun state(
        isPrivate: Boolean = false,
        tier: SidebarTier = SidebarTier.Tablet,
        screen: SidebarScreen = SidebarScreen.Browser,
    ) = store.state.toSidebarUiState(isPrivate = isPrivate, tier = tier, screen = screen)

    @Test
    fun `THEN the Tab List is newest-first, the reverse of the browser's tab order`() {
        assertEquals(listOf("3", "2", "1"), state().tabs.map { it.id })
        assertEquals(listOf("1", "2", "p1", "3"), store.state.tabs.map { it.id })
    }

    @Test
    fun `THEN only the selected tab carries the Active Tab Indicator`() {
        assertEquals(listOf(false, true, false), state().tabs.map { it.isSelected })
    }

    @Test
    fun `THEN the Tab List shows only the current Browsing Mode's tabs`() {
        assertEquals(listOf("3", "2", "1"), state(isPrivate = false).tabs.map { it.id })
        assertEquals(listOf("p1"), state(isPrivate = true).tabs.map { it.id })
    }

    @Test
    fun `GIVEN a tab without a title THEN the URL is shown`() {
        assertEquals("https://secret.example", state(isPrivate = true).tabs.single().title)
    }

    @Test
    fun `THEN the Sidebar shows on the browser and home screens only`() {
        assertTrue(state(screen = SidebarScreen.Browser).isVisible)
        assertTrue(state(screen = SidebarScreen.Home).isVisible)
        assertFalse(state(screen = SidebarScreen.Other).isVisible)
    }

    @Test
    fun `GIVEN a window under 600dp THEN the Sidebar is hidden`() {
        assertEquals(SidebarUiState.Hidden, state(tier = SidebarTier.None))
    }

    @Test
    fun `WHEN a tab is tapped THEN it becomes the selected tab`() {
        val handler =
            SidebarIntentHandler(
                selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
                openNewTab = {},
            )

        handler.handle(SidebarIntent.SelectTab("1"), state())

        assertEquals("1", store.state.selectedTabId)
        assertEquals(listOf(false, false, true), state().tabs.map { it.isSelected })
    }

    @Test
    fun `WHEN New Tab is tapped THEN a tab opens in the current Browsing Mode`() {
        val opened = mutableListOf<Boolean>()
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = { opened += it })

        handler.handle(SidebarIntent.NewTab, state(isPrivate = false))
        handler.handle(SidebarIntent.NewTab, state(isPrivate = true))

        assertEquals(listOf(false, true), opened)
    }
}
