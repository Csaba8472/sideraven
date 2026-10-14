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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** The Space icon row and the New Space dialog. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SpacesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(
        isPrivate: Boolean = false,
        spaces: List<SidebarSpace> =
            listOf(
                SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true),
                SidebarSpace("w", "Work", "💼", "ember", isCurrent = false),
            ),
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = isPrivate,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = if (isPrivate) emptyList() else spaces,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN a Space icon is tapped THEN that Space is switched to`() {
        render()

        composeRule.onNodeWithContentDescription("Work").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SwitchSpace("w")), intents)
    }

    @Test
    fun `THEN the current Space is marked selected`() {
        render()

        composeRule.onNodeWithContentDescription("Home").assertIsSelected()
    }

    @Test
    fun `WHEN a Space is created from the dialog THEN its name and icon are sent`() {
        render()

        composeRule.onNodeWithTag(SidebarTestTag.NEW_SPACE).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_NAME_FIELD).performTextInput("Reading")
        composeRule.onNodeWithText("📚").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CreateSpace("Reading", "📚")), intents)
    }

    @Test
    fun `GIVEN Private Mode THEN there is no Space row`() {
        render(isPrivate = true)

        composeRule.onAllNodesWithTag(SidebarTestTag.SPACE_ROW).assertCountEquals(0)
    }
}
