/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Switching a desktop Space off (Desktop-only Space) and back on. */
@RunWith(AndroidJUnit4::class)
class DesktopOnlySpacesTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val desktop = InMemoryDesktopViewStore()
    private val base = InMemorySyncBaseRepository()
    private val zenSync =
        ZenSync(
            { server.credentials },
            server,
            spaces,
            Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
            base,
            settings,
            desktop = desktop,
        )

    /** Desktop has Home and Work (with a pin and a Folder); the tablet replaced its data with it. */
    private fun setUp() {
        server.put(ZenRecords.space("home", "Home", children = listOf("news")))
        server.put(ZenRecords.pinned("news", "https://news.example", "home"))
        server.put(ZenRecords.space("work", "Work", children = listOf("docs", "mail")))
        server.put(ZenRecords.folder("docs", "Docs", "work", children = listOf("wiki")))
        server.put(ZenRecords.pinned("wiki", "https://wiki.example", "work", folder = "docs"))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(ZenRecords.layout(listOf("home", "work")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }
        spaces.switchTo("work")
        spaces.applyRemote { it.copy(assignments = it.assignments + ("tab1" to "work")) }
        server.requests.clear()
        now = 5_000L
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `WHEN a desktop Space is switched off THEN it leaves the tablet with its pins and Folders, and its tabs move`() {
        setUp()

        zenSync.setOnTablet("work", false)

        assertEquals(listOf("home"), spaces.data.spaces.map { it.id })
        assertTrue(spaces.data.pinned.none { it.spaceId == "work" })
        assertTrue(spaces.data.folders.none { it.spaceId == "work" })
        assertEquals("home", spaces.data.assignments["tab1"])
        assertEquals("home", spaces.data.currentSpaceId)
        assertEquals(setOf("work"), settings.settings.value.desktopOnly)
    }

    @Test
    fun `GIVEN a desktop Space switched off THEN syncs never change or delete it on desktop`() {
        setUp()
        zenSync.setOnTablet("work", false)

        sync()
        server.put(ZenRecords.space("work", "Office", children = listOf("docs", "mail")))
        sync()
        spaces.edit("home", "House", "🏠", "ember")
        sync()

        listOf("work", "docs", "wiki", "mail").forEach { id ->
            assertFalse("$id was deleted", ZenWire.isTombstone(server.cleartext(id)!!))
        }
        assertTrue(server.writes.none { request -> listOf("work", "docs", "wiki", "mail").any { request.url.path.endsWith("/$it") } })
        assertTrue(spaces.data.spaces.none { it.id == "work" })
        // Desktop's Space order still lists it where it was.
        val order = server.cleartext(LAYOUT_RECORD_ID)!!.getJSONObject("data").getJSONArray("spaces")
        assertEquals(listOf("home", "work"), order.strings())
    }

    @Test
    fun `WHEN a Desktop-only Space is switched back on THEN the next sync brings it back with its pins and Folders`() {
        setUp()
        zenSync.setOnTablet("work", false)
        sync()
        server.put(ZenRecords.space("work", "Office", children = listOf("docs", "mail")))

        zenSync.setOnTablet("work", true)
        sync()

        assertEquals(listOf("home", "work"), spaces.data.spaces.map { it.id })
        assertEquals("Office", spaces.data.spaces.last().name)
        assertEquals(setOf("wiki", "mail"), spaces.data.pinned.filter { it.spaceId == "work" }.map { it.id }.toSet())
        assertEquals(listOf("docs"), spaces.data.folders.filter { it.spaceId == "work" }.map { it.id })
        assertTrue(settings.settings.value.desktopOnly.isEmpty())
        assertTrue(server.writes.none { request -> listOf("work", "docs", "wiki", "mail").any { request.url.path.endsWith("/$it") } })
    }

    @Test
    fun `GIVEN a Space made on the tablet WHEN it is switched on THEN it uploads, and once on desktop, off leaves it there`() {
        setUp()
        val sketches = spaces.create("Sketches", "✏️", local = true)
        sync()
        assertTrue(sketches.id !in server.ids())

        zenSync.setOnTablet(sketches.id, true)
        sync()
        assertEquals("Sketches", server.cleartext(sketches.id)!!.getJSONObject("data").getString("name"))

        zenSync.setOnTablet(sketches.id, false)
        sync()
        assertTrue(spaces.data.spaces.none { it.id == sketches.id })
        assertFalse(ZenWire.isTombstone(server.cleartext(sketches.id)!!))
    }

    @Test
    fun `GIVEN only one Space on the tablet THEN it can't be switched off`() {
        setUp()
        zenSync.setOnTablet("work", false)

        zenSync.setOnTablet("home", false)

        assertEquals(listOf("home"), spaces.data.spaces.map { it.id })
        assertEquals(setOf("work"), settings.settings.value.desktopOnly)
    }

    @Test
    fun `THEN the Space list shows the tablet's Spaces, then the Desktop-only ones by name`() {
        setUp()
        spaces.create("Sketches", "✏️")
        zenSync.markLocal(spaces.data.spaces.last().id, true)
        zenSync.setOnTablet("work", false)

        val choices = zenSync.spaceChoices()

        assertEquals(listOf("Home", "Sketches", "Work"), choices.map { it.name })
        assertEquals(listOf(true, false, false), choices.map { it.syncs })
        assertEquals(listOf(true, true, false), choices.map { it.onTablet })
        assertEquals(listOf(true, false, true), choices.map { it.onDesktop })
    }

    @Test
    fun `GIVEN a sync already reading WHEN a Space is switched off THEN that sync doesn't bring it back, and later syncs leave desktop alone`() {
        setUp()
        var racing: ZenSync? = null
        var switched = false
        val transport =
            SyncHttpTransport { request ->
                server.execute(request).also {
                    if (!switched && request.method == "GET" && request.url.path.endsWith("/storage/$SPACES_COLLECTION")) {
                        switched = true
                        racing!!.setOnTablet("work", false)
                    }
                }
            }
        racing =
            ZenSync(
                { server.credentials },
                transport,
                spaces,
                Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
                base,
                settings,
                desktop = desktop,
            )
        spaces.edit("home", "House", "🏠", "ember")

        runBlocking { racing.sync() }
        assertTrue(switched)
        assertTrue(spaces.data.spaces.none { it.id == "work" })
        runBlocking { racing.sync() }

        assertTrue(spaces.data.spaces.none { it.id == "work" })
        listOf("work", "docs", "wiki", "mail").forEach { id -> assertFalse("$id was deleted", ZenWire.isTombstone(server.cleartext(id)!!)) }
        assertTrue(server.writes.none { request -> listOf("work", "docs", "wiki", "mail").any { request.url.path.endsWith("/$it") } })
    }

    @Test
    fun `GIVEN a sync noticing a new desktop Space WHEN a Space is switched off meanwhile THEN the switch isn't lost`() {
        setUp()
        server.put(ZenRecords.space("play", "Play"))
        var switched = false
        lateinit var racing: ZenSync
        racing =
            ZenSync(
                { server.credentials },
                SyncHttpTransport { request ->
                    server.execute(request).also {
                        if (!switched && request.method == "GET" && request.url.path.endsWith("/storage/$SPACES_COLLECTION")) {
                            switched = true
                            racing.setOnTablet("work", false)
                        }
                    }
                },
                spaces,
                Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
                base,
                settings,
                desktop = desktop,
            )

        runBlocking { racing.sync() }

        assertEquals(setOf("work", "play"), settings.settings.value.desktopOnly)
        assertTrue(spaces.data.spaces.none { it.id == "work" })
        assertTrue(server.writes.none { request -> listOf("work", "docs", "wiki", "mail").any { request.url.path.endsWith("/$it") } })
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
