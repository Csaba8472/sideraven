/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.Preference
import org.mozilla.fenix.settings.ComposePreference

/** One option of a choice: its [value], its [label], and for a picture picker an [icon] and a [caption]. */
data class Option<T>(
    val value: T,
    val label: Int,
    val icon: Int? = null,
    val caption: Int? = null,
)

/** A settings row that holds one choice: what's [selected], and [choose] to pick (as a tap does). */
interface Choice<T> {
    val selected: T

    /** Picks [value] and writes it, as tapping it does. */
    fun choose(value: T)

    /** Shows [value] as chosen without writing it (the stored value, when the screen is shown again). */
    fun show(value: T)
}

/**
 * Two or three short options as Material segmented buttons under the row's title, for a choice you
 * might flip (Left | Right). [summaryFor] adds a line under the title that follows the choice.
 */
class SegmentedPreference<T>(
    context: Context,
    private val options: List<Option<T>>,
    initial: T,
    private val onChoose: (T) -> Unit,
    private val summaryFor: (T) -> Int? = { null },
) : ComposePreference(context), Choice<T> {
    override var selected: T by mutableStateOf(initial)
        private set

    /** Runs after a pick has been written (another row may need to show what it changed). */
    var onChosen: () -> Unit = {}

    init {
        isSelectable = false
        isPersistent = false
        isIconSpaceReserved = false
    }

    override fun choose(value: T) {
        if (value == selected) return
        selected = value
        onChoose(value)
        onChosen()
    }

    override fun show(value: T) {
        selected = value
    }

    @Composable
    override fun Content() {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = title?.toString().orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            summaryFor(selected)?.let {
                Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option.value == selected,
                        onClick = { choose(option.value) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) {
                        Text(text = stringResource(option.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Two or three illustrated tiles, for a choice that changes the whole screen (the Sidebar's mode, the
 * address bar's place). Each tile shows its picture, name and a one-line caption; the chosen one is ringed.
 */
class PicturePickerPreference<T>(
    context: Context,
    private val options: List<Option<T>>,
    initial: T,
    private val onChoose: (T) -> Unit,
) : ComposePreference(context), Choice<T> {
    override var selected: T by mutableStateOf(initial)
        private set

    /** The options shown (a tier may offer fewer). */
    var offered: List<T> by mutableStateOf(options.map { it.value })

    init {
        isSelectable = false
        isPersistent = false
        isIconSpaceReserved = false
    }

    override fun choose(value: T) {
        if (value == selected) return
        selected = value
        onChoose(value)
    }

    override fun show(value: T) {
        selected = value
    }

    @Composable
    override fun Content() {
        val colors = MaterialTheme.colorScheme
        val shape = RoundedCornerShape(12.dp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            options.filter { it.value in offered }.forEach { option ->
                val chosen = option.value == selected
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .border(if (chosen) 2.dp else 1.dp, if (chosen) colors.primary else colors.outlineVariant, shape)
                            .background(if (chosen) colors.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .selectable(selected = chosen, role = Role.RadioButton) { choose(option.value) }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    option.icon?.let {
                        Icon(
                            painter = painterResource(it),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (chosen) colors.primary else colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(text = stringResource(option.label), style = MaterialTheme.typography.labelLarge, color = colors.onSurface, textAlign = TextAlign.Center)
                    option.caption?.let {
                        Text(text = stringResource(it), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * A choice of four or more options, or one rarely changed: a row with the current value as its summary
 * ("Essentials per row · Automatic"), opening a dialog to pick, as Android's own settings do.
 */
class ChoiceRowPreference<T>(
    context: Context,
    private val options: List<Option<T>>,
    initial: T,
    private val onChoose: (T) -> Unit,
) : Preference(context), Choice<T> {
    override var selected: T = initial
        private set

    init {
        isPersistent = false
        isIconSpaceReserved = false
        summaryProvider = SummaryProvider<ChoiceRowPreference<T>> { context.getString(it.labelOf(it.selected)) }
    }

    private fun labelOf(value: T): Int = options.first { it.value == value }.label

    override fun choose(value: T) {
        show(value)
        onChoose(value)
    }

    override fun show(value: T) {
        if (value == selected) return
        selected = value
        notifyChanged()
    }

    override fun onClick() {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setSingleChoiceItems(options.map { context.getString(it.label) }.toTypedArray(), options.indexOfFirst { it.value == selected }) { dialog, which ->
                choose(options[which].value)
                dialog.dismiss()
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
