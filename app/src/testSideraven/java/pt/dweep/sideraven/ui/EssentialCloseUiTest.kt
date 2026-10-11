/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Sidebar fixes 01: an Essential Tab closes from its menu in every mode (no Essential Bar). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class EssentialCloseUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun closeFromMenu(mode: SidebarMode) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = mode,
                essentials = listOf(SidebarEssential("https://mail.example", "Mail", icon = null, linkedTabId = "m", isSelected = true)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onAllNodesWithTag(SidebarTestTag.ESSENTIAL).onFirst().performTouchInput { longClick() }
        composeRule.onNodeWithText("Close tab").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseEssentialTab("https://mail.example")), intents)
    }

    @Test
    fun `GIVEN Persistent Mode WHEN Close tab is chosen from an Essential's menu THEN its tab closes`() = closeFromMenu(SidebarMode.Persistent)

    @Test
    fun `GIVEN Collapsed Mode WHEN Close tab is chosen from an Essential's menu THEN its tab closes`() = closeFromMenu(SidebarMode.Collapsed)
}
