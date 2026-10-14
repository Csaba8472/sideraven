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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Each category, and colour sharing, in both directions. */
@RunWith(AndroidJUnit4::class)
class ChooseWhatSyncsTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync = ZenSync({ server.credentials }, server, spaces, essentials, InMemorySyncBaseRepository(), settings)

    private fun theme(hex: String) = JSONObject().put("type", "gradient").put("gradientColors", JSONArray().put(JSONObject().put("c", hex).put("isPrimary", true)))

    private fun setUp() {
        server.put(ZenRecords.space("work", "Work", theme = theme("#6a4fb8")))
        server.put(ZenRecords.essential("cal", "https://calendar.example", "Calendar"))
        server.put(ZenRecords.layout(listOf("work"), mapOf("default" to listOf("cal"))))
        runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }
        server.requests.clear()
        now = 5_000L
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `GIVEN Spaces don't sync THEN a rename stays on its side, both ways`() {
        setUp()
        settings.save(settings.settings.value.copy(syncSpaces = false))
        spaces.edit("work", "Tablet name", "💼", "violet")
        server.put(ZenRecords.space("work", "Desk name", theme = theme("#6a4fb8")))

        sync()

        assertEquals("Tablet name", spaces.data.spaces.first { it.id == "work" }.name)
        assertEquals("Desk name", server.cleartext("work")!!.getJSONObject("data").getString("name"))
    }

    @Test
    fun `GIVEN Essentials don't sync THEN Essentials stay on their side, both ways`() {
        setUp()
        settings.save(settings.settings.value.copy(syncEssentials = false))
        essentials.pinUrl("https://notes.example", "Notes")
        server.put(ZenRecords.essential("chat", "https://chat.example"))

        sync()

        assertEquals(listOf("https://calendar.example", "https://notes.example"), essentialsRepository.essentials.value.map { it.url })
        assertFalse(server.ids().any { server.cleartext(it)?.optJSONObject("data")?.optString("url") == "https://notes.example" })
    }

    @Test
    fun `GIVEN colour sharing is on WHEN the colour changes on the tablet THEN desktop gets a solid colour`() {
        setUp()
        spaces.edit("work", "Work", "💼", "teal")

        sync()

        val theme = server.cleartext("work")!!.getJSONObject("data").getJSONObject("theme")
        assertEquals(1, theme.getJSONArray("gradientColors").length())
    }

    @Test
    fun `GIVEN colour sharing is on WHEN only the name changes THEN desktop's gradient is untouched`() {
        setUp()
        spaces.edit("work", "Office", "💼", "violet")

        sync()

        val data = server.cleartext("work")!!.getJSONObject("data")
        assertEquals("Office", data.getString("name"))
        assertEquals("#6a4fb8", data.getJSONObject("theme").getJSONArray("gradientColors").getJSONObject(0).getString("c"))
    }

    @Test
    fun `GIVEN colour sharing is off THEN colours stay local both ways`() {
        setUp()
        settings.save(settings.settings.value.copy(shareColours = false))
        spaces.edit("work", "Work", "💼", "teal")
        server.put(ZenRecords.space("work", "Work", theme = theme("#a8325a")))

        sync()

        assertEquals("teal", spaces.data.spaces.first { it.id == "work" }.accent)
        assertEquals("#a8325a", server.cleartext("work")!!.getJSONObject("data").getJSONObject("theme").getJSONArray("gradientColors").getJSONObject(0).getString("c"))
        assertTrue(server.writes.isEmpty())
    }
}
