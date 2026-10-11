/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0007 ticket 10: Animations Full, Reduced or Off. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AnimationLevelTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Where the revealed Auto-Hide panel's left edge is shortly after the reveal starts. */
    private fun panelLeftSoonAfterReveal(level: AnimationLevel): Float {
        composeRule.mainClock.autoAdvance = false
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                mode = SidebarMode.AutoHide,
                isRevealed = true,
                look = SidebarLook(animations = level),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        composeRule.mainClock.advanceTimeBy(64)
        return composeRule.onNodeWithTag(SidebarTestTag.OVERLAY_PANEL).getBoundsInRoot().left.value
    }

    @Test
    fun `GIVEN full animations THEN the revealed Sidebar is still sliding in`() {
        assertTrue(panelLeftSoonAfterReveal(AnimationLevel.Full) < -1f)
    }

    @Test
    fun `GIVEN reduced animations THEN it doesn't slide, it is in place at once and fades in`() {
        assertEquals(0f, panelLeftSoonAfterReveal(AnimationLevel.Reduced), 0.5f)
    }

    @Test
    fun `GIVEN no animations THEN it is in place at once`() {
        assertEquals(0f, panelLeftSoonAfterReveal(AnimationLevel.Off), 0.5f)
    }

    @Test
    fun `THEN it is a radio list writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.bindAnimations(context, screen, store, context.getSharedPreferences("radio-test", 0))
            .first { it.key.endsWith(".Reduced") }.callChangeListener(true)

        assertEquals(AnimationLevel.Reduced, store.animations.value)
    }
}
