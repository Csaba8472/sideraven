/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarInactiveSection
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 14, seam 2: the Inactive Tabs Section. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class InactiveTabsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(
        mode: SidebarMode,
        expanded: Boolean,
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("a", "Active", "https://a.example", icon = null, isSelected = true)),
                mode = mode,
                inactive =
                    SidebarInactiveSection(
                        tabs = listOf(SidebarTab("o", "Old", "https://o.example", icon = null, isSelected = false)),
                        expanded = expanded,
                    ),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `GIVEN a collapsed section THEN only its header shows, and tapping it opens the section`() {
        render(SidebarMode.Persistent, expanded = false)

        composeRule.onAllNodesWithContentDescription("Old").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Inactive (1)").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleInactiveSection), intents)
    }

    @Test
    fun `GIVEN an expanded section THEN its tabs show and can be selected`() {
        render(SidebarMode.Persistent, expanded = true)

        composeRule.onNodeWithContentDescription("Old").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectTab("o")), intents)
    }

    @Test
    fun `WHEN Close all inactive is tapped THEN a close-all intent is emitted`() {
        render(SidebarMode.Persistent, expanded = false)

        composeRule.onNodeWithTag(SidebarTestTag.INACTIVE_CLOSE_ALL).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CloseAllInactive), intents)
    }

    @Test
    fun `GIVEN Collapsed Mode THEN the section is an icon that reveals the full Sidebar`() {
        render(SidebarMode.Collapsed, expanded = false)

        composeRule.onNodeWithContentDescription("Show 1 inactive tabs").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.RevealInactiveSection), intents)
    }
}
