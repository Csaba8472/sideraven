/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag
import java.io.IOException

/** Zen Sync ticket 05: when syncs run, and what the user sees. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ZenSyncRunnerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val server = FakeSyncServer()
    private var online = true
    private val transport = SyncHttpTransport { if (online) server.execute(it) else throw IOException("offline") }
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
    private val settings = InMemoryZenSyncSettingsStore(ZenSyncSettings(enabled = true, firstSyncDone = true))
    private val zenSync =
        ZenSync({ server.credentials }, transport, spaces, Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())), InMemorySyncBaseRepository(), settings)

    private fun runner(scope: TestScope) = ZenSyncRunner(zenSync, settings, scope, io = StandardTestDispatcher(scope.testScheduler), clock = { 42L })

    @Test
    fun `WHEN local changes come in a burst THEN one sync runs after the quiet period`() =
        runTest {
            server.put(ZenRecords.layout(emptyList()))
            val runner = runner(this)

            repeat(5) { runner.localChanged() }
            advanceTimeBy(ZenSyncRunner.DEBOUNCE_MS - 1)
            assertEquals(0, server.requests.size)
            advanceUntilIdle()

            assertEquals(1, server.requests.count { it.url.host == "token.example.com" })
            assertEquals(42L, runner.status.value.lastSynced)
        }

    @Test
    fun `WHEN a sync fails THEN the problem is reported, and a later success clears it`() =
        runTest {
            val runner = runner(this)
            online = false
            runner.syncNow()
            advanceUntilIdle()
            assertEquals(ZenSyncStatus.Reason.Network, runner.status.value.problem)

            online = true
            runner.syncNow()
            advanceUntilIdle()
            assertNull(runner.status.value.problem)
        }

    @Test
    fun `GIVEN Zen Sync is off THEN nothing runs`() =
        runTest {
            settings.save(ZenSyncSettings(enabled = false))
            val runner = runner(this)

            runner.syncNow()
            runner.localChanged()
            advanceUntilIdle()

            assertEquals(0, server.requests.size)
        }

    private fun tokenCalls() = server.requests.count { it.url.host == "token.example.com" }

    @Test
    fun `GIVEN Sync automatically WHEN the app is in front THEN it syncs at once and every 5 minutes, until it goes to the back`() =
        runTest {
            server.put(ZenRecords.layout(emptyList()))
            val runner = runner(this)

            runner.inForeground(true)
            advanceTimeBy(1)
            assertEquals(1, tokenCalls())
            advanceTimeBy(ZenSyncRunner.PERIOD_MS)
            assertEquals(2, tokenCalls())
            runner.inForeground(false)
            advanceTimeBy(ZenSyncRunner.PERIOD_MS * 3)

            assertEquals(2, tokenCalls())
        }

    @Test
    fun `GIVEN Sync automatically off THEN only Sync now syncs`() =
        runTest {
            server.put(ZenRecords.layout(emptyList()))
            settings.save(settings.settings.value.copy(syncAutomatically = false))
            val runner = runner(this)

            runner.autoSync()
            runner.localChanged()
            runner.inForeground(true)
            advanceTimeBy(ZenSyncRunner.PERIOD_MS * 2)
            assertEquals(0, tokenCalls())

            runner.syncNow()
            advanceTimeBy(1)
            runner.inForeground(false)
            advanceUntilIdle()
            assertEquals(1, tokenCalls())
        }

    @Test
    fun `WHEN Sync automatically is turned on while the app is in front THEN the next tick syncs`() =
        runTest {
            server.put(ZenRecords.layout(emptyList()))
            settings.save(settings.settings.value.copy(syncAutomatically = false))
            val runner = runner(this)
            runner.inForeground(true)
            advanceTimeBy(1)
            assertEquals(0, tokenCalls())

            settings.save(settings.settings.value.copy(syncAutomatically = true))
            advanceTimeBy(ZenSyncRunner.PERIOD_MS)
            runner.inForeground(false)

            assertEquals(1, tokenCalls())
        }

    @Test
    fun `GIVEN a sync problem THEN the Space row shows the problem dot`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true)),
                syncProblem = true,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }

        assertNotNull(composeRule.onNodeWithTag(SidebarTestTag.SYNC_PROBLEM).fetchSemanticsNode())
    }
}
