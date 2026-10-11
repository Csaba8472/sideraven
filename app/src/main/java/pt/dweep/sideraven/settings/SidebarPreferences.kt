/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.os.Looper
import android.os.Handler
import android.content.SharedPreferences
import androidx.core.content.edit
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.settings.RadioButtonPreference
import org.mozilla.fenix.utils.view.addToRadioGroup
import android.content.Context
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import org.mozilla.fenix.R
import pt.dweep.sideraven.AddressBarLayout
import pt.dweep.sideraven.NewTabBehaviour
import pt.dweep.sideraven.NewTabPosition
import pt.dweep.sideraven.PreferencesSidebarModeStore
import pt.dweep.sideraven.SidebarGate
import pt.dweep.sideraven.SidebarMode
import pt.dweep.sideraven.SidebarModeStore
import pt.dweep.sideraven.SidebarSide
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.SidebarWidth
import pt.dweep.sideraven.AnimationLevel
import pt.dweep.sideraven.CloseButtons
import pt.dweep.sideraven.EssentialsPerRow
import pt.dweep.sideraven.Setting
import pt.dweep.sideraven.TabRows

/**
 * The categories of Sideraven Settings (spec 0008), each its own screen in
 * [SideravenSettingsFragment]; every choice sits under a heading with a one-line explanation.
 * All but Zen Sync configure the Sidebar, so they show only where there is one.
 */
object SidebarPreferences {
    const val POPUP_KEYBOARD_KEY = "sideraven_popup_keyboard"
    const val POPUP_OUTSIDE_KEY = "sideraven_popup_outside"
    const val MODE_KEY = "sideraven_sidebar_mode"
    const val PHONE_SIDEBAR_KEY = "sideraven_phone_sidebar"
    const val SWIPE_ZONE_KEY = "sideraven_sidebar_swipe_zone"
    const val INACTIVE_KEY = "sideraven_sidebar_inactive"
    const val FLAP_KEY = "sideraven_sidebar_flap"
    const val NEW_TAB_KEY = "sideraven_sidebar_new_tab"
    const val LAYOUT_KEY = "sideraven_sidebar_layout"
    const val WIDTH_KEY = "sideraven_sidebar_width"
    const val SIDE_KEY = "sideraven_sidebar_side"
    const val NEW_TAB_POSITION_KEY = "sideraven_new_tab_position"
    const val TAB_ROWS_KEY = "sideraven_tab_rows"
    const val HAPTICS_KEY = "sideraven_haptics"
    const val ANIMATIONS_KEY = "sideraven_animations"
    const val FRAME_KEY = "sideraven_rounded_frame"
    const val ACCENT_KEY = "sideraven_accent"
    const val ESSENTIALS_PER_ROW_KEY = "sideraven_essentials_per_row"
    const val SWIPE_TO_CLOSE_KEY = "sideraven_swipe_to_close"
    const val CLOSE_BUTTONS_KEY = "sideraven_close_buttons"

    /** Fills the screen of one [category]. Called from Sideraven Settings' onResume. */
    fun install(
        fragment: PreferenceFragmentCompat,
        category: SettingsCategory,
    ) {
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        install(context, screen, category, SidebarTier.from(context), PreferencesSidebarModeStore.get(context), context.components.settings.preferences) {
            SidebarTier.from(context)
        }
    }

