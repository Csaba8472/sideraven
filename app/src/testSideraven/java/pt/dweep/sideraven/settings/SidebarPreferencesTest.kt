/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTier

/** Ticket 07, and device-test feedback: Sideraven's choices are Fenix's inline radio lists. */
@RunWith(AndroidJUnit4::class)
class SidebarPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val group = PreferenceManager(context).createPreferenceScreen(context)
    private val checked = context.getSharedPreferences("radio-test", Context.MODE_PRIVATE)

    private fun modes(
        tier: SidebarTier,
        store: InMemorySidebarModeStore,
    ) = SidebarPreferences.bindMode(context, group, tier, store, checked)

    private fun offered(tier: SidebarTier, store: InMemorySidebarModeStore) =
        modes(tier, store).filter { it.isVisible }.map { it.key.substringAfterLast('.') }

    private fun checkedOne(prefix: String) = checked.all.filter { (key, value) -> key.startsWith(prefix) && value == true }.keys.map { it.substringAfterLast('.') }

    @Test
    fun `GIVEN a tablet THEN all three modes are listed and the current one is checked`() {
        assertEquals(listOf("Persistent", "Collapsed", "AutoHide"), offered(SidebarTier.Tablet, InMemorySidebarModeStore()))
        assertEquals(listOf("Persistent"), checkedOne(SidebarPreferences.MODE_KEY))
    }

    @Test
    fun `GIVEN a large phone THEN Persistent is not listed and a Persistent choice shows as Collapsed`() {
        assertEquals(listOf("Collapsed", "AutoHide"), offered(SidebarTier.LargePhone, InMemorySidebarModeStore(SidebarMode.Persistent)))
        assertEquals(listOf("Collapsed"), checkedOne(SidebarPreferences.MODE_KEY))
    }

    @Test
    fun `WHEN a mode is picked THEN it becomes the stored choice, as with the Toggle`() {
        val store = InMemorySidebarModeStore()
        val radios = modes(SidebarTier.Tablet, store)

        radios.first { it.key.endsWith(".AutoHide") }.callChangeListener(true)
        // Fenix's radio group reports the others being unchecked; that changes nothing.
        radios.first { it.key.endsWith(".Persistent") }.callChangeListener(false)

        assertEquals(SidebarMode.AutoHide, store.chosenMode.value)
    }

    @Test
    fun `WHEN the address bar is moved to the Sidebar THEN the layout store follows`() {
        val store = InMemorySidebarModeStore()
        val radios = SidebarPreferences.bindLayout(context, group, store, checked)

        radios.first { it.key.endsWith(".Sidebar") }.callChangeListener(true)

        assertEquals(AddressBarLayout.Sidebar, store.addressBarLayout.value)
    }
}
