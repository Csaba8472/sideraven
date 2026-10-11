/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0006 ticket 05: inline address autocomplete in the Search Pop-up. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class InlineAutocompleteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()

    private fun show() =
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = SearchRequest(newTab = true),
                    providers = emptyList(),
                    onSubmit = { submitted += it },
                    onSuggestion = {},
                    onDismiss = {},
                    options = SearchOptions(autocomplete = { text, _ -> "wikipedia.org".takeIf { it.startsWith(text) } }),
                )
            }
        }

    private fun field() = composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD)

    private fun shows(
        text: String,
        selection: TextRange,
    ) {
        composeRule.waitForIdle()
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)))
        field().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, selection))
    }

    @Test
    fun `WHEN an address is typed THEN it completes inline, the added part selected, and Enter opens the completion`() {
        show()

        field().performTextInput("wik")
        shows("wikipedia.org", TextRange(3, 13))

        field().performImeAction()
        assertEquals(listOf("wikipedia.org"), submitted)
    }

    @Test
    fun `WHEN Backspace is pressed on a completion THEN only the completion goes, and typing on completes again`() {
        show()
        field().performTextInput("wik")
        shows("wikipedia.org", TextRange(3, 13))

        field().performKeyInput { pressKey(Key.Backspace) }
        shows("wik", TextRange(3))

        field().performTextInput("i")
        shows("wikipedia.org", TextRange(4, 13))
    }

    @Test
    fun `GIVEN text no address starts with THEN nothing is added`() {
        show()

        field().performTextInput("cats")
        shows("cats", TextRange(4))
    }
}
