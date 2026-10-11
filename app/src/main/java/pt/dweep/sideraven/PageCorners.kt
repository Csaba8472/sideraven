/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View

/** How the frame looks right now: whether content is framed, its colour and corner radius. */
data class FrameLook(
    val framed: Boolean = false,
    val color: Int = 0,
    val radiusPx: Float = 0f,
)

/**
 * Where a toolbar meets the page, in the host's coordinates. [pageBelow] is true for a top
 * toolbar (the page starts at [y]) and false for a bottom one (the page ends at [y]).
 */
data class PageEdge(
    val y: Float,
    val pageBelow: Boolean,
)

/**
 * The edge each toolbar makes with the page: a toolbar in the upper half of [hostHeight] is a top
 * toolbar (its bottom is the edge), otherwise a bottom one (its top is the edge).
 */
fun pageEdges(
    hostHeight: Int,
    toolbars: List<Pair<Float, Float>>,
): List<PageEdge> =
    toolbars.map { (top, bottom) ->
        if ((top + bottom) / 2 < hostHeight / 2f) PageEdge(bottom, pageBelow = true) else PageEdge(top, pageBelow = false)
    }

/**
 * Framed content, where the page meets Fenix's toolbar: Fenix draws a straight divider there. This
 * rounds the page's corners on that edge (the top corners under a top toolbar, the bottom ones
 * above a bottom toolbar) and paints over the divider, so the page reads as a rounded card. It
 * paints in the frame colour, which is Fenix's toolbar colour, like [CornerMaskView].
 */
internal class PageCornersDrawable(
    private val host: View,
    private val toolbars: () -> List<View>,
    private val dividerPx: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()
    private val hostLocation = IntArray(2)
    private val location = IntArray(2)
    private var edges: List<PageEdge> = emptyList()

    var look: FrameLook = FrameLook()
        set(value) {
            field = value
            paint.color = value.color
            refresh(force = true)
        }

    /** Before each frame: redraw only when a toolbar moved (it scrolls with the page). */
    fun refresh(force: Boolean = false) {
        val now = if (look.framed && look.radiusPx > 0f) measure() else emptyList()
        if (force || now != edges) {
            edges = now
            invalidateSelf()
        }
    }

    private fun measure(): List<PageEdge> {
        host.getLocationInWindow(hostLocation)
        val shown =
            toolbars()
                .filter { it.isShown && it.height > 0 && it.alpha > 0f }
                .map {
                    it.getLocationInWindow(location)
                    val top = (location[1] - hostLocation[1]).toFloat()
                    top to top + it.height
                }
        return pageEdges(host.height, shown)
    }

    override fun draw(canvas: Canvas) {
        val r = look.radiusPx
        if (r <= 0f) return
        val w = bounds.width().toFloat()
        edges.forEach { edge ->
            val y = edge.y
            if (edge.pageBelow) {
                // Over the divider at the toolbar's bottom, then the page's top corners.
                canvas.drawRect(0f, y - dividerPx, w, y, paint)
                corners(canvas, w, bandTop = y, bandBottom = y + r, roundTop = y, roundBottom = y + 4 * r)
            } else {
                canvas.drawRect(0f, y, w, y + dividerPx, paint)
                corners(canvas, w, bandTop = y - r, bandBottom = y, roundTop = y - 4 * r, roundBottom = y)
            }
        }
    }

    /** The corner slivers inside the band: the band minus a rounded rect, clipped to the band. */
    private fun corners(
        canvas: Canvas,
        w: Float,
        bandTop: Float,
        bandBottom: Float,
        roundTop: Float,
        roundBottom: Float,
    ) {
        val r = look.radiusPx
        canvas.save()
        canvas.clipRect(0f, bandTop, w, bandBottom)
        path.reset()
        path.fillType = Path.FillType.EVEN_ODD
        path.addRect(0f, bandTop, w, bandBottom, Path.Direction.CW)
        rect.set(0f, roundTop, w, roundBottom)
        path.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
