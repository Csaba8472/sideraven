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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.CollapsedSection
import pt.dweep.sideraven.DEFAULT_SECTION_SHARE
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SectionShares
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarIntentHandler
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/**
 * Collapsed Mode (2026-10-12): hold the Divider under the Essentials Section or the Pinned Tabs and
 * drag it to show more or less of the section; the size is remembered.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class CollapsedSectionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val essentials =
        (1..12).map { SidebarEssential("https://e$it.example", "E$it", icon = null, linkedTabId = null, isSelected = false) }

    private fun state(
        mode: SidebarMode,
        shares: SectionShares = SectionShares(),
    ) = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = mode, essentials = essentials, sectionShares = shares)

    private fun show(
        state: SidebarUiState,
        onIntent: (SidebarIntent) -> Unit = {},
    ) = composeRule.setContent {
        SideravenTheme { SidebarContent(state = state, onIntent = onIntent, modifier = Modifier.height(800.dp)) }
    }

    @Test
    fun `THEN each section starts at a third of the Sidebar's height, and a resize is remembered within its limits`() {
        val store = InMemorySidebarModeStore(SidebarMode.Collapsed)
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, setSectionShare = { section, share -> store.sectionShare(section).set(share) })
        val ui = state(SidebarMode.Collapsed)
        assertEquals(DEFAULT_SECTION_SHARE, store.essentialsShare.value)
        assertEquals(DEFAULT_SECTION_SHARE, store.pinnedShare.value)

        handler.handle(SidebarIntent.ResizeSection(CollapsedSection.Essentials, 0.45f), ui)
        assertEquals(0.45f, store.essentialsShare.value)
        // Never so tall the Tab List vanishes, never so short the section does.
        handler.handle(SidebarIntent.ResizeSection(CollapsedSection.Pinned, 0.95f), ui)
        assertEquals(0.6f, store.pinnedShare.value)
        handler.handle(SidebarIntent.ResizeSection(CollapsedSection.Pinned, 0f), ui)
        assertEquals(0.05f, store.pinnedShare.value)
    }

    @Test
    fun `GIVEN Collapsed Mode THEN the Essentials Section is no taller than its share`() {
        show(state(SidebarMode.Collapsed, SectionShares(essentials = 0.1f)))
        val bounds = composeRule.onNodeWithTag(SidebarTestTag.ESSENTIALS).getBoundsInRoot()
        assertTrue("height ${bounds.bottom - bounds.top}", bounds.bottom - bounds.top <= 80.dp)
    }

    @Test
    fun `GIVEN Collapsed Mode WHEN the Divider is held and dragged down THEN the Essentials Section gets a larger share`() {
        val intents = mutableListOf<SidebarIntent>()
        show(state(SidebarMode.Collapsed, SectionShares(essentials = 0.1f))) { intents += it }

        composeRule.onNodeWithContentDescription("Resize Essentials").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(Offset(0f, 60f))
            moveBy(Offset(0f, 60f))
            up()
        }

        val resize = intents.filterIsInstance<SidebarIntent.ResizeSection>().single()
        assertEquals(CollapsedSection.Essentials, resize.section)
        assertTrue("share ${resize.share}", resize.share > 0.15f)
    }

    @Test
    fun `GIVEN Persistent Mode THEN the Dividers can't be dragged`() {
        show(state(SidebarMode.Persistent))
        composeRule.onAllNodesWithTag(SidebarTestTag.SECTION_DIVIDER).assertCountEquals(0)
    }
}
