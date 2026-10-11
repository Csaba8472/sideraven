/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.annotation.DrawableRes
import org.mozilla.fenix.R

/**
 * Sideraven's ravens in Fenix's app icon picker. Every Solid and Gradient icon is logo2's raven on
 * Fenix's background: yellow on the dark backgrounds, navy on the rest. The workspace's
 * `tools/brand/brand6.py` reads [yellowRaven] to draw the launcher icons, so the picker's previews
 * and the icons on the home screen match.
 */
object SideravenAppIcons {
    /** The Solid and Gradient icons (by alias suffix) whose background is dark enough for the yellow raven. */
    val yellowRaven =
        setOf(
            "AppSolidDark",
            "AppSolidRed",
            "AppSolidGreen",
            "AppSolidPurple",
            "AppSolidPurpleDark",
            "AppGradientTwilight",
            "AppGradientMidnight",
        )

    /** The picker's preview over an icon's background, for icons that don't name their own. */
    @DrawableRes
    fun previewForeground(aliasSuffix: String): Int =
        when (aliasSuffix) {
            "App" -> R.drawable.ic_wordmark_logo
            in yellowRaven -> R.drawable.sideraven_ic_icon_preview_raven_yellow
            else -> R.drawable.sideraven_ic_icon_preview_raven_navy
        }
}
