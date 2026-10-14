/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Full screen (Sideraven Settings): hides Android's status bar, or it and the navigation bar, in every
 * Sideraven window but another app's tab. A swipe in from the top or bottom edge shows them for a moment.
 * Fenix shows the bars again when a video leaves full screen; its browser screen calls [reapply] from a
 * `SIDERAVEN-HOOK` then.
 */
object SidebarFullScreen {
    /** The system bars [choice] hides (WindowInsetsCompat types), or 0. */
    fun hiddenBars(choice: FullScreen): Int =
        when (choice) {
            FullScreen.Off -> 0
            FullScreen.StatusBar -> WindowInsetsCompat.Type.statusBars()
            FullScreen.Both -> WindowInsetsCompat.Type.systemBars()
        }

    /** Follows the setting in [activity] for as long as it lives. */
    fun follow(activity: Activity) {
        if (!SidebarGate.hostsSidebar(activity)) return
        val owner = activity as? LifecycleOwner ?: return
        owner.lifecycleScope.launch {
            PreferencesSidebarModeStore.get(activity).fullScreen.flow.collect { apply(activity, it) }
        }
    }

    /** Applies the stored setting again (after Fenix showed the bars). */
    fun reapply(activity: Activity?) {
        if (activity == null || !SidebarGate.hostsSidebar(activity)) return
        apply(activity, PreferencesSidebarModeStore.get(activity).fullScreen.value)
    }

    private fun apply(
        activity: Activity,
        choice: FullScreen,
    ) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val hidden = hiddenBars(choice)
        controller.show(WindowInsetsCompat.Type.systemBars() and hidden.inv())
        if (hidden != 0) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(hidden)
        }
    }
}
