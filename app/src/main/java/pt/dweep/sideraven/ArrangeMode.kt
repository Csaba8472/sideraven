/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

/** The sections above the Tab List that Arrange Mode can resize. */
enum class CollapsedSection { Essentials, Pinned }

/**
 * The sizes set in Arrange Mode for one orientation, as shares of the strip's height (the sections and
 * the Tab List together); null where Section Sizing decides.
 */
data class ArrangedShares(
    val essentials: Float? = null,
    val pinned: Float? = null,
) {
    operator fun get(section: CollapsedSection): Float? =
        when (section) {
            CollapsedSection.Essentials -> essentials
            CollapsedSection.Pinned -> pinned
        }

    fun with(
        section: CollapsedSection,
        share: Float?,
    ): ArrangedShares =
        when (section) {
            CollapsedSection.Essentials -> copy(essentials = share)
            CollapsedSection.Pinned -> copy(pinned = share)
        }
}

/** The largest share a section may be arranged to: what the Tab List leaves. */
const val MAX_ARRANGED_SHARE = 1f - SectionSizing.TAB_LIST_SHARE
