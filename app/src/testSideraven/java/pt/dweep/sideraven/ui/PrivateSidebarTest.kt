/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarScreen
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.toSidebarUiState

/** Polish round 2 ticket 06: the private Sidebar is a title, the private tabs and the controls. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PrivateSidebarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tabs() = (1..3).map { SidebarTab("t$it", "Tab $it", "https://$it.example", icon = null, isSelected = it == 1) }

    private fun show(private: Boolean) {
        val state = SidebarUiState(isVisible = true, isPrivate = private, tabs = tabs(), mode = SidebarMode.Persistent)
        composeRule.setContent { SideravenTheme(isPrivate = private) { SidebarContent(state = state, onIntent = {}) } }
    }

    @Test
    fun `GIVEN Essentials WHEN the Sidebar is private THEN it has none`() {
        val browser = BrowserState(tabs = listOf(createTab("https://p.example", id = "p", private = true)), selectedTabId = "p")
        fun essentials(private: Boolean) =
            browser.toSidebarUiState(
                isPrivate = private,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                essentials = listOf(Essential("https://e.example", "E")),
            ).essentials.size

        assertEquals(1, essentials(private = false))
        assertEquals(0, essentials(private = true))
    }

    @Test
    fun `THEN the private Sidebar has a Private browsing title, and the normal one doesn't`() {
        show(private = true)

        composeRule.onAllNodesWithText("Private browsing").assertCountEquals(1)
    }

    @Test
    fun `THEN the normal Sidebar has no private title`() {
        show(private = false)

        composeRule.onAllNodesWithText("Private browsing").assertCountEquals(0)
    }

    @Test
    fun `WHEN a private tab is dragged THEN no Essentials or Space target appears`() {
        show(private = true)

        composeRule.onNodeWithContentDescription("Tab 2").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) {
                moveBy(Offset(0f, -4f))
                advanceEventTime(16)
            }
        }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(SidebarTestTag.ESSENTIALS_TARGET).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.PIN_TARGET).assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Tab 2").performTouchInput { up() }
    }
}
