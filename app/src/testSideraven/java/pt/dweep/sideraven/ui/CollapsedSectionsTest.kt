/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SectionSizing
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/**
 * Section Sizing in Collapsed Mode: the Tab List always keeps at least 40% of the strip; Essentials
 * and Pinned Tabs share the rest, each taking only what it holds. Dividers no longer drag.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class CollapsedSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun state(
        essentials: Int,
        pinned: Int,
        tabs: Int = 30,
    ) = SidebarUiState(
        isVisible = true,
        isPrivate = false,
        mode = SidebarMode.Collapsed,
        nextMode = SidebarMode.AutoHide,
        layoutMovable = false,
        tabs = (1..tabs).map { SidebarTab("t$it", "Tab $it", "https://t$it.example", icon = null, isSelected = it == 1) },
        essentials = (1..essentials).map { SidebarEssential("https://e$it.example", "E$it", icon = null, linkedTabId = null, isSelected = false) },
        pinned = (1..pinned).map { SidebarPinnedTab("p$it", "P$it", "https://p$it.example", icon = null, linkedTabId = null, isSelected = false) },
    )

    private fun show(
        state: SidebarUiState,
        height: Dp,
        onIntent: (SidebarIntent) -> Unit = {},
    ) = composeRule.setContent {
        SideravenTheme { SidebarContent(state = state, onIntent = onIntent, modifier = Modifier.height(height)) }
    }

    private fun height(tag: String): Float =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.height ?: 0f

    private fun assertTabListKeepsItsShare() {
        composeRule.waitForIdle()
        val essentials = height(SidebarTestTag.ESSENTIALS)
        val pinned = height(SidebarTestTag.PINNED_TABS)
        val tabList = height(SidebarTestTag.TAB_LIST)
        val shared = essentials + pinned + tabList
        // A few pixels of Dividers and padding sit between the sections.
        assertTrue("tab list $tabList of $shared", tabList >= shared * SectionSizing.TAB_LIST_SHARE - 4f)
    }

    @Test
    fun `GIVEN a short strip, like a phone held sideways, with many Essentials and Pinned Tabs THEN the Tab List keeps its share`() {
        show(state(essentials = 12, pinned = 12), height = 360.dp)
        assertTabListKeepsItsShare()
    }

    @Test
    fun `GIVEN a tall strip with many Essentials and Pinned Tabs THEN the Tab List keeps its share`() {
        show(state(essentials = 12, pinned = 12), height = 800.dp)
        assertTabListKeepsItsShare()
    }

    @Test
    fun `GIVEN a few Essentials and no Pinned Tabs THEN Essentials show in full, without scrolling`() {
        show(state(essentials = 2, pinned = 0), height = 800.dp)
        composeRule.waitForIdle()
        val section = composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS).getBoundsInRoot()
        // Two tiles fit well within the share; nothing is cut off, so the section is its content's height.
        assertTrue("essentials ${section.bottom - section.top}", section.bottom - section.top < 200.dp)
        composeRule.onAllNodesWithTag(SidebarTestTag.PINNED_TABS).assertCountEquals(0)
    }

    @Test
    fun `GIVEN Collapsed Mode WHEN a Divider is held and dragged THEN nothing is resized`() {
        val intents = mutableListOf<SidebarIntent>()
        show(state(essentials = 12, pinned = 0), height = 800.dp) { intents += it }
        composeRule.waitForIdle()
        val before = height(SidebarTestTag.ESSENTIALS)
        val bottom = composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS).getBoundsInRoot().bottom

        // On the Divider itself, just below the Essentials Section, in root coordinates: where 1.0's
        // draggable Divider sat. Long press, then drag in small steps.
        composeRule.onRoot().performTouchInput {
            val y = bottom.value * density + 1f
            down(Offset(centerX, y))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(12) {
                moveBy(Offset(0f, 10f))
                advanceEventTime(16)
            }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(before, height(SidebarTestTag.ESSENTIALS), 1f)
    }

    @Test
    fun `GIVEN a 1_0 Backup with dragged section sizes THEN it imports without error and they're ignored`() {
        val store = InMemorySidebarModeStore()
        store.importSettings(JSONObject().put("collapsed_essentials_share", 0.5).put("collapsed_pinned_share", 0.2).put("haptics", false))

        assertEquals(false, store.haptics.value)
        assertTrue(!store.exportSettings().has("collapsed_essentials_share"))
    }
}
