/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pt.dweep.sideraven.sync.PreferencesDesktopViewStore
import pt.dweep.sideraven.sync.PreferencesSyncBaseRepository
import pt.dweep.sideraven.sync.PreferencesZenSyncSettingsStore

/**
 * Sideraven's part of app startup (spec 0009). Its stores read their files from disk once; doing
 * that on a background thread as the process starts, alongside Gecko's own start-up, means the
 * Sidebar's first frame finds them loaded instead of reading on the main thread. A store asked
 * for while its preload is still running waits for it (each `get` is synchronised), never reading twice.
 */
object SideravenStartup {
    /** Starts loading every store on [dispatcher]; called once from the main process's start. */
    fun preload(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Job {
        val app = context.applicationContext
        return CoroutineScope(dispatcher).launch {
            pt.dweep.sideraven.sync.ZenBlobs.useFiles(app)
            stores(app)
        }
    }

    /** Every store the Sidebar opens on its first frame, loaded. */
    internal fun stores(context: Context): List<Any> =
        listOf(
            PreferencesSidebarModeStore.get(context),
            PreferencesEssentialsRepository.get(context),
            Spaces.get(context),
            PreferencesZenSyncSettingsStore.get(context),
            PreferencesSyncBaseRepository.get(context),
            PreferencesDesktopViewStore.get(context),
            IconStore.get(context),
        )
}
