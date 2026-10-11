/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Spec 0007 ticket 03: new desktop Spaces arrive switched off. */
@RunWith(AndroidJUnit4::class)
class NewDesktopSpacesTest {
    private val server = FakeSyncServer()
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync =
        ZenSync(
            { server.credentials },
            server,
            spaces,
            Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
            InMemorySyncBaseRepository(),
            settings,
            desktop = InMemoryDesktopViewStore(),
        )

    private fun setUp(choice: FirstSyncChoice = FirstSyncChoice.Replace) {
        server.put(ZenRecords.space("home", "Home"))
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.layout(listOf("home", "work")))
        runBlocking { zenSync.firstSync(choice) }
        server.requests.clear()
        now = 5_000L
    }

    private fun sync() = runBlocking { zenSync.sync() }

    @Test
    fun `GIVEN First Sync THEN desktop's Spaces count as seen and stay on the tablet`() {
        setUp()

        sync()

        assertEquals(listOf("home", "work"), spaces.data.spaces.map { it.id })
        assertTrue(zenSync.spaceChoices().none { it.isNew })
    }

    @Test
    fun `GIVEN Keep tablet separate THEN desktop's Spaces count as seen too`() {
        setUp(FirstSyncChoice.KeepSeparate)

        sync()

        assertTrue(spaces.data.spaces.any { it.id == "work" })
        assertTrue(zenSync.spaceChoices().none { it.isNew })
    }

    @Test
    fun `WHEN desktop adds a Space THEN it arrives switched off and marked new, untouched on desktop`() {
        setUp()
        server.put(ZenRecords.space("play", "Play", children = listOf("game")))
        server.put(ZenRecords.pinned("game", "https://game.example", "play"))
        server.put(ZenRecords.layout(listOf("home", "play", "work")))

        sync()
        sync()

        assertTrue(spaces.data.spaces.none { it.id == "play" })
        assertTrue(spaces.data.pinned.none { it.id == "game" })
        val play = zenSync.spaceChoices().single { it.id == "play" }
        assertTrue(play.isNew)
        assertFalse(play.onTablet)
        assertTrue(server.writes.none { request -> listOf("play", "game").any { request.url.path.endsWith("/$it") } })
    }

    @Test
    fun `WHEN the user switches a new Space on THEN it comes to the tablet and isn't new any more`() {
        setUp()
        server.put(ZenRecords.space("play", "Play"))
        sync()

        zenSync.setOnTablet("play", true)
        sync()

        assertTrue(spaces.data.spaces.any { it.id == "play" })
        assertFalse(zenSync.spaceChoices().single { it.id == "play" }.isNew)
    }

    @Test
    fun `WHEN the new Spaces have been shown THEN they aren't marked new again`() {
        setUp()
        server.put(ZenRecords.space("play", "Play"))
        sync()

        zenSync.acknowledgeNewSpaces()
        sync()

        val play = zenSync.spaceChoices().single { it.id == "play" }
        assertFalse(play.isNew)
        assertFalse(play.onTablet)
    }

    @Test
    fun `WHEN desktop deletes a Desktop-only Space THEN it leaves the list`() {
        setUp()
        zenSync.setOnTablet("work", false)
        sync()

        server.delete("work")
        sync()

        assertTrue(zenSync.spaceChoices().none { it.id == "work" })
        assertTrue(settings.settings.value.desktopOnly.isEmpty())
    }

    @Test
    fun `THEN a new Space's row says so`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)

        val switches =
            ZenSyncPreferences.bindSpaceList(
                context,
                screen,
                listOf(SpaceChoice("play", "Play", "🎮", syncs = false, onTablet = false, onDesktop = true, isNew = true)),
            ) { _, _ -> }

        assertEquals("New on desktop", switches.single().summary.toString())
    }
}
