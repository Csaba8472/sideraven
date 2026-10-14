/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertIsNotEnabled
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
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** A Space's long-press menu and the Edit Space dialog. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SpaceEditingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(spaces: List<SidebarSpace>) {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, spaces = spaces)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    private val two =
        listOf(
            SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true),
            SidebarSpace("w", "Work", "💼", "blue", isCurrent = false),
        )

    @Test
    fun `WHEN Edit is chosen from a Space's menu THEN the changes are sent`() {
        render(two)

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit space").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_NAME_FIELD).performTextReplacement("Office")
        composeRule.onNodeWithContentDescription("Teal").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.EditSpace("w", "Office", "💼", "teal")), intents)
    }

    @Test
    fun `WHEN Delete is chosen from a Space's menu THEN the Space is deleted`() {
        render(two)

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete space").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.DeleteSpace("w")), intents)
    }

    @Test
    fun `GIVEN only one Space THEN Delete is unavailable`() {
        render(two.take(1))

        composeRule.onNodeWithContentDescription("Home").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete space").assertIsNotEnabled()
    }
}
