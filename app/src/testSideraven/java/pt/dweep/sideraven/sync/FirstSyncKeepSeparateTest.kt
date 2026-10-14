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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.PinnedTab
import pt.dweep.sideraven.Spaces

/** First Sync, "Keep tablet separate". */
@RunWith(AndroidJUnit4::class)
class FirstSyncKeepSeparateTest {
    private val server = FakeSyncServer()
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentialLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    private val essentials = Essentials(essentialsRepository, essentialLinks)
    private val base = InMemorySyncBaseRepository()
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync = ZenSync({ server.credentials }, server, spaces, essentials, base, settings)

    private val purple = JSONObject().put("type", "gradient").put("gradientColors", JSONArray().put(JSONObject().put("c", "#6a4fb8").put("isPrimary", true)))

    private fun desktop() {
        server.put(ZenRecords.space("work", "Work", icon = "chrome://browser/skin/zen-icons/selectable/briefcase.svg", children = listOf("docs-folder", "mail"), theme = purple))
        server.put(ZenRecords.space("home", "Home", icon = "🏠", children = listOf("music")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Inbox (3)", staticLabel = "Mail"))
        server.put(ZenRecords.pinned("spec", "https://docs.example/spec", "work", folder = "docs-folder"))
        server.put(ZenRecords.folder("docs-folder", "Docs", "work", children = listOf("spec")).also { it.getJSONObject("data").put("live", JSONObject().put("kind", "rss")) })
        server.put(ZenRecords.pinned("music", "https://music.example", "home"))
        server.put(ZenRecords.normal("news", "https://news.example", "home"))
        server.put(ZenRecords.essential("cal", "https://calendar.example", "Calendar"))
        server.put(ZenRecords.essential("chat", "https://chat.example", "Chat"))
        server.put(ZenRecords.layout(listOf("home", "work"), mapOf("work-container" to listOf("chat"), "default" to listOf("cal"))))
    }

    private fun firstSync() = runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }

    @Test
    fun `WHEN Keep tablet separate runs THEN desktop's Spaces arrive in Zen's order with their icons and nearest colour`() {
        desktop()
        firstSync()

        val desktopSpaces = spaces.data.spaces.filterNot { it.isLocal }
        assertEquals(listOf("Home", "Work"), desktopSpaces.map { it.name })
        assertEquals("chrome://browser/skin/zen-icons/selectable/briefcase.svg", desktopSpaces[1].icon)
        assertEquals("violet", desktopSpaces[1].accent)
    }

    @Test
    fun `THEN Pinned Tabs and Folders arrive in their Spaces, with custom names, and normal tabs don't`() {
        desktop()
        firstSync()

        val data = spaces.data
        // Zen's order: the Space lists the Docs folder (holding spec) before mail.
        assertEquals(listOf("https://docs.example/spec", "Mail"), data.pinnedIn("work").map { it.displayName })
        assertEquals("docs-folder", data.pinnedIn("work").first { it.id == "spec" }.folderId)
        assertEquals(listOf("Docs"), data.foldersIn("work").map { it.name })
        assertEquals(listOf("music"), data.pinnedIn("home").map { it.id })
        assertTrue(data.pinned.none { it.url == "https://news.example" })
    }

    @Test
    fun `THEN Essentials from every container arrive, default first, keeping their container`() {
        desktop()
        firstSync()

        val list = essentialsRepository.essentials.value
        assertEquals(listOf("Calendar", "Chat"), list.map { it.title })
        assertEquals(listOf(null, "work-container"), list.map { it.bucket })
    }

    @Test
    fun `THEN existing tablet Spaces become Local and tablet-only Essentials move into a Local Space`() {
        val tabletHome = spaces.data.current.id
        essentialsRepository.save(listOf(Essential("https://calendar.example", "Calendar"), Essential("https://notes.example", "Notes")))
        essentialLinks.value = mapOf("https://notes.example" to "notes-tab")
        desktop()

        firstSync()

        val data = spaces.data
        assertTrue(data.spaces.first { it.id == tabletHome }.isLocal)
        val fromTablet = data.spaces.single { it.name == ZenSync.FROM_TABLET }
        assertTrue(fromTablet.isLocal)
        assertEquals(listOf("https://notes.example"), data.pinnedIn(fromTablet.id).map(PinnedTab::url))
        // Its open tab follows it.
        assertEquals("notes-tab", spaces.pinnedLinks.value[data.pinnedIn(fromTablet.id).single().id])
        assertEquals(listOf("https://calendar.example", "https://chat.example"), essentialsRepository.essentials.value.map { it.url })
    }

    @Test
    fun `THEN nothing is uploaded, the last-synced state matches the server, and Zen Sync is on`() {
        desktop()
        firstSync()

        assertTrue(server.writes.isEmpty())
        assertEquals(server.ids() - "news", base.base.value.entries.keys - "news")
        assertTrue(settings.settings.value.enabled && settings.settings.value.firstSyncDone)
    }

    @Test
    fun `THEN unknown fields like a live folder's config are kept with the item`() {
        desktop()
        firstSync()

        val folder = spaces.data.foldersIn("work").single()
        assertEquals("rss", ZenOrigin.decode(folder.extra)!!.data.getJSONObject("live").getString("kind"))
        assertEquals("docs-folder", ZenOrigin.decode(folder.extra)!!.recordId)
    }

    @Test
    fun `GIVEN colour sharing is off THEN Spaces keep the default accent`() {
        settings.save(ZenSyncSettings(shareColours = false))
        desktop()
        firstSync()

        assertEquals("ember", spaces.data.spaces.first { it.name == "Work" }.accent)
    }

    @Test
    fun `GIVEN Essentials don't sync THEN the tablet's Essentials are left alone`() {
        settings.save(ZenSyncSettings(syncEssentials = false))
        essentialsRepository.save(listOf(Essential("https://notes.example", "Notes")))
        desktop()
        firstSync()

        assertEquals(listOf("https://notes.example"), essentialsRepository.essentials.value.map { it.url })
        assertNull(spaces.data.spaces.firstOrNull { it.name == ZenSync.FROM_TABLET })
    }
}
