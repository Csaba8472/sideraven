/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Spaces ticket 06: Collapsed Mode shows one Space button that opens a menu of Spaces. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class CollapsedSpacesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    @Test
    fun `GIVEN Collapsed Mode WHEN a Space is chosen from the Space menu THEN it is switched to`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Collapsed,
                spaces =
                    listOf(
                        SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true),
                        SidebarSpace("w", "Work", "💼", "blue", isCurrent = false),
                    ),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onAllNodesWithTag(SidebarTestTag.SPACE_ROW).assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_SWITCHER).performClick()
        composeRule.onNodeWithText("Work").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SwitchSpace("w")), intents)
    }
}
