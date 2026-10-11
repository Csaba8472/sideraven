/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Device-test feedback: one drag model for tabs, Essentials and Pinned Tabs. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DragLayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private val zones =
        mapOf(
            DragZone.Essentials to Rect(0f, 0f, 100f, 50f),
            DragZone.Pin to Rect(0f, 60f, 100f, 100f),
            DragZone.Unpin to Rect(0f, 120f, 100f, 400f),
        )

    @Test
    fun `THEN each source drops only on the zones it can become`() {
        assertEquals(DragZone.Essentials, hoveredZone(DragSource.Tab, Offset(10f, 10f), zones))
        assertEquals(DragZone.Pin, hoveredZone(DragSource.Tab, Offset(10f, 80f), zones))
        // A tab over the Tab List reorders.
        assertNull(hoveredZone(DragSource.Tab, Offset(10f, 200f), zones))
        // An Essential over Essentials reorders; over the Tab List it unpins.
        assertNull(hoveredZone(DragSource.Essential, Offset(10f, 10f), zones))
        assertEquals(DragZone.Unpin, hoveredZone(DragSource.Essential, Offset(10f, 200f), zones))
        // A Pinned Tab over the pinned block reorders.
        assertNull(hoveredZone(DragSource.Pinned, Offset(10f, 80f), zones))
        assertEquals(DragZone.Essentials, hoveredZone(DragSource.Pinned, Offset(10f, 10f), zones))
    }

    private fun render(pinned: List<SidebarPinnedTab> = emptyList()) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs =
                    listOf(
                        SidebarTab("t1", "Tab one", "https://1.example", icon = null, isSelected = true),
                        SidebarTab("t2", "Tab two", "https://2.example", icon = null, isSelected = false),
                        SidebarTab("t3", "Tab three", "https://3.example", icon = null, isSelected = false),
                    ),
                spaces = listOf(SidebarSpace("h", "Home", "🏠", accent = "", isCurrent = true)),
                pinned = pinned,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    private fun TouchInjectionScope.longPressAndMove(dy: Float) {
        down(center)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        val steps = maxOf(20, kotlin.math.abs(dy).toInt() / 5)
        repeat(steps) {
            moveBy(Offset(0f, dy / steps))
            advanceEventTime(16)
        }
    }

    @Test
    fun `WHEN a tab drag starts THEN nothing moves, and the targets and the floating copy appear on top`() {
        render()
        val before = composeRule.onNodeWithContentDescription("Tab two").getBoundsInRoot().top

        composeRule.onNodeWithContentDescription("Tab three").performTouchInput { longPressAndMove(dy = 4f) }
        composeRule.waitForIdle()

        assertEquals(before, composeRule.onNodeWithContentDescription("Tab two").getBoundsInRoot().top)
        composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS_TARGET).assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.PIN_TARGET).assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.DRAG_GHOST).assertExists()
        composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS_TARGET).performTouchInput { up() }
    }

    @Test
    fun `WHEN a tab is dropped on the Essentials target THEN it is pinned, and the copy became a tile over it`() {
        render()
        val target = composeRule.onNodeWithContentDescription("Tab one").getBoundsInRoot()

        composeRule.onNodeWithContentDescription("Tab one").performTouchInput { longPressAndMove(dy = 4f) }
        val essentials = composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS_TARGET).getBoundsInRoot()
        val dy = (essentials.top + essentials.bottom) / 2 - (target.top + target.bottom) / 2
        composeRule.onNodeWithContentDescription("Tab one").performTouchInput {
            val steps = 30
            repeat(steps) {
                moveBy(Offset(0f, dy.toPx() / steps))
                advanceEventTime(16)
            }
        }
        composeRule.mainClock.advanceTimeBy(400)
        val ghost = composeRule.onNodeWithTag(SidebarTestTag.DRAG_GHOST).getBoundsInRoot()
        assertTrue("ghost ${ghost.right - ghost.left} wide", ghost.right - ghost.left < 100.dp)
        composeRule.onNodeWithContentDescription("Tab one").performTouchInput { up() }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.PinTab("t1")), intents.filterIsInstance<SidebarIntent.PinTab>())
        composeRule.onAllNodesWithTag(SidebarTestTag.DRAG_GHOST).assertCountEquals(0)
    }

    @Test
    fun `WHEN a Pinned Tab is dropped on the Tab List THEN it is unpinned, and a short drag within its block is not`() {
        render(
            pinned =
                listOf(
                    SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false),
                    SidebarPinnedTab("p2", "Docs", "https://docs.example", icon = null, linkedTabId = null, isSelected = false),
                ),
        )

        composeRule.onNodeWithContentDescription("Mail", substring = true).performTouchInput {
            longPressAndMove(dy = 10f)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.UnpinPinnedTab>())

        composeRule.onNodeWithContentDescription("Mail", substring = true).performTouchInput {
            longPressAndMove(dy = 400f)
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnpinPinnedTab("p1")), intents.filterIsInstance<SidebarIntent.UnpinPinnedTab>())
    }

}
