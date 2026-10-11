/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.concept.awesomebar.AwesomeBar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AddressSecurity
import pt.dweep.sideraven.SidebarAddress
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.theme.SideravenTheme

/** Sidebar Address Bar ticket 03: the Search Pop-up, with fake suggestion sources. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class SearchPopupTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val submitted = mutableListOf<String>()
    private val chosen = mutableListOf<String>()
    private var dismissed = 0

    /** Suggests "<text> docs" for anything typed. */
    private val provider =
        object : AwesomeBar.SuggestionProvider {
            override val id = "fake"

            override suspend fun onInputChanged(text: String): List<AwesomeBar.SuggestionItem> =
                if (text.isBlank()) {
                    emptyList()
                } else {
                    listOf(AwesomeBar.Suggestion(provider = this, title = "$text docs", onSuggestionClicked = { chosen += "$text docs" }))
                }
        }

    private fun show(request: SearchRequest = SearchRequest(newTab = false, initialText = "https://example.com/")) {
        composeRule.setContent {
            SideravenTheme {
                SearchPopup(
                    request = request,
                    providers = listOf(provider),
                    onSubmit = { submitted += it },
                    onSuggestion = { it.onSuggestionClicked?.invoke() },
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    @Test
    fun `WHEN it opens for the current tab THEN the field holds the current URL`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).assertTextEquals("https://example.com/")
    }

    @Test
    fun `WHEN text is typed THEN Fenix's suggestions show, and choosing one runs it`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("kotlin")
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("kotlin docs").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("kotlin docs").performClick()

        assertEquals(listOf("kotlin docs"), chosen)
    }

    @Test
    fun `WHEN Enter is pressed THEN the text is submitted`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performTextReplacement("github.com")
        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performImeAction()

        assertEquals(listOf("github.com"), submitted)
    }

    @Test
    fun `WHEN Esc is pressed THEN the pop-up closes without doing anything`() {
        show()

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_FIELD).performKeyInput { pressKey(Key.Escape) }
        // The pop-up fades out first (spec 0008).
        composeRule.waitForIdle()

        assertEquals(1, dismissed)
        assertEquals(emptyList<String>(), submitted + chosen)
    }
}
