/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Test

/** Section Sizing, worked through on a strip 1000 tall: the Tab List keeps 400, the sections share 600. */
class SectionSizingTest {
    private fun heights(
        essentials: Float,
        pinned: Float,
    ) = SectionSizing.maxHeights(height = 1000f, essentialsContent = essentials, pinnedContent = pinned)

    @Test
    fun `GIVEN both sections overflowing THEN each gets half of what the Tab List leaves`() {
        assertEquals(SectionSizing.Heights(essentials = 300f, pinned = 300f), heights(essentials = 900f, pinned = 800f))
    }

    @Test
    fun `GIVEN small sections THEN each takes only what it holds, and the Tab List gets the rest`() {
        assertEquals(SectionSizing.Heights(essentials = 120f, pinned = 80f), heights(essentials = 120f, pinned = 80f))
    }

    @Test
    fun `GIVEN one small section THEN the other may use what it leaves, never squeezing the Tab List`() {
        // Pinned uses 100 of its 300; Essentials may grow into the 200 left: 500 at most.
        assertEquals(SectionSizing.Heights(essentials = 500f, pinned = 100f), heights(essentials = 900f, pinned = 100f))
        // Without Pinned Tabs, Essentials may take all 600, still leaving the Tab List its 400.
        assertEquals(SectionSizing.Heights(essentials = 600f, pinned = 0f), heights(essentials = 900f, pinned = 0f))
    }

    @Test
    fun `GIVEN everything fitting THEN nothing is capped below its content`() {
        assertEquals(SectionSizing.Heights(essentials = 250f, pinned = 340f), heights(essentials = 250f, pinned = 340f))
    }

    @Test
    fun `GIVEN arranged sizes THEN they replace the automatic caps`() {
        assertEquals(
            SectionSizing.Heights(essentials = 150f, pinned = 400f),
            SectionSizing.maxHeights(1000f, essentialsContent = 900f, pinnedContent = 900f, arrangedEssentials = 150f, arrangedPinned = 400f),
        )
        // Only one arranged: the other keeps its automatic cap.
        assertEquals(
            SectionSizing.Heights(essentials = 100f, pinned = 300f),
            SectionSizing.maxHeights(1000f, essentialsContent = 900f, pinnedContent = 900f, arrangedEssentials = 100f),
        )
    }

    @Test
    fun `GIVEN arranged sizes that would squeeze the Tab List THEN they shrink together to leave it its share`() {
        val heights = SectionSizing.maxHeights(1000f, essentialsContent = 900f, pinnedContent = 900f, arrangedEssentials = 500f, arrangedPinned = 500f)
        assertEquals(600f, heights.essentials + heights.pinned, 0.01f)
        assertEquals(heights.essentials, heights.pinned, 0.01f)
    }
}
