/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Ticket 10: Essentials against a real browser store and an in-memory repository. */
@RunWith(AndroidJUnit4::class)
class EssentialsTest {
    private val store =
        BrowserStore(middleware = listOf(NewTabPositionMiddleware())).apply {
            dispatch(TabListAction.AddTabAction(createTab("https://mail.example", title = "Mail", id = "mail"), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://news.example", title = "News", id = "news")))
            dispatch(TabListAction.AddTabAction(createTab("https://secret.example", id = "p", private = true)))
        }
    private val repository = InMemoryEssentialsRepository()
    private val essentials = Essentials(repository, MutableStateFlow(emptyMap()))
    private val loaded = mutableListOf<Pair<String, String>>()
    private var nextId = 0

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            pinTab = { id -> store.state.findTab(id)?.let { essentials.pin(it) } ?: PinResult.PrivateRefused },
            openEssential = { url, private ->
                val id = "opened${nextId++}"
                store.dispatch(TabListAction.AddTabAction(createTab(url, id = id, private = private), select = true))
                if (!private) essentials.link(url, id)
            },
            unpinEssential = essentials::unpin,
            loadUrlInTab = { url, tabId -> loaded += url to tabId },
        )

    private fun state(isPrivate: Boolean = false) =
        store.state.toSidebarUiState(
            isPrivate = isPrivate,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = repository.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
        )

    @Test
    fun `WHEN a tab is pinned THEN it becomes an Essential and leaves the Tab List`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())

        assertEquals(listOf("https://mail.example"), state().essentials.map { it.url })
        assertEquals("mail", state().essentials.single().linkedTabId)
        assertEquals(listOf("news"), state().tabs.map { it.id })
    }

    @Test
    fun `GIVEN 12 Essentials THEN pinning is unavailable and refused`() {
        repository.save((1..ESSENTIALS_CAP).map { Essential("https://e$it.example", "E$it") })
        val refused = mutableListOf<PinResult>()
        val capped =
            SidebarIntentHandler(
                selectTab = {},
                openNewTab = {},
                pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
                onPinRefused = { refused += it },
            )

        assertEquals(PinResult.Full, state().pinBlock)
        capped.handle(SidebarIntent.PinTab("mail"), state())

        assertEquals(listOf(PinResult.Full), refused)
        assertEquals(ESSENTIALS_CAP, repository.essentials.value.size)
    }

    @Test
    fun `GIVEN a private tab THEN it can never become an Essential`() {
        assertEquals(PinResult.PrivateRefused, essentials.pin(store.state.findTab("p")!!))
        assertEquals(PinResult.PrivateRefused, state(isPrivate = true).pinBlock)
        assertTrue(repository.essentials.value.isEmpty())
    }

    @Test
    fun `WHEN the Essential Tab closes THEN the Essential stays as a Ghost Essential`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        store.dispatch(TabListAction.RemoveTabAction("mail"))

        val ghost = state().essentials.single()
        assertTrue(ghost.isGhost)
        assertEquals("https://mail.example", ghost.url)
    }

    @Test
    fun `WHEN a Ghost Essential is tapped THEN a tab opens at its URL and is linked`() {
        repository.save(listOf(Essential("https://docs.example", "Docs")))

        handler.handle(SidebarIntent.SelectEssential("https://docs.example"), state())

        val essential = state().essentials.single()
        assertFalse(essential.isGhost)
        assertEquals(essential.linkedTabId, store.state.selectedTabId)
        assertEquals("https://docs.example", store.state.findTab(essential.linkedTabId!!)!!.content.url)
        assertFalse(state().tabs.any { it.id == essential.linkedTabId })
    }

    @Test
    fun `WHEN an open Essential is tapped THEN it switches to its tab without a duplicate`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        store.dispatch(TabListAction.SelectTabAction("news"))
        val before = store.state.tabs.size

        handler.handle(SidebarIntent.SelectEssential("https://mail.example"), state())

        assertEquals("mail", store.state.selectedTabId)
        assertEquals(before, store.state.tabs.size)
    }

    @Test
    fun `WHEN the Essential Tab navigates elsewhere THEN it stays linked`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        store.dispatch(ContentAction.UpdateUrlAction("mail", "https://mail.example/inbox/42"))

        assertEquals("mail", state().essentials.single().linkedTabId)
        assertFalse(state().tabs.any { it.id == "mail" })
    }

    @Test
    fun `WHEN Reset to pinned URL is chosen THEN the Essential Tab loads the Essential's URL`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        store.dispatch(ContentAction.UpdateUrlAction("mail", "https://mail.example/inbox/42"))

        handler.handle(SidebarIntent.ResetEssential("https://mail.example"), state())

        assertEquals(listOf("https://mail.example" to "mail"), loaded)
    }

    @Test
    fun `WHEN unpinned THEN its tab returns to the Tab List`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        handler.handle(SidebarIntent.UnpinEssential("https://mail.example"), state())

        assertTrue(state().essentials.isEmpty())
        assertTrue(state().tabs.any { it.id == "mail" })
    }

    @Test
    fun `WHEN tabs are restored after a restart THEN only exact-URL matches are re-linked`() {
        repository.save(listOf(Essential("https://mail.example", "Mail"), Essential("https://news.example/today", "News")))

        essentials.relinkRestoredTabs(store.state.tabs)

        assertEquals("mail", essentials.linkedTabId("https://mail.example", store.state.tabs))
        assertNull(essentials.linkedTabId("https://news.example/today", store.state.tabs))
    }

    @Test
    fun `GIVEN Private Mode THEN Essentials can't be opened from the Sidebar, and stay linked to their normal tab`() {
        handler.handle(SidebarIntent.PinTab("mail"), state())
        val selected = store.state.selectedTabId

        handler.handle(SidebarIntent.SelectEssential("https://mail.example"), state(isPrivate = true))

        assertEquals(selected, store.state.selectedTabId)
        assertTrue(store.state.tabs.none { it.content.private && it.content.url == "https://mail.example" })
        assertEquals("mail", essentials.linkedTabs.value["https://mail.example"])
    }
}
