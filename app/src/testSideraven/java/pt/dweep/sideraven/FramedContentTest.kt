/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Ticket 17: framed content insets and corner masks. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FramedContentTest {
    @Test
    fun `GIVEN the Sidebar is shown THEN the content is inset by the gutter on every side`() {
        assertEquals(FrameInsets(start = 292, top = 12, end = 12, bottom = 12), frameInsets(280, 12, framed = true))
    }

    @Test
    fun `GIVEN no Sidebar THEN the content is edge to edge, as in plain Fenix`() {
        assertEquals(FrameInsets(0, 0, 0, 0), frameInsets(0, 12, framed = false))
    }

    @Test
    fun `THEN the corner mask paints only the corners, never the middle of the page`() {
        val mask =
            CornerMaskView(ApplicationProvider.getApplicationContext()).apply {
                radius = 20f
                frameColor = Color.RED
                layout(0, 0, 200, 100)
            }
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        mask.draw(Canvas(bitmap))

        assertTrue(bitmap.getPixel(0, 0) == Color.RED)
        assertTrue(bitmap.getPixel(199, 99) == Color.RED)
        assertEquals(0, Color.alpha(bitmap.getPixel(100, 50)))
        assertEquals(0, Color.alpha(bitmap.getPixel(100, 0)))
    }

    @Test
    fun `THEN the corner mask never takes touches`() {
        val mask = CornerMaskView(ApplicationProvider.getApplicationContext())

        assertFalse(mask.isClickable)
        assertFalse(mask.isFocusable)
    }
}
