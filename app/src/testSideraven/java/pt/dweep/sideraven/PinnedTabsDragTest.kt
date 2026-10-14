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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Reordering Pinned Tabs and moving pins between Essentials and a Space. */
@RunWith(AndroidJUnit4::class)
class PinnedTabsDragTest {
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()))
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val refused = mutableListOf<PinResult>()

    /** Mirrors the app's wiring. */
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
            onPinRefused = { refused += it },
            pinToSpace = { id -> spaces.pinToSpace(store.state.findTab(id)!!) },
            onSpacePinRefused = { refused += it },
            movePinnedTab = spaces::movePin,
            pinnedToEssentials = { pinId ->
                val pin = spaces.data.pinned.first { it.id == pinId }
                essentials.pinUrl(pin.url, pin.title, spaces.pinnedLinks.value[pinId], pin.customName)
                    .also { if (it == PinResult.Pinned) spaces.unpinFromSpace(pinId) }
            },
            essentialToSpace = { url ->
                val essential = essentials.essentials.value.first { it.url == url }
                spaces.pinUrl(url, essential.title, essentials.linkedTabId(url, store.state.tabs), essential.customName)
                    .also { if (it == PinResult.Pinned) essentials.unpin(url) }
            },
        )

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = essentialsRepository.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
            spaces = spaces.data,
            pinnedLinks = spaces.pinnedLinks.value,
        )

    private fun add(id: String) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", title = id, id = id)))

    private fun pinned() = state().pinned.map { it.title }

    private fun pinIdOf(title: String) = state().pinned.first { it.title == title }.id

    @Test
    fun `WHEN a Pinned Tab is dragged onto another THEN it takes that place`() {
        listOf("a", "b", "c").forEach {
            add(it)
            handler.handle(SidebarIntent.PinToSpace(it), state())
        }

        handler.handle(SidebarIntent.MovePinnedTab(pinIdOf("a"), pinIdOf("c")), state())
        assertEquals(listOf("b", "c", "a"), pinned())

        handler.handle(SidebarIntent.MovePinnedTab(pinIdOf("a"), pinIdOf("b")), state())
        assertEquals(listOf("a", "b", "c"), pinned())
    }

    @Test
    fun `WHEN a Pinned Tab is dragged into Essentials THEN it becomes an Essential, still linked to its tab`() {
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())

        handler.handle(SidebarIntent.PinnedToEssentials(pinIdOf("mail")), state())

        assertTrue(state().pinned.isEmpty())
        assertEquals("mail", state().essentials.single().linkedTabId)
        assertTrue(state().tabs.isEmpty())
    }

    @Test
    fun `WHEN an Essential is dragged into the Space's pins THEN it becomes a Pinned Tab, still linked`() {
        add("mail")
        handler.handle(SidebarIntent.PinTab("mail"), state())

        handler.handle(SidebarIntent.EssentialToSpace("https://mail.example"), state())

        assertTrue(state().essentials.isEmpty())
        assertEquals("mail", state().pinned.single().linkedTabId)
    }

    @Test
    fun `GIVEN Essentials are full WHEN a Pinned Tab is dragged in THEN it is refused and stays pinned`() {
        essentialsRepository.save((1..ESSENTIALS_CAP).map { Essential("https://e$it.example", "E$it") })
        add("mail")
        handler.handle(SidebarIntent.PinToSpace("mail"), state())

        handler.handle(SidebarIntent.PinnedToEssentials(pinIdOf("mail")), state())

        assertEquals(listOf(PinResult.Full), refused)
        assertEquals(listOf("mail"), pinned())
    }
}
