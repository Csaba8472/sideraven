/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
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

/** Swiping in from the Swipe Zone reveals the Sidebar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SwipeZoneUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(showsSwipeZone: Boolean) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.AutoHide,
                showsSwipeZone = showsSwipeZone,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `WHEN swiping right from the Swipe Zone THEN the Sidebar is revealed`() {
        render(showsSwipeZone = true)

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput { swipeRight(startX = 1f, endX = 400f) }

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Reveal), intents)
    }

    @Test
    fun `GIVEN the Swipe Zone is off THEN there is no strip`() {
        render(showsSwipeZone = false)

        composeRule.onAllNodesWithTag(SidebarTestTag.SWIPE_ZONE).assertCountEquals(0)
    }
}
