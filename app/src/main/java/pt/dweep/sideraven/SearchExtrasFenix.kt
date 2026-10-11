/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import mozilla.components.lib.state.ext.flow
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.components.appstate.AppAction
import org.mozilla.fenix.components.appstate.VoiceSearchAction
import org.mozilla.fenix.ext.components
import pt.dweep.sideraven.ui.SearchExtras

/**
 * The Search Pop-up's QR scanner and voice search, through Fenix's own: the same app-store
 * requests Fenix's toolbar makes, answered by the scanner and voice features Fenix's home and
 * browser screens register (the pop-up only opens over those).
 */
class SearchExtrasFenix(
    private val activity: HomeActivity,
) : SearchExtras {
    private val appStore get() = activity.components.appStore
    private var waiting: Job? = null

    override val canScanQr: Boolean
        get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    override val canUseVoice: Boolean
        get() =
            activity.components.settings.shouldShowVoiceSearch &&
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(activity.packageManager) != null

    override fun scanQr(onResult: (String) -> Unit) {
        waiting?.cancel()
        waiting =
            activity.lifecycleScope.launch {
                val scanned = appStore.flow().map { it.qrScannerState.lastScanData }.first { !it.isNullOrEmpty() }!!
                appStore.dispatch(AppAction.QrScannerAction.QrScannerInputConsumed)
                onResult(scanned)
            }
        appStore.dispatch(AppAction.QrScannerAction.QrScannerRequested)
    }

    override fun voice(onResult: (String) -> Unit) {
        waiting?.cancel()
        waiting =
            activity.lifecycleScope.launch {
                val said = appStore.flow().map { it.voiceSearchState.voiceInputResult }.first { !it.isNullOrEmpty() }!!
                appStore.dispatch(VoiceSearchAction.VoiceInputRequestCleared)
                onResult(said)
            }
        appStore.dispatch(VoiceSearchAction.VoiceInputRequested)
    }

    override fun cancel() {
        waiting?.cancel()
        waiting = null
    }
}
