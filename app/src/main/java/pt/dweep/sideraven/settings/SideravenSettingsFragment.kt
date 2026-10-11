/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.showToolbar
import pt.dweep.sideraven.SidebarGate
import pt.dweep.sideraven.SidebarTier
import pt.dweep.sideraven.sync.ZenSyncPreferences

/**
 * Sideraven Settings (CONTEXT.md): everything Sideraven adds, apart from Fenix's own settings.
 * Reached from the Sideraven entry near the top of Settings. Opened plain it lists the categories;
 * opened with a [SideravenSettings.ARG_CATEGORY] it shows that category's screen (spec 0008).
 */
class SideravenSettingsFragment : PreferenceFragmentCompat(), SystemInsetsPaddedFragment {
    // Backup's file pickers; registered with the fragment, used from the Backup screen.
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { BackupPreferences.export(requireContext(), it) } }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { BackupPreferences.confirmImport(requireContext(), it) } }

    private val category: SettingsCategory?
        get() = arguments?.getString(SideravenSettings.ARG_CATEGORY)?.let { name -> SettingsCategory.entries.firstOrNull { it.name == name } }

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
    }

    override fun onResume() {
        super.onResume()
        val category = category
        showToolbar(getString(category?.title ?: R.string.sideraven_settings_title))
        when (category) {
            null -> SideravenSettings.installMenu(this)
            SettingsCategory.Backup ->
                BackupPreferences.install(
                    requireContext(),
                    preferenceScreen,
                    onExport = { exportFile.launch(BackupPreferences.fileName()) },
                    // Some file managers don't label JSON as such; the file's own contents decide.
                    onImport = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                )
            else -> SideravenSettings.installCategory(this, category)
        }
    }
}

/** What Sideraven Settings holds, and the entry that leads to it. */
object SideravenSettings {
    const val ENTRY_KEY = "sideraven_settings_entry"
    const val NARROW_NOTE_KEY = "sideraven_settings_narrow_note"
    const val CATEGORY_PREFIX = "sideraven_category."
    const val ARG_CATEGORY = "category"

    /** The first screen: the categories, each opening its own screen. */
    fun installMenu(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        installMenu(context, screen, SidebarTier.from(context)) { category ->
            fragment.findNavController().navigate(R.id.action_global_sideravenSettingsFragment, Bundle().apply { putString(ARG_CATEGORY, category.name) })
        }
    }

    internal fun installMenu(
        context: Context,
        screen: PreferenceGroup,
        tier: SidebarTier,
        open: (SettingsCategory) -> Unit,
    ) {
        val showsSidebar = SidebarGate.showsSidebar(tier)
        // Without a Sidebar on this window, say why its options are missing.
        narrowNote(context, screen, showsSidebar)
        SettingsCategory.entries.forEachIndexed { index, category ->
            val row =
                screen.findPreference(CATEGORY_PREFIX + category.name)
                    ?: Preference(context).apply {
                        key = CATEGORY_PREFIX + category.name
                        title = context.getString(category.title)
                        summary = context.getString(category.summary)
                        isIconSpaceReserved = false
                        order = index + 1
                        screen.addPreference(this)
                    }
            row.isVisible = showsSidebar || !category.needsSidebar
            row.setOnPreferenceClickListener {
                open(category)
                true
            }
        }
    }

    /** One category's screen. */
    fun installCategory(
        fragment: PreferenceFragmentCompat,
        category: SettingsCategory,
    ) {
        if (category == SettingsCategory.ZenSync) ZenSyncPreferences.install(fragment) else SidebarPreferences.install(fragment, category)
    }

    /** The note shown instead of the Sidebar options on windows without a Sidebar. */
    fun narrowNote(
        context: Context,
        screen: PreferenceGroup,
        showsSidebar: Boolean,
    ): Preference {
        val note =
            screen.findPreference(NARROW_NOTE_KEY)
                ?: Preference(context).apply {
                    key = NARROW_NOTE_KEY
                    summary = context.getString(R.string.sideraven_settings_narrow)
                    isSelectable = false
                    isIconSpaceReserved = false
                    order = 0
                    screen.addPreference(this)
                }
        note.isVisible = !showsSidebar
        return note
    }

    /** The Sideraven entry, first in [general]; [open] navigates to Sideraven Settings. */
    fun entry(
        context: Context,
        general: PreferenceGroup,
        open: () -> Unit,
    ): Preference {
        val entry =
            general.findPreference(ENTRY_KEY)
                ?: Preference(context).apply {
                    key = ENTRY_KEY
                    title = context.getString(R.string.sideraven_settings_title)
                    summary = context.getString(R.string.sideraven_settings_entry_summary)
                    isIconSpaceReserved = false
                    // First in General: Sideraven's own settings, kept apart from Fenix's.
                    order = -1
                    general.addPreference(this)
                }
        entry.setOnPreferenceClickListener {
            open()
            true
        }
        return entry
    }

    /** The Sideraven entry near the top of Fenix's Settings (one `SIDERAVEN-HOOK`). */
    fun installEntry(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val general = fragment.findPreference<Preference>(context.getString(R.string.pref_key_search_settings))?.parent ?: return
        entry(context, general) { fragment.findNavController().navigate(R.id.action_global_sideravenSettingsFragment) }
    }
}
