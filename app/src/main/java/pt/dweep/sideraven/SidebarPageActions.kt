/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.browser.state.selector.selectedTab
import mozilla.components.support.ktx.kotlin.getOrigin
import mozilla.components.support.ktx.kotlin.isContentUrl
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.NavGraphDirections
import org.mozilla.fenix.components.menu.MenuAccessPoint
import org.mozilla.fenix.ext.components

/**
 * In the Sidebar layout, what Fenix's hidden toolbar offered: its ⋮ menu and its site panel,
 * opened exactly as the toolbar opens them.
 */
object SidebarPageActions {
    /** Fenix's main menu, from the browser or the home screen. */
    fun openMenu(
        activity: HomeActivity,
        onHome: Boolean,
    ) {
        sidebarNavController(activity)?.navigate(
            NavGraphDirections.actionGlobalMenuDialogFragment(accesspoint = if (onHome) MenuAccessPoint.Home else MenuAccessPoint.Browser),
        )
    }

    /** Fenix's site panel (permissions, trackers blocked, certificate) for the selected tab. */
    fun openSitePanel(activity: HomeActivity) {
        val components = activity.components
        val tab = components.core.store.state.selectedTab ?: return
        activity.lifecycleScope.launch {
            val permissions =
                withContext(Dispatchers.IO) {
                    tab.content.url.getOrigin()?.let { origin ->
                        components.core.geckoSitePermissionsStorage.findSitePermissionsBy(origin, private = tab.content.private)
                    }
                }
            components.useCases.trackingProtectionUseCases.containsException(tab.id) { hasException ->
                activity.lifecycleScope.launch {
                    sidebarNavController(activity)?.navigate(
                        NavGraphDirections.actionGlobalTrustPanelFragment(
                            sessionId = tab.id,
                            url = tab.content.url,
                            title = tab.content.title,
                            isLocalPdf = tab.content.url.isContentUrl(),
                            isSecured = tab.content.securityInfo.isSecure,
                            sitePermissions = permissions,
                            certificate = tab.content.securityInfo.certificate,
                            permissionHighlights = tab.content.permissionHighlights,
                            isTrackingProtectionEnabled = tab.trackingProtection.enabled && !hasException,
                        ),
                    )
                }
            }
        }
    }
}
