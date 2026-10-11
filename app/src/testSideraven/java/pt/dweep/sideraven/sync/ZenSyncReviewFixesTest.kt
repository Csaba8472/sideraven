/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Regression tests for the Zen Sync code review findings. */
@RunWith(AndroidJUnit4::class)
class ZenSyncReviewFixesTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync =
        ZenSync({ server.credentials }, server, spaces, Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap()), clock = { now }), InMemorySyncBaseRepository(), settings)

    private fun firstSync() = runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }

    private fun sync() = runBlocking { zenSync.sync() }

    private fun tombstones() = server.ids().filter { ZenWire.isTombstone(server.cleartext(it)!!) }

    @Test
    fun `GIVEN Spaces didn't sync at First Sync WHEN they're turned on THEN desktop's Spaces arrive and nothing is deleted`() {
        settings.save(ZenSyncSettings(syncSpaces = false))
        server.put(ZenRecords.space("work", "Work", children = listOf("mail")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()

        settings.save(settings.settings.value.copy(syncSpaces = true))
        sync()

        assertTrue(tombstones().isEmpty())
        assertEquals("Work", spaces.data.spaces.first { it.id == "work" }.name)
        assertTrue(spaces.data.pinned.any { it.id == "mail" })
    }

    @Test
    fun `GIVEN a desktop pin whose Space the tablet doesn't know THEN it is never deleted`() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.pinned("orphan", "https://orphan.example", "somewhere-else"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()

        now = 5_000L
        sync()
        sync()

        assertTrue(tombstones().isEmpty())
    }

    @Test
    fun `WHEN Zen Sync is turned off and on again THEN First Sync doesn't create duplicate ids`() {
        server.put(ZenRecords.space("work", "Work", children = listOf("mail")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()

        zenSync.turnOff()
        firstSync()

        val data = spaces.data
        assertEquals(data.spaces.size, data.spaces.map { it.id }.toSet().size)
        assertEquals(data.pinned.size, data.pinned.map { it.id }.toSet().size)
        // The old tablet copy stays (now Local), beside desktop's.
        assertEquals(2, data.spaces.count { it.name == "Work" })
        assertTrue(data.spaces.first { it.id == "work" }.let { !it.isLocal })
    }

    @Test
    fun `GIVEN colours weren't shared WHEN sharing is turned on THEN desktop gradients aren't overwritten`() {
        settings.save(ZenSyncSettings(shareColours = false))
        val gradient = JSONObject().put("type", "gradient").put("gradientColors", JSONArray().put(JSONObject().put("c", "#6a4fb8").put("isPrimary", true)))
        server.put(ZenRecords.space("work", "Work", theme = gradient))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()

        settings.save(settings.settings.value.copy(shareColours = true))
        server.requests.clear()
        sync()

        assertTrue(server.writes.isEmpty())
        assertEquals("violet", spaces.data.spaces.first { it.id == "work" }.accent)
    }

    @Test
    fun `GIVEN Zen interleaves pins and Folders THEN an untouched Space isn't re-uploaded`() {
        server.put(ZenRecords.space("work", "Work", children = listOf("tab-a", "folder-x", "tab-b")))
        server.put(ZenRecords.pinned("tab-a", "https://a.example", "work"))
        server.put(ZenRecords.pinned("tab-b", "https://b.example", "work"))
        server.put(ZenRecords.folder("folder-x", "X", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()
        server.requests.clear()

        sync()

        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `GIVEN a blank custom label on desktop THEN it round-trips unchanged`() {
        server.put(ZenRecords.space("work", "Work", children = listOf("mail")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work", staticLabel = ""))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()
        server.requests.clear()

        sync()

        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `WHEN a new record is uploaded THEN it carries no create-only condition`() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()
        now = 5_000L
        spaces.switchTo("work")
        spaces.pinUrl("https://news.example", "News")
        server.requests.clear()

        sync()

        val newPin = spaces.data.pinned.single { it.url == "https://news.example" }
        val put = server.writes.single { it.url.path.endsWith(newPin.id) }
        assertNull(put.headers["X-If-Unmodified-Since"])
        // Existing records are still written conditionally.
        assertFalse(server.writes.filter { it.url.path.endsWith("/work") }.any { it.headers["X-If-Unmodified-Since"] == null })
    }

    @Test
    fun `WHEN a Local Space re-joins THEN desktop's later edit isn't overridden by the act of re-joining`() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.layout(listOf("work")))
        firstSync()
        now = 9_000_000_000_000L
        zenSync.markLocal("work", true)
        sync()
        server.put(ZenRecords.space("work", "Office"))

        zenSync.markLocal("work", false)
        sync()

        assertEquals("Office", spaces.data.spaces.first { it.id == "work" }.name)
    }
}
