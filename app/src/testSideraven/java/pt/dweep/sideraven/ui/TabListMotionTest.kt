/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** Spec 0008 ticket 08 (audit 3): the Tab List follows the Animations setting. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class TabListMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun tab(n: Int) = SidebarTab("$n", "Tab $n", "https://$n.example", icon = null, isSelected = n == 1)

    /** Where the last tab is shortly after the first is closed, and once settled. */
    private fun lastRowAfterClose(level: AnimationLevel): Pair<Float, Float> {
        var tabs by mutableStateOf((1..4).map(::tab))
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent, look = SidebarLook(animations = level)),
                    onIntent = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        tabs = tabs.drop(1)
        Snapshot.sendApplyNotifications()
        fun top() = composeRule.onNodeWithContentDescription("Tab 4").fetchSemanticsNode().boundsInRoot.top
        composeRule.mainClock.advanceTimeBy(32)
        val soon = top()
        composeRule.mainClock.advanceTimeBy(1_000)
        return soon to top()
    }

    @Test
    fun `GIVEN no animations WHEN a tab closes THEN the rows below are in place at once`() {
        val (soon, settled) = lastRowAfterClose(AnimationLevel.Off)
        assertEquals(settled, soon, 0.5f)
    }

    @Test
    fun `GIVEN reduced animations WHEN a tab closes THEN the rows below don't slide`() {
        val (soon, settled) = lastRowAfterClose(AnimationLevel.Reduced)
        assertEquals(settled, soon, 0.5f)
    }

    @Test
    fun `GIVEN full animations WHEN a tab closes THEN the rows below glide up`() {
        val (soon, settled) = lastRowAfterClose(AnimationLevel.Full)
        assertTrue("$soon vs $settled", soon > settled + 0.5f)
    }
}
