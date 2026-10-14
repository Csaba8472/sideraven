/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** "Move to space" in the Tab Context Menu. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class MoveToSpaceUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(spaces: List<SidebarSpace>) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("a", "Alpha", "https://a.example", icon = null, isSelected = true)),
                mode = SidebarMode.Persistent,
                spaces = spaces,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    private fun openMenu() {
        composeRule.onNodeWithTag(SidebarTestTag.TAB_ITEM).performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun `WHEN Move to space is chosen THEN the other Spaces are offered and the tab moves`() {
        render(
            listOf(
                SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true),
                SidebarSpace("w", "Work", "💼", "blue", isCurrent = false),
            ),
        )
        openMenu()

        composeRule.onNodeWithTag(SidebarTestTag.MOVE_TO_SPACE).performClick()
        composeRule.onNodeWithText("Work").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.MoveTabToSpace("a", "w")), intents)
    }

    @Test
    fun `GIVEN a single Space THEN there is no Move to space entry`() {
        render(listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true)))
        openMenu()

        composeRule.onAllNodesWithTag(SidebarTestTag.MOVE_TO_SPACE).assertCountEquals(0)
    }
}
