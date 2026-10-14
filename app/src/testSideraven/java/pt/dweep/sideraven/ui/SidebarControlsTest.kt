/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.SidebarControlKind
import pt.dweep.sideraven.SidebarControlKind.BrowsingModeSwitch
import pt.dweep.sideraven.SidebarControlKind.LayoutButton
import pt.dweep.sideraven.SidebarControlKind.NewTab
import pt.dweep.sideraven.SidebarControlKind.Toggle
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarScreen
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.toSidebarUiState

/**
 * Sidebar Controls come from one ordered list in the Sidebar state: New Tab, the
 * Browsing Mode Switch, then the Toggle and the layout button where they apply. The row's buttons
 * share its width equally.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarControlsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun controls(
        tier: SidebarTier,
        mode: SidebarMode,
        flap: Boolean = true,
    ): List<SidebarControlKind> =
        BrowserState().toSidebarUiState(
            isPrivate = false,
            tier = tier,
            screen = SidebarScreen.Browser,
            chosenMode = mode,
            isRevealed = true,
            flapEnabled = flap,
            swipeZoneEnabled = false,
        ).controls

    @Test
    fun `GIVEN a tablet in Persistent Mode THEN it has all four, in order`() {
        assertEquals(listOf(NewTab, BrowsingModeSwitch, Toggle, LayoutButton), controls(SidebarTier.Tablet, SidebarMode.Persistent))
    }

    @Test
    fun `GIVEN Collapsed Mode THEN it has no layout button, since the address can't move there`() {
        assertEquals(listOf(NewTab, BrowsingModeSwitch, Toggle), controls(SidebarTier.Tablet, SidebarMode.Collapsed))
    }

    @Test
    fun `GIVEN a phone in portrait THEN it has a Toggle too, to switch to Collapsed`() {
        assertEquals(listOf(NewTab, BrowsingModeSwitch, Toggle, LayoutButton), controls(SidebarTier.Phone, SidebarMode.AutoHide))
    }

    @Test
    fun `GIVEN Auto-Hide with no way to pull the Sidebar out THEN it has no layout button`() {
        assertEquals(listOf(NewTab, BrowsingModeSwitch, Toggle), controls(SidebarTier.Tablet, SidebarMode.AutoHide, flap = false))
    }

    private fun widths(state: SidebarUiState): List<Dp> {
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        return state.controls.map { kind ->
            val bounds = composeRule.onNodeWithTag(kind.testTag).getBoundsInRoot()
            bounds.right - bounds.left
        }
    }

    private fun assertEqualWidths(
        widths: List<Dp>,
        // Whole pixels: a row that doesn't divide evenly rounds one button 1dp narrower.
        tolerance: Float = 1f,
    ) {
        widths.forEach { assertTrue("widths $widths", kotlin.math.abs((it - widths.first()).value) < tolerance) }
        // The buttons grow to share the row, wider than a 48dp icon button.
        assertTrue("widths $widths", widths.first() > 48.dp)
    }

    @Test
    fun `GIVEN the full Sidebar THEN the row draws the buttons left to right in the list's order`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), nextMode = SidebarMode.Collapsed, layoutMovable = true)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        val lefts = state.controls.map { composeRule.onNodeWithTag(it.testTag).getBoundsInRoot().left }
        assertEquals(lefts.sorted(), lefts)
    }

    @Test
    fun `GIVEN Collapsed Mode THEN the column draws the buttons top to bottom in the list's order`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Collapsed,
                nextMode = SidebarMode.AutoHide,
                layoutMovable = false,
            )
        assertEquals(listOf(NewTab, BrowsingModeSwitch, Toggle), state.controls)
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = {}) } }
        val tops = state.controls.map { composeRule.onNodeWithTag(it.testTag).getBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertTrue("tops $tops", tops.distinct().size == tops.size)
    }

    @Test
    fun `GIVEN five buttons THEN they share the row equally`() {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                nextMode = SidebarMode.Collapsed,
                layoutMovable = true,
                controlPlaces = pt.dweep.sideraven.ControlPlaces(syncedTabs = pt.dweep.sideraven.ControlPlace.InSidebar),
            )
        assertEquals(5, state.controls.size)
        assertEqualWidths(widths(state), tolerance = 1.5f)
    }

    @Test
    fun `GIVEN four buttons THEN they share the row equally`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), nextMode = SidebarMode.Collapsed, layoutMovable = true)
        assertEquals(4, state.controls.size)
        assertEqualWidths(widths(state))
    }

    @Test
    fun `GIVEN three buttons THEN they share the row equally`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), nextMode = SidebarMode.Collapsed, layoutMovable = false)
        assertEquals(3, state.controls.size)
        assertEqualWidths(widths(state))
    }

    @Test
    fun `GIVEN two buttons THEN they share the row equally`() {
        val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), nextMode = null, layoutMovable = false, chosenLayout = AddressBarLayout.Top)
        assertEquals(2, state.controls.size)
        assertEqualWidths(widths(state))
    }
}
