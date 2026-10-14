/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Device-test feedback: compact Sidebar Controls, the Pin target, and the Search Pop-up's dimmed layer. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SidebarPolishTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `GIVEN the full Sidebar THEN its controls are one row of icon buttons, named for where the address goes`() {
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = emptyList(),
                            mode = SidebarMode.Persistent,
                            chosenLayout = AddressBarLayout.Sidebar,
                        ),
                    onIntent = {},
                )
            }
        }

        val tops =
            listOf(SidebarTestTag.NEW_TAB, SidebarTestTag.MODE_SWITCH, SidebarTestTag.LAYOUT_BUTTON)
                .map { composeRule.onNodeWithTag(it).getBoundsInRoot().top }
        assertEquals(1, tops.distinct().size)
        // Fenix's toolbar may be at the top or the bottom.
        composeRule.onNodeWithTag(SidebarTestTag.LAYOUT_BUTTON).assertContentDescriptionEquals("Move address bar to the toolbar")
    }

    @Test
    fun `WHEN the dimmed layer around the Search Pop-up is tapped THEN the pop-up closes`() {
        var dismissed = 0
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true, initialText = ""),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = { dismissed++ },
                )
            }
        }

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_SCRIM).performTouchInput { click(Offset(4f, 4f)) }
        // The pop-up fades out first.
        composeRule.waitForIdle()

        assertEquals(1, dismissed)
    }
}
