/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.sync.ZenColours
import pt.dweep.sideraven.sync.ZenOrigin
import pt.dweep.sideraven.theme.SpaceThemes
import pt.dweep.sideraven.theme.pureBlack
import pt.dweep.sideraven.theme.zenColorScheme

/** Space themes: a Space's Zen gradient (or its accent) tints the frame; pure black replaces it. */
@RunWith(AndroidJUnit4::class)
class SpaceThemeTest {
    private fun fromZen(
        accent: String,
        syncedAccent: String = accent,
        dots: List<String> = listOf("#ff0000", "#0000ff"),
    ): Space {
        val theme = JSONObject().put("type", "gradient").put("gradientColors", JSONArray(dots.mapIndexed { i, c -> JSONObject().put("c", c).put("isPrimary", i == 0) }))
        val data = JSONObject().put("uuid", "s").put("name", "Work").put("theme", theme)
        return Space("s", "Work", "💼", accent, extra = ZenOrigin("rid", data, syncedAccent).encode())
    }

    @Test
    fun `GIVEN a Space from Zen THEN its gradient themes it, unless colours aren't shared or the accent was changed here`() {
        assertEquals(listOf("#ff0000", "#0000ff"), ZenColours.gradientOf(fromZen("red"), shareColours = true))
        assertEquals(emptyList<String>(), ZenColours.gradientOf(fromZen("red"), shareColours = false))
        assertEquals(emptyList<String>(), ZenColours.gradientOf(fromZen("teal", syncedAccent = "red"), shareColours = true))
        assertEquals(emptyList<String>(), ZenColours.gradientOf(Space("l", "Here", "🏠", "red"), shareColours = true))
    }

    @Test
    fun `THEN the frame runs from the gradient's first colour at the top to its last at the bottom, faintly`() {
        val surface = zenColorScheme(dark = true).surface
        val tint = SpaceThemes.tint(surface, accent = Color.Green, gradient = listOf(Color.Red, Color.Blue), dark = true)
        assertTrue("top leans red: ${tint.top}", tint.top.red > tint.top.blue && tint.top.red > surface.red)
        assertTrue("bottom leans blue: ${tint.bottom}", tint.bottom.blue > tint.bottom.red && tint.bottom.blue > surface.blue)

        // A Space made here: its accent, top to bottom.
        val local = SpaceThemes.tint(surface, accent = Color.Green, gradient = emptyList(), dark = true)
        assertEquals(local.top, local.bottom)
        assertTrue(local.top.green > surface.green)
    }

    @Test
    fun `THEN text stays readable on any tint, in both themes`() {
        val extremes = listOf(Color.White, Color.Black, Color.Yellow, Color.Red, Color.Blue, Color.Cyan, Color(0xFF00FF00))
        listOf(true, false).forEach { dark ->
            val scheme = zenColorScheme(dark)
            extremes.forEach { colour ->
                val tinted = SpaceThemes.tint(scheme.surface, colour, listOf(colour), dark).top
                assertTrue("dark=$dark $colour: ${contrast(scheme.onSurface, tinted)}", contrast(scheme.onSurface, tinted) >= 4.5)
                assertTrue("dark=$dark $colour secondary: ${contrast(scheme.onSurfaceVariant, tinted)}", contrast(scheme.onSurfaceVariant, tinted) >= 3.0)
            }
        }
    }

    @Test
    fun `GIVEN pure black THEN the frame is black and the accent stays`() {
        val scheme = zenColorScheme(dark = true).copy(primary = Color.Magenta).pureBlack()
        assertEquals(Color.Black, scheme.surface)
        assertEquals(Color.Black, scheme.background)
        assertEquals(Color.Magenta, scheme.primary)
        assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerHighest) >= 4.5)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }
}
