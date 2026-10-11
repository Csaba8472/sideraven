/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarScreen
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.toSidebarUiState

/** The Sidebar Flap: a visible, tappable handle for the Auto-Hide Sidebar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarFlapUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(showsFlap: Boolean) {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.AutoHide, showsFlap = showsFlap)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN the Flap is tapped THEN the Sidebar is revealed`() {
        render(showsFlap = true)

        composeRule.onNodeWithContentDescription("Open sidebar").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Reveal), intents)
    }

    @Test
    fun `GIVEN the Flap is off THEN it isn't drawn`() {
        render(showsFlap = false)

        composeRule.onAllNodesWithTag(SidebarTestTag.FLAP).assertCountEquals(0)
    }

    @Test
    fun `THEN the Flap shows only in Auto-Hide, hidden or open, and when enabled`() {
        fun flap(
            mode: SidebarMode,
            revealed: Boolean = false,
            enabled: Boolean = true,
        ) = BrowserState()
            .toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                chosenMode = mode,
                isRevealed = revealed,
                flapEnabled = enabled,
            ).showsFlap

        assertTrue(flap(SidebarMode.AutoHide))
        assertTrue(flap(SidebarMode.AutoHide, revealed = true))
        assertFalse(flap(SidebarMode.AutoHide, enabled = false))
        assertFalse(flap(SidebarMode.Persistent))
        assertFalse(flap(SidebarMode.Collapsed))
    }
}
