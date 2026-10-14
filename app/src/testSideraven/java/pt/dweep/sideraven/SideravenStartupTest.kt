/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sideraven's stores load in the background as the app starts. */
@RunWith(AndroidJUnit4::class)
class SideravenStartupTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `WHEN the app starts THEN the stores load on the background dispatcher, and the Sidebar then gets those same stores`() {
        val scheduler = TestCoroutineScheduler()
        val job = SideravenStartup.preload(context, StandardTestDispatcher(scheduler))
        assertFalse("nothing runs on the caller's thread", job.isCompleted)

        scheduler.advanceUntilIdle()
        runBlocking { job.join() }

        assertTrue(job.isCompleted)
        val loaded = SideravenStartup.stores(context)
        assertTrue(loaded.any { it === PreferencesEssentialsRepository.get(context) })
        assertTrue(loaded.any { it === Spaces.get(context) })
        assertTrue(loaded.any { it === PreferencesSidebarModeStore.get(context) })
    }
}
