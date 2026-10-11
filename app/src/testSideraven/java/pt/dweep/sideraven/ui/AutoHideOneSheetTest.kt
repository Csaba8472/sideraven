/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0008 ticket 06 (audit 1): the Auto-Hide Sidebar is kept, not rebuilt, between reveals. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AutoHideOneSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(revealed: Boolean) =
        SidebarUiState(
            isVisible = true,
            isPrivate = false,
            tabs = (1..40).map { SidebarTab("$it", "Tab $it", "https://$it.example", icon = null, isSelected = it == 1) },
            mode = SidebarMode.AutoHide,
            isRevealed = revealed,
        )

    @Test
    fun `WHEN the Sidebar is hidden and revealed again THEN it is where it was, scrolled where it was`() {
        var revealed by mutableStateOf(true)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state(revealed), onIntent = {}) } }
        composeRule.onNodeWithTag(SidebarTestTag.TAB_LIST).performScrollToIndex(30)
        composeRule.waitForIdle()

        revealed = false
        composeRule.waitForIdle()
        revealed = true
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Tab 30").assertIsDisplayed()
    }

    @Test
    fun `GIVEN the Sidebar hidden THEN none of it is shown or announced`() {
        composeRule.setContent { SideravenTheme { SidebarContent(state = state(revealed = false), onIntent = {}) } }
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(SidebarTestTag.OVERLAY_PANEL).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.SCRIM).assertCountEquals(0)
    }
}
