/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Swipes on the Sidebar Address Bar: up for a new tab, sideways to the neighbouring tab. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AddressSwipeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun show(look: SidebarLook = SidebarLook()) {
        val address = SidebarAddress("example.com", AddressSecurity.Secure, false, 0, canGoBack = true, canGoForward = false, isHome = false)
        val tabs = (1..3).map { SidebarTab("t$it", "Tab $it", "https://t$it.example", icon = null, isSelected = it == 2) }
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent, address = address, look = look),
                    onIntent = { intents += it },
                )
            }
        }
    }

    private fun swipe(gesture: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_BAR).performTouchInput(gesture)
        composeRule.waitForIdle()
    }

    @Test
    fun `WHEN the address bar is swiped up THEN a new tab opens`() {
        show()
        swipe { swipeUp() }
        assertEquals(listOf<SidebarIntent>(SidebarIntent.NewTab), intents)
    }

    @Test
    fun `WHEN the address bar is swiped left or right THEN the next or previous tab is selected`() {
        show()
        swipe { swipeLeft() }
        swipe { swipeRight() }
        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectTab("t3"), SidebarIntent.SelectTab("t1")), intents)
    }

    @Test
    fun `GIVEN both swipes are turned off THEN swiping does nothing and a tap still opens search`() {
        show(SidebarLook(addressSwipeNewTab = false, addressSwipeTabs = false))
        swipe { swipeUp() }
        swipe { swipeLeft() }
        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_PILL).performClick()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.OpenSearch(newTab = false)), intents)
    }
}
