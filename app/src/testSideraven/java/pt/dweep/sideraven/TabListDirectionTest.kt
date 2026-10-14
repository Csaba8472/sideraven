/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Tab List Direction: the Tab List can sit on the bottom edge and grow up, its sections above the tabs. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class TabListDirectionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        direction: TabListDirection,
        inactive: SidebarInactiveSection? = null,
    ) {
        val tabs = (1..3).map { SidebarTab("t$it", "Tab $it", "https://t$it.example", icon = null, isSelected = it == 1) }
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = tabs,
                            mode = SidebarMode.Persistent,
                            inactive = inactive,
                            look = SidebarLook(tabListDirection = direction),
                        ),
                    onIntent = {},
                    modifier = Modifier.height(800.dp),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().map { it.boundsInRoot }

    @Test
    fun `GIVEN top down THEN the tabs start under the top of the list`() {
        show(TabListDirection.TopDown)
        val list = composeRule.onNodeWithTag(SidebarTestTag.TAB_LIST).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds(SidebarTestTag.TAB_ITEM).first().top - list.top < 40f)
    }

    @Test
    fun `GIVEN bottom up THEN a short list sits on the bottom edge, empty space above it`() {
        show(TabListDirection.BottomUp)
        val list = composeRule.onNodeWithTag(SidebarTestTag.TAB_LIST).fetchSemanticsNode().boundsInRoot
        val rows = bounds(SidebarTestTag.TAB_ITEM)
        assertEquals(3, rows.size)
        assertTrue("last row ends at the bottom: ${rows.last()} in $list", list.bottom - rows.last().bottom < 40f)
        assertTrue("room above: ${rows.first()} in $list", rows.first().top - list.top > 200f)
    }

    @Test
    fun `GIVEN bottom up THEN the Inactive Tabs Section sits above the tabs`() {
        val inactive = SidebarInactiveSection(listOf(SidebarTab("old", "Old", "https://old.example", icon = null, isSelected = false)), expanded = false)
        show(TabListDirection.BottomUp, inactive)
        val header = composeRule.onAllNodesWithTag(SidebarTestTag.INACTIVE_HEADER).fetchSemanticsNodes().first().boundsInRoot
        assertTrue("header $header above ${bounds(SidebarTestTag.TAB_ITEM).first()}", header.bottom <= bounds(SidebarTestTag.TAB_ITEM).first().top + 1f)
    }

    @Test
    fun `WHEN bottom up is chosen in Settings THEN new tabs go to the bottom too`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        val position = SidebarPreferences.bindNewTabPosition(context, screen, store)
        SidebarPreferences.bindTabListDirection(context, screen, store) { position.show(store.newTabPosition.value) }.choose(TabListDirection.BottomUp)

        assertEquals(TabListDirection.BottomUp, store.tabListDirection.value)
        assertEquals(NewTabPosition.Bottom, store.newTabPosition.value)
        assertEquals(NewTabPosition.Bottom, position.selected)
    }
}
