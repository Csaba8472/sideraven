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
import org.junit.Test
import org.junit.runner.RunWith

/** Renaming Essentials and Pinned Tabs. */
@RunWith(AndroidJUnit4::class)
class RenamePinsTest {
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()))
    private val spacesRepository = InMemorySpacesRepository()
    private val spaces = Spaces(spacesRepository, pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val loaded = mutableListOf<String>()

    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            loadUrlInTab = { url, _ -> loaded += url },
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
            pinToSpace = { id -> spaces.pinToSpace(store.state.findTab(id)!!) },
            renameEssential = essentials::rename,
            renamePinnedTab = spaces::renamePin,
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

    private fun add(
        id: String,
        title: String,
    ) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", title = title, id = id)))

    @Test
    fun `WHEN an Essential is renamed THEN the custom name shows, survives Reset, and clearing it restores the title`() {
        add("mail", "Inbox (3) - Mail")
        handler.handle(SidebarIntent.PinTab("mail"), state())

        handler.handle(SidebarIntent.RenameEssential("https://mail.example", "Mail"), state())
        assertEquals("Mail", state().essentials.single().title)

        handler.handle(SidebarIntent.ResetEssential("https://mail.example"), state())
        assertEquals("Mail", state().essentials.single().title)

        handler.handle(SidebarIntent.RenameEssential("https://mail.example", null), state())
        assertEquals("Inbox (3) - Mail", state().essentials.single().title)
    }

    @Test
    fun `WHEN a Pinned Tab is renamed THEN the custom name shows and is stored`() {
        add("docs", "Untitled document - Docs")
        handler.handle(SidebarIntent.PinToSpace("docs"), state())
        val pin = state().pinned.single().id

        handler.handle(SidebarIntent.RenamePinnedTab(pin, "  Notes  "), state())

        assertEquals("Notes", state().pinned.single().title)
        assertEquals("Notes", SpacesCodec.decode(SpacesCodec.encode(spaces.data))!!.pinned.single().customName)
    }
}