    /**
     * Fills [screen] for [category] at [tier]. [currentTier] reads the tier again after a setting
     * that changes it ("Use the sidebar on phones"), so the screen can rebuild for the new one.
     */
    internal fun install(
        context: Context,
        screen: PreferenceGroup,
        category: SettingsCategory,
        tier: SidebarTier,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
        currentTier: () -> SidebarTier = { tier },
    ) {
        fun group(
            key: String,
            title: Int,
            about: Int?,
        ) = group(context, screen, key, title, about)
        when (category) {
            SettingsCategory.Sidebar -> {
                fun phoneSwitch() {
                    val phones = group("sideraven_group_phone", R.string.sideraven_group_phone, R.string.sideraven_group_phone_about)
                    val switch = switchFor(context, phones, PHONE_SIDEBAR_KEY, store.phoneSidebar, R.string.sideraven_settings_phone_sidebar, R.string.sideraven_settings_phone_sidebar_summary)
                    val save = switch.onPreferenceChangeListener
                    // It changes this window's tier: rebuild the screen for the new one, once the switch has settled.
                    switch.setOnPreferenceChangeListener { preference, value ->
                        (save?.onPreferenceChange(preference, value) ?: true).also {
                            Handler(Looper.getMainLooper()).post {
                                screen.removeAll()
                                install(context, screen, category, currentTier(), store, checkedState, currentTier)
                            }
                        }
                    }
                }
                // A phone with the Sidebar off: only the switch that brings it back.
                if (!SidebarGate.showsSidebar(tier)) {
                    phoneSwitch()
                    return
                }
                bindMode(context, group("sideraven_group_mode", R.string.sideraven_group_mode, R.string.sideraven_group_mode_about), tier, store, checkedState)
                phoneSwitch()
                bindSide(context, group("sideraven_group_side", R.string.sideraven_group_side, null), store, checkedState)
                bindWidth(context, group("sideraven_group_width", R.string.sideraven_group_width, null), store, checkedState)
                val opening = group("sideraven_group_opening", R.string.sideraven_group_opening, null)
                // Phones keep their own Swipe Zone setting (off by default); this shows the one in effect here.
                switchFor(context, opening, SWIPE_ZONE_KEY, store.swipeZone(tier), R.string.sideraven_settings_swipe_zone, R.string.sideraven_settings_swipe_zone_summary)
                switchFor(context, opening, FLAP_KEY, store.flapEnabled, R.string.sideraven_settings_flap, R.string.sideraven_settings_flap_summary)
            }
            SettingsCategory.Tabs -> {
                bindTabRows(context, group("sideraven_group_rows", R.string.sideraven_group_rows, null), store, checkedState)
                val closing = group("sideraven_group_closing", R.string.sideraven_group_closing, null)
                bindCloseButtons(context, closing, store, checkedState)
                switchFor(context, closing, SWIPE_TO_CLOSE_KEY, store.swipeToClose, R.string.sideraven_settings_swipe_close, R.string.sideraven_settings_swipe_close_summary)
                bindNewTab(context, group("sideraven_group_new_tabs", R.string.sideraven_settings_new_tabs, null), store, checkedState)
                bindNewTabPosition(context, group("sideraven_group_new_tab_position", R.string.sideraven_group_new_tab_position, null), store, checkedState)
                val inactive = group("sideraven_group_inactive", R.string.sideraven_group_inactive, null)
                switchFor(context, inactive, INACTIVE_KEY, store.showInactiveSection, R.string.sideraven_settings_inactive, R.string.sideraven_settings_inactive_summary)
            }
            SettingsCategory.Essentials ->
                bindEssentialsPerRow(context, group("sideraven_group_per_row", R.string.sideraven_group_per_row, R.string.sideraven_group_per_row_about), store, checkedState)
            SettingsCategory.AddressBar -> {
                bindLayout(context, group("sideraven_group_layout", R.string.sideraven_settings_layout, null), store, checkedState)
                bindPopupSwitches(context, group("sideraven_group_popup", R.string.sideraven_settings_popup, null), store)
            }
            SettingsCategory.LookAndFeel -> {
                val accent = group("sideraven_group_accent", R.string.sideraven_group_accent, R.string.sideraven_group_accent_about)
                if (accent.findPreference<AccentSwatchesPreference>(ACCENT_KEY) == null) {
                    accent.addPreference(AccentSwatchesPreference(context, store.accent).apply { key = ACCENT_KEY })
                }
                bindAnimations(context, group("sideraven_group_animations", R.string.sideraven_group_animations, R.string.sideraven_group_animations_about), store, checkedState)
                val feel = group("sideraven_group_feel", R.string.sideraven_group_feel, null)
                switchFor(context, feel, FRAME_KEY, store.roundedFrame, R.string.sideraven_settings_frame, R.string.sideraven_settings_frame_summary)
                switchFor(context, feel, HAPTICS_KEY, store.haptics, R.string.sideraven_settings_haptics, R.string.sideraven_settings_haptics_summary)
            }
            // Their own screens: ZenSyncPreferences and BackupPreferences.
            SettingsCategory.ZenSync, SettingsCategory.Backup -> Unit
        }
    }

