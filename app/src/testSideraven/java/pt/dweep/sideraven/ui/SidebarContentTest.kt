/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Seam 2: the Sidebar UI renders a fixed state and emits the right intents. */
@RunWith(AndroidJUnit4::class)
class SidebarContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state =
        SidebarUiState(
            isVisible = true,
            isPrivate = false,
            tabs =
                listOf(
                    SidebarTab(id = "3", title = "Three", url = "https://three.example", icon = null, isSelected = false),
                    SidebarTab(id = "2", title = "Two", url = "https://two.example", icon = null, isSelected = true),
                ),
        )

    private fun render(
        state: SidebarUiState,
        intents: MutableList<SidebarIntent>,
    ) {
        composeRule.setContent {
            SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) }
        }
    }

    @Test
    fun `WHEN a tab is tapped THEN a select intent is emitted for it`() {
        val intents = mutableListOf<SidebarIntent>()
        render(state, intents)

        composeRule.onNodeWithContentDescription("Three").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectTab("3")), intents)
    }

    @Test
    fun `THEN the selected tab is announced as selected`() {
        render(state, mutableListOf())

        composeRule.onNodeWithContentDescription("Two").assertIsSelected()
        composeRule.onAllNodesWithTag(SidebarTestTag.TAB_ITEM).assertCountEquals(2)
    }

    @Test
    fun `WHEN New Tab is tapped THEN a new tab intent is emitted`() {
        val intents = mutableListOf<SidebarIntent>()
        render(state, intents)

        composeRule.onNodeWithTag(SidebarTestTag.NEW_TAB).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.NewTab), intents)
    }

    @Test
    fun `GIVEN a hidden state THEN nothing is rendered`() {
        render(SidebarUiState.Hidden, mutableListOf())

        composeRule.onAllNodesWithTag(SidebarTestTag.ROOT).assertCountEquals(0)
    }
}
