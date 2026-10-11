/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.view.KeyEvent
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SearchPopup
import pt.dweep.sideraven.ui.SearchRequest
import pt.dweep.sideraven.ui.SidebarTestTag

/** Sidebar Address Bar tickets 04 and 05: what New Tab (and Ctrl+T) opens. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class NewTabBehaviourTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val store = InMemorySidebarModeStore()

    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = { calls += "home" },
            navigation =
                AddressNavigation(
                    openSearch = { newTab -> calls += if (newTab) "search:new" else "search:current" },
                    openBlank = { calls += "blank" },
                ),
            newTabBehaviour = { store.newTabBehaviour.value },
        )

    private val state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList())

    @Test
    fun `THEN by default New Tab opens the Search Pop-up for a new tab`() {
        assertEquals(NewTabBehaviour.Search, store.newTabBehaviour.value)

        handler.handle(SidebarIntent.NewTab, state)

        assertEquals(listOf("search:new"), calls)
    }

    @Test
    fun `GIVEN Home page THEN New Tab opens Fenix's home page as before`() {
        store.newTabBehaviour.set(NewTabBehaviour.HomePage)

        handler.handle(SidebarIntent.NewTab, state)

        assertEquals(listOf("home"), calls)
    }

    @Test
    fun `GIVEN Blank page THEN New Tab opens an empty tab with the pop-up ready for it`() {
        store.newTabBehaviour.set(NewTabBehaviour.Blank)

        handler.handle(SidebarIntent.NewTab, state)

        assertEquals(listOf("blank", "search:current"), calls)
    }

    @Test
    fun `THEN Ctrl+T is New Tab while the Sidebar is shown, and left alone otherwise`() {
        assertEquals(SidebarIntent.NewTab, SidebarKeyboard.intentFor(KeyEvent.KEYCODE_T, ctrl = true, shift = false, alt = false, meta = false, essentialsShown = 0))
        assertEquals(null, SidebarKeyboard.intentFor(KeyEvent.KEYCODE_T, ctrl = true, shift = false, alt = false, meta = false, essentialsShown = -1))
    }

    @Test
    fun `GIVEN a new-tab pop-up WHEN Open home page is chosen THEN the home page opens`() {
        var home = 0
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = {},
                    onOpenHome = { home++ },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_OPEN_HOME).performClick()

        assertEquals(1, home)
    }
}
