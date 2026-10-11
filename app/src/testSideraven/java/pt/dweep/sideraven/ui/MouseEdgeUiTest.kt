/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 16, seam 2: the mouse edge reveal. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class MouseEdgeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(mode: SidebarMode) {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = mode)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `GIVEN Auto-Hide WHEN the mouse rests at the left edge THEN the Sidebar reveals`() {
        render(SidebarMode.AutoHide)

        composeRule.onNodeWithTag(SidebarTestTag.MOUSE_EDGE).performMouseInput { enter(center) }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Reveal), intents)
    }

    @Test
    fun `GIVEN a docked mode THEN there is no mouse edge zone`() {
        render(SidebarMode.Persistent)

        composeRule.onAllNodesWithTag(SidebarTestTag.MOUSE_EDGE).assertCountEquals(0)
    }
}
