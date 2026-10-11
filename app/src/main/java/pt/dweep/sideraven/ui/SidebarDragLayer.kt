/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import pt.dweep.sideraven.SidebarSpace
import pt.dweep.sideraven.theme.SideravenMotion
import kotlin.math.roundToInt
import mozilla.components.ui.icons.R as iconsR

/** Where a drag in the Sidebar started. */
enum class DragSource { Tab, Essential, Pinned }

/** Where a dragged item can go, besides reordering inside its own section. */
enum class DragZone { Essentials, Pin, Unpin }

/** The zones a drag from this source can drop on. */
fun DragSource.zones(): List<DragZone> =
    when (this) {
        DragSource.Tab -> listOf(DragZone.Essentials, DragZone.Pin)
        DragSource.Essential -> listOf(DragZone.Pin, DragZone.Unpin)
        DragSource.Pinned -> listOf(DragZone.Essentials, DragZone.Unpin)
    }

/** The zone under [pointer] that accepts a drag from [source], or null (reorder, or nowhere). */
fun hoveredZone(
    source: DragSource,
    pointer: Offset,
    zones: Map<DragZone, Rect>,
): DragZone? = source.zones().firstOrNull { zones[it]?.contains(pointer) == true }

/** What a drag carries: enough to draw its floating copy. */
data class DragItem(
    val source: DragSource,
    val title: String,
    val url: String,
    val icon: Bitmap?,
    /** Where the finger held the item, from its centre: the copy stays under the finger there. */
    val grabOffset: Offset = Offset.Zero,
)

/**
 * One drag in the Sidebar, whichever section it started in. Sections report where the finger is
 * (root coordinates) and where they are; the drag layer draws the floating copy and the targets
 * above everything, so nothing moves when a drag starts.
 */
@Stable
class SidebarDragState {
    var item by mutableStateOf<DragItem?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set

    /** The sections, in root coordinates ([Rect.Zero] when absent). */
    var essentialsSection by mutableStateOf(Rect.Zero)
    var pinnedSection by mutableStateOf(Rect.Zero)
    var tabList by mutableStateOf(Rect.Zero)

    /** Where the Sidebar's content starts (below the status bar), in root coordinates. */
    var contentTop by mutableStateOf(0f)

    /** The zones the Sidebar offers right now; private browsing offers none (no Essentials, no Spaces). */
    var offered by mutableStateOf(DragZone.entries.toSet())

    /** The zones a drag from [source] can drop on here. */
    fun zonesFor(source: DragSource): List<DragZone> = source.zones().filter { it in offered }

    /** The targets the drag layer draws when a section is empty. */
    var essentialsTarget by mutableStateOf(Rect.Zero)
    var pinTarget by mutableStateOf(Rect.Zero)

    val zones: Map<DragZone, Rect>
        get() =
            mapOf(
                // Essentials is the topmost target: a finger past its top edge still counts.
                DragZone.Essentials to essentialsSection.orElse(essentialsTarget).let { if (it == Rect.Zero) it else it.copy(top = Float.NEGATIVE_INFINITY) },
                DragZone.Pin to pinnedSection.orElse(pinTarget),
                DragZone.Unpin to unpinArea,
            )

    /** The Tab List, below any target drawn over its top. */
    val unpinArea: Rect
        get() {
            val above = maxOf(essentialsTarget.bottom, pinTarget.bottom)
            return if (tabList != Rect.Zero && above > tabList.top) tabList.copy(top = minOf(above, tabList.bottom)) else tabList
        }

    val hovered: DragZone?
        get() = item?.let { hoveredZone(it.source, pointer, zones.filterKeys { zone -> zone in offered }) }

    fun start(
        item: DragItem,
        at: Offset,
    ) {
        this.item = item
        pointer = at
    }

    fun move(to: Offset) {
        pointer = to
    }

    /** Ends the drag; returns the zone it was dropped on. */
    fun end(): DragZone? {
        val zone = hovered
        item = null
        return zone
    }

    private fun Rect.orElse(other: Rect) = if (this == Rect.Zero) other else this
}

internal val LocalSidebarDrag = staticCompositionLocalOf { SidebarDragState() }

