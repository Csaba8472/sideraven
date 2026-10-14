/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Collapsed layout and the Toggle. */
@RunWith(AndroidJUnit4::class)
class SidebarModeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(
        mode: SidebarMode,
        nextMode: SidebarMode?,
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                mode = mode,
                nextMode = nextMode,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `GIVEN Collapsed Mode THEN tabs show favicons only, without titles or the Tab Close Button`() {
        render(SidebarMode.Collapsed, nextMode = SidebarMode.Persistent)

        composeRule.onAllNodesWithText("One").assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.CLOSE_BUTTON).assertCountEquals(0)
        // Still reachable by its accessible name.
        composeRule.onNodeWithContentDescription("One").performClick()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectTab("1")), intents)
    }

    @Test
    fun `WHEN the Toggle is tapped THEN a toggle intent is emitted and its label names the next mode`() {
        render(SidebarMode.Persistent, nextMode = SidebarMode.Collapsed)

        composeRule.onNodeWithContentDescription("Collapse sidebar").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleMode), intents)
    }

    @Test
    fun `GIVEN nothing to cycle THEN there is no Toggle`() {
        render(SidebarMode.Collapsed, nextMode = null)

        composeRule.onAllNodesWithTag(SidebarTestTag.TOGGLE).assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.NEW_TAB).performClick()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.NewTab), intents)
    }
}
