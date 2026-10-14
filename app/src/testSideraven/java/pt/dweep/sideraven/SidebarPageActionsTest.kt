/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Fenix's menu and site panel from the Sidebar Address Bar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarPageActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val address = SidebarAddress("example.com", AddressSecurity.Secure, false, 0, canGoBack = true, canGoForward = false, isHome = false)

    @Test
    fun `WHEN the menu button and the lock are tapped THEN Fenix's menu and site panel are asked for`() {
        val intents = mutableListOf<SidebarIntent>()
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, address = address),
                    onIntent = { intents += it },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_MENU).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_SITE_INFO).performClick()

        assertEquals(listOf(SidebarIntent.OpenMenu, SidebarIntent.OpenSitePanel), intents)
    }

    @Test
    fun `THEN the menu opens for the browser or the home screen, and the site panel only for a page`() {
        val calls = mutableListOf<String>()
        val handler =
            SidebarIntentHandler(
                selectTab = {},
                openNewTab = {},
                navigation = AddressNavigation(openMenu = { onHome -> calls += "menu:$onHome" }, openSitePanel = { calls += "site" }),
            )
        val page = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), address = address)
        val home = page.copy(address = address.copy(isHome = true))

        handler.handle(SidebarIntent.OpenMenu, page)
        handler.handle(SidebarIntent.OpenMenu, home)
        handler.handle(SidebarIntent.OpenSitePanel, home)
        handler.handle(SidebarIntent.OpenSitePanel, page)

        assertEquals(listOf("menu:false", "menu:true", "site"), calls)
    }
}
