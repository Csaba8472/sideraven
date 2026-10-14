/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.RestoreCompleteAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Keeps every normal tab in a Space.
 *
 * - A new tab joins its parent's Space, else the current Space.
 * - Restored tabs keep their Space (tab ids survive restarts and undo); any without one join the
 *   current Space. After the session restore, Spaces forget tabs that no longer exist.
 * - Selecting a tab remembers it as its Space's last selected tab, and selecting a tab of another
 *   Space (from the Tab Tray, or an app focusing an existing tab) makes that Space current.
 *   Essential Tabs are global ([isGlobal]) and never switch Spaces.
 * - Closing the selected tab keeps the selection inside its Space when the Space has other tabs.
 *
 * [spaces] is resolved lazily so the store can be built before Spaces are first read.
 */
class SpacesMiddleware(
    private val isGlobal: (tabId: String) -> Boolean = { false },
    private val spaces: () -> Spaces,
) : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        val before = store.state
        next(action)

        when (action) {
            is TabListAction.AddTabAction ->
                spaces().assignNewTab(action.tab, action.tab.parentId?.let { before.findTab(it) })
            is TabListAction.AddMultipleTabsAction, is TabListAction.RestoreAction -> spaces().adopt(store.state.tabs)
            RestoreCompleteAction -> spaces().adopt(store.state.tabs, prune = true)
            is TabListAction.SelectTabAction -> remember(store)
            is TabListAction.RemoveTabAction -> keepSelectionInSpace(store, before, action.tabId)
            else -> Unit
        }
        if (action is TabListAction.AddTabAction && action.select) remember(store)
    }

    private fun remember(store: Store<BrowserState, BrowserAction>) {
        val selected = store.state.selectedTabId?.let { store.state.findTab(it) } ?: return
        if (selected.content.private || isGlobal(selected.id)) return
        val spaces = spaces()
        val space = spaces.data.spaceOf(selected.id)
        if (space != spaces.data.current.id) spaces.switchTo(space)
        spaces.rememberSelection(selected.id)
    }

    private fun keepSelectionInSpace(
        store: Store<BrowserState, BrowserAction>,
        before: BrowserState,
        removedId: String,
    ) {
        val removed = before.findTab(removedId) ?: return
        if (before.selectedTabId != removedId || removed.content.private) return
        val data = spaces().data
        val space = data.spaceOf(removedId)
        val nowSelected = store.state.selectedTabId ?: return
        val selectedTab = store.state.findTab(nowSelected) ?: return
        if (!selectedTab.content.private && data.spaceOf(nowSelected) == space) return
        data.switchTarget(space, store.state.tabs)?.let { store.dispatch(TabListAction.SelectTabAction(it)) }
    }
}
