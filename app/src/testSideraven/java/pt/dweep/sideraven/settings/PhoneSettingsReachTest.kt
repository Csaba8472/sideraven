/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLooper
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarTier

/** Polish round 2 review: on a phone with the Sidebar off, the switch to turn it back on stays reachable. */
@RunWith(AndroidJUnit4::class)
class PhoneSettingsReachTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = InMemorySidebarModeStore()

    private fun screen(): PreferenceScreen = PreferenceManager(context).createPreferenceScreen(context)

    private fun PreferenceScreen.groups() = (0 until preferenceCount).map { getPreference(it) }.filterIsInstance<PreferenceCategory>().filter { it.isVisible }.map { it.title.toString() }

    @Test
    fun `GIVEN a phone with the Sidebar off THEN the menu still offers the Sidebar category`() {
        val screen = screen()
        SideravenSettings.installMenu(context, screen, SidebarTier.None) {}

        val sidebar = screen.findPreference<androidx.preference.Preference>(SideravenSettings.CATEGORY_PREFIX + "Sidebar")!!
        assertTrue(sidebar.isVisible)
    }

    @Test
    fun `GIVEN a phone with the Sidebar off THEN its Sidebar screen holds only the phone switch`() {
        val screen = screen()
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.None, store, context.getSharedPreferences("reach-test", 0))

        assertEquals(listOf("Sidebar on phones"), screen.groups())
    }

    @Test
    fun `WHEN the phone switch is turned back on THEN the screen rebuilds with the Sidebar's options`() {
        store.phoneSidebar.set(false)
        val screen = screen()
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.None, store, context.getSharedPreferences("reach-test", 0)) { store.phoneSidebar.value.let { if (it) SidebarTier.Phone else SidebarTier.None } }
        val phone = screen.findPreference<SwitchPreferenceCompat>(SidebarPreferences.PHONE_SIDEBAR_KEY)!!

        phone.onPreferenceChangeListener!!.onPreferenceChange(phone, true)
        ShadowLooper.idleMainLooper()

        assertTrue(store.phoneSidebar.value)
        assertTrue("rebuilt: ${screen.groups()}", "Opening the sidebar" in screen.groups())
    }
}
