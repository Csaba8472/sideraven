/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Device-test feedback: tabs opened after the Sidebar appeared showed no drop targets until a restart. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DragNewTabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN a tab opened after the Sidebar appeared WHEN it is dragged THEN the drop targets appear`() {
        val first = SidebarTab("t1", "Tab one", "https://1.example", icon = null, isSelected = true)
        var tabs by mutableStateOf(listOf(first))
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = tabs,
                            spaces = listOf(SidebarSpace("h", "Home", "🏠", accent = "", isCurrent = true)),
                        ),
                    onIntent = {},
                )
            }
        }
        // A first touch starts the list's gesture handler, as on a device in use.
        composeRule.onNodeWithContentDescription("Tab one").performTouchInput {
            down(center)
            advanceEventTime(50)
            up()
        }
        composeRule.waitForIdle()
        tabs = listOf(SidebarTab("new", "Fresh page", "https://new.example", icon = null, isSelected = false), first)
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Fresh page").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) {
                moveBy(Offset(0f, 1f))
                advanceEventTime(16)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS_TARGET).assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.DRAG_GHOST).assertExists()
    }
}
