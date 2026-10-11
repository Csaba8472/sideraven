/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** Spaces tickets 10 and 11: closing an Essential Tab by touch, and the adaptive grid. */
@RunWith(AndroidJUnit4::class)
class EssentialBarTest {
    private val repository = InMemoryEssentialsRepository()
    private val essentials = Essentials(repository, MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware()))
    private val closed = mutableListOf<String>()

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            closeTab = {
                closed += it
                store.dispatch(TabListAction.RemoveTabAction(it))
            },
            pinTab = { id -> essentials.pin(store.state.findTab(id)!!) },
        )

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = repository.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
        )

    @Test
    fun `WHEN the Essential Bar closes an Essential Tab THEN its tab closes and the Essential stays as a Ghost`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://mail.example", id = "mail"), select = true))
        handler.handle(SidebarIntent.PinTab("mail"), state())

        handler.handle(SidebarIntent.CloseEssentialTab("https://mail.example"), state())

        assertEquals(listOf("mail"), closed)
        assertNull(store.state.findTab("mail"))
        assertTrue(state().essentials.single().isGhost)
    }

    @Test
    fun `THEN Reset applies only once the Essential Tab has navigated away`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://mail.example", id = "mail"), select = true))
        handler.handle(SidebarIntent.PinTab("mail"), state())
        assertFalse(state().essentials.single().navigatedAway)

        store.dispatch(ContentAction.UpdateUrlAction("mail", "https://mail.example/inbox"))

        assertTrue(state().essentials.single().navigatedAway)
    }

    @Test
    fun `THEN 1 to 3 Essentials fill one row and 4 or more use four columns`() {
        assertEquals(listOf(1, 2, 3, 4, 4, 4), listOf(1, 2, 3, 4, 5, 13).map(::essentialsGridColumns))
    }

    @Test
    fun `GIVEN more Essentials than the cap (from sync) THEN all show and adding is blocked`() {
        repository.save((1..ESSENTIALS_CAP + 2).map { Essential("https://e$it.example", "E$it") })

        assertEquals(ESSENTIALS_CAP + 2, state().essentials.size)
        assertEquals(PinResult.Full, state().pinBlock)
    }
}
