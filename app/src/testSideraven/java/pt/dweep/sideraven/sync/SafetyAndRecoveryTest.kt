/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces

/** Never damage a newer Zen's data; recover by itself; never create keys. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SafetyAndRecoveryTest {
    private val server = FakeSyncServer()
    private var signedIn = true
    private val account = ZenAccount { if (signedIn) server.credentials else null }
    private var now = 1_000L
    private val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()), clock = { now })
    private val settings = InMemoryZenSyncSettingsStore()
    private val zenSync =
        ZenSync(account, server, spaces, Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())), InMemorySyncBaseRepository(), settings)

    private fun setUp() {
        server.put(ZenRecords.space("work", "Work"))
        server.put(ZenRecords.pinned("mail", "https://mail.example", "work"))
        server.put(ZenRecords.layout(listOf("work")))
        runBlocking { zenSync.firstSync(FirstSyncChoice.KeepSeparate) }
        server.requests.clear()
        now = 5_000L
    }

    @Test
    fun `GIVEN desktop's data is a newer version THEN desktop changes arrive but nothing is written`() {
        setUp()
        server.engineVersion = ZenConnection.SUPPORTED_ENGINE_VERSION + 1
        spaces.renamePin("mail", "Inbox")
        server.put(ZenRecords.space("work", "Office"))

        val outcome = runBlocking { zenSync.sync() }

        assertEquals(SyncOutcome.ReadOnly, outcome)
        assertTrue(server.writes.isEmpty())
        assertEquals("Office", spaces.data.spaces.first { it.id == "work" }.name)
        // The tablet's own change is kept, waiting.
        assertEquals("Inbox", spaces.data.pinned.first { it.id == "mail" }.customName)
    }

    @Test
    fun `GIVEN the supported version THEN syncing writes as usual`() {
        setUp()
        server.engineVersion = ZenConnection.SUPPORTED_ENGINE_VERSION
        spaces.renamePin("mail", "Inbox")

        assertEquals(SyncOutcome.Synced, runBlocking { zenSync.sync() })
        assertEquals("Inbox", server.cleartext("mail")!!.getJSONObject("data").getString("staticLabel"))
    }

    @Test
    fun `GIVEN the account has no Sync keys THEN syncing fails without creating any`() {
        setUp()
        server.hasKeys = false

        try {
            runBlocking { zenSync.sync() }
            fail("expected a failure")
        } catch (e: SyncError.KeysMissing) {
            assertTrue(server.writes.isEmpty())
        }
    }

    @Test
    fun `WHEN Firefox signs out and back in THEN the problem shows, then clears by itself`() =
        runTest {
            setUp()
            val runner = ZenSyncRunner(zenSync, settings, this, io = StandardTestDispatcher(testScheduler), clock = { 7L })

            signedIn = false
            runner.syncNow()
            advanceUntilIdle()
            assertEquals(ZenSyncStatus.Reason.SignIn, runner.status.value.problem)

            signedIn = true
            runner.syncNow()
            advanceUntilIdle()
            assertNull(runner.status.value.problem)
            assertEquals(7L, runner.status.value.lastSynced)
        }

    @Test
    fun `GIVEN a newer version THEN the runner reports it`() =
        runTest {
            setUp()
            server.engineVersion = ZenConnection.SUPPORTED_ENGINE_VERSION + 1
            val runner = ZenSyncRunner(zenSync, settings, this, io = StandardTestDispatcher(testScheduler))

            runner.syncNow()
            advanceUntilIdle()

            assertEquals(ZenSyncStatus.Reason.NewerVersion, runner.status.value.problem)
        }
}
