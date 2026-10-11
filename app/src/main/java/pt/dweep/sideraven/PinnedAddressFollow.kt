/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.BrowserState

/**
 * An Essential or Pinned Tab got a new address from Zen Sync (spec 0008). Its open tab follows
 * only while it is still on the old pinned page; a tab the user browsed away stays where it is,
 * and Reset goes to the new address.
 */
object PinnedAddressFollow {
    fun follow(
        browser: BrowserState,
        tabId: String,
        oldUrl: String,
        newUrl: String,
        loadUrl: (url: String, tabId: String) -> Unit,
    ) {
        val tab = browser.findTab(tabId) ?: return
        if (tab.content.url == oldUrl) loadUrl(newUrl, tabId)
    }
}
