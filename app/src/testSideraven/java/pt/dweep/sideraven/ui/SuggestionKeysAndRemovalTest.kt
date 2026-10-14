/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.concept.awesomebar.AwesomeBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** Removing a history suggestion, and moving through suggestions with the keyboard. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SuggestionKeysAndRemovalTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<String>()
    private val removed = mutableListOf<String?>()

    /** Two removable suggestions for anything typed, like Fenix's history. */
    private val provider =
        object : AwesomeBar.SuggestionProvider {
            override val id = "history"

            override suspend fun onInputChanged(text: String): List<AwesomeBar.SuggestionItem> =
                if (text.isBlank()) {
                    emptyList()
                } else {
                    listOf("docs", "news").map { kind ->
                        AwesomeBar.Suggestion(
                            provider = this,
                            id = kind,
                            title = "$text $kind",
                            isRemovalAllowed = true,
                            onSuggestionClicked = { chosen += "$text $kind" },
                        )
                    }
                }
        }

    private fun show() =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = listOf(provider),
                    onSubmit = {},
                    onSuggestion = { it.onSuggestionClicked?.invoke() },
                    onDismiss = {},
                    options = SearchOptions(onRemoveSuggestion = { removed += (it.suggestion as? AwesomeBar.Suggestion)?.title }),
                )
            }
        }


    @Test
    fun `WHEN a history suggestion is removed THEN it leaves the list and is deleted`() {
        show()
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("kotlin")
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("kotlin docs").fetchSemanticsNodes().isNotEmpty() }

        composeRule.onAllNodesWithContentDescription("Delete this history entry", useUnmergedTree = true).onFirst().performClick()
        composeRule.waitForIdle()

        assertEquals(listOf<String?>("kotlin docs"), removed)
        composeRule.onAllNodesWithText("kotlin docs").assertCountEquals(0)
    }

    @Test
    fun `WHEN Down is pressed THEN the field previews the next suggestion, and Enter opens it`() {
        show()
        val field = composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD)
        field.performTextReplacement("kotlin")
        composeRule.waitUntil(3_000) { composeRule.onAllNodesWithText("kotlin news").fetchSemanticsNodes().isNotEmpty() }

        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.performKeyInput { pressKey(Key.DirectionDown) }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("kotlin news")))
        field.performKeyInput { pressKey(Key.DirectionUp) }
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("kotlin docs")))

        field.performKeyInput { pressKey(Key.Enter) }
        assertEquals(listOf("kotlin docs"), chosen)
    }
}
