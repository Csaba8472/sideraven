/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.EssentialsCodec
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.NewTabPositionMiddleware
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.SpacesCodec
import pt.dweep.sideraven.SpacesMiddleware

/** Edit times, the Local flag, Essential buckets and the last-synced store. */
@RunWith(AndroidJUnit4::class)
class SyncReadyModelTest {
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))

    private fun add(id: String) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", title = id, id = id)))

    @Test
    fun `WHEN the user edits a Space, a pin or a Folder THEN only what changed gets the edit time`() {
        val home = spaces.data.current.id
        val work = spaces.create("Work", "💼")
        add("mail")
        spaces.pinToSpace(store.state.findTab("mail")!!)
        val folder = spaces.createFolder("Docs")
        now = 2_000L

        spaces.renamePin(spaces.data.pinned.single().id, "Inbox")

        assertEquals(2_000L, spaces.data.pinned.single().modified)
        // The pin's Space (its record lists its pins) is touched; the other Space isn't.
        assertEquals(2_000L, spaces.data.spaces.first { it.id == work.id }.modified)
        assertEquals(1_000L, spaces.data.folders.single { it.id == folder.id }.modified)
        assertFalse(spaces.data.spaces.first { it.id == home }.modified == 2_000L)
    }

    @Test
    fun `WHEN only device-local state changes THEN nothing is stamped`() {
        add("a")
        val before = spaces.data
        now = 5_000L

        spaces.switchTo(spaces.create("Work", "💼").id).also { now = 9_000L }
        val stamped = spaces.data
        spaces.switchTo(before.current.id)
        spaces.rememberSelection("a")
        spaces.toggleFolder("none")

        assertEquals(stamped.spaces.map { it.modified }, spaces.data.spaces.map { it.modified })
    }

    @Test
    fun `WHEN Spaces are reordered THEN the layout gets the edit time`() {
        val home = spaces.data.current.id
        spaces.create("Work", "💼")
        now = 3_000L

        spaces.move(home, 1)

        assertEquals(3_000L, spaces.data.layoutModified)
    }

    @Test
    fun `THEN the Local flag and edit times survive storage`() {
        spaces.create("Work", "💼")
        val data = spaces.data.copy(spaces = spaces.data.spaces.map { it.copy(isLocal = true) })

        assertEquals(data, SpacesCodec.decode(SpacesCodec.encode(data)))
    }

    @Test
    fun `WHEN an Essential is changed or moved THEN it gets the edit time, and its container bucket is kept`() {
        val repository = InMemoryEssentialsRepository()
        val essentials = Essentials(repository, MutableStateFlow(emptyMap()), clock = { now })
        essentials.pinUrl("https://a.example", "A")
        essentials.pinUrl("https://b.example", "B")
        now = 4_000L

        essentials.move("https://b.example", 0)

        assertEquals(listOf(4_000L, 4_000L), repository.essentials.value.map { it.modified })
        val withBucket = listOf(Essential("https://w.example", "W", id = "w", bucket = "work", modified = 7))
        assertEquals(withBucket, EssentialsCodec.decode(EssentialsCodec.encode(withBucket)).first)
    }

    @Test
    fun `THEN the last-synced store round-trips`() {
        val base = SyncBase(mapOf("layout" to BaseEntry("abc", 1700000100.5), "s1" to BaseEntry("def", 1700000000.0)), 1700000100.5)

        assertEquals(base, SyncBaseCodec.decode(SyncBaseCodec.encode(base)))
        assertEquals(SyncBase(), SyncBaseCodec.decode(null))
    }
}
