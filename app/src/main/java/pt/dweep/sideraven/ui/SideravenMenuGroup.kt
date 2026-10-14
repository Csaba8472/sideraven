/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import mozilla.components.ui.icons.R as iconsR
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.compose.MenuGroup
import org.mozilla.fenix.components.menu.compose.MenuItem
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.PreferencesSidebarModeStore
import pt.dweep.sideraven.SidebarMenu
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarPageActions
import pt.dweep.sideraven.SideravenMenuEntry
import pt.dweep.sideraven.collectControlPlaces
import pt.dweep.sideraven.sideravenMenuEntries

/**
 * Sideraven's entries in Fenix's Main Menu: the Optional Controls placed In the menu, each doing what
 * its Sidebar button does in this window. Drawn by a `SIDERAVEN-HOOK` in Fenix's menu; nothing when
 * there are none, and nothing in previews. [onChosen] closes the menu.
 */
@Composable
internal fun SideravenMenuGroup(onChosen: () -> Unit) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val link = remember(context) { SidebarMenu.of(context) } ?: return
    val store = remember(context) { PreferencesSidebarModeStore.get(context) }
    val places = store.collectControlPlaces()
    val sidebar by link.sidebar.collectAsState()
    val entries = sideravenMenuEntries(places, sidebar)
    if (entries.isEmpty()) return
    MenuGroup {
        entries.forEach { entry ->
            when (entry) {
                // Each entry says and pictures what a tap does, with its button's own label and icon.
                SideravenMenuEntry.SyncedTabs ->
                    MenuItem(
                        label = stringResource(R.string.sideraven_synced_tabs),
                        beforeIconPainter = painterResource(R.drawable.sideraven_ic_synced_tabs_24),
                        // Navigating away closes the menu, as Fenix's own entries do; close it here only if that can't happen.
                        onClick = { SidebarMenu.activityOf(context)?.let(SidebarPageActions::openSyncedTabs) ?: onChosen() },
                    )
                SideravenMenuEntry.ChangeSidebarMode ->
                    MenuItem(
                        label =
                            stringResource(
                                when (sidebar?.nextMode) {
                                    SidebarMode.Persistent -> R.string.sideraven_expand_sidebar
                                    SidebarMode.AutoHide -> R.string.sideraven_autohide_sidebar
                                    else -> R.string.sideraven_collapse_sidebar
                                },
                            ),
                        beforeIconPainter =
                            painterResource(
                                when (sidebar?.nextMode) {
                                    SidebarMode.Persistent -> R.drawable.sideraven_ic_sidebar_persistent_24
                                    SidebarMode.AutoHide -> R.drawable.sideraven_ic_sidebar_autohide_24
                                    else -> R.drawable.sideraven_ic_sidebar_collapsed_24
                                },
                            ),
                        onClick = {
                            link.choose(entry)
                            onChosen()
                        },
                    )
                SideravenMenuEntry.ArrangeSidebar ->
                    MenuItem(
                        label = stringResource(R.string.sideraven_arrange_sidebar),
                        beforeIconPainter = painterResource(iconsR.drawable.mozac_ic_edit_24),
                        onClick = {
                            link.choose(entry)
                            onChosen()
                        },
                    )
                SideravenMenuEntry.MoveAddressBar -> {
                    val toToolbar = sidebar?.chosenLayout == AddressBarLayout.Sidebar
                    MenuItem(
                        label = stringResource(if (toToolbar) R.string.sideraven_layout_to_top else R.string.sideraven_layout_to_sidebar),
                        beforeIconPainter =
                            painterResource(if (toToolbar) R.drawable.sideraven_ic_address_in_toolbar_24 else R.drawable.sideraven_ic_address_in_sidebar_24),
                        onClick = {
                            link.choose(entry)
                            onChosen()
                        },
                    )
                }
            }
        }
    }
}
