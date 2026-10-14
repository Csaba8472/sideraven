/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Drag targets tick only when they would take the drop. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DragHapticsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ticks = mutableListOf<HapticFeedbackType>()

    private fun dragOverEssentials(pinBlock: PinResult?) {
        val fake =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    ticks += hapticFeedbackType
                }
            }
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = (1..3).map { SidebarTab("t$it", "Tab $it", "https://$it.example", icon = null, isSelected = it == 1) },
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", accent = "", isCurrent = true)),
                pinBlock = pinBlock,
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides fake) { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        }
        val row = composeRule.onNodeWithContentDescription("Tab 1").getBoundsInRoot()
        composeRule.onNodeWithContentDescription("Tab 1").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) {
                moveBy(Offset(0f, 0.5f))
                advanceEventTime(16)
            }
        }
        val target = composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS_TARGET).getBoundsInRoot()
        val dy = (target.top + target.bottom) / 2 - (row.top + row.bottom) / 2
        composeRule.onNodeWithContentDescription("Tab 1").performTouchInput {
            repeat(30) {
                moveBy(Offset(0f, dy.toPx() / 30))
                advanceEventTime(16)
            }
        }
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.onNodeWithContentDescription("Tab 1").performTouchInput { up() }
        composeRule.waitForIdle()
    }

    @Test
    fun `GIVEN room in Essentials THEN entering it ticks`() {
        dragOverEssentials(pinBlock = null)
        assertEquals(true, HapticFeedbackType.SegmentTick in ticks)
    }

    @Test
    fun `GIVEN a full Essentials Section THEN entering it doesn't tick, and the drop is refused with a reject`() {
        dragOverEssentials(pinBlock = PinResult.Full)
        assertEquals(false, HapticFeedbackType.SegmentTick in ticks)
        assertEquals(true, HapticFeedbackType.Reject in ticks)
    }
}
