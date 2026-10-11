/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** v1 prep review: a backup carries no open tabs, leaves open tabs where they are, and a damaged one changes nothing. */
@RunWith(AndroidJUnit4::class)
class BackupReviewTest {
    private val home = Space("home", "Home", "🏠", lastSelectedTabId = "tab-1")
    private val work = Space("work", "Work", "💼", lastSelectedTabId = "tab-2")
    private val saved =
        SpacesData(
            spaces = listOf(home, work),
            currentSpaceId = "home",
            assignments = mapOf("tab-1" to "home", "tab-2" to "work"),
        )
    private val essentials = listOf(Essential("https://cal.example", "Calendar"))

    private fun spaces(initial: SpacesData?) = Spaces(InMemorySpacesRepository(initial), pinLinks = MutableStateFlow(emptyMap()))

    private fun essentials(initial: List<Essential> = emptyList()) = Essentials(InMemoryEssentialsRepository(initial), MutableStateFlow(emptyMap()))

    @Test
    fun `THEN the file holds no open tabs`() {
        val json = JSONObject(SideravenBackup.export(saved, essentials, InMemorySidebarModeStore()))

        val restored = SpacesCodec.decode(json.getJSONObject("spaces").toString())!!
        assertEquals(emptyMap<String, String>(), restored.assignments)
        assertNull(restored.spaces.first().lastSelectedTabId)
    }

    @Test
    fun `WHEN a backup is imported THEN open tabs keep their Spaces`() {
        val text = SideravenBackup.export(saved, essentials, InMemorySidebarModeStore())
        // On this device, a live tab sits in Work.
        val live = spaces(SpacesData(spaces = listOf(home, work), currentSpaceId = "home", assignments = mapOf("live-tab" to "work")))

        SideravenBackup.import(text, live, essentials(), InMemorySidebarModeStore())

        assertEquals("work", live.data.spaceOf("live-tab"))
    }

    @Test
    fun `GIVEN a backup whose Essentials or Spaces are damaged, or missing THEN it's refused and nothing changes`() {
        val good = JSONObject(SideravenBackup.export(saved, essentials, InMemorySidebarModeStore()))
        val brokenEssentials = JSONObject(good.toString()).put("essentials", JSONArray().put(JSONObject().put("title", "no url")))
        val noSpaces = JSONObject(good.toString()).apply { remove("spaces") }
        val mine = essentials(listOf(Essential("https://mine.example", "Mine")))
        val store = InMemorySidebarModeStore()

        listOf(brokenEssentials, noSpaces).forEach { file ->
            assertEquals(SideravenBackup.Result.NotABackup, SideravenBackup.import(file.toString(), spaces(null), mine, store))
        }
        assertEquals(listOf("https://mine.example"), mine.essentials.value.map { it.url })
        assertFalse("chosen mode untouched", store.chosenMode.value != null)
    }
}
