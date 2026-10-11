/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Every new tab appears at the top of the Tab List (CONTEXT.md, "Tab List"), or at its bottom when
 * Sideraven Settings say so ([position]).
 *
 * The Tab List shows the browser's tab order newest-first, so "top" means the **end** of the
 * browser's order and "bottom" its start. Fenix appends New Tab and intent tabs at the end, but
 * inserts link-opened child tabs right after their parent; this moves every new tab to the chosen
 * end. The parent link itself is kept, so closing a child still returns to its parent.
 *
 * Restores (session restore, undo close) go through other actions and keep their positions.
 */
class NewTabPositionMiddleware(
    private val position: () -> NewTabPosition = { NewTabPosition.Top },
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        next(action)

        if (action !is TabListAction.AddTabAction) return

        val atTop = position() == NewTabPosition.Top
        val end = (if (atTop) store.state.tabs.lastOrNull() else store.state.tabs.firstOrNull()) ?: return
        if (end.id != action.tab.id) {
            store.dispatch(
                TabListAction.MoveTabsAction(
                    tabIds = listOf(action.tab.id),
                    targetTabId = end.id,
                    placeAfter = atTop,
                ),
            )
        }
    }

    companion object {
        /** The app's: the position chosen in Sideraven Settings. */
        fun from(context: Context) = NewTabPositionMiddleware { PreferencesSidebarModeStore.get(context).newTabPosition.value }
    }
}
