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

/** Every new tab lands at the top of the Tab List. Real store, real reducer. */
class NewTabsOnTopTest {
    private lateinit var store: BrowserStore

    private fun sidebarTabIds(isPrivate: Boolean = false) =
        store.state
            .toSidebarUiState(isPrivate = isPrivate, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser)
            .tabs
            .map { it.id }

    @Before
    fun setUp() {
        store = BrowserStore(middleware = listOf(NewTabPositionMiddleware()))
        store.dispatch(TabListAction.AddTabAction(createTab("https://one.example", id = "1"), select = true))
        store.dispatch(TabListAction.AddTabAction(createTab("https://two.example", id = "2")))
    }

    @Test
    fun `WHEN New Tab or an external intent opens a tab THEN it is at the top`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://new.example", id = "new"), select = true))

        assertEquals(listOf("new", "2", "1"), sidebarTabIds())
        assertEquals("new", store.state.selectedTabId)
    }

    @Test
    fun `WHEN a link opens a child tab THEN it is at the top, not next to its parent`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://child.example", id = "child", parent = store.state.tabs[0])))

        assertEquals(listOf("child", "2", "1"), sidebarTabIds())
        assertEquals("1", store.state.tabs.single { it.id == "child" }.parentId)
    }

    @Test
    fun `WHEN a private child tab opens THEN it is at the top of the private Tab List`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://p.example", id = "p", private = true)))
        store.dispatch(
            TabListAction.AddTabAction(
                createTab("https://pc.example", id = "pc", private = true, parent = store.state.tabs.single { it.id == "p" }),
            ),
        )

        assertEquals(listOf("pc", "p"), sidebarTabIds(isPrivate = true))
    }

    @Test
    fun `WHEN tabs are restored THEN their order is kept`() {
        store.dispatch(
            TabListAction.AddMultipleTabsAction(
                listOf(createTab("https://r1.example", id = "r1"), createTab("https://r2.example", id = "r2")),
            ),
        )

        assertEquals(listOf("1", "2", "r1", "r2"), store.state.tabs.map { it.id })
    }

    @Test
    fun `THEN the Tab Tray order (oldest-first) is the reverse of the Tab List`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://child.example", id = "child", parent = store.state.tabs[0])))

        assertEquals(sidebarTabIds().reversed(), store.state.tabs.map { it.id })
    }
}
