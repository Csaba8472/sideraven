/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.ArrangedShares
import pt.dweep.sideraven.CollapsedSection
import pt.dweep.sideraven.ControlPlaces
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarIntentHandler
import pt.dweep.sideraven.SidebarMenuInfo
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarScreen
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.SideravenMenuEntry
import pt.dweep.sideraven.sidebarIntent
import pt.dweep.sideraven.sideravenMenuEntries
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.toSidebarUiState

/**
 * Arrange Mode: started from the Main Menu in Collapsed Mode, it turns each Divider into a grab bar.
 * Done keeps the sizes, Reset returns the current orientation to Section Sizing; sizes are kept per
 * orientation and in Backup.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ArrangeModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val places = ControlPlaces(syncedTabs = pt.dweep.sideraven.ControlPlace.InSidebar)

    @Test
    fun `THEN the Main Menu offers Arrange sidebar only in Collapsed Mode, with something to arrange`() {
        assertTrue(SideravenMenuEntry.ArrangeSidebar in sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.AutoHide, mode = SidebarMode.Collapsed, arrangeable = true)))
        // No Essentials or Pinned Tabs (private browsing, or an empty Sidebar): nothing to arrange.
        assertFalse(SideravenMenuEntry.ArrangeSidebar in sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.AutoHide, mode = SidebarMode.Collapsed, arrangeable = false)))
        assertFalse(SideravenMenuEntry.ArrangeSidebar in sideravenMenuEntries(places, SidebarMenuInfo(nextMode = SidebarMode.Collapsed, mode = SidebarMode.Persistent, arrangeable = true)))
        assertFalse(SideravenMenuEntry.ArrangeSidebar in sideravenMenuEntries(places, sidebar = null))
        assertEquals(SidebarIntent.StartArranging, SideravenMenuEntry.ArrangeSidebar.sidebarIntent)
    }

    @Test
    fun `THEN Arrange Mode shows only in Collapsed Mode`() {
        fun arranging(mode: SidebarMode) =
            BrowserState().toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, chosenMode = mode, arranging = true).arranging
        assertTrue(arranging(SidebarMode.Collapsed))
        assertFalse(arranging(SidebarMode.Persistent))
    }

    @Test
    fun `WHEN arranging THEN Done ends it, a drag is stored for its section, and Reset clears this orientation`() {
        var arranging = false
        val stored = mutableMapOf<CollapsedSection, Float?>()
        val handler =
            SidebarIntentHandler(
                selectTab = {},
                openNewTab = {},
                setArranging = { arranging = it },
                setArrangedShare = { section, share -> stored[section] = share },
            )
        val collapsed = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Collapsed)

        handler.handle(SidebarIntent.StartArranging, collapsed)
        assertTrue(arranging)
        handler.handle(SidebarIntent.ArrangeSection(CollapsedSection.Essentials, 0.25f), collapsed)
        // Never more than what the Tab List leaves.
        handler.handle(SidebarIntent.ArrangeSection(CollapsedSection.Pinned, 0.9f), collapsed)
        assertEquals(0.25f, stored[CollapsedSection.Essentials])
        assertEquals(0.6f, stored[CollapsedSection.Pinned])
        handler.handle(SidebarIntent.ResetArrangement, collapsed)
        assertNull(stored[CollapsedSection.Essentials])
        assertNull(stored[CollapsedSection.Pinned])
        handler.handle(SidebarIntent.DoneArranging, collapsed)
        assertFalse(arranging)

        // Not in Persistent Mode.
        handler.handle(SidebarIntent.StartArranging, collapsed.copy(mode = SidebarMode.Persistent))
        assertFalse(arranging)
    }

    @Test
    fun `THEN portrait and landscape keep their own sizes, and they're in Backup`() {
        val store = InMemorySidebarModeStore()
        store.arrangedShare(CollapsedSection.Essentials, landscape = false).set(0.2f)
        store.arrangedShare(CollapsedSection.Essentials, landscape = true).set(0.4f)
        assertEquals(ArrangedShares(essentials = 0.2f), store.arrangedShares(landscape = false))
        assertEquals(ArrangedShares(essentials = 0.4f), store.arrangedShares(landscape = true))

        val restored = InMemorySidebarModeStore().apply { importSettings(store.exportSettings()) }
        assertEquals(ArrangedShares(essentials = 0.4f), restored.arrangedShares(landscape = true))
    }

    private fun arrangingState() =
        SidebarUiState(
            isVisible = true,
            isPrivate = false,
            mode = SidebarMode.Collapsed,
            nextMode = SidebarMode.AutoHide,
            layoutMovable = false,
            arranging = true,
            tabs = (1..10).map { SidebarTab("t$it", "Tab $it", "https://t$it.example", icon = null, isSelected = it == 1) },
            essentials = (1..12).map { SidebarEssential("https://e$it.example", "E$it", icon = null, linkedTabId = null, isSelected = false) },
        )

    @Test
    fun `GIVEN Arrange Mode THEN Dividers are grab bars, taps on tabs do nothing, and Done and Reset replace the controls`() {
        val intents = mutableListOf<SidebarIntent>()
        composeRule.setContent { SideravenTheme { SidebarContent(state = arrangingState(), onIntent = { intents += it }, modifier = Modifier.height(800.dp)) } }

        composeRule.onAllNodesWithTag(SidebarTestTag.GRAB_BAR).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SidebarTestTag.NEW_TAB).assertCountEquals(0)

        // A tab row near the top of the Tab List (Collapsed Mode shows icons, not titles).
        composeRule.onNodeWithTag(SidebarTestTag.TAB_LIST).performTouchInput { click(Offset(centerX, 70f)) }
        assertTrue("no tab selected: $intents", intents.none { it is SidebarIntent.SelectTab })

        composeRule.onNodeWithTag(SidebarTestTag.ARRANGE_RESET).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.ARRANGE_DONE).performClick()
        assertEquals(listOf<SidebarIntent>(SidebarIntent.ResetArrangement, SidebarIntent.DoneArranging), intents)
    }

    @Test
    fun `GIVEN Arrange Mode WHEN a grab bar is dragged down THEN its section is stored taller, and TalkBack can do the same`() {
        val intents = mutableListOf<SidebarIntent>()
        composeRule.setContent { SideravenTheme { SidebarContent(state = arrangingState(), onIntent = { intents += it }, modifier = Modifier.height(800.dp)) } }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).performTouchInput {
            down(center)
            repeat(10) {
                moveBy(Offset(0f, 10f))
                advanceEventTime(16)
            }
            up()
        }
        val dragged = intents.filterIsInstance<SidebarIntent.ArrangeSection>().last()
        assertEquals(CollapsedSection.Essentials, dragged.section)
        assertTrue("share ${dragged.share}", dragged.share > 0f)

        val actions = composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertEquals(listOf("Show more", "Show less"), actions?.map { it.label })
    }

    @Test
    fun `GIVEN Arrange Mode WHEN the Pinned Tabs grab bar is dragged THEN the section follows the finger frame by frame`() {
        val state =
            arrangingState().copy(
                essentials = emptyList(),
                pinned = (1..20).map { SidebarPinnedTab("p$it", "P$it", "https://p$it.example", icon = null, linkedTabId = null, isSelected = false) },
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}, modifier = Modifier.height(800.dp)) } }
        composeRule.waitForIdle()
        fun barTop() = composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).fetchSemanticsNode().boundsInRoot.top
        val before = barTop()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).performTouchInput {
            down(center)
            moveBy(Offset(0f, -20f))
        }
        repeat(10) {
            composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).performTouchInput { moveBy(Offset(0f, -10f)) }
            composeRule.mainClock.advanceTimeByFrame()
        }
        composeRule.mainClock.advanceTimeByFrame()
        // Still held: the section, and the bar under the finger, have already moved up by about what the finger did (past the touch slop).
        val shrunk = before - barTop()
        composeRule.onNodeWithTag(SidebarTestTag.GRAB_BAR).performTouchInput { up() }
        assertTrue("shrank $shrunk of about 100 while held", shrunk > 80f)
    }
}
