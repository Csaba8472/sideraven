/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarTier

/** Sideraven Settings in categories, every choice headed and explained. */
@RunWith(AndroidJUnit4::class)
class SettingsCategoriesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val screen = PreferenceManager(context).createPreferenceScreen(context)
    private val store = InMemorySidebarModeStore()

    private fun PreferenceGroup.children() = (0 until preferenceCount).map { getPreference(it) }.sortedBy { it.order }

    @Test
    fun `THEN the first screen lists the categories, each saying what it holds, and opens them`() {
        val opened = mutableListOf<SettingsCategory>()
        SideravenSettings.installMenu(context, screen, SidebarTier.Tablet) { opened += it }

        val rows = screen.children().filter { it.isVisible && it.key?.startsWith(SideravenSettings.CATEGORY_PREFIX) == true }
        assertEquals(listOf("Sidebar", "Tabs", "Address bar & search", "Look and feel", "Zen Sync", "Backup"), rows.map { it.title.toString() })
        rows.forEach { assertTrue(it.title.toString(), !it.summary.isNullOrBlank()) }
        rows[1].performClick()
        assertEquals(listOf(SettingsCategory.Tabs), opened)
    }

    @Test
    fun `GIVEN a window without a Sidebar THEN the Sidebar (for its phone switch), Zen Sync and Backup are offered, with the note saying why`() {
        SideravenSettings.installMenu(context, screen, SidebarTier.None) {}

        val rows = screen.children().filter { it.isVisible && it.key?.startsWith(SideravenSettings.CATEGORY_PREFIX) == true }
        assertEquals(listOf("Sidebar", "Zen Sync", "Backup"), rows.map { it.title.toString() })
        assertTrue(screen.findPreference<Preference>(SideravenSettings.NARROW_NOTE_KEY)!!.isVisible)
    }

    @Test
    fun `THEN every choice on every category screen has a heading, and an explanation only where it has one`() {
        SettingsCategory.entries.filter { it != SettingsCategory.ZenSync }.forEach { category ->
            val page = PreferenceManager(context).createPreferenceScreen(context)
            if (category == SettingsCategory.Backup) {
                BackupPreferences.install(context, page, onExport = {}, onImport = {})
            } else {
                SidebarPreferences.install(context, page, category, SidebarTier.Tablet, store)
            }

            val groups = page.children().filterIsInstance<PreferenceCategory>()
            assertFalse("$category has groups", groups.isEmpty())
            groups.forEach { group ->
                assertFalse("${group.key} has a heading", group.title.isNullOrBlank())
                // Fenix's way: most choices speak for themselves; an explanation is never blank.
                val about = group.children().firstOrNull { it.key.endsWith(".about") }
                assertTrue("${group.key}'s explanation isn't blank", about == null || !about.summary.isNullOrBlank())
                assertTrue("${group.key} holds a choice", group.children().any { !it.key.endsWith(".about") })
            }
        }
    }

    @Test
    fun `THEN the Sidebar screen holds its mode, position and size, buttons, how to open it and the phone switch`() {
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.Tablet, store)

        assertEquals(
            listOf("Mode", "Position and size", "Buttons in the sidebar", "Opening the sidebar", "Phones"),
            screen.children().filterIsInstance<PreferenceCategory>().map { it.title.toString() },
        )
    }

    @Test
    fun `THEN the accent is chosen from colour swatches`() {
        SidebarPreferences.install(context, screen, SettingsCategory.LookAndFeel, SidebarTier.Tablet, store)

        val swatches = screen.findPreference<AccentSwatchesPreference>(SidebarPreferences.ACCENT_KEY)!!
        swatches.choose("teal")

        assertEquals("teal", store.accent.value)
        assertEquals("teal", swatches.selected)
    }

    @Test
    fun `WHEN Use the sidebar on phones is switched off THEN the setting follows`() {
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.Phone, store)
        val phone = screen.findPreference<androidx.preference.SwitchPreferenceCompat>(SidebarPreferences.PHONE_SIDEBAR_KEY)!!
        assertTrue(phone.isChecked)

        phone.onPreferenceChangeListener!!.onPreferenceChange(phone, false)

        assertFalse(store.phoneSidebar.value)
    }

    @Test
    fun `GIVEN a phone THEN the Swipe Zone switch shows the phone's own setting, off by default`() {
        SidebarPreferences.install(context, screen, SettingsCategory.Sidebar, SidebarTier.Phone, store)
        val zone = screen.findPreference<androidx.preference.SwitchPreferenceCompat>(SidebarPreferences.SWIPE_ZONE_KEY)!!

        assertFalse(zone.isChecked)
        zone.onPreferenceChangeListener!!.onPreferenceChange(zone, true)
        assertTrue(store.phoneSwipeZone.value)
        assertTrue("the tablet setting is untouched", store.swipeZoneEnabled.value)
    }
}
