/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.theme.SideravenTheme

/** Spec 0008 ticket 11 (audit 6): the Search Pop-up enters and exits, as the Animations setting says. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class PopupMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var dismissed = 0

    private fun show(level: AnimationLevel) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalSidebarMotion provides SidebarMotion(level)) {
                SideravenTheme {
                    SearchPopup(request = SearchRequest(newTab = true), providers = emptyList(), onSubmit = {}, onSuggestion = {}, onDismiss = { dismissed++ })
                }
            }
        }
    }

    private fun cardWidth() = composeRule.onNodeWithTag(SidebarTestTag.SEARCH_POPUP, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width

    @Test
    fun `GIVEN full animations THEN the card grows in, and closing fades it before the pop-up goes`() {
        show(AnimationLevel.Full)
        composeRule.mainClock.advanceTimeBy(32)
        val soon = cardWidth()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue("$soon vs ${cardWidth()}", soon < cardWidth() - 0.5f)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_SCRIM).performTouchInput { click(Offset(5f, 5f)) }
        composeRule.mainClock.advanceTimeBy(32)
        assertEquals(0, dismissed)
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, dismissed)
    }

    @Test
    fun `GIVEN no animations THEN it opens and closes at once`() {
        show(AnimationLevel.Off)
        composeRule.mainClock.advanceTimeBy(32)
        val soon = cardWidth()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(cardWidth(), soon, 0.5f)

        composeRule.onNodeWithTag(SidebarTestTag.SEARCH_SCRIM).performTouchInput { click(Offset(5f, 5f)) }
        composeRule.mainClock.advanceTimeBy(32)
        assertEquals(1, dismissed)
    }
}
