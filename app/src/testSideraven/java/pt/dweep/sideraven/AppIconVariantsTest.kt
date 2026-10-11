/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.R
import org.mozilla.fenix.iconpicker.AppIcon

/** The app icon picker's previews match the launcher icons: logo2's raven, yellow on dark backgrounds. */
class AppIconVariantsTest {
    @Test
    fun `THEN every yellow-raven background is one of the picker's icons`() {
        val aliases = AppIcon.entries.map { it.aliasSuffix }.toSet()
        assertTrue(SideravenAppIcons.yellowRaven - aliases == emptySet<String>())
    }

    @Test
    fun `THEN the dark backgrounds preview the yellow raven and the light ones the navy raven`() {
        assertEquals(R.drawable.sideraven_ic_icon_preview_raven_yellow, AppIcon.AppSolidDark.iconForegroundId)
        assertEquals(R.drawable.sideraven_ic_icon_preview_raven_yellow, AppIcon.AppGradientMidnight.iconForegroundId)
        assertEquals(R.drawable.sideraven_ic_icon_preview_raven_navy, AppIcon.AppSolidLight.iconForegroundId)
        assertEquals(R.drawable.sideraven_ic_icon_preview_raven_navy, AppIcon.AppGradientGoldenHour.iconForegroundId)
    }

    @Test
    fun `THEN the Featured icons preview their own ravens`() {
        assertEquals(R.drawable.sideraven_ic_icon_preview_classic, AppIcon.AppSideravenClassic.iconForegroundId)
        assertEquals(R.drawable.sideraven_ic_icon_preview_teal_and_purple, AppIcon.AppSideravenTealAndPurple.iconForegroundId)
        assertEquals(R.drawable.sideraven_ic_icon_preview_black_and_white, AppIcon.AppSideravenBlackAndWhite.iconForegroundId)
    }
}
