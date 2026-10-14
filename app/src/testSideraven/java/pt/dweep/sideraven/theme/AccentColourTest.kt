/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The accent colour. */
@RunWith(AndroidJUnit4::class)
class AccentColourTest {
    @Test
    fun `THEN Spaces on the default colour follow the chosen accent, and other Spaces keep theirs`() {
        assertEquals("teal", effectiveAccent(spaceAccent = "ember", chosen = "teal"))
        assertEquals("rose", effectiveAccent(spaceAccent = "rose", chosen = "teal"))
        // No Space accent (no Spaces yet): the chosen accent, or the plain Zen scheme for the default.
        assertEquals("teal", effectiveAccent(spaceAccent = null, chosen = "teal"))
        assertNull(effectiveAccent(spaceAccent = null, chosen = "ember"))
        assertEquals("ember", effectiveAccent(spaceAccent = "ember", chosen = "ember"))
    }
}
