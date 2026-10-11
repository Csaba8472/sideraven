/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.NewTabPositionMiddleware
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.SpacesMiddleware

/** Zen Sync ticket 06: First Sync's Replace and Merge choices. */
@RunWith(AndroidJUnit4::class)
class FirstSyncReplaceMergeTest {
    private val server = FakeSyncServer()
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()))
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync = ZenSync({ server.credentials }, server, spaces, essentials, InMemorySyncBaseRepository(), settings)

    /** The tablet: Home (with a pin and a tab), Reading (a pin); Essentials Calendar and Notes. */
    private fun tablet() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://a.example", id = "tab-a")))
        spaces.pinUrl("https://mail.example", "Mail")
        spaces.pinUrl("https://blog.example", "Blog")
        spaces.create("Reading", "📚")
        spaces.pinUrl("https://books.example", "Books")
        essentialsRepository.save(listOf(Essential("https://calendar.example", "Calendar"), Essential("https://notes.example", "Notes")))
    }

    /** Desktop: " home " (mail) and Work; Essential Calendar. */
    private fun desktop() {
        server.put(ZenRecords.space("d-home", " home ", children = listOf("d-mail")))
        server.put(ZenRecords.space("d-work", "Work"))
        server.put(ZenRecords.pinned("d-mail", "https://mail.example", "d-home"))
        server.put(ZenRecords.essential("d-cal", "https://calendar.example", "Calendar"))
        server.put(ZenRecords.layout(listOf("d-home", "d-work"), mapOf("default" to listOf("d-cal"))))
    }

    @Test
    fun `WHEN Replace runs THEN the tablet mirrors desktop, open tabs move to the first desktop Space, and nothing is uploaded`() {
        tablet()
        desktop()

        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }

        val data = spaces.data
        assertEquals(listOf(" home ", "Work"), data.spaces.map { it.name })
        assertEquals(listOf("https://mail.example"), data.pinned.map { it.url })
        assertEquals("d-home", data.spaceOf("tab-a"))
        assertEquals(listOf("https://calendar.example"), essentialsRepository.essentials.value.map { it.url })
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `WHEN Merge is previewed THEN it lists what it adds and the name matches, changing nothing yet`() {
        tablet()
        desktop()

        val preview = runBlocking { zenSync.previewMerge() }

        // Reading is new; Home joins " home "; Blog and Books are new pins (Mail matches by URL); Notes is new.
        assertEquals(MergeSummary(spaces = 1, pinnedTabs = 2, essentials = 1, matches = listOf("Home" to " home ")), preview.summary)
        assertTrue(server.writes.isEmpty())
        assertEquals(listOf("Home", "Reading"), spaces.data.spaces.map { it.name })
    }

    @Test
    fun `WHEN a Merge is confirmed THEN both devices end up with everything, desktop's order first`() {
        tablet()
        desktop()

        runBlocking { zenSync.applyMerge(zenSync.previewMerge()) }

        val data = spaces.data
        assertEquals(listOf(" home ", "Work", "Reading"), data.spaces.map { it.name })
        assertEquals(setOf("https://mail.example", "https://blog.example"), data.pinnedIn("d-home").map { it.url }.toSet())
        assertEquals("d-home", data.spaceOf("tab-a"))
        assertEquals(listOf("https://calendar.example", "https://notes.example"), essentialsRepository.essentials.value.map { it.url })

        // Desktop got the tablet-only items.
        val reading = data.spaces.first { it.name == "Reading" }
        assertEquals("Reading", server.cleartext(reading.id)!!.getJSONObject("data").getString("name"))
        val blog = data.pinned.first { it.url == "https://blog.example" }
        assertEquals("d-home", server.cleartext(blog.id)!!.getJSONObject("data").getString("workspaceUuid"))
        val notes = essentialsRepository.essentials.value.first { it.url == "https://notes.example" }
        assertTrue(server.cleartext(notes.id)!!.getJSONObject("data").getBoolean("essential"))
        // The desktop pin that matched by URL wasn't duplicated.
        assertEquals(1, server.ids().count { server.cleartext(it)?.optJSONObject("data")?.optString("url") == "https://mail.example" })
    }
}