    /**
     * Finds or adds one headed group of a category screen. [about] explains it in a first line, only
     * where its choices don't speak for themselves.
     */
    internal fun group(
        context: Context,
        screen: PreferenceGroup,
        key: String,
        title: Int,
        about: Int?,
    ): PreferenceCategory {
        val group =
            screen.findPreference(key)
                ?: PreferenceCategory(context).apply {
                    this.key = key
                    this.title = context.getString(title)
                    layoutResource = R.layout.preference_cat_style
                    isIconSpaceReserved = false
                    order = screen.preferenceCount
                    screen.addPreference(this)
                    about?.let {
                        addPreference(
                            Preference(context).apply {
                                this.key = "$key.about"
                                summary = context.getString(it)
                                isSelectable = false
                                isIconSpaceReserved = false
                                order = -1
                            },
                        )
                    }
                }
        return group
    }

    /** A boolean [Setting] as a switch: finds or adds it, shows the value and writes changes. */
    internal fun switchFor(
        context: Context,
        group: PreferenceGroup,
        key: String,
        setting: Setting<Boolean>,
        title: Int,
        summary: Int?,
    ): SwitchPreferenceCompat {
        val preference =
            group.findPreference(key)
                ?: SwitchPreferenceCompat(context).apply {
                    this.key = key
                    this.title = context.getString(title)
                    this.summary = summary?.let(context::getString)
                    isPersistent = false
                    isIconSpaceReserved = false
                    group.addPreference(this)
                }
        preference.isChecked = setting.value
        preference.setOnPreferenceChangeListener { _, newValue ->
            setting.set(newValue as Boolean)
            true
        }
        return preference
    }

    /** The Search Pop-up's dismiss rules: hiding the keyboard, and tapping outside. */
    internal fun bindPopupSwitches(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
    ): Pair<SwitchPreferenceCompat, SwitchPreferenceCompat> {
        fun switch(
            key: String,
            title: Int,
            summary: Int?,
            value: Boolean,
            onChange: (Boolean) -> Unit,
        ): SwitchPreferenceCompat {
            val preference =
                group.findPreference(key)
                    ?: SwitchPreferenceCompat(context).apply {
                        this.key = key
                        this.title = context.getString(title)
                        this.summary = summary?.let(context::getString)
                        isPersistent = false
                        isIconSpaceReserved = false
                        group.addPreference(this)
                    }
            preference.isChecked = value
            preference.setOnPreferenceChangeListener { _, newValue ->
                onChange(newValue as Boolean)
                true
            }
            return preference
        }
        return switch(
            POPUP_KEYBOARD_KEY,
            R.string.sideraven_settings_popup_keyboard,
            R.string.sideraven_settings_popup_keyboard_summary,
            store.popupCloseOnKeyboardHide.value,
            store.popupCloseOnKeyboardHide::set,
        ) to
            switch(
                POPUP_OUTSIDE_KEY,
                R.string.sideraven_settings_popup_outside,
                null,
                store.popupCloseOnTapOutside.value,
                store.popupCloseOnTapOutside::set,
            )
    }

