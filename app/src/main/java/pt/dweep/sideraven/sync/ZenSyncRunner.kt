/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import mozilla.components.support.base.log.logger.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Where Zen Sync stands, for the settings status line and the Space row's problem dot.
 *
 * @property lastSynced When the last sync succeeded (epoch ms), or null.
 * @property problem Why the last sync failed, or null when it succeeded.
 */
data class ZenSyncRunStatus(
    val running: Boolean = false,
    val lastSynced: Long? = null,
    val problem: ZenSyncStatus.Reason? = null,
)

/**
 * Runs Zen Sync while the app is open (never in the background). "Sync now" always syncs. With
 * Sync automatically on (the default) it also syncs at app start and whenever the app comes to the
 * front, every [PERIOD_MS] while it is in front, a few seconds after the user changes Spaces, pins,
 * Folders or Essentials, and alongside Fenix's own sync. One sync at a time.
 */
class ZenSyncRunner(
    private val zenSync: ZenSync,
    private val settings: ZenSyncSettingsStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::currentTimeMillis,
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    private val state = MutableStateFlow(ZenSyncRunStatus())
    val status: StateFlow<ZenSyncRunStatus> = state.asStateFlow()

    private val mutex = Mutex()
    private var pending: Job? = null
    private var ticking: Job? = null

    private val automatic get() = settings.settings.value.let { it.enabled && it.firstSyncDone && it.syncAutomatically }

    /** An automatic sync (app start, Fenix's sync): only with Sync automatically on. */
    fun autoSync() {
        if (automatic) syncNow()
    }

    /** The app came to the front (sync now, then every [PERIOD_MS]) or went to the back (stop). */
    fun inForeground(front: Boolean) {
        ticking?.cancel()
        ticking = null
        if (!front) return
        // Ticks while in front; each tick syncs only if Sync automatically is on by then, so turning
        // it on (or finishing First Sync) mid-session counts from the next tick.
        ticking =
            scope.launch {
                while (true) {
                    if (automatic) syncNow().join()
                    delay(PERIOD_MS)
                }
            }
    }

    /** Sync now (app start, "Sync now", Fenix's sync). */
    fun syncNow(): Job =
        scope.launch {
            if (!settings.settings.value.let { it.enabled && it.firstSyncDone }) return@launch
            mutex.withLock {
                state.value = state.value.copy(running = true)
                val failure =
                    withContext(io) {
                        try {
                            if (zenSync.sync() == SyncOutcome.ReadOnly) ZenSyncStatus.Reason.NewerVersion else null
                        } catch (e: SyncError) {
                            Logger("ZenSync").warn("Zen Sync failed: ${e::class.simpleName}: ${e.message}", e)
                            ZenSyncCheck.reasonOf(e)
                        } catch (_: IOException) {
                            ZenSyncStatus.Reason.Network
                        }
                    }
                state.value =
                    if (failure == null) {
                        ZenSyncRunStatus(running = false, lastSynced = clock(), problem = null)
                    } else {
                        state.value.copy(running = false, problem = failure)
                    }
            }
        }

    /** A local change: sync a few seconds later, restarting the wait on further changes. */
    fun localChanged() {
        if (!automatic) return
        pending?.cancel()
        pending =
            scope.launch {
                delay(debounceMs)
                syncNow().join()
            }
    }

    companion object {
        const val DEBOUNCE_MS = 3_000L

        /** How often an app in front syncs on its own. */
        const val PERIOD_MS = 5 * 60_000L
    }
}

/** The synced view of the tablet's data, compared to decide whether a local change needs a sync. */
data class SyncedView(
    val spaces: List<pt.dweep.sideraven.Space>,
    val pinned: List<pt.dweep.sideraven.PinnedTab>,
    val folders: List<pt.dweep.sideraven.Folder>,
    val essentials: List<pt.dweep.sideraven.Essential>,
) {
    companion object {
        fun of(
            data: pt.dweep.sideraven.SpacesData?,
            essentials: List<pt.dweep.sideraven.Essential>,
        ) = SyncedView(
            spaces = data?.spaces.orEmpty().map { it.copy(lastSelectedTabId = null) },
            pinned = data?.pinned.orEmpty(),
            folders = data?.folders.orEmpty(),
            essentials = essentials,
        )
    }
}
