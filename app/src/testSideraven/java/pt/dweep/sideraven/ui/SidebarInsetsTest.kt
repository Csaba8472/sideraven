/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Device-test feedback: on web pages the Sidebar got the status bar's height twice. */
class SidebarInsetsTest {
    @Test
    fun `GIVEN Fenix already pads the content below the status bar THEN the Sidebar adds nothing more`() {
        // Home screen: edge to edge, the Sidebar starts at the window's top.
        assertEquals(60f, remainingInset(insetPx = 60f, gapToWindowEdgePx = 0f))
        // Web page: Fenix's content padding already put the Sidebar below the status bar.
        assertEquals(0f, remainingInset(insetPx = 60f, gapToWindowEdgePx = 60f))
        assertEquals(0f, remainingInset(insetPx = 60f, gapToWindowEdgePx = 72f))
        // Partly covered: only the rest.
        assertEquals(20f, remainingInset(insetPx = 60f, gapToWindowEdgePx = 40f))
    }
}