    /** The Sidebar modes available at [tier]; the stored choice is checked, as with the Toggle. */
    internal fun bindMode(
        context: Context,
        group: PreferenceGroup,
        tier: SidebarTier,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = MODE_KEY,
            all = SidebarMode.entries,
            offered = SidebarMode.available(tier),
            current = SidebarMode.effective(store.chosenMode.value, tier),
            label = ::labelFor,
            checkedState = checkedState,
            onChoose = store.chosenMode::set,
        )

    fun labelFor(mode: SidebarMode): Int =
        when (mode) {
            SidebarMode.Persistent -> R.string.sideraven_mode_persistent
            SidebarMode.Collapsed -> R.string.sideraven_mode_collapsed
            SidebarMode.AutoHide -> R.string.sideraven_mode_autohide
        }

    /** Where new tabs appear: the top or the bottom of the Tab List. */
    internal fun bindNewTabPosition(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = NEW_TAB_POSITION_KEY,
            all = NewTabPosition.entries,
            current = store.newTabPosition.value,
            label = {
                when (it) {
                    NewTabPosition.Top -> R.string.sideraven_new_tab_top
                    NewTabPosition.Bottom -> R.string.sideraven_new_tab_bottom
                }
            },
            checkedState = checkedState,
            onChoose = store.newTabPosition::set,
        )

    /** Which edge the Sidebar docks on. */
    internal fun bindSide(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = SIDE_KEY,
            all = SidebarSide.entries,
            current = store.sidebarSide.value,
            label = {
                when (it) {
                    SidebarSide.Left -> R.string.sideraven_side_left
                    SidebarSide.Right -> R.string.sideraven_side_right
                }
            },
            checkedState = checkedState,
            onChoose = store.sidebarSide::set,
        )

    /** How tall tab rows are. */
    internal fun bindTabRows(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ) = choiceList(
        context,
        group,
        TAB_ROWS_KEY,
        store.tabRows,
        mapOf(TabRows.Comfortable to R.string.sideraven_tab_rows_comfortable, TabRows.Compact to R.string.sideraven_tab_rows_compact),
        checkedState,
    )

    /** How much the Sidebar animates. */
    internal fun bindAnimations(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ) = choiceList(
        context,
        group,
        ANIMATIONS_KEY,
        store.animations,
        mapOf(
            AnimationLevel.Full to R.string.sideraven_animations_full,
            AnimationLevel.Reduced to R.string.sideraven_animations_reduced,
            AnimationLevel.Off to R.string.sideraven_animations_off,
        ),
        checkedState,
    )

    /** How many Essentials sit in a row. */
    internal fun bindEssentialsPerRow(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ) = choiceList(
        context,
        group,
        ESSENTIALS_PER_ROW_KEY,
        store.essentialsPerRow,
        mapOf(
            EssentialsPerRow.Auto to R.string.sideraven_essentials_auto,
            EssentialsPerRow.Three to R.string.sideraven_essentials_three,
            EssentialsPerRow.Four to R.string.sideraven_essentials_four,
            EssentialsPerRow.Five to R.string.sideraven_essentials_five,
        ),
        checkedState,
    )

    /** Which tab rows show a close button. */
    internal fun bindCloseButtons(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ) = choiceList(
        context,
        group,
        CLOSE_BUTTONS_KEY,
        store.closeButtons,
        mapOf(CloseButtons.SelectedTab to R.string.sideraven_close_selected, CloseButtons.EveryTab to R.string.sideraven_close_every),
        checkedState,
    )

    /** A [Setting] as an inline radio list, one option per entry of [labels] in order. */
    private inline fun <reified T : Enum<T>> choiceList(
        context: Context,
        group: PreferenceGroup,
        keyPrefix: String,
        setting: Setting<T>,
        labels: Map<T, Int>,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = keyPrefix,
            all = labels.keys.toList(),
            current = setting.value,
            label = { labels.getValue(it) },
            checkedState = checkedState,
            onChoose = setting::set,
        )

