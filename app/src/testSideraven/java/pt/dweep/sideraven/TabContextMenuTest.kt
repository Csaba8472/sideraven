/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Duplicate from the Tab Context Menu, with Fenix's real use case and store. */
class TabContextMenuTest {
    private val store =
        BrowserStore(middleware = listOf(NewTabPositionMiddleware())).apply {
            dispatch(TabListAction.AddTabAction(createTab("https://one.example", id = "1"), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://two.example", id = "2")))
        }
    private val tabsUseCases = TabsUseCases(store)
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            duplicateTab = { id -> store.state.findTab(id)?.let { tabsUseCases.duplicateTab.invoke(it, selectNewTab = true) } },
        )

    private fun state() =
        store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser)

    @Test
    fun `WHEN a tab is duplicated THEN the copy appears at the top and is selected`() {
        handler.handle(SidebarIntent.DuplicateTab("1"), state())

        val top = state().tabs.first()
        assertNotEquals("1", top.id)
        assertEquals("https://one.example", top.url)
        assertEquals(top.id, store.state.selectedTabId)
        assertEquals(3, state().tabs.size)
    }
}
