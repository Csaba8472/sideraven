/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.theme

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The frame's colours under a Space theme: a vertical gradient from [top] to [bottom]. The frame is
 * the Sidebar, the gutter around the page and Fenix's toolbar beside it.
 */
data class FrameTint(
    val top: Color,
    val bottom: Color,
) {
    val brush: Brush get() = Brush.verticalGradient(listOf(top, bottom))

    /** The colour halfway down, for small pieces that can't carry the gradient (the Sidebar Flap). */
    val middle: Color get() = lerp(top, bottom, 0.5f)
}

/**
 * Space themes: the current Space's colours, as Zen desktop paints them behind its sidebar, laid
 * faintly over the frame's surface. A Space from Zen brings its gradient; a Space made here, its accent.
 */
object SpaceThemes {
    /** How much of the Space's colour shows through. Light surfaces show colour more, so they take less. */
    private const val DARK_STRENGTH = 0.22f
    private const val LIGHT_STRENGTH = 0.16f

    /** The frame for a Space with [gradient] (top to bottom; empty for none) or else [accent], over [surface]. */
    fun tint(
        surface: Color,
        accent: Color,
        gradient: List<Color>,
        dark: Boolean,
    ): FrameTint {
        val strength = if (dark) DARK_STRENGTH else LIGHT_STRENGTH
        val first = gradient.firstOrNull() ?: accent
        val last = gradient.lastOrNull() ?: accent
        return FrameTint(lerp(surface, first, strength), lerp(surface, last, strength))
    }
}

/**
 * Pure black, for OLED screens in the dark theme: the frame and the page's surroundings are black,
 * the raised surfaces near-black greys, and the accent stays.
 */
fun ColorScheme.pureBlack(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0B),
        surfaceContainer = Color(0xFF111113),
        surfaceContainerHigh = Color(0xFF1A1A1D),
        surfaceContainerHighest = Color(0xFF242428),
        surfaceBright = Color(0xFF242428),
        surfaceVariant = Color(0xFF1A1A1D),
    )

/** The current Space theme's frame, or null for a plain surface (pure black, Private, or themes off). */
val LocalFrameTint = staticCompositionLocalOf<FrameTint?> { null }

/**
 * How strongly a raised piece of the Sidebar (an Essential tile, the address bar, a selected row) stands
 * out from the panel. Under a Space theme it's a see-through layer of the text colour, so the gradient
 * shows through and each piece takes the colour of where it sits; otherwise the theme's container colour.
 */
enum class SidebarFill(
    internal val alpha: Float,
) {
    Container(0.06f),
    High(0.10f),
    Highest(0.14f),
}

/** The colour of a raised piece of the Sidebar at [level] (see [SidebarFill]). */
@Composable
fun sidebarFill(level: SidebarFill): Color {
    val colors = MaterialTheme.colorScheme
    if (LocalFrameTint.current != null) return colors.onSurface.copy(alpha = level.alpha)
    return when (level) {
        SidebarFill.Container -> colors.surfaceContainer
        SidebarFill.High -> colors.surfaceContainerHigh
        SidebarFill.Highest -> colors.surfaceContainerHighest
    }
}

/** The frame's background: the Space theme's gradient, else the surface colour. */
@Composable
fun Modifier.frameBackground(): Modifier = LocalFrameTint.current?.let { background(it.brush) } ?: background(MaterialTheme.colorScheme.surface)
