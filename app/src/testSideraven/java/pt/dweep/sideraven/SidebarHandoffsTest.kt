/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.PointF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.browser.SwipeGestureListener

/** Fenix's toolbar gestures and address-bar focus, beside a Sidebar. */
@RunWith(AndroidJUnit4::class)
class SidebarHandoffsTest {
    private class Recorder : SwipeGestureListener {
        val calls = mutableListOf<String>()

        override fun onSwipeStarted(
            start: PointF,
            next: PointF,
        ): Boolean {
            calls += "started"
            return true
        }

        override fun onSwipeUpdate(
            distanceX: Float,
            distanceY: Float,
        ) {
            calls += "update"
        }

        override fun onSwipeFinished(
            velocityX: Float,
            velocityY: Float,
        ) {
            calls += "finished"
        }
    }

    @Test
    fun `GIVEN a Sidebar THEN swiping Fenix's toolbar up doesn't open the Tab Tray, and without one it does`() {
        var sidebar = true
        val fenix = Recorder()
        val gesture = SidebarGestures.onlyWithoutSidebar({ sidebar }, fenix)

        assertFalse(gesture.onSwipeStarted(PointF(0f, 100f), PointF(0f, 80f)))
        assertTrue(fenix.calls.isEmpty())

        sidebar = false
        assertTrue(gesture.onSwipeStarted(PointF(0f, 100f), PointF(0f, 80f)))
        gesture.onSwipeFinished(0f, -500f)
        assertEquals(listOf("started", "finished"), fenix.calls)
    }

    @Test
    fun `GIVEN the Sidebar layout THEN a new tab opens the Search Pop-up instead of focusing Fenix's hidden address bar`() {
        assertFalse(SearchHandoff.focusFenixAddressBar(wants = true, toolbarHidden = true, private = true))
        assertEquals(true, SearchHandoff.pending.value)

        // Only the private Sidebar (after the restart) takes it, once.
        assertFalse(SearchHandoff.take(private = false))
        assertTrue(SearchHandoff.take(private = true))
        assertFalse(SearchHandoff.take(private = true))
        assertNull(SearchHandoff.pending.value)
    }

    @Test
    fun `GIVEN Fenix's toolbar shows THEN Fenix focuses it as before and nothing waits`() {
        assertTrue(SearchHandoff.focusFenixAddressBar(wants = true, toolbarHidden = false, private = false))
        assertFalse(SearchHandoff.focusFenixAddressBar(wants = false, toolbarHidden = true, private = false))
        assertNull(SearchHandoff.pending.value)
    }
}
