/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Desktop's open tabs are kept read-only as Desktop Tabs. */
@RunWith(AndroidJUnit4::class)
class DesktopTabsSyncTest {
    private val server = FakeSyncServer()
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val desktop = InMemoryDesktopViewStore()
    private val zenSync =
        ZenSync(
            { server.credentials },
            server,
            spaces,
            Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
            InMemorySyncBaseRepository(),
            InMemoryZenSyncSettingsStore(),
            desktop = desktop,
        )

    private fun titled(
        record: JSONObject,
        title: String,
    ) = record.apply { getJSONObject("data").put("title", title) }

    @Test
    fun `GIVEN desktop shares its open tabs THEN each Space's are kept in desktop's order, and never written`() {
        server.put(ZenRecords.space("work", "Work", children = listOf("mail", "t2", "t1")))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(titled(ZenRecords.normal("t1", "https://a.example", "work"), "A"))
        server.put(titled(ZenRecords.normal("t2", "https://b.example", "work"), "B"))
        server.put(ZenRecords.space("home", "Home"))
        server.put(ZenRecords.normal("t3", "https://c.example", "home"))
        server.put(ZenRecords.layout(listOf("home", "work")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }
        spaces.edit("work", "Office", "💼", "ember")

        runBlocking { zenSync.sync() }

        val view = desktop.view.value
        assertTrue(view.sharesOpenTabs)
        assertEquals(listOf("https://b.example", "https://a.example"), view.tabs["work"].orEmpty().map { it.url })
        assertEquals(listOf("B", "A"), view.tabs["work"].orEmpty().map { it.title })
        assertEquals(listOf("https://c.example"), view.tabs["home"].orEmpty().map { it.url })
        assertTrue(server.writes.none { request -> listOf("t1", "t2", "t3").any { request.url.path.endsWith("/$it") } })
        // Never turned into Pinned Tabs.
        assertEquals(listOf("mail"), spaces.data.pinned.map { it.id })
    }

    @Test
    fun `WHEN desktop closes a tab THEN the next sync drops it, and without shared tabs there are none`() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.normal("t1", "https://a.example", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }
        assertEquals(1, desktop.view.value.tabs["work"].orEmpty().size)

        server.delete("t1")
        runBlocking { zenSync.sync() }

        assertFalse(desktop.view.value.sharesOpenTabs)
        assertTrue(desktop.view.value.tabs.isEmpty())
    }

    @Test
    fun `THEN the view survives being stored`() {
        val view = DesktopView(listOf(DesktopSpace("w", "Work", "💼")), mapOf("w" to listOf(DesktopTab("https://a.example", "A"))))

        assertEquals(view, DesktopView.decode(view.encode()))
    }
}
