/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** The choice, the gate, and the layout button. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AddressBarLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = BrowserStore().apply { dispatch(TabListAction.AddTabAction(createTab("https://example.com", id = "t"), select = true)) }

    private fun state(
        layout: AddressBarLayout,
        mode: SidebarMode,
    ) = store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, chosenMode = mode, addressBarLayout = layout)

    @Test
    fun `THEN the Sidebar layout applies when chosen, with the Sidebar shown, in Persistent Mode or a revealable Auto-Hide`() {
        val sidebar = AddressBarLayout.Sidebar
        assertEquals(AddressBarLayout.Sidebar, SidebarGate.effectiveLayout(sidebar, sidebarShown = true, mode = SidebarMode.Persistent, revealable = false))
        assertEquals(AddressBarLayout.Top, SidebarGate.effectiveLayout(sidebar, sidebarShown = true, mode = SidebarMode.Collapsed, revealable = true))
        assertEquals(AddressBarLayout.Sidebar, SidebarGate.effectiveLayout(sidebar, sidebarShown = true, mode = SidebarMode.AutoHide, revealable = true))
        assertEquals(AddressBarLayout.Top, SidebarGate.effectiveLayout(sidebar, sidebarShown = true, mode = SidebarMode.AutoHide, revealable = false))
        assertEquals(AddressBarLayout.Top, SidebarGate.effectiveLayout(sidebar, sidebarShown = false, mode = SidebarMode.Persistent, revealable = true))
        assertEquals(AddressBarLayout.Top, SidebarGate.effectiveLayout(AddressBarLayout.Top, sidebarShown = true, mode = SidebarMode.Persistent, revealable = true))
    }

    @Test
    fun `GIVEN the Top layout THEN the Sidebar has no address bar, Collapsed Mode included`() {
        assertNull(state(AddressBarLayout.Top, SidebarMode.Persistent).address)
        // Collapsed Mode has no search icon either: New Tab opens the same Search Pop-up.
        assertNull(state(AddressBarLayout.Top, SidebarMode.Collapsed).address)
        assertNotNull(state(AddressBarLayout.Sidebar, SidebarMode.Persistent).address)
        // Auto-Hide: AutoHideAddressBarTest.
    }

    @Test
    fun `THEN the stored choice defaults to the Sidebar layout (to introduce it) and the layout button flips it`() {
        val modeStore = InMemorySidebarModeStore()
        assertEquals(AddressBarLayout.Sidebar, modeStore.addressBarLayout.value)
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, setAddressBarLayout = modeStore.addressBarLayout::set)

        handler.handle(SidebarIntent.ToggleAddressBarLayout, state(modeStore.addressBarLayout.value, SidebarMode.Persistent))
        assertEquals(AddressBarLayout.Top, modeStore.addressBarLayout.value)

        handler.handle(SidebarIntent.ToggleAddressBarLayout, state(modeStore.addressBarLayout.value, SidebarMode.Persistent))
        assertEquals(AddressBarLayout.Sidebar, modeStore.addressBarLayout.value)
    }

    @Test
    fun `THEN the layout button is labelled with where it moves the address, in Persistent Mode only`() {
        val intents = mutableListOf<SidebarIntent>()
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(state = state(AddressBarLayout.Top, SidebarMode.Persistent), onIntent = { intents += it })
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.LAYOUT_BUTTON).assertContentDescriptionEquals("Move address bar to the sidebar")
        composeRule.onNodeWithTag(SidebarTestTag.LAYOUT_BUTTON).performClick()
        composeRule.onAllNodesWithTag(SidebarTestTag.ADDRESS_BAR).assertCountEquals(0)

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleAddressBarLayout), intents)
    }
}
