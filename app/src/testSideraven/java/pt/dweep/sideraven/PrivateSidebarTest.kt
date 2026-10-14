/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Sidebar in Private Mode, and the guarantee that nothing private is persisted. */
class PrivateSidebarTest {
    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://normal.example", id = "n"), select = true))
            dispatch(TabListAction.AddTabAction(createTab("https://secret.example", id = "p", private = true)))
        }
    private val repository = InMemoryEssentialsRepository(listOf(Essential("https://mail.example", "Mail")))
    private val essentials = Essentials(repository, MutableStateFlow(emptyMap()))
    private val switched = mutableListOf<Boolean>()
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
            switchBrowsingMode = { switched += it },
        )

    private fun state(isPrivate: Boolean) =
        store.state.toSidebarUiState(
            isPrivate = isPrivate,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = repository.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
        )

    @Test
    fun `THEN the Tab List shows only the current Browsing Mode`() {
        assertEquals(listOf("n"), state(isPrivate = false).tabs.map { it.id })
        assertEquals(listOf("p"), state(isPrivate = true).tabs.map { it.id })
    }

    @Test
    fun `THEN Essentials are hidden in Private Mode, and back in normal browsing`() {
        assertEquals(emptyList<String>(), state(isPrivate = true).essentials.map { it.url })
        assertEquals(listOf("https://mail.example"), state(isPrivate = false).essentials.map { it.url })
    }

    @Test
    fun `WHEN the Browsing Mode Switch is used THEN it switches to the other mode`() {
        handler.handle(SidebarIntent.SwitchBrowsingMode, state(isPrivate = false))
        handler.handle(SidebarIntent.SwitchBrowsingMode, state(isPrivate = true))

        assertEquals(listOf(true, false), switched)
    }

    @Test
    fun `WHEN a private tab is pinned by any route THEN no private URL reaches the repository`() {
        handler.handle(SidebarIntent.PinTab("p"), state(isPrivate = true))
        essentials.pin(store.state.findTab("p")!!, atIndex = 0)

        assertTrue(repository.essentials.value.none { it.url == "https://secret.example" })
        assertEquals(PinResult.PrivateRefused, state(isPrivate = true).pinBlock)
    }
}
