/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.state.BrowserState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Sidebar Flap stays where it was moved. */
class FlapPositionTest {
    private val store = InMemorySidebarModeStore(SidebarMode.AutoHide)
    private val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, setFlapPosition = store.flapPosition::set)
    private val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.AutoHide)

    @Test
    fun `THEN the Flap starts in the middle of the edge`() {
        assertEquals(0.5f, store.flapPosition.value)
    }

    @Test
    fun `WHEN the Flap is moved THEN its place is remembered, within the edge`() {
        handler.handle(SidebarIntent.MoveFlap(0.2f), state)
        assertEquals(0.2f, store.flapPosition.value)

        handler.handle(SidebarIntent.MoveFlap(1.4f), state)
        assertEquals(1f, store.flapPosition.value)
    }

    @Test
    fun `THEN the Sidebar shows the Flap where it was left`() {
        val ui =
            BrowserState().toSidebarUiState(
                isPrivate = false,
                tier = SidebarTier.Tablet,
                screen = SidebarScreen.Browser,
                chosenMode = SidebarMode.AutoHide,
                flapPosition = 0.8f,
            )
        assertEquals(0.8f, ui.flapPosition)
    }
}
