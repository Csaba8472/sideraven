/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarTier
import java.time.LocalDate

/** Sideraven Settings has a Backup screen with export and import. */
@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `THEN the menu offers Backup, even without a Sidebar`() {
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        SideravenSettings.installMenu(context, screen, SidebarTier.None) {}

        assertTrue(screen.findPreference<Preference>(SideravenSettings.CATEGORY_PREFIX + "Backup")!!.isVisible)
    }

    @Test
    fun `WHEN Export and Import are tapped THEN they open the file pickers, and the export suggests a dated name`() {
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val tapped = mutableListOf<String>()
        BackupPreferences.install(context, screen, onExport = { tapped += "export" }, onImport = { tapped += "import" })

        screen.findPreference<Preference>(BackupPreferences.EXPORT_KEY)!!.performClick()
        screen.findPreference<Preference>(BackupPreferences.IMPORT_KEY)!!.performClick()

        assertEquals(listOf("export", "import"), tapped)
        assertEquals("sideraven-backup-2026-10-10.json", BackupPreferences.fileName(LocalDate.of(2026, 10, 10)))
    }
}
