/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.os.StrictMode

/**
 * Runs [block] with main-thread disk reads allowed. Debug builds make StrictMode fatal (Fenix's
 * policy); the one-off first read of Sideraven's small preference files happens on first use,
 * which can be the main thread. Same approach as Fenix's own `resetAfter(allowThreadDiskReads())`.
 */
internal inline fun <T> allowDiskReads(block: () -> T): T {
    val old = StrictMode.allowThreadDiskReads()
    try {
        return block()
    } finally {
        StrictMode.setThreadPolicy(old)
    }
}
