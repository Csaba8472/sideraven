/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 0007 ticket 02: Zen Sync's "Spaces on this tablet" list. */
@RunWith(AndroidJUnit4::class)
class SpaceListPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val screen = PreferenceManager(context).createPreferenceScreen(context)

    private val choices =
        listOf(
            SpaceChoice("home", "Home", "🏠", syncs = true, onTablet = true, onDesktop = true),
            SpaceChoice("sk", "Sketches", "✏️", syncs = false, onTablet = true, onDesktop = false),
            SpaceChoice("work", "Work", "💼", syncs = false, onTablet = false, onDesktop = true),
        )

    @Test
    fun `THEN each Space is a switch saying where it lives, and switching reports it`() {
        val switched = mutableListOf<Pair<String, Boolean>>()

        val switches = ZenSyncPreferences.bindSpaceList(context, screen, choices) { id, on -> switched += id to on }

        assertEquals(listOf("🏠 Home", "✏️ Sketches", "💼 Work"), switches.map { it.title.toString() })
        assertEquals(listOf(true, false, false), switches.map { it.isChecked })
        assertEquals(
            listOf("On this tablet and desktop", "Only on this tablet", "Only on desktop"),
            switches.map { it.summary.toString() },
        )
        switches[2].callChangeListener(true)
        switches[0].callChangeListener(false)
        assertEquals(listOf("work" to true, "home" to false), switched)
    }

    @Test
    fun `GIVEN only one Space on the tablet that desktop has THEN its switch can't be turned off`() {
        val switches = ZenSyncPreferences.bindSpaceList(context, screen, choices.filterNot { it.id == "sk" }) { _, _ -> }

        assertFalse(switches[0].isEnabled)
    }
}
