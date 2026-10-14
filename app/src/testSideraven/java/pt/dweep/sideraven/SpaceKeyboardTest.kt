/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Ctrl+Alt+Left/Right switch Spaces, wrapping and without animation. */
@RunWith(AndroidJUnit4::class)
class SpaceKeyboardTest {
    private val spaces = Spaces(InMemorySpacesRepository())
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val animations = mutableListOf<Boolean>()

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            switchSpace = { id ->
                spaces.switchTo(id)
                spaces.data.switchTarget(id, store.state.tabs)
            },
            setAnimateModeChanges = { animations += it },
        )

    private fun state() =
        store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, spaces = spaces.data)

    private fun key(
        code: Int,
        shift: Boolean = false,
        alt: Boolean = true,
    ) = SidebarKeyboard.intentFor(code, ctrl = true, shift = shift, alt = alt, meta = false, essentialsShown = 0)

    @Test
    fun `THEN Ctrl+Alt+arrows map to the previous and next Space, and Ctrl+Shift+arrows stay with text fields`() {
        assertEquals(SidebarIntent.KeyboardSwitchSpace(-1), key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(SidebarIntent.KeyboardSwitchSpace(1), key(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertNull(key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true, alt = false))
        assertNull(key(KeyEvent.KEYCODE_DPAD_RIGHT, shift = true))
    }

    @Test
    fun `WHEN the next Space is requested from the last THEN it wraps to the first, without animation`() {
        store.dispatch(TabListAction.AddTabAction(createTab("https://a.example", id = "a"), select = true))
        spaces.create("Work", "💼")
        spaces.create("Reading", "📚")

        handler.handle(SidebarIntent.KeyboardSwitchSpace(1), state())

        assertEquals("Home", state().currentSpace?.name)
        assertEquals(listOf(false), animations)
    }

    @Test
    fun `WHEN the previous Space is requested THEN it moves back one, and a touch switch animates again`() {
        spaces.create("Work", "💼")

        handler.handle(SidebarIntent.KeyboardSwitchSpace(-1), state())
        assertEquals("Home", state().currentSpace?.name)

        handler.handle(SidebarIntent.SwitchSpace(spaces.data.spaces[1].id), state())
        assertEquals("Work", state().currentSpace?.name)
        assertEquals(listOf(false, true), animations)
    }
}
