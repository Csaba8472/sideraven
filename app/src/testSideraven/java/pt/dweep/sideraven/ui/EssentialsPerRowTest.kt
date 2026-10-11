/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.EssentialsPerRow
import pt.dweep.sideraven.InMemorySidebarModeStore
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarLook
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.essentialsGridColumns
import pt.dweep.sideraven.settings.SidebarPreferences
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0007 ticket 08: Essentials per row. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class EssentialsPerRowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `THEN Auto adapts to the count and a fixed number is always used`() {
        assertEquals(listOf(1, 2, 4, 4), listOf(1, 2, 4, 9).map { essentialsGridColumns(it, EssentialsPerRow.Auto) })
        assertEquals(listOf(3, 3), listOf(1, 9).map { essentialsGridColumns(it, EssentialsPerRow.Three) })
        assertEquals(listOf(5, 5), listOf(2, 9).map { essentialsGridColumns(it, EssentialsPerRow.Five) })
    }

    @Test
    fun `GIVEN five per row and two Essentials THEN the tiles keep a fifth of the row, not stretching`() {
        var look by mutableStateOf(SidebarLook())
        composeRule.setContent {
            SideravenTheme {
                SidebarContent(
                    state =
                        SidebarUiState(
                            isVisible = true,
                            isPrivate = false,
                            tabs = emptyList(),
                            mode = SidebarMode.Persistent,
                            essentials = listOf("a", "b").map { SidebarEssential("https://$it.example", it, icon = null, linkedTabId = null, isSelected = false) },
                            look = look,
                        ),
                    onIntent = {},
                )
            }
        }
        fun widths() = composeRule.onAllNodesWithTag(SidebarTestTag.ESSENTIAL).fetchSemanticsNodes().map { it.size.width }
        val auto = widths()

        look = SidebarLook(essentialsPerRow = EssentialsPerRow.Five)
        composeRule.waitForIdle()

        val five = widths()
        assertEquals(2, five.size)
        // Two of five columns instead of two of two: well under half the width.
        assertTrue("$five vs $auto", five.all { it * 2 < auto.first() })
    }

    @Test
    fun `THEN it is a radio list writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.bindEssentialsPerRow(context, screen, store, context.getSharedPreferences("radio-test", 0))
            .first { it.key.endsWith(".Four") }.callChangeListener(true)

        assertEquals(EssentialsPerRow.Four, store.essentialsPerRow.value)
    }
}
