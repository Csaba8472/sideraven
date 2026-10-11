/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Zen Sync ticket 05: two-way sync against the fake server, after a Keep-separate First Sync. */
@RunWith(AndroidJUnit4::class)
class TwoWaySyncTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()), clock = { now })
    private val base = InMemorySyncBaseRepository()
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync = ZenSync({ server.credentials }, server, spaces, essentials, base, settings)

    private fun setUp() {
        server.put(ZenRecords.space("work", "Work", children = listOf("docs-folder", "mail", "split-1")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Mail"))
        server.put(ZenRecords.pinned("spec", "https://docs.example/spec", "work", folder = "docs-folder"))
        server.put(ZenRecords.folder("docs-folder", "Docs", "work", children = listOf("spec")).also { it.getJSONObject("data").put("live", JSONObject().put("kind", "rss")) })
        server.put(ZenWire.cleartext("split-1", "split", JSONObject().put("splitId", "split-1").put("tabs", org.json.JSONArray().put("a").put("b")).put("workspaceUuid", "work")))
        server.put(ZenRecords.essential("cal", "https://calendar.example", "Calendar"))
        server.put(ZenRecords.layout(listOf("work"), mapOf("default" to listOf("cal"))))
        runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }
        server.requests.clear()
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `GIVEN nothing changed WHEN syncing THEN nothing is uploaded`() {
        setUp()

        sync()

        assertTrue(server.writes.map { it.url.path }.toString(), server.writes.isEmpty())
    }

    @Test
    fun `WHEN a pin is renamed on the tablet THEN Zen gets it as its custom label, everything else kept`() {
        setUp()
        now = 5_000L
        spaces.renamePin("mail", "Inbox")

        sync()

        val mail = server.cleartext("mail")!!.getJSONObject("data")
        assertEquals("Inbox", mail.getString("staticLabel"))
        assertEquals("https://mail.example", mail.getString("url"))
        assertEquals(1, server.writes.size)
    }

    @Test
    fun `WHEN a Space is renamed on desktop THEN the tablet shows the new name`() {
        setUp()
        server.put(ZenRecords.space("work", "Office", children = listOf("docs-folder", "mail", "split-1")))

        sync()

        assertEquals("Office", spaces.data.spaces.first { it.id == "work" }.name)
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `WHEN a pin is added on the tablet THEN Zen gets the tab and the Space's children, with foreign ids kept`() {
        setUp()
        now = 5_000L
        spaces.switchTo("work")
        spaces.pinUrl("https://news.example", "News")

        sync()

        val newPin = spaces.data.pinnedIn("work").first { it.url == "https://news.example" }
        val record = server.cleartext(newPin.id)!!.getJSONObject("data")
        assertEquals("work", record.getString("workspaceUuid"))
        val children = server.cleartext("work")!!.getJSONObject("data").getJSONArray("children").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(listOf("docs-folder", "mail", "split-1", newPin.id), children)
    }

    @Test
    fun `WHEN an Essential is added on desktop THEN it appears on the tablet`() {
        setUp()
        server.put(ZenRecords.essential("chat", "https://chat.example", "Chat"))
        server.put(ZenRecords.layout(listOf("work"), mapOf("default" to listOf("cal", "chat"))))

        sync()

        assertEquals(listOf("Calendar", "Chat"), essentialsRepository.essentials.value.map { it.title })
    }

    @Test
    fun `WHEN both sides changed the same pin THEN the later edit wins`() {
        setUp()
        // Desktop renamed it first (server time 1700000xxx s), the tablet later (ms clock far ahead).
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Mail", staticLabel = "Desk"))
        now = 1_800_000_000_000L
        spaces.renamePin("mail", "Tablet")

        sync()

        assertEquals("Tablet", server.cleartext("mail")!!.getJSONObject("data").getString("staticLabel"))
        assertEquals("Tablet", spaces.data.pinned.first { it.id == "mail" }.customName)
    }

    @Test
    fun `WHEN both changed and desktop was later THEN desktop wins`() {
        setUp()
        now = 2_000L
        spaces.renamePin("mail", "Tablet")
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Mail", staticLabel = "Desk"))

        sync()

        assertEquals("Desk", spaces.data.pinned.first { it.id == "mail" }.customName)
        assertEquals("Desk", server.cleartext("mail")!!.getJSONObject("data").getString("staticLabel"))
    }

    @Test
    fun `GIVEN a desktop record the tablet never had THEN it is never deleted from desktop`() {
        setUp()
        // Desktop adds a pin and a folder; the tablet syncs; nothing of desktop's is tombstoned.
        server.put(ZenRecords.pinned("extra", "https://extra.example", "work"))

        sync()

        assertTrue(server.writes.isEmpty())
        assertTrue(spaces.data.pinned.any { it.id == "extra" })
    }

    @Test
    fun `WHEN a pin is unpinned on the tablet THEN only it is deleted on desktop`() {
        setUp()
        now = 5_000L
        spaces.unpinFromSpace("mail")

        sync()

        assertTrue(ZenWire.isTombstone(server.cleartext("mail")!!))
        assertTrue(server.cleartext("spec")!!.has("data"))
    }

    @Test
    fun `WHEN desktop deletes a pin THEN it disappears from the tablet`() {
        setUp()
        server.delete("mail")

        sync()

        assertNull(spaces.data.pinned.firstOrNull { it.id == "mail" })
    }

    @Test
    fun `WHEN desktop writes during the sync THEN the sync retries once and still converges`() {
        setUp()
        now = 5_000L
        spaces.renamePin("mail", "Inbox")
        var raced = false
        val racing =
            SyncHttpTransport { request ->
                if (!raced && request.method == "PUT") {
                    raced = true
                    server.put(ZenRecords.space("work", "Office", children = listOf("docs-folder", "mail", "split-1")))
                    server.put(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Mail"))
                }
                server.execute(request)
            }
        val racingSync = ZenSync({ server.credentials }, racing, spaces, essentials, base, settings)

        runBlocking { racingSync.sync() }

        assertEquals("Office", spaces.data.spaces.first { it.id == "work" }.name)
        assertEquals("Inbox", server.cleartext("mail")!!.getJSONObject("data").getString("staticLabel"))
    }

    @Test
    fun `THEN unknown fields survive a round trip byte for byte`() {
        setUp()
        val before = server.cleartext("docs-folder").toString()
        now = 5_000L
        spaces.renamePin("spec", "Spec")

        sync()

        assertEquals(before, server.cleartext("docs-folder").toString())
        assertEquals("rss", server.cleartext("spec")!!.getJSONObject("data").let { server.cleartext("docs-folder")!!.getJSONObject("data").getJSONObject("live").getString("kind") })
    }
}
