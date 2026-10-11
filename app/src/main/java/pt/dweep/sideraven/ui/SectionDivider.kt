/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import pt.dweep.sideraven.CollapsedSection
import pt.dweep.sideraven.DEFAULT_SECTION_SHARE
import pt.dweep.sideraven.MAX_SECTION_SHARE
import pt.dweep.sideraven.MIN_SECTION_SHARE
import pt.dweep.sideraven.SectionShares
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.theme.SideravenMotion

/** TalkBack's "Show more" and "Show less" move a Divider by this share of the Sidebar's height. */
private const val ACCESSIBILITY_STEP = 0.1f

/** Tall enough to hold with a finger; the line itself stays thin. */
private val DividerTouchHeight = 12.dp

/**
 * Collapsed Mode: the height a section above the Tab List may take, set by dragging its Divider.
 * While the Divider is held the section follows the finger; on release the share is remembered.
 */
internal class SectionResizer(private val section: CollapsedSection) {
    // Refreshed on every composition (rememberSectionResizer).
    internal var share = DEFAULT_SECTION_SHARE
    internal var panelPx = 1f
    internal var density = 1f
    internal var measuredPx: () -> Float = { 0f }
    internal var onIntent: (SidebarIntent) -> Unit = {}

    /** The section's height from a drag, until the remembered share catches up; null otherwise. */
    private var draggedPx by mutableStateOf<Float?>(null)

    /** Whether the Divider is held right now (it shows in the accent colour). */
    var held by mutableStateOf(false)
        private set

    /** Whether the section follows a drag instead of animating. */
    val resizing: Boolean get() = draggedPx != null

    /** The section's maximum height: where the finger is, or the remembered share. */
    val maxHeight: Dp get() = ((draggedPx ?: (share * panelPx)) / density).dp

    private fun clamp(px: Float) = px.coerceIn(MIN_SECTION_SHARE * panelPx, MAX_SECTION_SHARE * panelPx)

    /** The Divider was picked up: start from the section's height on screen. */
    fun pickUp(): Float {
        held = true
        return clamp(measuredPx()).also { draggedPx = it }
    }

    /** The Divider is [px] below the section's top. */
    fun drag(px: Float) {
        draggedPx = clamp(px)
    }

    /** Remembers where the finger stopped (a section with less in it stays its own height). */
    fun release() {
        held = false
        val px = draggedPx ?: return
        if (kotlin.math.abs(px / panelPx - share) < SHARE_EPSILON) {
            draggedPx = null
        } else {
            onIntent(SidebarIntent.ResizeSection(section, px / panelPx))
        }
    }

    /** TalkBack: one step taller or shorter. */
    fun step(by: Float) = onIntent(SidebarIntent.ResizeSection(section, clamp(measuredPx()) / panelPx + by))

    /** The remembered share changed: it now holds the dragged height. */
    internal fun settle() {
        draggedPx = null
    }
}

private const val SHARE_EPSILON = 0.0001f

@Composable
internal fun rememberSectionResizer(
    section: CollapsedSection,
    shares: SectionShares,
    panelHeight: Dp,
    onIntent: (SidebarIntent) -> Unit,
    measuredPx: () -> Float,
): SectionResizer {
    val resizer = remember(section) { SectionResizer(section) }
    val density = LocalDensity.current.density
    resizer.share = shares[section]
    resizer.density = density
    resizer.panelPx = panelHeight.value * density
    resizer.measuredPx = measuredPx
    resizer.onIntent = onIntent
    LaunchedEffect(shares[section]) { resizer.settle() }
    return resizer
}

/**
 * The Divider under the Essentials Section or the Pinned Tabs. With a [resizer] (Collapsed Mode),
 * hold it and drag to show more or less of the section above; it thickens in the accent colour
 * while held.
 */
@Composable
internal fun SectionDivider(
    resizer: SectionResizer?,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    if (resizer == null) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = colors.outlineVariant)
        return
    }
    val haptics = LocalHapticFeedback.current
    val held = resizer.held
    val thickness by animateDpAsState(if (held) 3.dp else 1.dp, LocalSidebarMotion.current.move(SideravenMotion.SELECTION_FADE_MS), label = "dividerThickness")
    val more = stringResource(R.string.sideraven_section_show_more)
    val less = stringResource(R.string.sideraven_section_show_less)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(DividerTouchHeight)
                .pointerInput(resizer) {
                    var top = 0f
                    var dy = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            top = resizer.pickUp()
                            dy = 0f
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dy += amount.y
                            resizer.drag(top + dy)
                        },
                        onDragEnd = { resizer.release() },
                        onDragCancel = { resizer.release() },
                    )
                }
                .semantics {
                    contentDescription = label
                    customActions =
                        listOf(
                            CustomAccessibilityAction(more) { resizer.step(ACCESSIBILITY_STEP).let { true } },
                            CustomAccessibilityAction(less) { resizer.step(-ACCESSIBILITY_STEP).let { true } },
                        )
                }
                .testTag(SidebarTestTag.SECTION_DIVIDER),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = if (held) 8.dp else 12.dp),
            thickness = thickness,
            color = if (held) colors.primary else colors.outlineVariant,
        )
    }
}
