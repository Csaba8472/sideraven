/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A Space's accent colour, in a light- and a dark-theme variant. Each variant keeps 4.5:1 against
 * the Sidebar surface (so it works under text and as the Active Tab Indicator), and the light one
 * keeps 4.5:1 for white text on it.
 */
data class SpaceAccent(
    val key: String,
    val light: Color,
    val dark: Color,
)

object SpaceAccents {
    /** The palette, in picker order. The first is the Zen accent and the default. */
    val all =
        listOf(
            SpaceAccent("ember", light = Color(0xFFA9502D), dark = Color(0xFFF0A987)),
            SpaceAccent("rose", light = Color(0xFFA8325A), dark = Color(0xFFFF9EBB)),
            SpaceAccent("violet", light = Color(0xFF6A4FB8), dark = Color(0xFFC9B8FF)),
            SpaceAccent("blue", light = Color(0xFF2E5DAE), dark = Color(0xFFA6C1FF)),
            SpaceAccent("teal", light = Color(0xFF00676A), dark = Color(0xFF74D6D3)),
            SpaceAccent("green", light = Color(0xFF3A6B2D), dark = Color(0xFFA2D88D)),
            SpaceAccent("amber", light = Color(0xFF835600), dark = Color(0xFFF2C063)),
            SpaceAccent("slate", light = Color(0xFF4D5965), dark = Color(0xFFBCC7D2)),
        )

    /** The accent for [key]; unknown keys (e.g. from a newer version) fall back to the default. */
    fun of(key: String?): SpaceAccent = all.firstOrNull { it.key == key } ?: all.first()
}

/**
 * The accent to theme with: the current Space's own colour, else the [chosen] accent (Sideraven
 * Settings) for a Space on the default colour or no Space at all; null for the plain Zen scheme.
 */
fun effectiveAccent(
    spaceAccent: String?,
    chosen: String,
): String? =
    when {
        spaceAccent != null && spaceAccent != pt.dweep.sideraven.DEFAULT_SPACE_ACCENT -> spaceAccent
        chosen != pt.dweep.sideraven.DEFAULT_SPACE_ACCENT -> chosen
        else -> spaceAccent
    }

/** [scheme] with its accent replaced by the Space's, for the given theme. */
fun ColorScheme.withSpaceAccent(
    key: String?,
    dark: Boolean,
): ColorScheme {
    val accent = SpaceAccents.of(key).let { if (dark) it.dark else it.light }
    return copy(primary = accent, surfaceTint = accent)
}
