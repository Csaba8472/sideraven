/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import pt.dweep.sideraven.ui.SearchPlacement

/**
 * The search request gate (spec 0006): Fenix's toolbar address, tapped, opens the Search Pop-up
 * instead of Fenix's full-screen search wherever a Sidebar exists. Fenix's toolbar hooks call
 * [openFromToolbar]; the Sidebar host listens to [requests].
 */
object ToolbarSearch {
    /** Where Fenix's toolbar is (window pixels) and how tall its window is; reported by the toolbar hooks. */
    data class ToolbarAt(
        val bounds: Rect,
        val windowHeight: Float,
    )

    /** The toolbar on screen now; null when none shows. */
    val toolbar = MutableStateFlow<ToolbarAt?>(null)

    /** A screen's toolbar went away: forgets it, unless another screen has reported since. */
    fun forget(reported: ToolbarAt?) {
        if (reported != null) toolbar.compareAndSet(reported, null)
    }

    /** What Fenix asked the Sidebar host for. */
    sealed interface Request {
        /** The Search Pop-up, for the current tab; [fromHome] when Fenix's home screen asked. */
        data class Search(
            val placement: SearchPlacement,
            val fromHome: Boolean = false,
        ) : Request

        /** A new tab, opened the way New Tab Behaviour says. */
        data class NewTab(val private: Boolean) : Request
    }

    private val pending = MutableSharedFlow<Request>(extraBufferCapacity = 1)

    /** Requests from Fenix's toolbar and tab tray, for the Sidebar host. */
    val requests: SharedFlow<Request> = pending

    /** Whether the Sidebar handles Fenix's request here: a Sidebar in this window and its host listening. */
    fun handles(
        sidebarShown: Boolean,
        hosted: Boolean,
    ): Boolean = sidebarShown && hosted

    /**
     * Fenix's "New tab" (toolbar +, tab tray): true when it opens the way New Tab Behaviour says,
     * as the Sidebar's + does; false where Fenix should handle it.
     */
    fun openNewTab(
        context: Context,
        private: Boolean,
    ): Boolean {
        if (!handles(SidebarGate.showsSidebar(SidebarTier.from(context)), pending.subscriptionCount.value > 0)) return false
        return pending.tryEmit(Request.NewTab(private))
    }

    /**
     * Fenix's toolbar address was tapped ([fromHome]: on its home screen): true when the Search
     * Pop-up opens instead of Fenix's search.
     */
    fun openFromToolbar(
        context: Context,
        fromHome: Boolean = false,
    ): Boolean {
        val placement =
            placementFor(
                sidebarShown = SidebarGate.showsSidebar(SidebarTier.from(context)),
                hosted = pending.subscriptionCount.value > 0,
                toolbar = toolbar.value,
            ) ?: return false
        return pending.tryEmit(Request.Search(placement, fromHome))
    }

    /**
     * Whether a search opens a new tab rather than loading in the selected one. The selected tab
     * is the target only when it is of this browsing mode: Fenix keeps a normal tab selected behind
     * the private home, and loading there would leave private browsing. A home screen gets a new
     * tab, as Fenix's own home search does, unless the home is a tab itself (Fenix's homepage as a
     * new tab).
     */
    fun opensNewTab(
        asked: Boolean,
        fromHome: Boolean,
        homeIsATab: Boolean,
        selectedPrivate: Boolean?,
        private: Boolean,
    ): Boolean = asked || selectedPrivate != private || (fromHome && !homeIsATab)

    /**
     * Where the pop-up opens for a toolbar tap, or null when Fenix should search: without a
     * Sidebar here, or with no Sidebar host listening. Anchored over the toolbar when its place is
     * known (a toolbar in the upper half of its window is a top toolbar), centred otherwise.
     */
    fun placementFor(
        sidebarShown: Boolean,
        hosted: Boolean,
        toolbar: ToolbarAt?,
    ): SearchPlacement? =
        when {
            !handles(sidebarShown, hosted) -> null
            toolbar == null -> SearchPlacement.Centred
            else -> SearchPlacement.Anchored(toolbar.bounds, toolbarAtTop = toolbar.bounds.center.y < toolbar.windowHeight / 2)
        }
}
