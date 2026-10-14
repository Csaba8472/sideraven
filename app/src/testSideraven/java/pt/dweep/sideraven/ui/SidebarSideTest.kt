/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.FrameInsets
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.frameInsets
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** The Sidebar on the left or the right. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarSideTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN the Sidebar on the right THEN the content's frame leaves room on the right`() {
        assertEquals(FrameInsets(start = 286, top = 6, end = 6, bottom = 6), frameInsets(280, 6, framed = true, side = SidebarSide.Left))
        assertEquals(FrameInsets(start = 6, top = 6, end = 286, bottom = 6), frameInsets(280, 6, framed = true, side = SidebarSide.Right))
        assertEquals(FrameInsets(0, 0, 280, 0), frameInsets(280, 6, framed = false, side = SidebarSide.Right))
    }

    private fun render(
        side: SidebarSide,
        mode: SidebarMode,
    ) = composeRule.setContent {
        SideravenTheme {
            SidebarContent(
                state =
                    SidebarUiState(
                        isVisible = true,
                        isPrivate = false,
                        tabs = listOf(SidebarTab("1", "One", "https://one.example", icon = null, isSelected = true)),
                        mode = mode,
                        side = side,
                        showsSwipeZone = mode == SidebarMode.AutoHide,
                        showsFlap = mode == SidebarMode.AutoHide,
                    ),
                onIntent = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Test
    fun `GIVEN the Sidebar on the right THEN it docks on the right edge, its titles still read left to right`() {
        render(SidebarSide.Right, SidebarMode.Persistent)
        val screen = composeRule.onRoot().getBoundsInRoot()

        val docked = composeRule.onNodeWithTag(SidebarTestTag.ROOT).getBoundsInRoot()
        assertEquals(screen.right, docked.right)
        assertTrue(docked.left > screen.left)
        // Inside, the tab's favicon still comes before its title.
        val tab = composeRule.onNodeWithTag(SidebarTestTag.TAB_ITEM).getBoundsInRoot()
        assertTrue(tab.left >= docked.left)
    }

    @Test
    fun `GIVEN Auto-Hide on the right THEN the Swipe Zone and the Flap are on the right edge`() {
        render(SidebarSide.Right, SidebarMode.AutoHide)
        val screen = composeRule.onRoot().getBoundsInRoot()

        assertEquals(screen.right, composeRule.onNodeWithTag(SidebarTestTag.SWIPE_ZONE).getBoundsInRoot().right)
        assertEquals(screen.right, composeRule.onNodeWithTag(SidebarTestTag.FLAP).getBoundsInRoot().right)
    }

    @Test
    fun `THEN the side is a segmented choice writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.bindSide(context, screen, store).choose(SidebarSide.Right)

        assertEquals(SidebarSide.Right, store.sidebarSide.value)
    }
}