    /** The Sidebar's width: compact, standard or wide. */
    internal fun bindWidth(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = WIDTH_KEY,
            all = SidebarWidth.entries,
            current = store.sidebarWidth.value,
            label = {
                when (it) {
                    SidebarWidth.Compact -> R.string.sideraven_width_compact
                    SidebarWidth.Standard -> R.string.sideraven_width_standard
                    SidebarWidth.Wide -> R.string.sideraven_width_wide
                }
            },
            checkedState = checkedState,
            onChoose = store.sidebarWidth::set,
        )

    /** "New tab opens": the Search Pop-up, Fenix's home page, or a blank page. */
    internal fun bindNewTab(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = NEW_TAB_KEY,
            all = NewTabBehaviour.entries,
            current = store.newTabBehaviour.value,
            label = {
                when (it) {
                    NewTabBehaviour.Search -> R.string.sideraven_new_tab_search
                    NewTabBehaviour.HomePage -> R.string.sideraven_new_tab_home
                    NewTabBehaviour.Blank -> R.string.sideraven_new_tab_blank
                }
            },
            checkedState = checkedState,
            onChoose = store.newTabBehaviour::set,
        )

    /** "Address bar": in Fenix's toolbar (top or bottom, as Fenix is set), or in the Sidebar. */
    internal fun bindLayout(
        context: Context,
        group: PreferenceGroup,
        store: SidebarModeStore,
        checkedState: SharedPreferences,
    ): List<RadioButtonPreference> =
        radioGroup(
            context = context,
            group = group,
            keyPrefix = LAYOUT_KEY,
            all = AddressBarLayout.entries,
            current = store.addressBarLayout.value,
            label = {
                when (it) {
                    AddressBarLayout.Top -> R.string.sideraven_layout_top
                    AddressBarLayout.Sidebar -> R.string.sideraven_layout_sidebar
                }
            },
            checkedState = checkedState,
            onChoose = store.addressBarLayout::set,
        )

    /**
     * One choice as Fenix's inline radio list. Each option is a [RadioButtonPreference] keyed
     * `keyPrefix.NAME`; Fenix's radio reads its checked state from [checkedState] (Fenix's
     * preferences), so that is set from the store first. Options not [offered] are hidden.
     */
    private fun <T : Enum<T>> radioGroup(
        context: Context,
        group: PreferenceGroup,
        keyPrefix: String,
        all: List<T>,
        current: T?,
        label: (T) -> Int,
        checkedState: SharedPreferences,
        onChoose: (T) -> Unit,
        offered: List<T> = all,
    ): List<RadioButtonPreference> {
        val byName = all.associateBy { it.name }
        return radioKeys(
            context = context,
            group = group,
            keyPrefix = keyPrefix,
            keys = all.map { it.name },
            current = current?.name,
            label = { label(byName.getValue(it)) },
            checkedState = checkedState,
            onChoose = { onChoose(byName.getValue(it)) },
            offered = offered.map { it.name },
        )
    }

    /** [radioGroup] for options named by plain keys. */
    private fun radioKeys(
        context: Context,
        group: PreferenceGroup,
        keyPrefix: String,
        keys: List<String>,
        current: String?,
        label: (String) -> Int,
        checkedState: SharedPreferences,
        onChoose: (String) -> Unit,
        offered: List<String> = keys,
    ): List<RadioButtonPreference> {
        var created = false
        val radios =
            keys.map { option ->
                val key = "$keyPrefix.$option"
                val radio =
                    group.findPreference(key)
                        ?: RadioButtonPreference(context).apply {
                            this.key = key
                            title = context.getString(label(option))
                            isPersistent = false
                            isIconSpaceReserved = false
                            group.addPreference(this)
                            created = true
                        }
                checkedState.edit { putBoolean(key, option == current) }
                radio.isVisible = option in offered
                radio.setOnPreferenceChangeListener { _, checked ->
                    if (checked == true) onChoose(option)
                    true
                }
                radio
            }
        if (created) addToRadioGroup(*radios.toTypedArray())
        return radios
    }
}
