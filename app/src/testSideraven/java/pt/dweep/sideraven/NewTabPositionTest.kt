/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.settings.SidebarPreferences

/** Spec 0006 ticket 12: new tabs appear at the top or the bottom of the Tab List. */
@RunWith(AndroidJUnit4::class)
class NewTabPositionTest {
    private fun tabListAfterOpening(position: NewTabPosition): List<String> {
        val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware { position }))
        listOf("first", "second", "third").forEach { id ->
            store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", id = id)))
        }
        return store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser).tabs.map { it.id }
    }

    @Test
    fun `THEN new tabs appear at the top, or at the bottom when chosen`() {
        assertEquals(listOf("third", "second", "first"), tabListAfterOpening(NewTabPosition.Top))
        assertEquals(listOf("first", "second", "third"), tabListAfterOpening(NewTabPosition.Bottom))
    }

    @Test
    fun `THEN the position is a radio list writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.bindNewTabPosition(context, screen, store, context.getSharedPreferences("radio-test", 0))
            .first { it.key.endsWith(".Bottom") }
            .callChangeListener(true)

        assertEquals(NewTabPosition.Bottom, store.newTabPosition.value)
    }
}
