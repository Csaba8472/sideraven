/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.ToolbarSearch
import pt.dweep.sideraven.theme.SideravenTheme

/** Fenix's toolbar address opens the Search Pop-up, over the toolbar. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp-mdpi")
class ToolbarSearchTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val top = Rect(300f, 24f, 1260f, 80f)
    private val bottom = Rect(300f, 720f, 1260f, 776f)

    private fun at(bounds: Rect) = ToolbarSearch.ToolbarAt(bounds, windowHeight = 800f)

    @Test
    fun `THEN the gate opens the pop-up only where a Sidebar exists, over the toolbar when its place is known`() {
        // Narrow window, or no Sidebar host: Fenix searches.
        assertNull(ToolbarSearch.placementFor(sidebarShown = false, hosted = true, toolbar = at(top)))
        assertNull(ToolbarSearch.placementFor(sidebarShown = true, hosted = false, toolbar = at(top)))
        assertEquals(SearchPlacement.Centred, ToolbarSearch.placementFor(sidebarShown = true, hosted = true, toolbar = null))
        assertEquals(SearchPlacement.Anchored(top, toolbarAtTop = true), ToolbarSearch.placementFor(true, true, at(top)))
        assertEquals(SearchPlacement.Anchored(bottom, toolbarAtTop = false), ToolbarSearch.placementFor(true, true, at(bottom)))
        // A toolbar is top or bottom within its own window (split screen), not the whole display.
        assertEquals(
            SearchPlacement.Anchored(Rect(0f, 340f, 600f, 396f), toolbarAtTop = false),
            ToolbarSearch.placementFor(true, true, ToolbarSearch.ToolbarAt(Rect(0f, 340f, 600f, 396f), windowHeight = 400f)),
        )
    }

    @Test
    fun `THEN Fenix's New tab is the Sidebar's only where a Sidebar exists and listens`() {
        assertTrue(ToolbarSearch.handles(sidebarShown = true, hosted = true))
        assertFalse(ToolbarSearch.handles(sidebarShown = false, hosted = true))
        assertFalse(ToolbarSearch.handles(sidebarShown = true, hosted = false))
    }

    @Test
    fun `THEN a search loads in the shown tab only when that tab is of this browsing mode, never over a home screen`() {
        // A tab's address bar, in its own mode: that tab.
        assertFalse(ToolbarSearch.opensNewTab(asked = false, fromHome = false, homeIsATab = false, selectedPrivate = false, private = false))
        assertFalse(ToolbarSearch.opensNewTab(asked = false, fromHome = false, homeIsATab = false, selectedPrivate = true, private = true))
        // The private home with no private tab: Fenix keeps the normal tab selected behind it. Never load there.
        assertTrue(ToolbarSearch.opensNewTab(asked = false, fromHome = true, homeIsATab = false, selectedPrivate = false, private = true))
        assertTrue(ToolbarSearch.opensNewTab(asked = false, fromHome = false, homeIsATab = false, selectedPrivate = false, private = true))
        assertTrue(ToolbarSearch.opensNewTab(asked = false, fromHome = false, homeIsATab = false, selectedPrivate = true, private = false))
        // The normal home: a new tab, as Fenix's own home search does, unless the home is a tab itself.
        assertTrue(ToolbarSearch.opensNewTab(asked = false, fromHome = true, homeIsATab = false, selectedPrivate = false, private = false))
        assertFalse(ToolbarSearch.opensNewTab(asked = false, fromHome = true, homeIsATab = true, selectedPrivate = false, private = false))
        // No tab at all, or a new tab asked for.
        assertTrue(ToolbarSearch.opensNewTab(asked = false, fromHome = false, homeIsATab = false, selectedPrivate = null, private = false))
        assertTrue(ToolbarSearch.opensNewTab(asked = true, fromHome = false, homeIsATab = false, selectedPrivate = false, private = false))
    }

    private fun show(placement: SearchPlacement) =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = false, initialText = "https://example.com/", placement = placement),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = {},
                )
            }
        }

    private fun popupBounds() = composeRule.onNodeWithTag(SidebarTestTag.SEARCH_POPUP, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `GIVEN a top toolbar THEN the pop-up opens downward from the toolbar, at its place and width`() {
        show(SearchPlacement.Anchored(top, toolbarAtTop = true))

        val card = popupBounds()
        assertEquals(top.top, card.top, 1f)
        assertEquals(top.left, card.left, 1f)
        assertEquals(top.width, card.width, 1f)
    }

    @Test
    fun `GIVEN a bottom toolbar THEN the pop-up rises from the toolbar, its bottom on the toolbar's`() {
        show(SearchPlacement.Anchored(bottom, toolbarAtTop = false))

        val card = popupBounds()
        assertEquals(bottom.bottom, card.bottom, 1f)
        assertTrue("card ${card.top} rises above the toolbar", card.top < bottom.top)
    }
}
