/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.theme.SideravenTheme

/** A row being swiped knows it, so it can show it's being touched. */
@RunWith(AndroidJUnit4::class)
class SwipedRowHighlightTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `WHEN a row is being swiped THEN it is told so until the finger lifts and it springs back`() {
        composeRule.setContent {
            SideravenTheme {
                SwipeToClose(onClose = {}, modifier = Modifier.fillMaxWidth().height(48.dp).testTag("row")) { swiping ->
                    Box { Text(if (swiping) "touched" else "idle") }
                }
            }
        }
        composeRule.onAllNodesWithText("idle").assertCountEquals(1)

        composeRule.onNodeWithTag("row").performTouchInput {
            down(centerRight - Offset(4f, 0f))
            repeat(6) { moveBy(Offset(-8f, 0f), delayMillis = 40) }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("touched").assertCountEquals(1)

        composeRule.onNodeWithTag("row").performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("idle").assertCountEquals(1)
    }
}
