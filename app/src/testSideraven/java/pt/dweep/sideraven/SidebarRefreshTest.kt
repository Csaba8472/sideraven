/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.createTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every input of the Sidebar's state shows at once. */
@RunWith(AndroidJUnit4::class)
class SidebarRefreshTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val browser =
        BrowserState(
            tabs = listOf(createTab("https://example.org", id = "a"), createTab("https://example.com", id = "b")),
            selectedTabId = "a",
        )

    @Test
    fun `GIVEN the Sidebar shown WHEN width, side or sleeping tabs change alone THEN its state follows at once`() {
        var inputs by mutableStateOf(SidebarInputs(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser))
        lateinit var state: SidebarUiState
        composeRule.setContent { state = rememberSidebarUiState(browser, inputs) }

        inputs = inputs.copy(sidebarWidth = SidebarWidth.Wide)
        composeRule.waitForIdle()
        assertEquals(SidebarWidth.Wide, state.width)

        inputs = inputs.copy(sidebarSide = SidebarSide.Right)
        composeRule.waitForIdle()
        assertEquals(SidebarSide.Right, state.side)

        inputs = inputs.copy(sleepingTabIds = setOf("b"))
        composeRule.waitForIdle()
        assertEquals(listOf("b"), state.tabs.filter { it.sleeping }.map { it.id })
    }
}
