/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 0006 ticket 02: the Search Pop-up starts with the search words on a results page. */
class SearchStartingTextTest {
    @Test
    fun `THEN a results page starts with its search words, any other page with its URL, and Fenix's own pages empty`() {
        assertEquals("mystery novels", searchStartingText("https://www.google.com/search?q=mystery+novels", "mystery novels"))
        assertEquals("https://example.com/a", searchStartingText("https://example.com/a", ""))
        assertEquals("https://example.com/a", searchStartingText("https://example.com/a", null))
        assertEquals("", searchStartingText("about:home", null))
        assertEquals("", searchStartingText(null, null))
    }
}
