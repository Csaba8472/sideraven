/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** The Haptic ticks setting. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class HapticsSettingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val ticks = mutableListOf<HapticFeedbackType>()
    private val fake =
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                ticks += hapticFeedbackType
            }
        }

    private fun longPressSpace(haptics: Boolean) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("w", "Work", "💼", "ember", isCurrent = true), SidebarSpace("h", "Home", "🏠", "ember", isCurrent = false)),
                look = SidebarLook(haptics = haptics),
            )
        composeRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides fake) {
                SideravenTheme { SidebarContent(state = state, onIntent = {}) }
            }
        }
        composeRule.onNodeWithContentDescription("Work").performTouchInput { longClick() }
        composeRule.waitForIdle()
    }

    @Test
    fun `GIVEN haptic ticks on THEN a long press ticks`() {
        longPressSpace(haptics = true)

        assertEquals(listOf(HapticFeedbackType.LongPress), ticks)
    }

    @Test
    fun `GIVEN haptic ticks off THEN nothing ticks`() {
        longPressSpace(haptics = false)

        assertEquals(emptyList<HapticFeedbackType>(), ticks)
    }

    @Test
    fun `THEN it is a switch writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.switchFor(context, screen, SidebarPreferences.HAPTICS_KEY, store.haptics, R.string.sideraven_settings_haptics, R.string.sideraven_settings_haptics_summary)
            .callChangeListener(false)

        assertFalse(store.haptics.value)
    }
}
