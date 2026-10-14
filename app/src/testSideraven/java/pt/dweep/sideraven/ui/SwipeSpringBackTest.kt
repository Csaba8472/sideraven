/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
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

/** A swipe let go short of acting always springs back to rest. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SwipeSpringBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun tabs() = listOf(SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false), SidebarTab("2", "Two", "https://two.example", icon = null, isSelected = true))

    @Test
    fun `WHEN a tab is swiped and let go short of closing in the same instant THEN it ends at rest`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs(), mode = SidebarMode.Persistent)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
        val row = composeRule.onNodeWithContentDescription("Three")
        val rest = row.getBoundsInRoot().left

        // Small moves, the last one in the same instant as the lift: its position update is still queued.
        row.performTouchInput {
            down(centerRight - Offset(4f, 0f))
            repeat(8) { moveBy(Offset(-6f, 0f), delayMillis = 16) }
            moveBy(Offset(-6f, 0f), delayMillis = 0)
            up()
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.CloseTab>())
        assertEquals(rest.value, row.getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `WHEN the open tray is dragged and let go short of closing THEN it ends fully open`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs(), mode = SidebarMode.AutoHide, isRevealed = true)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
        composeRule.waitForIdle()
        val panel = composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL)
        val rest = panel.getBoundsInRoot().left

        panel.performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-5f, 0f), delayMillis = 16) }
            moveBy(Offset(-5f, 0f), delayMillis = 0)
            up()
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()

        assertTrue("not dismissed", intents.none { it is SidebarIntent.Dismiss })
        assertEquals(rest.value, panel.getBoundsInRoot().left.value, 0.5f)
        assertTrue(rest < 1.dp)
    }
}
