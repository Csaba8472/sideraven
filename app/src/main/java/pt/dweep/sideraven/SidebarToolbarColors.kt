/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/**
 * Fenix's toolbar beside a Sidebar (spec 0008): it takes the Sidebar's surface colour, so the
 * toolbar is part of the frame and only the page is the rounded card. Fenix's toolbars call this
 * from one `SIDERAVEN-HOOK` each, where they build their colour scheme.
 */
object SidebarToolbarColors {
    /** [base] with the Sidebar's surface, unless [customTab] (another app's tab has no Sidebar). */
    @Composable
    fun scheme(
        base: ColorScheme,
        customTab: Boolean = false,
    ): ColorScheme {
        val surface by SidebarGate.sidebarSurface.collectAsState()
        return surface?.takeUnless { customTab }?.let { base.copy(surface = Color(it)) } ?: base
    }
}
