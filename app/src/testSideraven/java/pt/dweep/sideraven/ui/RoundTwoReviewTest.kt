/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.SidebarWidth
import pt.dweep.sideraven.theme.SideravenTheme

/** Review fixes in the Sidebar's UI. */
@RunWith(AndroidJUnit4::class)
class RoundTwoReviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tabs() = (1..2).map { SidebarTab("t$it", "Tab $it", "https://$it.example", icon = null, isSelected = it == 1) }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun `GIVEN private browsing WHEN a tab's menu opens THEN it has no Pin to Essentials`() {
        val state = SidebarUiState(isVisible = true, isPrivate = true, tabs = tabs(), mode = SidebarMode.Persistent, pinBlock = PinResult.PrivateRefused)
        composeRule.setContent { SideravenTheme(isPrivate = true) { SidebarContent(state = state, onIntent = {}) } }

        composeRule.onNodeWithContentDescription("Tab 2").performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(SidebarTestTag.CONTEXT_MENU).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SidebarTestTag.PIN_ITEM).assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "w360dp-h740dp")
    fun `GIVEN a narrow phone and the Wide setting WHEN the Sidebar is out THEN it leaves the page a margin`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs(), mode = SidebarMode.AutoHide, isRevealed = true, width = SidebarWidth.Wide)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        composeRule.waitForIdle()

        val panel = composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL).getBoundsInRoot()
        assertTrue("panel ${panel.right - panel.left} on a 360dp screen", panel.right - panel.left <= 360.dp - 56.dp)
    }
}
