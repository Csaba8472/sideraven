/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spaces ticket 09, and spec 0006 ticket 09: closing a pinned tab leaves a Ghost; Unload puts a tab
 * to sleep, keeping it (and its pin) in place.
 */
@RunWith(AndroidJUnit4::class)
class GhostsAndUnloadTest {
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val essentials = Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val closedWithUndo = mutableListOf<Boolean>()
    private val slept = mutableListOf<String>()
    private var nextId = 0

    /** The app suspends these tabs' engine sessions (that needs Fenix's engine middleware); here, recorded. */
    private fun sleep(ids: List<String>) {
        slept += ids
    }

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            closeTab = { store.dispatch(TabListAction.RemoveTabAction(it)) },
            onTabClosed = { _, wasLast -> closedWithUndo += wasLast },
            pinToSpace = { id -> spaces.pinToSpace(store.state.findTab(id)!!) },
            openPinnedTab = { pinId, url ->
                val id = "opened${nextId++}"
                store.dispatch(TabListAction.AddTabAction(createTab(url, id = id), select = true))
                spaces.linkPin(pinId, id)
            },
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
            openEssential = { url, _ ->
                val id = "opened${nextId++}"
                store.dispatch(TabListAction.AddTabAction(createTab(url, id = id), select = true))
                essentials.link(url, id)
            },
            sleepTabs = ::sleep,
            unloadSpace = { spaceId ->
                val selected = store.state.selectedTabId
                sleep(spaces.data.pinnedIn(spaceId).mapNotNull { spaces.pinnedLinks.value[it.id] }.filter { it != selected })
            },
        )

    private fun add(
        id: String,
        select: Boolean = false,
    ) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", id = id), select = select))

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = essentials.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
            spaces = spaces.data,
            pinnedLinks = spaces.pinnedLinks.value,
            sleepingTabIds = slept.toSet(),
        )

    private fun pinMail(): String {
        add("news", select = true)
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        return state().pinned.single().id
    }

    @Test
    fun `WHEN a Pinned Tab is closed THEN its tab closes and it stays pinned as a Ghost, with Undo offered`() {
        val pin = pinMail()

        handler.handle(SidebarIntent.ClosePinnedTab(pin), state())

        assertNull(store.state.findTab("mail"))
        assertTrue(state().pinned.single().isGhost)
        assertEquals(listOf(false), closedWithUndo)
    }

    @Test
    fun `WHEN a Pinned Tab is Unloaded THEN its tab sleeps, still pinned and open, not a Ghost`() {
        val pin = pinMail()

        handler.handle(SidebarIntent.UnloadPinnedTab(pin), state())

        assertEquals(listOf("mail"), slept)
        assertNotNull(store.state.findTab("mail"))
        val pinned = state().pinned.single()
        assertFalse(pinned.isGhost)
        assertTrue(pinned.sleeping)
        assertEquals(emptyList<Boolean>(), closedWithUndo)
    }

    @Test
    fun `WHEN the tab on screen is Unloaded THEN nothing sleeps, so the page never reloads under you`() {
        val pin = pinMail()
        handler.handle(SidebarIntent.SelectPinnedTab(pin), state())

        handler.handle(SidebarIntent.UnloadPinnedTab(pin), state())
        handler.handle(SidebarIntent.UnloadTab("mail"), state())

        assertEquals(emptyList<String>(), slept)
    }

    @Test
    fun `WHEN a normal tab is Unloaded THEN it sleeps and stays in the Tab List`() {
        add("news", select = true)
        add("docs")

        handler.handle(SidebarIntent.UnloadTab("docs"), state())

        assertEquals(listOf("docs"), slept)
        assertTrue(state().tabs.single { it.id == "docs" }.sleeping)
        assertFalse(state().tabs.single { it.id == "news" }.sleeping)
    }

    @Test
    fun `WHEN Unload all is chosen for a Space THEN its Pinned Tabs sleep, and no tab closes`() {
        add("news", select = true)
        add("mail")
        add("docs")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())
        handler.handle(SidebarIntent.PinToSpace("docs"), state())

        handler.handle(SidebarIntent.UnloadSpace(spaces.data.current.id), state())

        assertEquals(setOf("mail", "docs"), slept.toSet())
        assertEquals(setOf("news", "mail", "docs"), store.state.tabs.map { it.id }.toSet())
        assertTrue(state().pinned.none { it.isGhost })
    }

    @Test
    fun `WHEN an Essential is Unloaded THEN its tab sleeps and it is not a Ghost`() {
        add("news", select = true)
        add("mail")
        handler.handle(SidebarIntent.PinTab("mail"), state())

        handler.handle(SidebarIntent.UnloadEssential("https://mail.example"), state())

        assertEquals(listOf("mail"), slept)
        val essential = state().essentials.single()
        assertFalse(essential.isGhost)
        assertTrue(essential.sleeping)
    }
}
