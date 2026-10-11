/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Polish round 2 ticket 04: a gesture let go short of acting springs back with a little bounce. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SpringBackTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** The row's left edge, frame by frame, from letting go of a swipe a third of the way to closing. */
    private fun releaseAndTrack(level: AnimationLevel): Pair<Float, List<Float>> {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false), SidebarTab("2", "Two", "https://two.example", icon = null, isSelected = true)),
                mode = SidebarMode.Persistent,
                look = SidebarLook(animations = level),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        val row = composeRule.onNodeWithContentDescription("Three")
        val rest = row.getBoundsInRoot().left.value
        row.performTouchInput {
            down(centerRight - Offset(4f, 0f))
            repeat(10) { moveBy(Offset(-7f, 0f), delayMillis = 40) }
            // Hold still before letting go, so the release carries no speed.
            moveBy(Offset(0f, 0f), delayMillis = 200)
        }
        composeRule.mainClock.autoAdvance = false
        row.performTouchInput { up() }
        val frames = List(60) {
            composeRule.mainClock.advanceTimeByFrame()
            row.getBoundsInRoot().left.value
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        return rest to frames + row.getBoundsInRoot().left.value
    }

    @Test
    fun `GIVEN full motion WHEN a swiped tab is let go short of closing THEN it overshoots its place a little and settles there`() {
        val (rest, frames) = releaseAndTrack(AnimationLevel.Full)

        assertTrue("passes its place: ${frames.max()} vs $rest", frames.max() > rest + 0.5f)
        assertTrue("only a little: ${frames.max()} vs $rest", frames.max() < rest + 12f)
        assertEquals(rest, frames.last(), 0.5f)
    }

    @Test
    fun `GIVEN reduced motion WHEN a swiped tab is let go THEN it is back in place at once, no bounce`() {
        val (rest, frames) = releaseAndTrack(AnimationLevel.Reduced)

        assertTrue(frames.all { it <= rest + 0.5f })
        assertEquals(rest, frames.first(), 0.5f)
    }
}
