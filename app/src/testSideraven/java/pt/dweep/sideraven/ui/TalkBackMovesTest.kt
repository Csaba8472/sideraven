/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** v1 prep ticket 02: everything that moves by dragging also moves from TalkBack's actions. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class TalkBackMovesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun show(state: SidebarUiState) {
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    private fun base() = SidebarUiState(isVisible = true, isPrivate = false, tabs = listOf(SidebarTab("t", "Tab", "https://t.example", icon = null, isSelected = true)), mode = SidebarMode.Persistent)

    private fun SemanticsNodeInteraction.actions() = fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty()

    private fun SemanticsNodeInteraction.labels() = actions().map { it.label }

    private fun SemanticsNodeInteraction.act(label: String) {
        actions().single { it.label == label }.action()
        composeRule.waitForIdle()
    }

    @Test
    fun `THEN an Essential moves earlier or later, and the first can't move earlier`() {
        show(base().copy(essentials = listOf("A", "B", "C").map { SidebarEssential("https://${it.lowercase()}.example", it, icon = null, linkedTabId = null, isSelected = false) }))

        composeRule.onNodeWithContentDescription("B").act("Move earlier")
        composeRule.onNodeWithContentDescription("B").act("Move later")

        assertEquals(listOf(SidebarIntent.MoveEssential("https://b.example", 0), SidebarIntent.MoveEssential("https://b.example", 2)), intents.filterIsInstance<SidebarIntent.MoveEssential>())
        assertFalse("Move earlier" in composeRule.onNodeWithContentDescription("A").labels())
        assertFalse("Move later" in composeRule.onNodeWithContentDescription("C").labels())
    }

    @Test
    fun `THEN a Pinned Tab moves up or down among its neighbours`() {
        val pins = listOf("Mail", "Docs", "Chat").map { SidebarPinnedTab(it.lowercase(), it, "https://${it.lowercase()}.example", icon = null, linkedTabId = null, isSelected = false) }
        show(base().copy(pinned = pins))

        composeRule.onNodeWithContentDescription("Docs").act("Move up")
        composeRule.onNodeWithContentDescription("Docs").act("Move down")

        assertEquals(listOf(SidebarIntent.MovePinnedTab("docs", "mail"), SidebarIntent.MovePinnedTab("docs", "chat")), intents.filterIsInstance<SidebarIntent.MovePinnedTab>())
        assertFalse("Move up" in composeRule.onNodeWithContentDescription("Mail").labels())
    }

    @Test
    fun `THEN a Space moves earlier or later in the row`() {
        val spaces = listOf("Home", "Work").mapIndexed { i, name -> SidebarSpace(name.lowercase(), name, "🏠", accent = "", isCurrent = i == 0) }
        show(base().copy(spaces = spaces))

        composeRule.onNodeWithContentDescription("Work").act("Move earlier")

        assertEquals(listOf(SidebarIntent.MoveSpace("work", 0)), intents.filterIsInstance<SidebarIntent.MoveSpace>())
        assertFalse("Move later" in composeRule.onNodeWithContentDescription("Work").labels())
    }

    @Test
    fun `THEN the Auto-Hide handle moves up or down the edge`() {
        show(base().copy(mode = SidebarMode.AutoHide, showsFlap = true, flapPosition = 0.5f))

        composeRule.onNodeWithTag(SidebarTestTag.FLAP).act("Move handle up")
        composeRule.onNodeWithTag(SidebarTestTag.FLAP).act("Move handle down")

        assertEquals(listOf(SidebarIntent.MoveFlap(0.4f), SidebarIntent.MoveFlap(0.6f)), intents.filterIsInstance<SidebarIntent.MoveFlap>())
    }

    @Test
    fun `GIVEN the handle at the top THEN it offers only Move handle down`() {
        show(base().copy(mode = SidebarMode.AutoHide, showsFlap = true, flapPosition = 0f))

        assertEquals(listOf("Move handle down"), composeRule.onNodeWithTag(SidebarTestTag.FLAP).labels())
    }

    @Test
    fun `THEN a Pinned Tab can be added to Essentials, and an Essential pinned to the Space, without dragging`() {
        val pins = listOf(SidebarPinnedTab("mail", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false))
        val essentials = listOf(SidebarEssential("https://cal.example", "Calendar", icon = null, linkedTabId = null, isSelected = false))
        val spaces = listOf(SidebarSpace("home", "Home", "🏠", accent = "", isCurrent = true))
        show(base().copy(pinned = pins, essentials = essentials, spaces = spaces))

        composeRule.onNodeWithContentDescription("Mail").act("Pin to Essentials")
        composeRule.onNodeWithContentDescription("Calendar").act("Pin to this space")

        assertEquals(listOf(SidebarIntent.PinnedToEssentials("mail"), SidebarIntent.EssentialToSpace("https://cal.example")), intents.filter { it is SidebarIntent.PinnedToEssentials || it is SidebarIntent.EssentialToSpace })
    }
}