/**
 * The drag layer, above the whole Sidebar: the targets for empty sections, the Unpin highlight over
 * the Tab List, and the floating copy under the finger, shaped like what it would become.
 *
 * @param essentialsBlocked Pinning to Essentials would be refused (cap reached, or Private Mode).
 * @param pinBlocked Pinning to the Space would be refused.
 */
@Composable
internal fun BoxScope.SidebarDragLayer(
    drag: SidebarDragState,
    showLabels: Boolean,
    currentSpace: SidebarSpace?,
    essentialsBlocked: Boolean,
    pinBlocked: Boolean,
) {
    val item = drag.item
    val haptics = LocalHapticFeedback.current
    val hovered = drag.hovered
    // A light tick when the finger enters a target that would take the drop (a full one doesn't tick).
    val accepts =
        when (hovered) {
            DragZone.Essentials -> !essentialsBlocked
            DragZone.Pin -> !pinBlocked
            DragZone.Unpin -> true
            null -> false
        }
    LaunchedEffect(hovered) { if (accepts) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    val density = LocalDensity.current
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.matchParentSize().onGloballyPositioned { layerOrigin = it.boundsInRoot().topLeft })
    if (item == null) return
    val zones = drag.zonesFor(item.source)
    val gap = with(density) { 8.dp.toPx() }

    // Essentials, when the section is empty: a target at the top of the Sidebar.
    var nextTop = drag.contentTop
    if (DragZone.Essentials in zones && drag.essentialsSection == Rect.Zero) {
        DropTargetBox(
            top = nextTop + gap - layerOrigin.y,
            height = 52.dp,
            label = if (showLabels) stringResource(R.string.sideraven_essentials) else null,
            iconRes = iconsR.drawable.mozac_ic_pin_24,
            active = hovered == DragZone.Essentials,
            blocked = essentialsBlocked,
            testTag = SidebarTestTag.ESSENTIALS_TARGET,
            onBounds = { drag.essentialsTarget = it },
        )
        nextTop += gap + with(density) { 52.dp.toPx() }
    } else if (drag.essentialsSection != Rect.Zero) {
        nextTop = drag.essentialsSection.bottom
    }
    // Pin to Space, when the Space has no Pinned Tabs yet: just below the Essentials.
    if (DragZone.Pin in zones && drag.pinnedSection == Rect.Zero && currentSpace != null) {
        DropTargetBox(
            top = nextTop + gap / 2 - layerOrigin.y,
            height = 40.dp,
            label = if (showLabels) stringResource(R.string.sideraven_pin) else null,
            iconRes = iconsR.drawable.mozac_ic_pin_24,
            leading = { SpaceIcon(currentSpace.icon, size = 16.dp) },
            active = hovered == DragZone.Pin,
            blocked = pinBlocked,
            testTag = SidebarTestTag.PIN_TARGET,
            onBounds = { drag.pinTarget = it },
        )
    }
    // Unpin: the Tab List lights up, labelled.
    if (DragZone.Unpin in zones && drag.tabList != Rect.Zero) {
        val list = drag.unpinArea
        val active = hovered == DragZone.Unpin
        val colors = MaterialTheme.colorScheme
        val fade by animateFloatAsState(if (active) 1f else 0.6f, LocalSidebarMotion.current.fade(SideravenMotion.SELECTION_FADE_MS), label = "unpin")
        Box(
            modifier =
                Modifier
                    .offset { IntOffset((list.left - layerOrigin.x).roundToInt(), (list.top - layerOrigin.y).roundToInt()) }
                    .size(with(density) { list.width.toDp() }, with(density) { list.height.toDp() })
                    .padding(6.dp)
                    .graphicsLayer { alpha = fade }
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.primary.copy(alpha = if (active) 0.14f else 0.05f))
                    .border(if (active) 2.dp else 1.dp, colors.primary.copy(alpha = if (active) 1f else 0.5f), MaterialTheme.shapes.medium)
                    .testTag(SidebarTestTag.UNPIN_TARGET),
            contentAlignment = Alignment.TopCenter,
        ) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(iconsR.drawable.mozac_ic_pin_slash_24), contentDescription = null, tint = colors.primary, modifier = Modifier.size(18.dp))
                if (showLabels) {
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sideraven_unpin), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                }
            }
        }
    }

    DragGhost(item, hovered, drag.pointer - layerOrigin, layerWidthPx = drag.tabList.width.takeIf { it > 0f })
}

