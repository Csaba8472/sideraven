/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.LastAccessAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What drops between sections do to real state. */
class DragBetweenSectionsTest {
    private val now = 100_000_000L
    private val old = now - 50_000L
    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://a.example", id = "a", lastAccess = now), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://b.example", id = "b", lastAccess = now)))
            dispatch(TabListAction.AddTabAction(createTab("https://c.example", id = "c", lastAccess = now)))
            dispatch(TabListAction.AddTabAction(createTab("https://old.example", id = "old", lastAccess = old, createdAt = old)))
        }
    private val repository = InMemoryEssentialsRepository()
    private val essentials = Essentials(repository, MutableStateFlow(emptyMap()))
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
            unpinEssential = essentials::unpin,
            moveEssential = essentials::move,
            reactivateTab = { id -> store.dispatch(LastAccessAction.UpdateLastAccessAction(id, now)) },
        )

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = repository.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
            inactiveTabsEnabled = true,
            inactiveExpanded = true,
            maxActiveTime = 10_000L,
            now = now,
        )

    @Test
    fun `WHEN a tab is dropped on the Essentials Section THEN it is pinned and leaves the Tab List`() {
        handler.handle(SidebarIntent.PinTab("b"), state())

        assertEquals(listOf("https://b.example"), state().essentials.map { it.url })
        assertTrue(state().tabs.none { it.id == "b" })
    }

    @Test
    fun `WHEN an Essential is dragged out THEN it is unpinned and its tab rejoins the Tab List`() {
        handler.handle(SidebarIntent.PinTab("b"), state())
        handler.handle(SidebarIntent.UnpinEssential("https://b.example"), state())

        assertTrue(state().essentials.isEmpty())
        assertTrue(state().tabs.any { it.id == "b" })
    }

    @Test
    fun `WHEN Essentials are reordered by drag THEN the new order persists`() {
        handler.handle(SidebarIntent.PinTab("a"), state())
        handler.handle(SidebarIntent.PinTab("b"), state())
        handler.handle(SidebarIntent.PinTab("c"), state())

        handler.handle(SidebarIntent.MoveEssential("https://c.example", 0), state())

        assertEquals(
            listOf("https://c.example", "https://a.example", "https://b.example"),
            repository.essentials.value.map { it.url },
        )
    }

    @Test
    fun `WHEN an inactive tab is dragged out THEN it becomes active again`() {
        assertEquals(listOf("old"), state().inactive!!.tabs.map { it.id })

        handler.handle(SidebarIntent.ReactivateTab("old"), state())

        assertNull(state().inactive)
        assertTrue(state().tabs.any { it.id == "old" })
    }

    @Test
    fun `WHEN an inactive tab is dropped on Essentials THEN it is pinned`() {
        handler.handle(SidebarIntent.PinTab("old"), state())

        assertEquals(listOf("https://old.example"), state().essentials.map { it.url })
        assertNull(state().inactive)
    }
}
