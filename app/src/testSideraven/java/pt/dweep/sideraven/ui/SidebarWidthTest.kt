/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.SidebarWidth
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0006 ticket 11: the Sidebar's width, compact, standard or wide. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarWidthTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN a wide Sidebar in Persistent Mode THEN it docks wider, and Collapsed Mode is unchanged`() {
        assertEquals(240.dp, sidebarWidth(SidebarMode.Persistent, SidebarWidth.Compact))
        assertEquals(280.dp, sidebarWidth(SidebarMode.Persistent, SidebarWidth.Standard))
        assertEquals(340.dp, sidebarWidth(SidebarMode.Persistent, SidebarWidth.Wide))
        assertEquals(CollapsedSidebarWidth, sidebarWidth(SidebarMode.Collapsed, SidebarWidth.Wide))

        var docked = 0.dp
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, width = SidebarWidth.Wide),
                    onIntent = {},
                    onDockedWidthChanged = { docked = it },
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(340f, docked.value, 1f)
    }

    @Test
    fun `THEN the width is a radio list writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        val radios = SidebarPreferences.bindWidth(context, screen, store, context.getSharedPreferences("radio-test", 0))
        radios.first { it.key.endsWith(".Wide") }.callChangeListener(true)

        assertEquals(SidebarWidth.Wide, store.sidebarWidth.value)
    }
}
