/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.feature.tabs.TabsUseCases
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The display-index -> browser-order translation. The Tab List shows the browser's
 * order reversed, so every on-screen move must land the opposite way in the store.
 */
class TabDragTest {
    // Browser order A B C D (+ a private tab and an Essential Tab interleaved); Tab List shows D C B A.
    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://a.example", id = "A"), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://b.example", id = "B")))
            dispatch(TabListAction.AddTabAction(createTab("https://p.example", id = "P", private = true)))
            dispatch(TabListAction.AddTabAction(createTab("https://c.example", id = "C")))
            dispatch(TabListAction.AddTabAction(createTab("https://e.example", id = "E")))
            dispatch(TabListAction.AddTabAction(createTab("https://d.example", id = "D")))
        }
    private val essentials =
        Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())).also {
            it.pin(store.state.findTab("E")!!)
        }
    private val tabsUseCases = TabsUseCases(store)
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            moveTab = { id, target, after -> tabsUseCases.moveTabs(listOf(id), target, after) },
        )

    private fun tabList() =
        store.state
            .toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                essentials = essentials.essentials.value,
                essentialLinks = essentials.linkedTabs.value,
            ).tabs
            .map { it.id }

    /** Simulates dragging the item at [from] onto the slot at [to], as the list reports it. */
    private fun drag(
        from: Int,
        to: Int,
    ) {
        val list = tabList()
        handler.handle(SidebarIntent.MoveTab(list[from], list[to], movedDown = to > from), store.state.toSidebarUiState(false, SidebarTier.Tablet, SidebarScreen.Browser))
    }

    @Test
    fun `GIVEN the start THEN the Tab List is newest-first without the Essential Tab`() {
        assertEquals(listOf("D", "C", "B", "A"), tabList())
    }

    @Test
    fun `WHEN the top tab is dragged down one slot THEN it lands below its neighbour`() {
        drag(from = 0, to = 1)

        assertEquals(listOf("C", "D", "B", "A"), tabList())
    }

    @Test
    fun `WHEN the bottom tab is dragged up one slot THEN it lands above its neighbour`() {
        drag(from = 3, to = 2)

        assertEquals(listOf("D", "C", "A", "B"), tabList())
    }

    @Test
    fun `WHEN a tab is dragged step by step to the very top THEN it ends first`() {
        drag(from = 3, to = 2)
        drag(from = 2, to = 1)
        drag(from = 1, to = 0)

        assertEquals(listOf("A", "D", "C", "B"), tabList())
    }

    @Test
    fun `WHEN a tab is dragged step by step to the very bottom THEN it ends last`() {
        drag(from = 0, to = 1)
        drag(from = 1, to = 2)
        drag(from = 2, to = 3)

        assertEquals(listOf("C", "B", "A", "D"), tabList())
    }

    @Test
    fun `WHEN dragging past an interleaved Essential Tab and private tab THEN only the visible order changes`() {
        // C and D straddle the Essential Tab E in browser order.
        drag(from = 0, to = 1)

        assertEquals(listOf("C", "D", "B", "A"), tabList())
        assertEquals(listOf("E"), essentials.essentials.value.map { essentials.linkedTabId(it.url, store.state.tabs) })
        assertEquals(true, store.state.findTab("P")!!.content.private)
    }

    @Test
    fun `THEN the Tab Tray order is always the reverse of the Tab List`() {
        drag(from = 3, to = 1)

        val trayOrder = store.state.tabs.filter { !it.content.private && it.id != "E" }.map { it.id }
        assertEquals(tabList().reversed(), trayOrder)
    }
}
