/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0007 ticket 14: Fenix's one-time question about search suggestions in private browsing. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PrivateSuggestionsPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<Boolean>()

    private inner class FakePrompt(
        override val needed: Boolean,
    ) : PrivateSuggestionsPrompt {
        override fun answer(allow: Boolean) {
            answers += allow
        }

        override fun learnMore() = Unit
    }

    private fun show(needed: Boolean) =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = {},
                    onSuggestion = {},
                    onDismiss = {},
                    options = SearchOptions(privatePrompt = FakePrompt(needed)),
                )
            }
        }

    @Test
    fun `GIVEN the question is due WHEN something is typed THEN it is asked, and an answer is given once`() {
        show(needed = true)
        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("cats")
        composeRule.onNodeWithText("Allow").performClick()

        assertEquals(listOf(true), answers)
        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)
    }

    @Test
    fun `WHEN suggestions aren't allowed THEN that answer is given`() {
        show(needed = true)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("cats")
        composeRule.onNodeWithText("Don’t allow").performClick()

        assertEquals(listOf(false), answers)
    }

    @Test
    fun `GIVEN the question isn't due THEN it never shows`() {
        show(needed = false)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("cats")

        composeRule.onAllNodesWithText("Allow").assertCountEquals(0)
    }
}
