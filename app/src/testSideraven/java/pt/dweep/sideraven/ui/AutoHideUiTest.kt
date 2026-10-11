/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 06, seam 2: the Auto-Hide overlay. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AutoHideUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val overlayActive = mutableListOf<Boolean>()

    private fun render(isRevealed: Boolean) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                mode = SidebarMode.AutoHide,
                nextMode = SidebarMode.Persistent,
                isRevealed = isRevealed,
            )
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = state,
                    onIntent = { intents += it },
                    onOverlayActiveChanged = { overlayActive += it },
                )
            }
        }
    }

    @Test
    fun `GIVEN Auto-Hide and not revealed THEN nothing covers the content`() {
        render(isRevealed = false)

        composeRule.onAllNodesWithTag(SidebarTestTag.SCRIM).assertCountEquals(0)
        composeRule.onAllNodesWithTag(SidebarTestTag.ROOT).assertCountEquals(0)
    }

    @Test
    fun `GIVEN revealed THEN the full Sidebar shows over a Scrim and the host is told to cover the content`() {
        render(isRevealed = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL).assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.SCRIM).assertExists()
        assertTrue(overlayActive.contains(true))
    }

    @Test
    fun `WHEN the Scrim is tapped THEN a dismiss intent is emitted`() {
        render(isRevealed = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SidebarTestTag.SCRIM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Dismiss), intents)
    }

    @Test
    fun `WHEN a tab in the revealed Sidebar is tapped THEN it is selected`() {
        render(isRevealed = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("One").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectTab("1")), intents)
    }
}
