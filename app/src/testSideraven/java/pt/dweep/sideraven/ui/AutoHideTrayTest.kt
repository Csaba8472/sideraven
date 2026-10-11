/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Polish round 2: the Auto-Hide Sidebar comes out like a tray, one rigid piece with its handle. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AutoHideTrayTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val ticks = mutableListOf<HapticFeedbackType>()
    private var state by mutableStateOf(SidebarUiState(isVisible = false, isPrivate = false, tabs = emptyList()))

    private fun show(
        side: SidebarSide = SidebarSide.Left,
        flap: Boolean = false,
        revealed: Boolean = false,
        flapPosition: Float = 0.5f,
    ) {
        val fake =
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                    ticks += hapticFeedbackType
                }
            }
        state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                mode = SidebarMode.AutoHide,
                isRevealed = revealed,
                showsSwipeZone = !revealed,
                showsFlap = flap,
                flapPosition = flapPosition,
                side = side,
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides fake) {
                SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rowWidth() = composeRule.onNodeWithTag(SidebarTestTag.TAB_ITEM, useUnmergedTree = true).fetchSemanticsNode().size.width

    private fun panel() = composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun flap() = composeRule.onNodeWithTag(SidebarTestTag.FLAP).getBoundsInRoot()

    private fun pulledRowWidthMatchesOpen(side: SidebarSide) {
        show(side)
        val inward = if (side == SidebarSide.Left) 1f else -1f

        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput {
            down(if (side == SidebarSide.Left) centerLeft + Offset(2f, 0f) else centerRight - Offset(2f, 0f))
            repeat(6) { moveBy(Offset(10f * inward, 0f), delayMillis = 50) }
        }
        composeRule.waitForIdle()
        val pulled = rowWidth()
        composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).performTouchInput { up() }
        composeRule.waitForIdle()

        state = state.copy(isRevealed = true, showsSwipeZone = false)
        composeRule.waitForIdle()

        assertEquals("a tab row keeps its width while the Sidebar is part way out", rowWidth(), pulled)
    }

    @Test
    fun `WHEN the Sidebar on the left is part way out THEN its rows are as wide as when it is open`() = pulledRowWidthMatchesOpen(SidebarSide.Left)

    @Test
    fun `WHEN the Sidebar on the right is part way out THEN its rows are as wide as when it is open`() = pulledRowWidthMatchesOpen(SidebarSide.Right)

    @Test
    fun `WHEN the Flap is pulled THEN it comes out on the Sidebar's outer edge`() {
        show(flap = true)
        val width = flap().let { it.right - it.left }

        composeRule.onNodeWithTag(SidebarTestTag.FLAP).performTouchInput {
            down(centerLeft + Offset(2f, 0f))
            repeat(6) { moveBy(Offset(10f, 0f), delayMillis = 50) }
        }
        composeRule.waitForIdle()

        assertEquals(panel().right, flap().left.value * composeRule.density.density, 1.5f)
        assertEquals("the Flap keeps its size", width, flap().let { it.right - it.left })
    }

    @Test
    fun `GIVEN the Sidebar is open THEN the Flap sits on its outer edge, and tapping it closes the Sidebar`() {
        show(flap = true, revealed = true)

        assertEquals(panel().right, flap().left.value * composeRule.density.density, 1.5f)

        composeRule.onNodeWithTag(SidebarTestTag.FLAP).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.Dismiss), intents)
    }

    @Test
    fun `GIVEN the Sidebar on the right is open THEN the Flap sits on its outer edge`() {
        show(side = SidebarSide.Right, flap = true, revealed = true)

        assertEquals(panel().left, flap().right.value * composeRule.density.density, 1.5f)
    }

    @Test
    fun `WHEN the Flap is held and dragged down THEN it moves with the finger, ticks, and remembers where it was left`() {
        show(flap = true)
        val before = flap()

        composeRule.onNodeWithTag(SidebarTestTag.FLAP).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(20) {
                moveBy(Offset(0f, 10f))
                advanceEventTime(16)
            }
        }
        composeRule.waitForIdle()
        val during = flap()
        composeRule.onNodeWithTag(SidebarTestTag.FLAP).performTouchInput { up() }
        composeRule.waitForIdle()

        assertTrue("moved down: ${before.top} -> ${during.top}", during.top > before.top + 20.dp)
        assertEquals("keeps its size", before.bottom - before.top, during.bottom - during.top)
        assertEquals(HapticFeedbackType.LongPress, ticks.first())
        val moved = intents.filterIsInstance<SidebarIntent.MoveFlap>().single()
        assertTrue("further down than the middle: ${moved.position}", moved.position > 0.5f)
        assertEquals("nothing opened", emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.Reveal>())
    }

    @Test
    fun `WHEN the Flap is held and let go without moving THEN the Sidebar doesn't open`() {
        show(flap = true)

        composeRule.onNodeWithTag(SidebarTestTag.FLAP).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.Reveal>())
    }

    @Test
    fun `GIVEN the Flap moved to the top THEN it and the Swipe Zone sit at the top`() {
        show(flap = true, flapPosition = 0f)

        val zone = composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).getBoundsInRoot()
        assertTrue("zone top ${zone.top}", zone.top < 1.dp)
        val flapCentre = (flap().top + flap().bottom) / 2
        assertEquals((zone.top + zone.bottom).value / 2, flapCentre.value, 1f)
    }
}
