/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.ContentAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Domain, security, loading and navigation. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AddressBarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val store = BrowserStore()
    private val calls = mutableListOf<String>()
    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            navigation =
                AddressNavigation(
                    goBack = { calls += "back" },
                    goForward = { calls += "forward" },
                    reload = { calls += "reload" },
                    stop = { calls += "stop" },
                    showHistory = { calls += "history" },
                ),
        )

    private fun open(
        url: String,
        security: SecurityInfo = SecurityInfo.Unknown,
    ) {
        store.dispatch(TabListAction.AddTabAction(createTab(url, id = "t"), select = true))
        store.dispatch(ContentAction.UpdateSecurityInfoAction("t", security))
    }

    private fun state(screen: SidebarScreen = SidebarScreen.Browser) =
        store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = screen, addressBarLayout = AddressBarLayout.Sidebar)

    @Test
    fun `THEN the bar shows the domain without www, and the connection's security`() {
        open("https://www.github.com/mozilla/gecko", SecurityInfo.Secure(host = "github.com"))
        assertEquals("github.com", state().address!!.domain)
        assertEquals(AddressSecurity.Secure, state().address!!.security)
    }

    @Test
    fun `THEN a plain http page reads as not secure, and an about page shows its URL with no security`() {
        open("http://example.com/")
        assertEquals(AddressSecurity.Insecure, state().address!!.security)

        store.dispatch(ContentAction.UpdateUrlAction("t", "about:addons"))
        assertEquals("about:addons", state().address!!.domain)
        assertEquals(AddressSecurity.None, state().address!!.security)
    }

    @Test
    fun `GIVEN the home screen THEN the bar shows its placeholder`() {
        open("https://example.com")
        assertTrue(state(SidebarScreen.Home).address!!.isHome)
    }

    @Test
    fun `THEN back and forward only act when the page can go that way, and reload becomes stop while loading`() {
        open("https://example.com")
        handler.handle(SidebarIntent.GoBack, state())
        assertTrue(calls.isEmpty())

        store.dispatch(ContentAction.UpdateBackNavigationStateAction("t", true))
        store.dispatch(ContentAction.UpdateLoadingStateAction("t", true))
        handler.handle(SidebarIntent.GoBack, state())
        handler.handle(SidebarIntent.StopLoading, state())

        assertEquals(listOf("back", "stop"), calls)
        assertTrue(state().address!!.loading)
        assertFalse(state().address!!.canGoForward)
    }

    @Test
    fun `WHEN back is long-pressed THEN the tab's history opens, and buttons emit their intents`() {
        val intents = mutableListOf<SidebarIntent>()
        val address = SidebarAddress("example.com", AddressSecurity.Secure, loading = false, progress = 0, canGoBack = true, canGoForward = false, isHome = false)
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, address = address),
                    onIntent = { intents += it },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_BACK).performTouchInput { longClick() }
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_RELOAD).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_FORWARD).assertIsNotEnabled()

        assertEquals(listOf(SidebarIntent.ShowTabHistory, SidebarIntent.Reload), intents)
    }

    @Test
    fun `GIVEN Collapsed Mode THEN there is no address bar`() {
        val address = SidebarAddress("example.com", AddressSecurity.Secure, false, 0, false, false, false)
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Collapsed, address = address),
                    onIntent = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(SidebarTestTag.ADDRESS_BAR).assertCountEquals(0)
    }
}
