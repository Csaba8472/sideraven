/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.NewTabBehaviour

/** Address Bar Layout ticket 01: Sideraven Settings, and its entry in Fenix's Settings. */
@RunWith(AndroidJUnit4::class)
class SideravenSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val screen = PreferenceManager(context).createPreferenceScreen(context)

    @Test
    fun `THEN the Sideraven entry comes first in General and opens Sideraven Settings`() {
        val general = PreferenceCategory(context).also { screen.addPreference(it) }
        general.addPreference(Preference(context).apply { key = "search" })
        var opened = 0

        val entry = SideravenSettings.entry(context, general) { opened++ }
        entry.performClick()

        assertEquals("Sideraven", entry.title)
        assertTrue(entry.order < general.findPreference<Preference>("search")!!.order)
        assertEquals(1, opened)
    }

    @Test
    fun `GIVEN a window without a Sidebar THEN a note explains the missing Sidebar options, and it hides on a wide window`() {
        assertTrue(SideravenSettings.narrowNote(context, screen, showsSidebar = false).isVisible)
        assertFalse(SideravenSettings.narrowNote(context, screen, showsSidebar = true).isVisible)
    }

    @Test
    fun `THEN the moved New Tab setting still writes the same store`() {
        val store = InMemorySidebarModeStore()
        val radios = SidebarPreferences.bindNewTab(context, screen, store, context.getSharedPreferences("radio-test", 0))

        radios.first { it.key.endsWith(".Blank") }.callChangeListener(true)

        assertEquals(NewTabBehaviour.Blank, store.newTabBehaviour.value)
    }
}
