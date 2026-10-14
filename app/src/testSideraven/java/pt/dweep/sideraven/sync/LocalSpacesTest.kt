/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Local Spaces. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class LocalSpacesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync =
        ZenSync({ server.credentials }, server, spaces, Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())), InMemorySyncBaseRepository(), settings)

    private fun setUp() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.layout(listOf("work")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }
        server.requests.clear()
        now = 5_000L
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `GIVEN a synced Space is made Local THEN neither side's changes cross, and desktop keeps its copy`() {
        setUp()
        zenSync.markLocal("work", true)
        spaces.edit("work", "Tablet only", "💼", "ember")
        server.put(ZenRecords.space("work", "Desk"))

        sync()

        assertEquals("Tablet only", spaces.data.spaces.first { it.id == "work" }.name)
        assertEquals("Desk", server.cleartext("work")!!.getJSONObject("data").getString("name"))
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `GIVEN a Local Space is deleted THEN nothing is deleted on desktop (its desktop copy comes back as synced)`() {
        setUp()
        spaces.create("Spare", "📁")
        zenSync.markLocal("work", true)
        spaces.delete("work")

        sync()

        assertFalse(ZenWire.isTombstone(server.cleartext("work")!!))
    }

    @Test
    fun `WHEN a tablet-only Local Space is unmarked THEN it uploads`() {
        setUp()
        val home = spaces.data.spaces.first { it.isLocal }

        zenSync.markLocal(home.id, false)
        sync()

        assertEquals(home.name, server.cleartext(home.id)!!.getJSONObject("data").getString("name"))
    }

    @Test
    fun `WHEN a Space made Local is synced again THEN it re-joins its desktop copy`() {
        setUp()
        zenSync.markLocal("work", true)
        sync()
        server.put(ZenRecords.space("work", "Office"))

        zenSync.markLocal("work", false)
        sync()

        assertEquals("Office", spaces.data.spaces.first { it.id == "work" }.name)
        assertEquals(1, server.ids().count { it == "work" })
    }

    @Test
    fun `GIVEN Zen Sync is on WHEN a Space is edited THEN Don't sync this Space can be switched`() {
        val intents = mutableListOf<SidebarIntent>()
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("w", "Work", "💼", "ember", isCurrent = true)),
                zenSyncOn = true,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit space").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_LOCAL).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_CONFIRM).performClick()

        assertEquals(SidebarIntent.SetSpaceLocal("w", true), intents.first())
    }

    @Test
    fun `GIVEN Zen Sync is off THEN the option isn't shown`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("w", "Work", "💼", "ember", isCurrent = true)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }

        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit space").performClick()
        composeRule.onAllNodesWithTag(SidebarTestTag.SPACE_LOCAL).assertCountEquals(0)
    }
}
