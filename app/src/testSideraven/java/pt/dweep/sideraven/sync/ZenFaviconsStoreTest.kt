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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.EssentialsCodec
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Spec 0009 ticket 02: Zen's favicons are kept out of the stored items, and still reach Zen unchanged. */
@RunWith(AndroidJUnit4::class)
class ZenFaviconsStoreTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val essentialsRepository = InMemoryEssentialsRepository()
    private val essentials = Essentials(essentialsRepository, MutableStateFlow(emptyMap()), clock = { now })
    private val zenSync = ZenSync({ server.credentials }, server, spaces, essentials, InMemorySyncBaseRepository(), InMemoryZenSyncSettingsStore())

    // A favicon as Zen sends it: a long data URI.
    private val icon = "data:image/png;base64," + "iVBORw0KGgo".repeat(400)
    private val otherIcon = "data:image/png;base64," + "R0lGODlh".repeat(400)

    @Before
    fun freshBlobs() {
        ZenBlobs.store = InMemoryBlobStore()
    }

    private fun withIcon(
        record: JSONObject,
        value: String = icon,
    ) = record.apply { getJSONObject("data").put("icon", value) }

    private fun importDesktop() {
        server.put(withIcon(ZenRecords.essential("cal", "https://calendar.example", "Calendar")))
        server.put(ZenRecords.space("work", "Work", children = listOf("mail")))
        server.put(withIcon(ZenRecords.pinned("mail", "https://mail.example", "work", title = "Mail"), otherIcon))
        server.put(ZenRecords.layout(listOf("work"), mapOf("default" to listOf("cal"))))
        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }
        server.requests.clear()
    }

    private fun storedText() = EssentialsCodec.encode(essentials.essentials.value) + spaces.data.toString()

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `GIVEN desktop records with favicons WHEN imported THEN the stored items hold no favicon data`() {
        importDesktop()

        assertFalse(storedText().contains("data:image"))
        assertEquals(1, essentials.essentials.value.size)
        assertEquals("Work", spaces.data.spaces.single { it.id == "work" }.name)
    }

    @Test
    fun `GIVEN imported favicons WHEN nothing changed THEN a sync uploads nothing`() {
        importDesktop()

        sync()

        assertTrue(server.writes.map { it.url.path }.toString(), server.writes.isEmpty())
    }

    @Test
    fun `WHEN an Essential and a Pinned Tab are renamed THEN Zen gets their favicons exactly as it sent them`() {
        importDesktop()
        now = 5_000L
        essentials.rename("https://calendar.example", "Agenda")
        spaces.renamePin("mail", "Inbox")

        sync()

        val cal = server.cleartext("cal")!!.getJSONObject("data")
        assertEquals("Agenda", cal.getString("staticLabel"))
        assertEquals(icon, cal.getString("icon"))
        val mail = server.cleartext("mail")!!.getJSONObject("data")
        assertEquals("Inbox", mail.getString("staticLabel"))
        assertEquals(otherIcon, mail.getString("icon"))
        assertFalse(server.ids().any { server.cleartext(it).toString().contains("sideraven-blob") })
    }

    @Test
    fun `GIVEN items stored with inline favicons by an older build WHEN syncing THEN they are slimmed and nothing is uploaded`() {
        importDesktop()
        fun inline(extra: String?) = extra?.let { JSONObject(it).put("data", ZenOrigin.decode(it)!!.fullData()).toString() }
        essentials.applyRemote(essentials.essentials.value.map { it.copy(extra = inline(it.extra)) })
        spaces.applyRemote { data -> data.copy(pinned = data.pinned.map { it.copy(extra = inline(it.extra)) }) }
        assertTrue(storedText().contains("data:image"))

        sync()

        assertFalse(storedText().contains("data:image"))
        assertTrue(server.writes.map { it.url.path }.toString(), server.writes.isEmpty())
    }

    @Test
    fun `THEN short icons stay inline, and a favicon whose data is gone is left out rather than sent as a reference`() {
        val data = JSONObject().put("icon", icon).put("emoji", "🏠").put("url", "https://a.example")
        val stashed = ZenBlobs.stash(data)
        assertEquals("🏠", stashed.getString("emoji"))
        assertTrue(stashed.getString("icon").startsWith("sideraven-blob:"))
        assertEquals(data.toString(), ZenBlobs.restore(stashed).toString())

        ZenBlobs.store = InMemoryBlobStore()
        val restored = ZenBlobs.restore(stashed)
        assertFalse(restored.has("icon"))
        assertEquals("https://a.example", restored.getString("url"))
    }

    @Test
    fun `GIVEN the stored favicon data was lost WHEN an Essential is renamed THEN Zen keeps its favicon`() {
        importDesktop()
        ZenBlobs.store = InMemoryBlobStore()
        now = 5_000L
        essentials.rename("https://calendar.example", "Agenda")

        sync()

        val cal = server.cleartext("cal")!!.getJSONObject("data")
        assertEquals("Agenda", cal.getString("staticLabel"))
        assertEquals(icon, cal.getString("icon"))
    }

    @Test
    fun `GIVEN the stored favicon data was lost WHEN nothing changed THEN nothing is uploaded`() {
        importDesktop()
        ZenBlobs.store = InMemoryBlobStore()

        sync()

        assertTrue(server.writes.map { it.url.path }.toString(), server.writes.isEmpty())
    }

    @Test
    fun `WHEN storing a favicon fails or there is no store yet THEN it stays inline`() {
        val data = JSONObject().put("icon", icon)
        ZenBlobs.store =
            object : BlobStore {
                override fun put(
                    key: String,
                    value: String,
                ) = false

                override fun get(key: String): String? = null
            }
        assertEquals(icon, ZenBlobs.stash(data).getString("icon"))

        ZenBlobs.store = null
        assertEquals(icon, ZenBlobs.stash(data).getString("icon"))
    }

    @Test
    fun `THEN an item holding only short data URIs is not taken for old inline data`() {
        val short = ZenOrigin("r", JSONObject().put("icon", "data:image/png;base64,AAAA").put("note", "n".repeat(600))).encode()
        val long = JSONObject().put("rid", "r").put("data", JSONObject().put("icon", icon)).toString()

        assertFalse(ZenBlobs.holdsInline(short))
        assertTrue(ZenBlobs.holdsInline(long))
    }
}
