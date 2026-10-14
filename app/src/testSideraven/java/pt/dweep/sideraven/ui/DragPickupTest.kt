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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** A dragged tab lifts from where the finger holds it. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DragPickupTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `WHEN a tab is picked up by its left end THEN the floating copy stays under the finger where it was held`() {
        val tabs = (1..3).map { SidebarTab("t$it", "Tab $it", "https://$it.example", icon = null, isSelected = it == 1) }
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent), onIntent = {})
            }
        }
        val row = composeRule.onNodeWithContentDescription("Tab 2").getBoundsInRoot()

        composeRule.onNodeWithContentDescription("Tab 2").performTouchInput {
            down(Offset(30f, height / 2f))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) {
                moveBy(Offset(0f, 0.5f))
                advanceEventTime(16)
            }
        }
        composeRule.mainClock.advanceTimeBy(400)
        val ghost = composeRule.onNodeWithTag(SidebarTestTag.DRAG_GHOST).getBoundsInRoot()

        // Held near its left end, the copy sits where the row was instead of centring on the finger.
        assertEquals(row.left.value, ghost.left.value, 12f)
        composeRule.onNodeWithContentDescription("Tab 2").performTouchInput { up() }
    }
}
