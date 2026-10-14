/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.PinnedAddressFollow
import pt.dweep.sideraven.Spaces

/** Open tabs follow when desktop changes an Essential's or Pinned Tab's address. */
@RunWith(AndroidJUnit4::class)
class FollowAddressChangeTest {
    private val server = FakeSyncServer()
    private val essentialLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    private val pinLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    private val essentials = Essentials(InMemoryEssentialsRepository(), essentialLinks)
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = pinLinks)
    private val moved = mutableListOf<Triple<String, String, String>>()
    private val zenSync =
        ZenSync(
            { server.credentials },
            server,
            spaces,
            essentials,
            InMemorySyncBaseRepository(),
            InMemoryZenSyncSettingsStore(),
            desktop = InMemoryDesktopViewStore(),
            followAddress = { tabId, old, new -> moved += Triple(tabId, old, new) },
        )

    private val post = "https://www.reddit.com/r/x/comments/1"
    private val home = "https://www.reddit.com/"

    private fun setUp() {
        server.put(ZenRecords.essential("reddit", post, "Reddit"))
        server.put(ZenRecords.space("work", "Work", children = listOf("mail")))
        server.put(ZenRecords.pinned("mail", "https://mail.example/inbox", "work"))
        server.put(ZenRecords.layout(listOf("work"), mapOf("default" to listOf("reddit"))))
        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }
        essentials.link(post, "tab-e")
        pinLinks.value = mapOf("mail" to "tab-p")
    }

    @Test
    fun `WHEN desktop changes an Essential's address THEN its open tab stays linked and is told to follow`() {
        setUp()
        server.put(ZenRecords.essential("reddit", home, "Reddit"))

        runBlocking { zenSync.sync() }

        assertEquals(home, essentials.essentials.value.single().url)
        assertEquals(mapOf(home to "tab-e"), essentialLinks.value)
        assertEquals(listOf(Triple("tab-e", post, home)), moved)
    }

    @Test
    fun `WHEN desktop changes a Pinned Tab's address THEN its open tab is told to follow`() {
        setUp()
        server.put(ZenRecords.pinned("mail", "https://mail.example/", "work"))

        runBlocking { zenSync.sync() }

        assertEquals(listOf(Triple("tab-p", "https://mail.example/inbox", "https://mail.example/")), moved)
        assertEquals(mapOf("mail" to "tab-p"), pinLinks.value)
    }

    @Test
    fun `THEN a tab still on the old pinned page loads the new one, and a tab browsed away stays`() {
        val loaded = mutableListOf<Pair<String, String>>()
        val browser =
            BrowserState(tabs = listOf(createTab(post, id = "on-pin"), createTab("https://www.reddit.com/r/other", id = "away")))

        PinnedAddressFollow.follow(browser, "on-pin", post, home) { url, tab -> loaded += url to tab }
        PinnedAddressFollow.follow(browser, "away", post, home) { url, tab -> loaded += url to tab }
        PinnedAddressFollow.follow(browser, "gone", post, home) { url, tab -> loaded += url to tab }

        assertEquals(listOf(home to "on-pin"), loaded)
        assertNull(browser.tabs.firstOrNull { it.id == "gone" })
    }
}
