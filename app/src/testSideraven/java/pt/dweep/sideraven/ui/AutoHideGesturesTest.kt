/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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

/** The Auto-Hide Sidebar follows the finger. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AutoHideGesturesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun show(
        revealed: Boolean,
        level: pt.dweep.sideraven.AnimationLevel = pt.dweep.sideraven.AnimationLevel.Full,
    ) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                mode = SidebarMode.AutoHide,
                isRevealed = revealed,
                showsSwipeZone = !revealed,
                look = pt.dweep.sideraven.SidebarLook(animations = level),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN the edge is pulled a little THEN the Sidebar comes out with the finger, and goes back when let go`() {
        show(revealed = false)

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput {
            down(centerLeft + Offset(2f, 0f))
            repeat(6) { moveBy(Offset(10f, 0f), delayMillis = 50) }
        }
        val panel = composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("panel right ${panel.right}", panel.right in 30f..120f)

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.Reveal>())
        composeRule.onAllNodesWithTag(SidebarTestTag.OVERLAY_PANEL).assertCountEquals(0)
    }

    @Test
    fun `WHEN the edge is pulled past a third of the Sidebar THEN it opens`() {
        show(revealed = false)

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput {
            down(centerLeft + Offset(2f, 0f))
            repeat(12) { moveBy(Offset(10f, 0f), delayMillis = 50) }
            up()
        }

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Reveal), intents.filterIsInstance<SidebarIntent.Reveal>())
    }

    @Test
    fun `WHEN the open Sidebar is flung toward the edge THEN it closes, even after a short drag`() {
        show(revealed = true)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL).performTouchInput { swipe(start = Offset(centerX, bottom - 4f), end = Offset(centerX - 80f, bottom - 4f), durationMillis = 60) }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Dismiss), intents.filterIsInstance<SidebarIntent.Dismiss>())
    }

    @Test
    fun `GIVEN reduced animations WHEN the edge is pulled THEN nothing slides out, and letting go far enough still opens it`() {
        show(revealed = false, level = pt.dweep.sideraven.AnimationLevel.Reduced)

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput {
            down(centerLeft + Offset(2f, 0f))
            repeat(12) { moveBy(Offset(10f, 0f), delayMillis = 50) }
        }
        composeRule.onAllNodesWithTag(SidebarTestTag.OVERLAY_PANEL).assertCountEquals(0)
        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput { up() }

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Reveal), intents.filterIsInstance<SidebarIntent.Reveal>())
    }
}
