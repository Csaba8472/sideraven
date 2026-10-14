/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.SidebarFolder
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPinnedRow
import pt.dweep.sideraven.SidebarPinnedTab
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.theme.SideravenTheme

/** Collapsed Mode's Folder popup, and Folder reordering. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class FolderPopupUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    @Test
    fun `GIVEN Collapsed Mode WHEN a Folder is tapped THEN a popup lists its pins, which open on tap`() {
        val folder = SidebarFolder("f1", "Work", "💼", depth = 0, expanded = true, count = 1)
        val mail = SidebarPinnedTab("p1", "Mail", "https://mail.example", icon = null, linkedTabId = null, isSelected = false, folderId = "f1")
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Collapsed,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true, pinnedCount = 1)),
                pinned = listOf(mail),
                folders = listOf(folder),
                pinnedRows = listOf(SidebarPinnedRow.FolderRow(folder), SidebarPinnedRow.PinRow(mail, 1)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onNodeWithContentDescription("Work").performClick()
        composeRule.onNodeWithText("Mail").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectPinnedTab("p1")), intents)
    }

    @Test
    fun `WHEN a Folder is dropped on a sibling THEN it takes that place`() {
        val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
        val a = spaces.createFolder("A")
        spaces.createFolder("B")
        val c = spaces.createFolder("C")

        spaces.moveFolderTo(a.id, c.id)
        assertEquals(listOf("B", "C", "A"), spaces.data.folders.map { it.name })

        spaces.moveFolderTo(a.id, spaces.data.folders.first().id)
        assertEquals(listOf("A", "B", "C"), spaces.data.folders.map { it.name })
    }
}
