/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Sidebar Address Bar ticket 02: Reset and Close for Essential Tabs and Pinned Tabs. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AddressBarPinTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val essentials = Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap()))
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))

    private fun select(
        id: String,
        url: String,
    ) = store.dispatch(TabListAction.AddTabAction(createTab(url, id = id), select = true))

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            essentials = essentials.essentials.value,
            essentialLinks = essentials.linkedTabs.value,
            spaces = spaces.data,
            addressBarLayout = AddressBarLayout.Sidebar,
            pinnedLinks = spaces.pinnedLinks.value,
        )

    @Test
    fun `GIVEN an Essential Tab THEN the bar offers Close, and Reset once it navigates away`() {
        select("mail", "https://mail.example")
        essentials.pin(store.state.findTab("mail")!!)
        assertEquals(AddressPin.OfEssential("https://mail.example", navigatedAway = false), state().address!!.pin)

        store.dispatch(ContentAction.UpdateUrlAction("mail", "https://mail.example/inbox"))

        assertEquals(AddressPin.OfEssential("https://mail.example", navigatedAway = true), state().address!!.pin)
    }

    @Test
    fun `GIVEN a Pinned Tab's tab THEN the bar offers its pin controls`() {
        select("docs", "https://docs.example")
        spaces.pinToSpace(store.state.findTab("docs")!!)
        val pinId = spaces.data.pinned.single().id

        assertEquals(AddressPin.OfPinnedTab(pinId, navigatedAway = false), state().address!!.pin)
    }

    @Test
    fun `GIVEN an ordinary tab THEN there are no pin controls`() {
        select("news", "https://news.example")

        assertNull(state().address!!.pin)
    }

    @Test
    fun `WHEN Reset and Close are tapped THEN the pin's own intents are sent`() {
        val intents = mutableListOf<SidebarIntent>()
        val address =
            SidebarAddress("mail.example", AddressSecurity.Secure, false, 0, false, false, false, pin = AddressPin.OfPinnedTab("p1", navigatedAway = true))
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, address = address),
                    onIntent = { intents += it },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_RESET).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_CLOSE).performClick()

        assertEquals(listOf(SidebarIntent.ResetPinnedTab("p1"), SidebarIntent.ClosePinnedTab("p1")), intents)
    }

    @Test
    fun `GIVEN a pin that hasn't navigated away THEN Reset isn't shown`() {
        val address =
            SidebarAddress("mail.example", AddressSecurity.Secure, false, 0, false, false, false, pin = AddressPin.OfEssential("https://mail.example", navigatedAway = false))
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, address = address),
                    onIntent = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(SidebarTestTag.ADDRESS_RESET).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.ADDRESS_CLOSE).assertCountEquals(1)
    }
}
