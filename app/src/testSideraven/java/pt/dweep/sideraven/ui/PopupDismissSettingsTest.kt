/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Whether hiding the keyboard or tapping outside closes the Search Pop-up. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PopupDismissSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN tapping outside is set not to close THEN a tap on the dimmed layer keeps the pop-up open`() {
        var dismissed = 0
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = { dismissed++ },
                    options = SearchOptions(closeOnTapOutside = false),
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_SCRIM).performTouchInput { click(Offset(4f, 4f)) }

        assertEquals(0, dismissed)
    }

    @Test
    fun `THEN Sideraven Settings has both switches, writing the store, and hiding the keyboard keeps the pop-up open by default`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        val (keyboard, outside) = SidebarPreferences.bindPopupSwitches(context, screen, store)
        // Hiding the keyboard leaves room to scroll the suggestions, so it doesn't close the pop-up unless asked.
        assertEquals(false, keyboard.isChecked)
        assertEquals(true, outside.isChecked)

        keyboard.callChangeListener(true)
        outside.callChangeListener(false)

        assertEquals(true, store.popupCloseOnKeyboardHide.value)
        assertFalse(store.popupCloseOnTapOutside.value)
    }
}
