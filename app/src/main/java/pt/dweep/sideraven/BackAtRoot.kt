/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.app.Activity

/**
 * Back at the start of a tab's history, like Chrome (spec 0008). Fenix's own last-tab feature runs
 * first: a tab opened from another tab closes to its parent, and one opened from another app closes
 * back to that app. Fenix would then show its home screen; with a Sidebar every tab is a tap away,
 * so Sideraven moves to the back instead, as other Android apps do. Called from one
 * `SIDERAVEN-HOOK` at the end of Fenix's browser back handling.
 */
object BackAtRoot {
    /** Whether back was handled: with a Sidebar at [tier], [moveToBack] runs. */
    fun onUnhandledBack(
        tier: SidebarTier,
        moveToBack: () -> Unit,
    ): Boolean {
        if (!SidebarGate.showsSidebar(tier)) return false
        moveToBack()
        return true
    }

    fun onUnhandledBack(activity: Activity): Boolean =
        onUnhandledBack(SidebarTier.from(activity)) { activity.moveTaskToBack(true) }
}
