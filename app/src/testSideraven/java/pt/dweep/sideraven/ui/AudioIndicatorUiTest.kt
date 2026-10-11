/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarTab
import pt.dweep.sideraven.SidebarUiState
import pt.dweep.sideraven.TabAudio
import pt.dweep.sideraven.theme.SideravenTheme

/** Ticket 15, seam 2: audio indicator and dot. */
@RunWith(AndroidJUnit4::class)
class AudioIndicatorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<SidebarIntent>()

    private fun render(mode: SidebarMode) {
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs =
                    listOf(
                        SidebarTab("m", "Music", "https://m.example", icon = null, isSelected = false, audio = TabAudio.Playing),
                        SidebarTab("s", "Silent", "https://s.example", icon = null, isSelected = false),
                    ),
                mode = mode,
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }
    }

    @Test
    fun `GIVEN Persistent Mode WHEN the speaker is tapped THEN the tab is muted`() {
        render(SidebarMode.Persistent)

        composeRule.onNodeWithContentDescription("Mute tab").performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.ToggleMute("m")), intents)
    }

    @Test
    fun `THEN a playing tab is announced as playing audio`() {
        render(SidebarMode.Persistent)

        composeRule
            .onNodeWithContentDescription("Music")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing audio"))
    }

    @Test
    fun `GIVEN Collapsed Mode THEN only playing tabs carry the audio dot`() {
        render(SidebarMode.Collapsed)

        composeRule.onAllNodesWithTag(SidebarTestTag.AUDIO_DOT, useUnmergedTree = true).assertCountEquals(1)
        composeRule.onAllNodesWithTag(SidebarTestTag.AUDIO_BUTTON).assertCountEquals(0)
    }
}
