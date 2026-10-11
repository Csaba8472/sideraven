/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** Device-test feedback: Zen Sync called the token server's base URL and failed after sign-in. */
class TokenEndpointTest {
    @Test
    fun `THEN the Sync 1_5 endpoint is used whether the account gives the base or the full URL`() {
        val full = "https://token.services.mozilla.com/1.0/sync/1.5"
        assertEquals(full, ZenConnection.tokenEndpoint("https://token.services.mozilla.com"))
        assertEquals(full, ZenConnection.tokenEndpoint("https://token.services.mozilla.com/"))
        assertEquals(full, ZenConnection.tokenEndpoint(full))
        assertEquals(full, ZenConnection.tokenEndpoint("$full/"))
    }
}
