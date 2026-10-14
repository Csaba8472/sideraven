/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Swipe to close shows the tab closing. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SwipeToCloseMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()
    private val ticks = mutableListOf<HapticFeedbackType>()

    private fun show(
        level: AnimationLevel = AnimationLevel.Full,
        side: SidebarSide = SidebarSide.Left,
    ) {
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
                tabs = listOf(SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false), SidebarTab("2", "Two", "https://two.example", icon = null, isSelected = true)),
                mode = SidebarMode.Persistent,
                side = side,
                look = SidebarLook(animations = level),
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides fake) {
                SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) }
            }
        }
    }

    private fun closes() = intents.filterIsInstance<SidebarIntent.CloseTab>()

    @Test
    fun `WHEN a tab is swiped less than the threshold THEN it springs back, still open`() {
        show()

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft(startX = right - 2f, endX = right - 2f - width * 0.3f) }
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(emptyList<SidebarIntent.CloseTab>(), closes())
    }

    @Test
    fun `WHEN a tab is swiped past the threshold THEN one tick marks it, and it closes once it has folded away`() {
        show()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft(startX = right - 2f, endX = left + 2f, durationMillis = 200) }
        composeRule.mainClock.advanceTimeBy(16)
        assertEquals(emptyList<SidebarIntent.CloseTab>(), closes())
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(listOf(SidebarIntent.CloseTab("3")), closes())
        assertEquals(1, ticks.count { it == HapticFeedbackType.GestureThresholdActivate })
    }

    @Test
    fun `GIVEN no animations WHEN a tab is swiped away THEN it closes at once`() {
        show(level = AnimationLevel.Off)
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft(startX = right - 2f, endX = left + 2f, durationMillis = 200) }
        composeRule.mainClock.advanceTimeBy(16)

        assertEquals(listOf(SidebarIntent.CloseTab("3")), closes())
    }

    @Test
    fun `GIVEN the Sidebar on the right THEN tabs close toward the right edge, and a left swipe doesn't close`() {
        show(side = SidebarSide.Right)

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft(startX = right - 2f, endX = left + 2f) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(emptyList<SidebarIntent.CloseTab>(), closes())

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeRight(startX = left + 2f, endX = right - 2f) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf(SidebarIntent.CloseTab("3")), closes())
    }
}
