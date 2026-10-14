/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Moving tabs between Spaces, and selection following a tab's Space. */
@RunWith(AndroidJUnit4::class)
class MoveTabsBetweenSpacesTest {
    private val spaces = Spaces(InMemorySpacesRepository())
    private val essentialTabs = mutableSetOf<String>()
    private val store =
        BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware(isGlobal = { it in essentialTabs }) { spaces }))
    private val home = spaces.data.current.id
    private val work = spaces.create("Work", "💼").id.also { spaces.switchTo(home) }

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            moveTabToSpace = { tabId, spaceId ->
                val wasSelected = store.state.selectedTabId == tabId
                spaces.moveTab(tabId, spaceId)
                if (wasSelected) spaces.data.switchTarget(spaces.data.currentSpaceId, store.state.tabs) else null
            },
            moveTab = { id, target, after -> store.dispatch(TabListAction.MoveTabsAction(listOf(id), target, after)) },
        )

    private fun add(
        id: String,
        select: Boolean = false,
    ) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", id = id), select = select))

    private fun state() =
        store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, spaces = spaces.data)

    @Test
    fun `WHEN a tab is moved to another Space THEN it leaves this Tab List and the current Space stays`() {
        add("a")
        add("b")

        handler.handle(SidebarIntent.MoveTabToSpace("a", work), state())

        assertEquals(home, state().currentSpace?.id)
        assertEquals(listOf("b"), state().tabs.map { it.id })
        spaces.switchTo(work)
        assertEquals(listOf("a"), state().tabs.map { it.id })
    }

    @Test
    fun `WHEN the selected tab is moved away THEN selection stays in this Space`() {
        add("a")
        add("b", select = true)

        handler.handle(SidebarIntent.MoveTabToSpace("b", work), state())

        assertEquals(home, state().currentSpace?.id)
        assertEquals("a", store.state.selectedTabId)
    }

    @Test
    fun `WHEN a tab of another Space is selected elsewhere (Tab Tray) THEN its Space becomes current`() {
        add("a", select = true)
        spaces.switchTo(work)
        add("w")
        spaces.switchTo(home)

        store.dispatch(TabListAction.SelectTabAction("w"))

        assertEquals(work, state().currentSpace?.id)
        assertEquals(listOf("w"), state().tabs.map { it.id })
    }

    @Test
    fun `WHEN an Essential Tab is selected THEN the Space doesn't change`() {
        add("a", select = true)
        spaces.switchTo(work)
        add("essential")
        essentialTabs += "essential"
        spaces.switchTo(home)

        store.dispatch(TabListAction.SelectTabAction("essential"))

        assertEquals(home, state().currentSpace?.id)
    }

    @Test
    fun `GIVEN a tab whose Space is gone THEN it shows in the current Space`() {
        add("a")
        spaces.moveTab("a", work)
        spaces.delete(work)

        assertEquals(listOf("a"), state().tabs.map { it.id })
    }

    @Test
    fun `WHEN tabs are reordered inside a Space with other Spaces' tabs in between THEN the order follows the drag`() {
        add("h1")
        spaces.switchTo(work)
        add("w1")
        spaces.switchTo(home)
        add("h2")
        // Tab List (newest first): h2, h1. Drag h1 above h2.

        handler.handle(SidebarIntent.MoveTab("h1", "h2", movedDown = false), state())

        assertEquals(listOf("h1", "h2"), state().tabs.map { it.id })
    }
}
