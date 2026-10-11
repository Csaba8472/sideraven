/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.sync.InMemoryBlobStore
import pt.dweep.sideraven.sync.ZenBlobs
import pt.dweep.sideraven.sync.ZenOrigin

/** v1 prep ticket 01: a backup file restores Spaces, Essentials and settings on a fresh install. */
@RunWith(AndroidJUnit4::class)
class SideravenBackupTest {
    private val icon = "data:image/png;base64," + "iVBORw0KGgo".repeat(400)

    private val work = Space("work", "Work", "💼", "blue")
    private val spaces =
        SpacesData(
            spaces = listOf(Space("home", "Home", "🏠"), work),
            currentSpaceId = "work",
            pinned = listOf(PinnedTab("p1", "work", "https://mail.example", "Mail", folderId = "f1")),
            folders = listOf(Folder("f1", "work", "Inbox")),
        )

    private lateinit var essentials: List<Essential>

    @Before
    fun setUp() {
        ZenBlobs.store = InMemoryBlobStore()
        essentials =
            listOf(
                Essential("https://cal.example", "Calendar", customName = "Agenda"),
                Essential("https://zen.example", "Zen", extra = ZenOrigin("rid-1", JSONObject().put("url", "https://zen.example").put("icon", icon)).encode()),
            )
    }

    private fun spacesOf(repository: InMemorySpacesRepository) = Spaces(repository, pinLinks = kotlinx.coroutines.flow.MutableStateFlow(emptyMap()))

    private fun essentialsOf(repository: InMemoryEssentialsRepository) = Essentials(repository, kotlinx.coroutines.flow.MutableStateFlow(emptyMap()))

    private fun source(): SidebarModeStore =
        InMemorySidebarModeStore().apply {
            chosenMode.set(SidebarMode.Collapsed)
            sidebarSide.set(SidebarSide.Right)
            haptics.set(false)
            accent.set("green")
            flapPosition.set(0.25f)
        }

    @Test
    fun `WHEN a backup is exported and imported into a fresh install THEN everything comes back`() {
        val text = SideravenBackup.export(spaces, essentials, source())
        val spacesRepo = InMemorySpacesRepository()
        val essentialsRepo = InMemoryEssentialsRepository()
        val fresh = InMemorySidebarModeStore()

        val result = SideravenBackup.import(text, spacesOf(spacesRepo), essentialsOf(essentialsRepo), fresh)

        assertEquals(SideravenBackup.Result.Restored, result)
        assertEquals(spaces, spacesRepo.data.value)
        assertEquals(essentials.map { it.url to it.displayName }, essentialsRepo.essentials.value.map { it.url to it.displayName })
        assertEquals(SidebarMode.Collapsed, fresh.chosenMode.value)
        assertEquals(SidebarSide.Right, fresh.sidebarSide.value)
        assertFalse(fresh.haptics.value)
        assertEquals("green", fresh.accent.value)
        assertEquals(0.25f, fresh.flapPosition.value)
    }

    @Test
    fun `THEN Zen favicons are written in full, and go back to the favicon store on import`() {
        val text = SideravenBackup.export(spaces, essentials, source())
        val saved = org.json.JSONObject(text).getJSONArray("essentials").let { list -> (0 until list.length()).map { list.getJSONObject(it) } }
        val zenExtra = saved.single { it.getString("url") == "https://zen.example" }.getString("extra")
        assertEquals("inline in the file", icon, ZenOrigin.decode(zenExtra)!!.data.getString("icon"))

        ZenBlobs.store = InMemoryBlobStore()
        val essentialsRepo = InMemoryEssentialsRepository()
        SideravenBackup.import(text, spacesOf(InMemorySpacesRepository()), essentialsOf(essentialsRepo), InMemorySidebarModeStore())

        val stored = essentialsRepo.essentials.value.single { it.url == "https://zen.example" }.extra!!
        assertFalse("slim again once stored", stored.contains("data:image"))
        assertEquals(icon, ZenOrigin.decode(stored)!!.fullData().getString("icon"))
    }

    @Test
    fun `GIVEN a file that isn't a backup, or is from a newer Sideraven THEN it's refused and nothing changes`() {
        val spacesRepo = InMemorySpacesRepository()
        val fresh = InMemorySidebarModeStore()
        val newer = JSONObject(SideravenBackup.export(spaces, essentials, source())).put("version", 99).toString()

        assertEquals(SideravenBackup.Result.NotABackup, SideravenBackup.import("hello", spacesOf(spacesRepo), essentialsOf(InMemoryEssentialsRepository()), fresh))
        assertEquals(SideravenBackup.Result.NotABackup, SideravenBackup.import("""{"format":"something-else"}""", spacesOf(spacesRepo), essentialsOf(InMemoryEssentialsRepository()), fresh))
        assertEquals(SideravenBackup.Result.TooNew, SideravenBackup.import(newer, spacesOf(spacesRepo), essentialsOf(InMemoryEssentialsRepository()), fresh))
        assertNull(spacesRepo.data.value)
        assertEquals(null, fresh.chosenMode.value)
    }

    @Test
    fun `GIVEN unknown or mistyped settings THEN they're skipped and the rest applied`() {
        val json = JSONObject(SideravenBackup.export(spaces, essentials, source()))
        json.getJSONObject("settings").put("haptics", "not a boolean").put("from_the_future", true).put("sidebar_side", "Diagonal")
        val fresh = InMemorySidebarModeStore()

        SideravenBackup.import(json.toString(), spacesOf(InMemorySpacesRepository()), essentialsOf(InMemoryEssentialsRepository()), fresh)

        assertTrue("default kept", fresh.haptics.value)
        assertEquals(SidebarSide.Left, fresh.sidebarSide.value)
        assertEquals("green", fresh.accent.value)
    }
}
