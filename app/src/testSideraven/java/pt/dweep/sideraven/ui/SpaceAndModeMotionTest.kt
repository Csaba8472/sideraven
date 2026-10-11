/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme
import kotlin.math.abs

/** Spec 0008 ticket 10 (audit 5 and 8): Space switches and mode changes without double exposure or jumps. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SpaceAndModeMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(name: String) = SidebarTab(name, name, "https://$name.example", icon = null, isSelected = false)

    @Test
    fun `WHEN the Space changes THEN the old Space's tabs are gone at once, never shown with the new ones`() {
        var home by mutableStateOf(true)
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = if (home) listOf(tab("Old one"), tab("Old two")) else listOf(tab("New one")),
                            mode = SidebarMode.Persistent,
                            spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = home), SidebarSpace("w", "Work", "💼", "ember", isCurrent = !home)),
                        ),
                    onIntent = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        home = false
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(32)

        composeRule.onAllNodesWithContentDescription("Old one").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("New one").assertCountEquals(1)
    }

    @Test
    fun `WHEN the Sidebar collapses THEN the Tab List glides to its new place instead of jumping`() {
        var mode by mutableStateOf(SidebarMode.Persistent)
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = listOf(tab("First")),
                            mode = mode,
                            essentials = (1..6).map { SidebarEssential("https://e$it.example", "E$it", icon = null, linkedTabId = null, isSelected = false) },
                        ),
                    onIntent = {},
                )
            }
        }
        composeRule.waitForIdle()
        fun top() = composeRule.onNodeWithContentDescription("First").fetchSemanticsNode().boundsInRoot.top
        val before = top()
        composeRule.mainClock.autoAdvance = false

        mode = SidebarMode.Collapsed
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(32)
        val soon = top()
        composeRule.mainClock.advanceTimeBy(1_000)
        val after = top()

        assertTrue("before $before soon $soon after $after", abs(after - before) > 20f)
        assertTrue("before $before soon $soon after $after", abs(soon - before) < abs(after - before) / 2)
    }
}
