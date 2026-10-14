/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Fenix's toolbar in the Sidebar layout: hidden while
 * [SidebarGate.toolbarHidden] says so. The browser and home screens each call in from one
 * `SIDERAVEN-HOOK`.
 */
object SidebarToolbar {
    /**
     * Browser screen: hide the toolbar with [hide] (Fenix's own full-screen expansion) when the
     * Sidebar layout takes effect, and bring it back with [show] when it ends. Changes only. In full
     * screen the change is only recorded: leaving full screen runs Fenix's own collapse, which
     * follows the gate. Another app's tab keeps its toolbar ([SidebarGate.hostsSidebar]).
     */
    fun observe(
        fragment: Fragment,
        isFullScreen: () -> Boolean,
        hide: () -> Unit,
        show: () -> Unit,
        toolbars: () -> List<View> = { emptyList() },
    ) {
        if (!SidebarGate.hostsSidebar(fragment.activity)) return
        val owner = fragment.viewLifecycleOwner
        val follower = ToolbarFollower(isFullScreen, hide, show)
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SidebarGate.toolbarHidden.collect(follower::onChange)
            }
        }
        roundPageAtToolbars(fragment, toolbars)
    }

    /** The page's corners at the toolbar's edge, over Fenix's straight divider ([PageCornersDrawable]). */
    private fun roundPageAtToolbars(
        fragment: Fragment,
        toolbars: () -> List<View>,
    ) {
        val host = fragment.requireView()
        val owner = fragment.viewLifecycleOwner
        val corners = PageCornersDrawable(host, toolbars, dividerPx = host.resources.displayMetrics.density)
        corners.setBounds(0, 0, host.width, host.height)
        host.overlay.add(corners)
        val onLayout = View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> corners.setBounds(0, 0, v.width, v.height) }
        host.addOnLayoutChangeListener(onLayout)
        var reported: ToolbarSearch.ToolbarAt? = null
        val onPreDraw =
            ViewTreeObserver.OnPreDrawListener {
                corners.refresh()
                val now = toolbars().firstNotNullOfOrNull(::windowBounds)?.let { ToolbarSearch.ToolbarAt(it, host.rootView.height.toFloat()) }
                if (now != reported) {
                    ToolbarSearch.forget(reported)
                    now?.let { ToolbarSearch.toolbar.value = it }
                    reported = now
                }
                true
            }
        host.viewTreeObserver.addOnPreDrawListener(onPreDraw)
        owner.lifecycleScope.launch { SidebarGate.frameLook.collect { corners.look = it } }
        owner.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    ToolbarSearch.forget(reported)
                    host.viewTreeObserver.removeOnPreDrawListener(onPreDraw)
                    host.removeOnLayoutChangeListener(onLayout)
                    host.overlay.remove(corners)
                }
            },
        )
    }

    /** Applies the gate's changes to the browser screen's toolbar, remembering what it last applied. */
    internal class ToolbarFollower(
        private val isFullScreen: () -> Boolean,
        private val hide: () -> Unit,
        private val show: () -> Unit,
    ) {
        private var applied = false

        fun onChange(hidden: Boolean) {
            if (hidden == applied) return
            applied = hidden
            if (isFullScreen()) return
            if (hidden) hide() else show()
        }
    }

    /** Whether [activity]'s toolbar must stay hidden (for Fenix's own paths that would show it again). */
    fun isHidden(activity: Activity?): Boolean = SidebarGate.hostsSidebar(activity) && SidebarGate.toolbarHidden.value

    /** Home screen: the toolbar's content, left out while the Sidebar layout is in effect. */
    @Composable
    fun HideInSidebarLayout(content: @Composable () -> Unit) {
        val hidden by SidebarGate.toolbarHidden.collectAsState()
        val root = LocalView.current.rootView
        val reported = remember { arrayOfNulls<ToolbarSearch.ToolbarAt>(1) }
        DisposableEffect(Unit) { onDispose { ToolbarSearch.forget(reported[0]) } }
        if (!hidden) {
            // Where the home toolbar is, for the Search Pop-up to open over it.
            Box(
                Modifier.onGloballyPositioned {
                    val now = ToolbarSearch.ToolbarAt(it.boundsInWindow(), root.height.toFloat())
                    reported[0] = now
                    ToolbarSearch.toolbar.value = now
                },
            ) { content() }
        }
    }

    /** A shown view's bounds in its window, or null. */
    private fun windowBounds(view: View): Rect? {
        if (!view.isShown || view.height == 0) return null
        val location = IntArray(2)
        view.getLocationInWindow(location)
        return Rect(location[0].toFloat(), location[1].toFloat(), (location[0] + view.width).toFloat(), (location[1] + view.height).toFloat())
    }
}
