/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 04, seam 2: every way of closing a tab emits a close intent for that tab. */
@RunWith(AndroidJUnit4::class)
class SidebarCloseUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs =
                    listOf(
                        SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false),
                        SidebarTab("2", "Two", "https://two.example", icon = null, isSelected = true),
                    ),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a tab is swiped left THEN it is closed`() {
        render()

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseTab("3")), intents)
    }

    @Test
    fun `WHEN a tab is swiped right THEN nothing is closed`() {
        render()

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(emptyList<SidebarIntent>(), intents)
    }

    @Test
    fun `THEN only the selected tab shows the Tab Close Button, and it closes that tab`() {
        render()

        composeRule.onAllNodesWithTag(SidebarTestTag.CLOSE_BUTTON).assertCountEquals(1)
        composeRule.onNodeWithTag(SidebarTestTag.CLOSE_BUTTON).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseTab("2")), intents)
    }

    @Test
    fun `THEN every tab exposes a Close tab accessibility action`() {
        render()

        composeRule
            .onNodeWithContentDescription("Three")
            .assert(
                SemanticsMatcher("has Close tab action") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Close tab" }
                },
            )
    }
}
