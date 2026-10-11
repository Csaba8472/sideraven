/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
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
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Zen Sync ticket 09: deletes and Undo across devices, and split views. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class DeletesAcrossDevicesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val zenSync =
        ZenSync(
            { server.credentials },
            server,
            spaces,
            Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
            InMemorySyncBaseRepository(),
            InMemoryZenSyncSettingsStore(),
        )

    private fun setUp() {
        server.put(ZenRecords.space("work", "Work", children = listOf("docs", "mail", "split-1")))
        server.put(ZenRecords.space("play", "Play"))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(ZenRecords.pinned("a", "https://a.example", "work"))
        server.put(ZenRecords.pinned("b", "https://b.example", "work"))
        server.put(ZenRecords.folder("docs", "Docs", "work"))
        server.put(ZenWire.cleartext("split-1", "split", JSONObject().put("splitId", "split-1").put("gridType", "vsep").put("tabs", JSONArray().put("a").put("b")).put("workspaceUuid", "work")))
        server.put(ZenRecords.layout(listOf("work", "play")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }
        server.requests.clear()
        now = 5_000L
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `WHEN a Space is deleted on the tablet THEN it, and only it, is deleted on desktop`() {
        setUp()
        spaces.delete("play")

        sync()

        assertTrue(ZenWire.isTombstone(server.cleartext("play")!!))
        assertFalse(ZenWire.isTombstone(server.cleartext("work")!!))
    }

    @Test
    fun `WHEN a synced Space delete is undone THEN it comes back on desktop too`() {
        setUp()
        val snapshot = spaces.delete("play")!!
        sync()

        spaces.restore(snapshot)
        sync()

        assertEquals("Play", server.cleartext("play")!!.getJSONObject("data").getString("name"))
        assertTrue(server.cleartext(LAYOUT_RECORD_ID)!!.getJSONObject("data").getJSONArray("spaces").toString().contains("play"))
    }

    @Test
    fun `WHEN a Folder is deleted on the tablet THEN it is deleted on desktop`() {
        setUp()
        spaces.deleteFolder("docs", keepPins = true)

        sync()

        assertTrue(ZenWire.isTombstone(server.cleartext("docs")!!))
    }

    @Test
    fun `WHEN a tab in a desktop split is unpinned THEN it leaves the split, which is otherwise untouched`() {
        setUp()
        spaces.unpinFromSpace("a")

        sync()

        val split = server.cleartext("split-1")!!.getJSONObject("data")
        assertEquals("[\"b\"]", split.getJSONArray("tabs").toString())
        assertEquals("vsep", split.getString("gridType"))
        // A second sync has nothing left to do.
        server.requests.clear()
        sync()
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `GIVEN Zen Sync is on WHEN a Space is deleted THEN the confirmation says it reaches other devices`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces =
                    listOf(
                        SidebarSpace("w", "Work", "💼", "ember", isCurrent = true),
                        SidebarSpace("p", "Play", "🎮", "ember", isCurrent = false),
                    ),
                zenSyncOn = true,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }

        composeRule.onNodeWithContentDescription("Play").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete space").performClick()

        composeRule.onNodeWithTag(SidebarTestTag.DELETE_MESSAGE).assertTextContains("Also removes it from your other devices.")
    }
}
