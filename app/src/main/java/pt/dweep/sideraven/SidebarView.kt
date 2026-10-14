/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.ContentState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.TabSessionState

/**
 * The part of the browser state the Sidebar shows: per tab its id, address, title,
 * icon, Browsing Mode, container, parent, activity times and audio, plus the selection and whether
 * restore is done. Loading progress, security, navigation and the rest of a page's churn are left
 * out, so they leave this view equal and the Sidebar untouched.
 */
internal fun BrowserState.sidebarView(): BrowserState =
    BrowserState(
        tabs = tabs.map { it.sidebarView() },
        selectedTabId = selectedTabId,
        restoreComplete = restoreComplete,
    )

private fun TabSessionState.sidebarView() =
    TabSessionState(
        id = id,
        content = ContentState(url = content.url, private = content.private, title = content.title, icon = content.icon),
        mediaSessionState =
            mediaSessionState?.let { MediaSessionState(it.controller, playbackState = it.playbackState, muted = it.muted, timestamp = 0L) },
        contextId = contextId,
        parentId = parentId,
        lastAccess = lastAccess,
        createdAt = createdAt,
    )

/**
 * The selected tab as the Sidebar Address Bar shows it: its address, Browsing Mode, loading
 * progress, security and back/forward. Observed on its own, so a loading page moves only the bar.
 */
internal fun BrowserState.selectedPageView(): TabSessionState? =
    selectedTab?.let { tab ->
        TabSessionState(
            id = tab.id,
            content =
                ContentState(
                    url = tab.content.url,
                    private = tab.content.private,
                    progress = tab.content.progress,
                    loading = tab.content.loading,
                    securityInfo = tab.content.securityInfo,
                    canGoBack = tab.content.canGoBack,
                    canGoForward = tab.content.canGoForward,
                ),
            createdAt = 0L,
        )
    }

/** [this] state with the Sidebar Address Bar following [selectedPage] (keeping its pin). */
internal fun SidebarUiState.withLiveAddress(
    selectedPage: TabSessionState?,
    screen: SidebarScreen,
): SidebarUiState =
    address?.let { shown -> copy(address = sidebarAddress(selectedPage, screen).copy(pin = shown.pin)) } ?: this
