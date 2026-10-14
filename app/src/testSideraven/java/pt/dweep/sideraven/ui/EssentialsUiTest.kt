/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.PinResult
import pt.dweep.sideraven.SidebarEssential
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** The Essentials Section and pinning from the Tab Context Menu. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class EssentialsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(pinBlock: PinResult? = null) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = listOf(SidebarTab("t", "Tab", "https://tab.example", icon = null, isSelected = false)),
                essentials =
                    listOf(
                        SidebarEssential("https://mail.example", "Mail", icon = null, linkedTabId = "m", isSelected = true),
                        SidebarEssential("https://docs.example", "Docs", icon = null, linkedTabId = null, isSelected = false),
                    ),
                pinBlock = pinBlock,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `THEN Essentials show above the Tab List and a Ghost Essential is announced as not open`() {
        render()

        composeRule.onAllNodesWithTag(SidebarTestTag.ESSENTIAL).assertCountEquals(2)
        composeRule
            .onNodeWithContentDescription("Docs")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not open"))
    }

    @Test
    fun `WHEN an Essential is tapped THEN a select-Essential intent is emitted`() {
        render()

        composeRule.onNodeWithContentDescription("Docs").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.SelectEssential("https://docs.example")), intents)
    }

    @Test
    fun `WHEN an Essential is long-pressed THEN it can be unpinned`() {
        render()

        composeRule.onNodeWithContentDescription("Mail").performTouchInput { longClick() }
        composeRule.onNodeWithText("Unpin").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.UnpinEssential("https://mail.example")), intents)
    }

    @Test
    fun `WHEN Pin to Essentials is chosen from a tab's menu THEN a pin intent is emitted`() {
        render()

        composeRule.onNodeWithContentDescription("Tab").performTouchInput { longClick() }
        composeRule.onNodeWithTag(SidebarTestTag.PIN_ITEM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.PinTab("t")), intents)
    }

    @Test
    fun `GIVEN the Essentials Cap THEN Pin to Essentials is disabled with the reason`() {
        render(pinBlock = PinResult.Full)

        composeRule.onNodeWithContentDescription("Tab").performTouchInput { longClick() }

        composeRule.onNodeWithTag(SidebarTestTag.PIN_ITEM).assertIsNotEnabled()
        composeRule.onNodeWithText("Essentials are full (12 max). Unpin one first.").assertExists()
    }
}
