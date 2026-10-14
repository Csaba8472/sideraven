/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Unload in a normal tab's menu, and sleeping tabs marked. */
@RunWith(AndroidJUnit4::class)
class SleepingUiTest {
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
                        SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true),
                        SidebarTab("2", "Two", "https://two.example", icon = null, isSelected = false),
                        SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false, sleeping = true),
                    ),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN Unload is chosen from a normal tab's menu THEN that tab is Unloaded`() {
        render()

        composeRule.onNodeWithContentDescription("Two").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unload").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnloadTab("2")), intents)
    }

    @Test
    fun `THEN a sleeping tab is announced as asleep`() {
        render()

        composeRule.onNodeWithContentDescription("Three").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Asleep"))
    }
}
