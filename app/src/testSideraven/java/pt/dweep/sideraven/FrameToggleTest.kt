/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.fenix.R
import pt.dweep.sideraven.settings.SidebarPreferences

/** Spec 0007 ticket 11: the Rounded frame setting. */
@RunWith(AndroidJUnit4::class)
class FrameToggleTest {
    private fun state(
        visible: Boolean,
        roundedFrame: Boolean,
    ) = SidebarUiState(isVisible = visible, isPrivate = false, tabs = emptyList(), mode = SidebarMode.Persistent, look = SidebarLook(roundedFrame = roundedFrame))

    @Test
    fun `GIVEN the Sidebar shown THEN content is framed only while Rounded frame is on`() {
        assertTrue(state(visible = true, roundedFrame = true).framesContent)
        assertFalse(state(visible = true, roundedFrame = false).framesContent)
        assertFalse(state(visible = false, roundedFrame = true).framesContent)
    }

    @Test
    fun `GIVEN no frame THEN content sits right against the Sidebar, with no gutter`() {
        val insets = frameInsets(dockedWidthPx = 280, gutterPx = 6, framed = false, side = SidebarSide.Left)

        assertEquals(280, insets.start)
        assertEquals(listOf(0, 0, 0), listOf(insets.top, insets.end, insets.bottom))
    }

    @Test
    fun `THEN it is a switch writing the store`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        val store = InMemorySidebarModeStore()

        SidebarPreferences.switchFor(context, screen, SidebarPreferences.FRAME_KEY, store.roundedFrame, R.string.sideraven_settings_frame, R.string.sideraven_settings_frame_summary)
            .callChangeListener(false)

        assertFalse(store.roundedFrame.value)
    }
}
