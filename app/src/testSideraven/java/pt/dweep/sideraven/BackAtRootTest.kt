/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back at the start of a tab's history. Closing a child tab to its parent and
 * a tab from another app back to that app is Fenix's own (its last-tab feature runs first); with a
 * Sidebar, any other tab leaves Sideraven instead of showing the home screen.
 */
@RunWith(AndroidJUnit4::class)
class BackAtRootTest {
    private var left = 0

    @Test
    fun `GIVEN a Sidebar WHEN back isn't handled at the start of a tab THEN Sideraven moves to the back`() {
        assertTrue(BackAtRoot.onUnhandledBack(SidebarTier.Tablet) { left++ })
        assertTrue(BackAtRoot.onUnhandledBack(SidebarTier.LargePhone) { left++ })
        assertEquals(2, left)
    }

    @Test
    fun `GIVEN no Sidebar THEN Fenix goes to its home screen as before`() {
        assertFalse(BackAtRoot.onUnhandledBack(SidebarTier.None) { left++ })
        assertEquals(0, left)
    }
}
