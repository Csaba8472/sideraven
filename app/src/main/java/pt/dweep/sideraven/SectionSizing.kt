/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import kotlin.math.min

/**
 * Section Sizing: how Collapsed Mode shares the strip's height between the Essentials Section, the
 * Pinned Tabs and the Tab List. The Tab List always keeps [TAB_LIST_SHARE]; the two sections above
 * share the rest, each taking only what it holds, and scroll beyond their share.
 */
object SectionSizing {
    /** The share of the height the Tab List always keeps. */
    const val TAB_LIST_SHARE = 0.4f

    /** Each section's maximum height, in the same unit as the heights given. */
    data class Heights(val essentials: Float, val pinned: Float)

    /**
     * The maximum heights for a strip whose sections and Tab List together are [height] tall, with
     * Essentials and Pinned Tabs holding [essentialsContent] and [pinnedContent] (0 when empty).
     * Each section gets up to half of what the Tab List leaves; what one leaves unused goes to the
     * other first, and the rest stays with the Tab List. [arrangedEssentials] and [arrangedPinned], set in
     * Arrange Mode, replace a section's cap; both shrink together if they'd squeeze the Tab List.
     */
    fun maxHeights(
        height: Float,
        essentialsContent: Float,
        pinnedContent: Float,
        arrangedEssentials: Float? = null,
        arrangedPinned: Float? = null,
    ): Heights {
        val room = height * (1f - TAB_LIST_SHARE)
        val half = room / 2f
        val autoEssentials = min(essentialsContent, half)
        val autoPinned = min(pinnedContent, half)
        // Arrange Mode's sizes replace the automatic caps; what's shown still has to leave the Tab List its share.
        val essentials = arrangedEssentials?.coerceIn(0f, room) ?: min(essentialsContent, autoEssentials + (half - autoPinned))
        val pinned = arrangedPinned?.coerceIn(0f, room) ?: min(pinnedContent, autoPinned + (half - autoEssentials))
        val shown = min(essentials, essentialsContent) + min(pinned, pinnedContent)
        if (shown <= room) return Heights(essentials, pinned)
        val scale = room / shown
        return Heights(min(essentials, essentialsContent) * scale, min(pinned, pinnedContent) * scale)
    }
}
