/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.FrameTint
import pt.dweep.sideraven.theme.LocalFrameTint
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.theme.SidebarFill
import pt.dweep.sideraven.theme.sidebarFill
import pt.dweep.sideraven.theme.zenColorScheme

/** Raised Sidebar pieces under a Space theme, an empty Space's home page, and Full screen. */
@RunWith(AndroidJUnit4::class)
class LastChangesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN a Space theme THEN raised pieces are see-through so the gradient shows, and without one they're the theme's containers`() {
        var themed = Color.Unspecified
        var plain = Color.Unspecified
        composeRule.setContent {
            SideravenTheme(darkTheme = true) {
                plain = sidebarFill(SidebarFill.Container)
                CompositionLocalProvider(LocalFrameTint provides FrameTint(Color.Red, Color.Blue)) {
                    themed = sidebarFill(SidebarFill.High)
                }
            }
        }
        composeRule.waitForIdle()

        assertEquals(zenColorScheme(dark = true).surfaceContainer, plain)
        assertTrue("see-through: $themed", themed.alpha in 0.05f..0.2f)
    }

    @Test
    fun `WHEN switching to a Space with no tabs THEN its home page opens, with no search`() {
        val calls = mutableListOf<String>()
        val handler =
            SidebarIntentHandler(
                selectTab = { calls += "select $it" },
                openNewTab = { calls += "new tab" },
                openSpaceHome = { calls += "space home" },
                switchSpace = { null },
            )
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                spaces = listOf(SidebarSpace("a", "A", "🅰", "red", isCurrent = true), SidebarSpace("b", "B", "🅱", "teal", isCurrent = false)),
            )

        handler.handle(SidebarIntent.SwitchSpace("b"), state)
        handler.handle(SidebarIntent.CreateSpace("C", "🌊"), state)

        assertEquals(listOf("space home", "space home"), calls)
    }

    @Test
    fun `THEN Full screen hides nothing, the status bar, or both bars, and is off until chosen`() {
        assertEquals(0, SidebarFullScreen.hiddenBars(FullScreen.Off))
        assertEquals(WindowInsetsCompat.Type.statusBars(), SidebarFullScreen.hiddenBars(FullScreen.StatusBar))
        assertEquals(WindowInsetsCompat.Type.systemBars(), SidebarFullScreen.hiddenBars(FullScreen.Both))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = InMemorySidebarModeStore()
        assertEquals(FullScreen.Off, store.fullScreen.value)
        SidebarPreferences.bindFullScreen(context, PreferenceManager(context).createPreferenceScreen(context), store).choose(FullScreen.Both)
        assertEquals(FullScreen.Both, store.fullScreen.value)
    }
}
