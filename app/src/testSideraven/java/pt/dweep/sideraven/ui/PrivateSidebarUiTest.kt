/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** The Browsing Mode Switch. */
@RunWith(AndroidJUnit4::class)
class PrivateSidebarUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(isPrivate: Boolean) {
        val state = SidebarUiState(isVisible = true, isPrivate = isPrivate, tabs = emptyList())
        composeRule.setContent {
            SideravenTheme(isPrivate = isPrivate) { SidebarContent(state = state, onIntent = { intents += it }) }
        }
    }

    @Test
    fun `GIVEN Normal Mode THEN the switch offers private browsing`() {
        render(isPrivate = false)

        composeRule.onNodeWithContentDescription("Private browsing").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SwitchBrowsingMode), intents)
    }

    @Test
    fun `GIVEN Private Mode THEN the switch offers leaving private browsing`() {
        render(isPrivate = true)

        composeRule.onNodeWithContentDescription("Leave private browsing").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SwitchBrowsingMode), intents)
    }
}
