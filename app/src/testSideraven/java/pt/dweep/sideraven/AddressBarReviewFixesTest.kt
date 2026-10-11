/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.SearchState
import mozilla.components.feature.search.ext.createSearchEngine
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Regression tests for the Sidebar Address Bar code review findings. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class AddressBarReviewFixesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN no engine named WHEN searching THEN the default engine is used (never none, which would load the text as an address)`() {
        val engine = createSearchEngine(name = "Search", url = "https://search.example/?q={searchTerms}", icon = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888))
        val search = SearchState(regionSearchEngines = listOf(engine), regionDefaultSearchEngineId = engine.id)

        assertEquals(engine, SidebarSearch.engineFor(null, search))
    }

    @Test
    fun `GIVEN back can't go back WHEN it is long-pressed THEN the tab's history still opens`() {
        val intents = mutableListOf<SidebarIntent>()
        val address = SidebarAddress("example.com", AddressSecurity.Secure, false, 0, canGoBack = false, canGoForward = true, isHome = false)
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, address = address),
                    onIntent = { intents += it },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.ADDRESS_BACK).performTouchInput { longClick() }

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ShowTabHistory), intents)
    }
}
