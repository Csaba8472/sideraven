/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 0006 ticket 14: Fenix's home screen as a card inside the frame. */
@RunWith(AndroidJUnit4::class)
class HomeCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun reset() {
        SidebarGate.frameLook.value = FrameLook()
    }

    private fun show() =
        composeRule.setContent {
            HomeCard(contentPadding = PaddingValues(top = 56.dp, bottom = 64.dp)) { Text("home") }
        }

    @Test
    fun `GIVEN framed content THEN the area between the home toolbars is a card, under the home content`() {
        SidebarGate.frameLook.value = FrameLook(framed = true, color = 0, radiusPx = 12f)
        show()

        val screen = composeRule.onRoot().getBoundsInRoot()
        val card = composeRule.onNodeWithTag(HOME_CARD_TAG).getBoundsInRoot()
        assertEquals(screen.top + 56.dp, card.top)
        assertEquals(screen.bottom - 64.dp, card.bottom)
        composeRule.onNodeWithText("home").assertExists()
    }

    @Test
    fun `GIVEN no frame (no Sidebar here) THEN home is as Fenix draws it`() {
        show()

        composeRule.onAllNodesWithTag(HOME_CARD_TAG).assertCountEquals(0)
        composeRule.onNodeWithText("home").assertExists()
    }
}
