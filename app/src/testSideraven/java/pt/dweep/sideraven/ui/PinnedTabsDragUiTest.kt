/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Dragging Pinned Tabs. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PinnedTabsDragUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("news", "News", "https://news.example", icon = null, isSelected = false)),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 2)),
                pinned =
                    listOf(
                        SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false),
                        SidebarPinnedTab("p2", "Docs", "https://docs.example", icon = null, linkedTabId = null, isSelected = false),
                    ),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    /** Long-press, then move in small steps (a real drag), then release. */
    private fun dragBy(
        label: String,
        dy: Float,
    ) {
        composeRule.onNodeWithContentDescription(label).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy(Offset(0f, dy / 10)) }
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `WHEN a Pinned Tab is dragged onto the next one THEN it moves there`() {
        render()

        dragBy("Mail", 46f * composeRule.density.density)

        assertEquals(listOf<SidebarIntent>(SidebarIntent.MovePinnedTab("p1", "p2")), intents)
    }

    @Test
    fun `WHEN a Pinned Tab is dragged out below the pinned block THEN it is unpinned`() {
        render()

        dragBy("Docs", 160f * composeRule.density.density)

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnpinPinnedTab("p2")), intents)
    }
}
