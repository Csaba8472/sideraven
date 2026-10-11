/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.dweep.sideraven.ui.DragSource
import pt.dweep.sideraven.ui.DragZone
import pt.dweep.sideraven.ui.SidebarDragState

/** Device-test feedback: the page's rounded edge at the toolbar, and Pin vs Unpin while dragging. */
class PageEdgesTest {
    @Test
    fun `THEN a top toolbar's bottom is where the page starts, and a bottom toolbar's top where it ends`() {
        assertEquals(listOf(PageEdge(56f, pageBelow = true)), pageEdges(1000, listOf(0f to 56f)))
        assertEquals(listOf(PageEdge(944f, pageBelow = false)), pageEdges(1000, listOf(944f to 1000f)))
        // A top toolbar scrolled half away still rounds the page where it now starts.
        assertEquals(listOf(PageEdge(28f, pageBelow = true)), pageEdges(1000, listOf(-28f to 28f)))
    }

    @Test
    fun `GIVEN an Essential is dragged and the Pin target sits over the Tab List THEN Unpin starts below Pin`() {
        val drag =
            SidebarDragState().apply {
                tabList = Rect(0f, 100f, 200f, 600f)
                pinTarget = Rect(0f, 104f, 200f, 144f)
            }
        drag.start(pt.dweep.sideraven.ui.DragItem(DragSource.Essential, "Mail", "https://mail.example", null), Offset(50f, 120f))

        assertEquals(DragZone.Pin, drag.hovered)
        assertEquals(144f, drag.unpinArea.top)
        drag.move(Offset(50f, 130f))
        assertEquals(DragZone.Pin, drag.hovered)
        drag.move(Offset(50f, 300f))
        assertEquals(DragZone.Unpin, drag.hovered)
        drag.move(Offset(50f, 700f))
        assertNull(drag.hovered)
    }
}
