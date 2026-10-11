/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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

/**
 * The address bar in the Auto-Hide Sidebar (2026-10-11): the layout button moves the address into the
 * Sidebar in Auto-Hide Mode too, tablets and phones alike, whenever the Sidebar can still be pulled out
 * (by its handle or the edge swipe). With neither, Fenix's toolbar stays (it holds the Sidebar Button),
 * and the layout button isn't offered.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AutoHideAddressBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = BrowserStore().apply { dispatch(TabListAction.AddTabAction(createTab("https://example.com", id = "t"), select = true)) }

    private fun state(
        layout: AddressBarLayout,
        tier: SidebarTier = SidebarTier.Tablet,
        flap: Boolean = true,
        swipeZone: Boolean = true,
        revealed: Boolean = true,
    ) = store.state.toSidebarUiState(
        isPrivate = false,
        tier = tier,
        screen = SidebarScreen.Browser,
        chosenMode = SidebarMode.AutoHide,
        isRevealed = revealed,
        flapEnabled = flap,
        swipeZoneEnabled = swipeZone,
        addressBarLayout = layout,
    )

    @Test
    fun `GIVEN Auto-Hide WHEN the Sidebar layout is chosen THEN the address moves into the Sidebar and the toolbar goes`() {
        val autoHide = state(AddressBarLayout.Sidebar)
        assertEquals(AddressBarLayout.Sidebar, autoHide.layout)
        assertNotNull(autoHide.address)
    }

    @Test
    fun `GIVEN a phone WHEN the Sidebar layout is chosen THEN the address moves into the Sidebar too`() {
        // Phones keep the handle by default and the edge swipe off.
        val phone = state(AddressBarLayout.Sidebar, tier = SidebarTier.Phone, swipeZone = false)
        assertEquals(AddressBarLayout.Sidebar, phone.layout)
        assertNotNull(phone.address)
    }

    @Test
    fun `GIVEN only the edge swipe or only the handle THEN the Sidebar can still be pulled out, so the address moves`() {
        assertEquals(AddressBarLayout.Sidebar, state(AddressBarLayout.Sidebar, flap = false, swipeZone = true).layout)
        assertEquals(AddressBarLayout.Sidebar, state(AddressBarLayout.Sidebar, flap = true, swipeZone = false).layout)
    }

    @Test
    fun `GIVEN neither the handle nor the edge swipe THEN Fenix's toolbar stays, with no address in the Sidebar`() {
        val stuck = state(AddressBarLayout.Sidebar, flap = false, swipeZone = false)
        assertEquals(AddressBarLayout.Top, stuck.layout)
        assertNull(stuck.address)
    }

    @Test
    fun `GIVEN neither the handle nor the edge swipe THEN the layout button isn't offered, since it couldn't act`() {
        composeRule.setContent {
            SideravenTheme { SidebarContent(state = state(AddressBarLayout.Top, flap = false, swipeZone = false), onIntent = {}) }
        }
        composeRule.onAllNodesWithTag(SidebarTestTag.LAYOUT_BUTTON).assertCountEquals(0)
    }

    @Test
    fun `GIVEN the pulled-out Auto-Hide Sidebar THEN the layout button is offered`() {
        composeRule.setContent {
            SideravenTheme { SidebarContent(state = state(AddressBarLayout.Top), onIntent = {}) }
        }
        composeRule.onAllNodesWithTag(SidebarTestTag.LAYOUT_BUTTON).assertCountEquals(1)
    }
}