/** A drop target drawn over the Sidebar while a drag is in progress. */
@Composable
private fun DropTargetBox(
    top: Float,
    height: Dp,
    label: String?,
    iconRes: Int,
    active: Boolean,
    blocked: Boolean,
    testTag: String,
    onBounds: (Rect) -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val tint = if (blocked) colors.outline else colors.primary
    DisposableEffect(Unit) { onDispose { onBounds(Rect.Zero) } }
    Row(
        modifier =
            Modifier
                .offset { IntOffset(0, top.roundToInt()) }
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(height)
                .onGloballyPositioned { onBounds(it.boundsInRoot()) }
                .clip(MaterialTheme.shapes.medium)
                .background(if (active && !blocked) colors.primary.copy(alpha = 0.22f) else colors.surfaceContainerHigh)
                .border(if (active) 2.dp else 1.dp, tint.copy(alpha = if (active) 1f else 0.6f), MaterialTheme.shapes.medium)
                .testTag(testTag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

/**
 * The floating copy under the finger. It takes the shape of what the item would become where it
 * is: an Essential tile over Essentials, a Pinned Tab row over Pin, a tab row elsewhere.
 */
@Composable
private fun DragGhost(
    item: DragItem,
    hovered: DragZone?,
    pointer: Offset,
    layerWidthPx: Float?,
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val asTile = hovered == DragZone.Essentials || (hovered == null && item.source == DragSource.Essential)
    val rowWidth = layerWidthPx?.let { with(density) { it.toDp() } - 16.dp } ?: 240.dp
    val motion = LocalSidebarMotion.current
    val width by animateDpAsState(if (asTile) 64.dp else rowWidth, motion.move(GHOST_MORPH_MS), label = "ghostWidth")
    val height by animateDpAsState(
        when {
            asTile -> 48.dp
            hovered == DragZone.Pin -> 40.dp
            else -> 44.dp
        },
        motion.move(GHOST_MORPH_MS),
        label = "ghostHeight",
    )
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    // Held where it was grabbed; as a tile over Essentials it centres under the finger.
    val anchor by androidx.compose.animation.core.animateOffsetAsState(
        if (asTile) Offset.Zero else item.grabOffset,
        motion.move(GHOST_MORPH_MS),
        label = "ghostAnchor",
    )
    // Lifts off the list: a slight grow and its shadow coming in.
    val lift = remember { Animatable(0f) }
    LaunchedEffect(Unit) { lift.animateTo(1f, motion.fade(GHOST_MORPH_MS)) }
    Row(
        modifier =
            Modifier
                .offset { IntOffset((pointer.x - anchor.x - widthPx / 2).roundToInt(), (pointer.y - anchor.y - heightPx / 2).roundToInt()) }
                .size(width, height)
                .graphicsLayer {
                    val scale = if (motion.moves) 1f + GHOST_LIFT * lift.value else 1f
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = 8.dp.toPx() * lift.value
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
                .background(colors.surfaceContainerHigh)
                .border(1.dp, colors.primary.copy(alpha = if (hovered != null) 0.9f else 0.3f), RoundedCornerShape(10.dp))
                .testTag(SidebarTestTag.DRAG_GHOST)
                .padding(horizontal = 12.dp),
        horizontalArrangement = if (asTile) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.icon != null) {
            Image(item.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)))
        } else {
            LocalFaviconLoader.current(item.url, 20.dp)
        }
        if (!asTile) {
            Spacer(Modifier.width(12.dp))
            if (hovered == DragZone.Pin) {
                Icon(painterResource(iconsR.drawable.mozac_ic_pin_24), contentDescription = null, tint = colors.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val GHOST_MORPH_MS = 150

/** How much the floating copy grows as it lifts off the list. */
private const val GHOST_LIFT = 0.03f
