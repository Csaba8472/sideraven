/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Address Bar Layout ticket 03: the home screen's toolbar follows the Sidebar layout. */
@RunWith(AndroidJUnit4::class)
class SidebarToolbarTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun reset() {
        SidebarGate.toolbarHidden.value = false
    }

    @Test
    fun `WHEN the Sidebar layout takes effect and ends THEN the home toolbar hides and comes back`() {
        composeRule.setContent { SidebarToolbar.HideInSidebarLayout { Text("toolbar") } }
        composeRule.onAllNodesWithText("toolbar").assertCountEquals(1)

        SidebarGate.toolbarHidden.value = true
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("toolbar").assertCountEquals(0)

        SidebarGate.toolbarHidden.value = false
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("toolbar").assertCountEquals(1)
    }
}
