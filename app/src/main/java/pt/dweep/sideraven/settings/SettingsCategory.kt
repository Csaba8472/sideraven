/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import org.mozilla.fenix.R

/**
 * The categories of Sideraven Settings, in menu order, each its own screen.
 *
 * @property needsSidebar Only shown where there is a Sidebar to configure.
 */
enum class SettingsCategory(
    val title: Int,
    val summary: Int,
    val needsSidebar: Boolean = true,
) {
    Sidebar(R.string.sideraven_settings_sidebar, R.string.sideraven_category_sidebar_summary, needsSidebar = false),
    Tabs(R.string.sideraven_category_tabs, R.string.sideraven_category_tabs_summary),
    AddressBar(R.string.sideraven_category_address, R.string.sideraven_category_address_summary),
    LookAndFeel(R.string.sideraven_settings_look, R.string.sideraven_category_look_summary),
    ZenSync(R.string.sideraven_zen_sync, R.string.sideraven_category_zen_summary, needsSidebar = false),
    Backup(R.string.sideraven_group_backup, R.string.sideraven_category_backup_summary, needsSidebar = false),
}
