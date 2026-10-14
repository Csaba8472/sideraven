/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Reading (only reading) what desktop has. */
@RunWith(AndroidJUnit4::class)
class ReadOnlyConnectTest {
    private val server = FakeSyncServer(pageSize = 2)
    private val signedIn = ZenAccount { server.credentials }

    private fun check(account: ZenAccount = signedIn) = runBlocking { ZenSyncCheck.run(account, server) }

    private fun desktop() {
        server.put(ZenRecords.space("s1", "Work", children = listOf("t1")))
        server.put(ZenRecords.space("s2", "Home"))
        server.put(ZenRecords.pinned("t1", "https://mail.example", "s1"))
        server.put(ZenRecords.pinned("t2", "https://docs.example", "s2"))
        server.put(ZenRecords.essential("e1", "https://music.example"))
        server.put(ZenRecords.normal("n1", "https://news.example", "s1"))
        server.put(ZenRecords.folder("f1", "Docs", "s1"))
        server.put(ZenRecords.layout(listOf("s1", "s2"), mapOf("default" to listOf("e1"))))
    }

    @Test
    fun `GIVEN signed in WHEN desktop has Spaces THEN their counts are shown, across pages, ignoring normal tabs`() {
        desktop()

        assertEquals(ZenSyncStatus.Connected(ZenSummary(spaces = 2, pinnedTabs = 2, essentials = 1, folders = 1)), check())
    }

    @Test
    fun `THEN nothing is ever written`() {
        desktop()
        check()

        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `GIVEN signed out THEN the status asks to sign in, without contacting the server`() {
        assertEquals(ZenSyncStatus.SignedOut, check(ZenAccount { null }))
        assertTrue(server.requests.isEmpty())
    }

    @Test
    fun `GIVEN the account has no Sync keys THEN that is reported, and nothing is created`() {
        server.hasKeys = false
        desktop()

        assertEquals(ZenSyncStatus.Failed(ZenSyncStatus.Reason.KeysMissing), check())
        assertTrue(server.writes.isEmpty())
    }

    @Test
    fun `GIVEN desktop hasn't synced Spaces THEN the status says so`() {
        assertEquals(ZenSyncStatus.NothingFromDesktop, check())
    }

    @Test
    fun `GIVEN no network THEN a network problem is reported`() {
        val offline = SyncHttpTransport { throw IOException("offline") }

        assertEquals(ZenSyncStatus.Failed(ZenSyncStatus.Reason.Network), runBlocking { ZenSyncCheck.run(signedIn, offline) })
    }

    @Test
    fun `GIVEN Firefox signed out on the server side THEN sign-in is asked for`() {
        val expired = ZenAccount { server.credentials.copy(accessToken = "expired") }

        assertEquals(ZenSyncStatus.Failed(ZenSyncStatus.Reason.SignIn), check(expired))
    }

    @Test
    fun `THEN each status reads as a sentence in settings`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertEquals("2 spaces, 5 pinned tabs, 3 Essentials", ZenSyncText.describe(context, ZenSyncStatus.Connected(ZenSummary(2, 5, 3, 0))))
        assertEquals("Sign in to Firefox Sync first", ZenSyncText.describe(context, ZenSyncStatus.SignedOut))
    }
}
