/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.CloseButtons
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.TabRows
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Tab row density and the close button setting. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class TabRowsCloseButtonTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val tabs =
        listOf(
            SidebarTab("a", "Alpha", "https://a.example", icon = null, isSelected = true),
            SidebarTab("b", "Beta", "https://b.example", icon = null, isSelected = false),
            SidebarTab("c", "Gamma", "https://c.example", icon = null, isSelected = false),
        )

    private fun show(look: SidebarLook) =
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent, look = look),
                    onIntent = {},
                )
            }
        }

    private fun rowHeights() =
        composeRule.onAllNodesWithTag(SidebarTestTag.TAB_ITEM).fetchSemanticsNodes().map { with(it.layoutInfo.density) { it.size.height.toDp() } }

    @Test
    fun `GIVEN compact rows THEN tab rows are 8dp shorter`() {
        var look by mutableStateOf(SidebarLook())
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state = SidebarUiState(isVisible = true, isPrivate = false, tabs = tabs, mode = SidebarMode.Persistent, look = look),
                    onIntent = {},
                )
            }
        }
        val comfortable = rowHeights()

        look = SidebarLook(tabRows = TabRows.Compact)
        composeRule.waitForIdle()

        val compact = rowHeights()
        assertEquals(3, compact.size)
        compact.zip(comfortable).forEach { (small, big) -> assertEquals(big - 8.dp, small) }
    }

    @Test
    fun `GIVEN close buttons on the selected tab THEN only it has one, and on every tab all do`() {
        show(SidebarLook())
        composeRule.onAllNodesWithTag(SidebarTestTag.CLOSE_BUTTON, useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `GIVEN close buttons on every tab THEN all rows have one`() {
        show(SidebarLook(closeButtons = CloseButtons.EveryTab))
        composeRule.onAllNodesWithTag(SidebarTestTag.CLOSE_BUTTON, useUnmergedTree = true).assertCountEquals(3)
    }

    @Test
    fun `THEN both are radio lists writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.bindTabRows(context, screen, store).choose(TabRows.Compact)
        val every = SidebarPreferences.bindCloseButtons(context, screen, store)
        assertEquals("Only on the selected tab", every.summaryOff.toString())
        every.onPreferenceChangeListener!!.onPreferenceChange(every, true)

        assertEquals(TabRows.Compact, store.tabRows.value)
        assertEquals(CloseButtons.EveryTab, store.closeButtons.value)
    }
}
