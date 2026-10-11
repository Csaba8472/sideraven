/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0007 ticket 07: turning Swipe to close off. */
@RunWith(AndroidJUnit4::class)
class SwipeToCloseSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN Swipe to close is off WHEN a tab is swiped left THEN nothing is closed`() {
        val intents = mutableListOf<SidebarIntent>()
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("3", "Three", "https://three.example", icon = null, isSelected = false)),
                look = SidebarLook(swipeToClose = false),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onNodeWithContentDescription("Three").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(emptyList<SidebarIntent>(), intents.filterIsInstance<SidebarIntent.CloseTab>())
    }

    @Test
    fun `THEN it is a switch writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        val switch: SwitchPreferenceCompat = SidebarPreferences.switchFor(context, screen, SidebarPreferences.SWIPE_TO_CLOSE_KEY, store.swipeToClose, R.string.sideraven_settings_swipe_close, R.string.sideraven_settings_swipe_close_summary)
        switch.callChangeListener(false)

        assertFalse(store.swipeToClose.value)
    }
}
