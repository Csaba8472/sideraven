/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarInactiveSection
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 12, seam 2: drags between the Tab List, the Essentials Section and the Inactive Tabs Section. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DragBetweenSectionsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(pinBlock: PinResult? = null) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs =
                    listOf(
                        SidebarTab("t1", "Tab one", "https://1.example", icon = null, isSelected = true),
                        SidebarTab("t2", "Tab two", "https://2.example", icon = null, isSelected = false),
                    ),
                essentials =
                    listOf(
                        SidebarEssential("https://mail.example", "Mail", icon = null, linkedTabId = null, isSelected = false),
                        SidebarEssential("https://docs.example", "Docs", icon = null, linkedTabId = null, isSelected = false),
                    ),
                inactive =
                    SidebarInactiveSection(
                        tabs = listOf(SidebarTab("old", "Old tab", "https://old.example", icon = null, isSelected = false)),
                        expanded = true,
                    ),
                pinBlock = pinBlock,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    /** Long-press, then move in small steps by [dy] (and [dx]), then release. */
    private fun TouchInjectionScope.longPressDrag(
        dy: Float,
        dx: Float = 0f,
    ) {
        down(center)
        advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
        // Realistic finger motion: small steps (a single jump past the touch slop is a flick).
        val steps = maxOf(20, (kotlin.math.abs(dy) + kotlin.math.abs(dx)).toInt() / 5)
        repeat(steps) {
            moveBy(Offset(dx / steps, dy / steps))
            advanceEventTime(16)
        }
        up()
    }

    @Test
    fun `WHEN a tab is dragged up onto the Essentials Section THEN it is pinned`() {
        render()

        composeRule.onNodeWithContentDescription("Tab one").performTouchInput { longPressDrag(dy = -400f) }
        composeRule.waitForIdle()

        assertEquals("all intents: $intents", listOf<SidebarIntent>(SidebarIntent.PinTab("t1")), intents.filterIsInstance<SidebarIntent.PinTab>())
    }

    @Test
    fun `WHEN an Essential is dragged down out of the section THEN it is unpinned`() {
        render()

        composeRule.onNodeWithContentDescription("Mail").performTouchInput { longPressDrag(dy = 400f) }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnpinEssential("https://mail.example")), intents)
    }

    @Test
    fun `WHEN an Essential is dragged onto another THEN it moves there`() {
        render()

        composeRule.onNodeWithContentDescription("Docs").performTouchInput { longPressDrag(dy = 0f, dx = -80f) }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.MoveEssential("https://docs.example", 0)), intents)
    }

    @Test
    fun `WHEN an inactive tab is dragged up out of its section THEN it is reactivated`() {
        render()

        composeRule.onNodeWithContentDescription("Old tab").performTouchInput { longPressDrag(dy = -120f) }
        composeRule.waitForIdle()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ReactivateTab("old")), intents.filterIsInstance<SidebarIntent.ReactivateTab>())
    }

    // Guards the gesture itself: an earlier change consumed long-presses and silently broke drags.
    @Test
    fun `WHEN a tab is long-pressed and dragged down one slot THEN it reorders the Tab List`() {
        render()

        composeRule.onNodeWithContentDescription("Tab one").performTouchInput { longPressDrag(dy = 60f) }
        composeRule.waitForIdle()

        // The test's state is fixed, so the swap repeats while the finger moves; the app's store
        // reorders after the first one.
        assertEquals(
            listOf(SidebarIntent.MoveTab("t1", "t2", movedDown = true)),
            intents.filterIsInstance<SidebarIntent.MoveTab>().distinct(),
        )
        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.SelectTab>())
    }
}
