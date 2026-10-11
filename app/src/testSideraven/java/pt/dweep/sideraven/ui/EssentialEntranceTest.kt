/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0008 ticket 07 (audit 2): only a newly pinned Essential plays its entrance. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class EssentialEntranceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun essential(name: String) = SidebarEssential("https://$name.example", name, icon = null, linkedTabId = null, isSelected = false)

    /** Tile widths shortly after a fourth Essential is pinned, and once everything has settled. */
    private fun widthsAfterPinning(level: AnimationLevel): Pair<List<Float>, List<Float>> {
        var essentials by mutableStateOf(listOf("a", "b", "c").map(::essential))
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = emptyList(),
                            mode = SidebarMode.Persistent,
                            essentials = essentials,
                            look = SidebarLook(animations = level),
                        ),
                    onIntent = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        essentials = essentials + essential("d")
        Snapshot.sendApplyNotifications()
        fun widths() = composeRule.onAllNodesWithTag(SidebarTestTag.ESSENTIAL).fetchSemanticsNodes().map { it.boundsInRoot.width }
        composeRule.mainClock.advanceTimeBy(48)
        val soon = widths()
        composeRule.mainClock.advanceTimeBy(1_000)
        return soon to widths()
    }

    @Test
    fun `WHEN an Essential is pinned THEN only it grows in, and the others don't replay`() {
        val (soon, settled) = widthsAfterPinning(AnimationLevel.Full)

        assertEquals(4, soon.size)
        soon.take(3).zip(settled.take(3)).forEach { (now, end) -> assertEquals(end, now, 0.5f) }
        assertTrue("${soon.last()} vs ${settled.last()}", soon.last() < settled.last() - 0.5f)
    }

    @Test
    fun `GIVEN reduced animations THEN the new Essential fades in without growing`() {
        val (soon, settled) = widthsAfterPinning(AnimationLevel.Reduced)

        assertEquals(settled.last(), soon.last(), 0.5f)
    }
}
