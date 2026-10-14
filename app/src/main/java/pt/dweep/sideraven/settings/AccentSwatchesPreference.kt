/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import androidx.compose.ui.graphics.toArgb
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import org.mozilla.fenix.R
import pt.dweep.sideraven.Setting
import pt.dweep.sideraven.theme.SpaceAccents
import pt.dweep.sideraven.ui.accentLabel

/**
 * The accent colour as a row of swatches, one per Space colour: the chosen one is
 * ringed. Each swatch is a button named after its colour for screen readers.
 */
class AccentSwatchesPreference(
    context: Context,
    private val setting: Setting<String>,
) : Preference(context) {
    init {
        layoutResource = R.layout.sideraven_swatches_preference
        isSelectable = false
        isPersistent = false
        isIconSpaceReserved = false
    }

    /** The chosen accent key. */
    val selected: String get() = setting.value

    fun choose(key: String) {
        if (key == setting.value) return
        setting.set(key)
        notifyChanged()
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val row = holder.findViewById(R.id.sideraven_swatches) as LinearLayout
        row.removeAllViews()
        val density = context.resources.displayMetrics.density
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val size = (SWATCH_DP * density).toInt()
        val gap = (GAP_DP * density).toInt()
        SpaceAccents.all.forEach { accent ->
            val chosen = accent.key == setting.value
            row.addView(
                View(context).apply {
                    background =
                        GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor((if (dark) accent.dark else accent.light).toArgb())
                            if (chosen) setStroke((RING_DP * density).toInt(), (if (dark) 0xFFFFFFFF else 0xFF000000).toInt())
                        }
                    contentDescription = context.getString(accentLabel(accent.key))
                    isSelected = chosen
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { choose(accent.key) }
                },
                LinearLayout.LayoutParams(size, size).apply { marginEnd = gap },
            )
        }
    }

    private companion object {
        const val SWATCH_DP = 32
        const val GAP_DP = 12
        const val RING_DP = 3
    }
}
