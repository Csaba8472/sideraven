/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarFolder
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedRow
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Spaces ticket 13: Folder rows, their menu, and Move to folder. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class FoldersUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val folder = SidebarFolder("f1", "Work", "💼", depth = 0, expanded = true, count = 1)
    private val mail = SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false)
    private val news = SidebarPinnedTab("p2", "News", "https://news.example", icon = null, linkedTabId = null, isSelected = false)

    private fun render() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 2)),
                pinned = listOf(mail, news),
                folders = listOf(folder),
                pinnedRows = listOf(SidebarPinnedRow.FolderRow(folder), SidebarPinnedRow.PinRow(mail, 1), SidebarPinnedRow.PinRow(news, 0)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a Folder row is tapped THEN it collapses or expands`() {
        render()

        composeRule.onNodeWithContentDescription("Work").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleFolder("f1")), intents)
    }

    @Test
    fun `WHEN a pin is moved into a Folder from its menu THEN the move is sent`() {
        render()

        composeRule.onNodeWithContentDescription("News").performTouchInput { longClick() }
        composeRule.onNodeWithTag(SidebarTestTag.MOVE_TO_FOLDER).performClick()
        composeRule.onAllNodesWithText("Work").onLast().performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.MoveIntoFolder("p2", "f1")), intents)
    }

    @Test
    fun `WHEN a Folder is deleted THEN the user chooses whether its pins stay`() {
        render()

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete folder").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.FOLDER_DELETE_KEEP).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.DeleteFolder("f1", keepPins = true)), intents)
    }

    @Test
    fun `WHEN New folder is chosen from the Space's menu THEN a Folder is created`() {
        render()

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        composeRule.onNodeWithText("New folder").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_NAME_FIELD).performTextInput("Reading")
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CreateFolder("Reading", "📁")), intents)
    }
}
