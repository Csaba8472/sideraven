/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Rename in a Pinned Tab's menu. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class RenameUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 1)),
                pinned = listOf(SidebarPinnedTab("p1", "Untitled document", "https://docs.example", icon = null, linkedTabId = null, isSelected = false)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a Pinned Tab is renamed THEN the new name is sent`() {
        render()

        composeRule.onNodeWithContentDescription("Untitled document").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.RENAME_FIELD).performTextReplacement("Notes")
        composeRule.onNodeWithTag(SidebarTestTag.RENAME_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.RenamePinnedTab("p1", "Notes")), intents)
    }

    @Test
    fun `WHEN the name is emptied THEN the custom name is cleared`() {
        render()

        composeRule.onNodeWithContentDescription("Untitled document").performTouchInput { longClick() }
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.RENAME_FIELD).performTextReplacement("")
        composeRule.onNodeWithTag(SidebarTestTag.RENAME_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.RenamePinnedTab("p1", null)), intents)
    }
}
