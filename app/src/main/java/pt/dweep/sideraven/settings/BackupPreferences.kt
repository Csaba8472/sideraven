/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenix.R
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.PreferencesEssentialsRepository
import pt.dweep.sideraven.PreferencesSidebarModeStore
import pt.dweep.sideraven.SideravenBackup
import pt.dweep.sideraven.Spaces
import pt.dweep.sideraven.sync.ZenBlobs
import java.time.LocalDate

/** The Backup screen of Sideraven Settings: export to a file, import from one ([SideravenBackup]). */
internal object BackupPreferences {
    const val EXPORT_KEY = "sideraven_backup_export"
    const val IMPORT_KEY = "sideraven_backup_import"

    /** The name the file picker suggests for an export. */
    fun fileName(today: LocalDate = LocalDate.now()): String = "sideraven-backup-$today.json"

    fun install(
        context: Context,
        screen: PreferenceGroup,
        onExport: () -> Unit,
        onImport: () -> Unit,
    ) {
        val group = SidebarPreferences.group(context, screen, "sideraven_group_backup", R.string.sideraven_group_backup, R.string.sideraven_group_backup_about)
        fun action(
            key: String,
            title: Int,
            summary: Int,
            onClick: () -> Unit,
        ) {
            val preference =
                group.findPreference(key)
                    ?: Preference(context).apply {
                        this.key = key
                        isIconSpaceReserved = false
                        group.addPreference(this)
                    }
            preference.title = context.getString(title)
            preference.summary = context.getString(summary)
            preference.setOnPreferenceClickListener {
                onClick()
                true
            }
        }
        action(EXPORT_KEY, R.string.sideraven_backup_export, R.string.sideraven_backup_export_summary, onExport)
        action(IMPORT_KEY, R.string.sideraven_backup_import, R.string.sideraven_backup_import_summary, onImport)
    }

    /** Backups finish even if the screen is left or turned: they run beyond it. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Writes the backup to [uri], off the main thread, and says how it went. */
    fun export(
        context: Context,
        uri: Uri,
    ) {
        val app = context.applicationContext
        scope.launch {
            val written =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val text =
                            SideravenBackup.export(
                                Spaces.get(app).data,
                                PreferencesEssentialsRepository.get(app).essentials.value,
                                PreferencesSidebarModeStore.get(app),
                            )
                        app.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                    }.isSuccess
                }
            toast(app, if (written) R.string.sideraven_backup_exported else R.string.sideraven_backup_export_failed)
        }
    }

    /** Asks first, then replaces the Spaces, Essentials and settings with the backup in [uri]. */
    fun confirmImport(
        context: Context,
        uri: Uri,
    ) {
        AlertDialog.Builder(context)
            .setTitle(R.string.sideraven_backup_import_title)
            .setMessage(R.string.sideraven_backup_import_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.sideraven_backup_import_replace) { _, _ -> import(context, uri) }
            .show()
    }

    private fun import(
        context: Context,
        uri: Uri,
    ) {
        val app = context.applicationContext
        scope.launch {
            // Reading, parsing and putting the favicons back in their store all happen off the main thread.
            val result =
                withContext(Dispatchers.IO) {
                    val text = runCatching { app.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
                    ZenBlobs.useFiles(app)
                    text?.let {
                        SideravenBackup.import(it, Spaces.get(app), Essentials(PreferencesEssentialsRepository.get(app), Essentials.processLinks), PreferencesSidebarModeStore.get(app))
                    } ?: SideravenBackup.Result.NotABackup
                }
            toast(
                app,
                when (result) {
                    SideravenBackup.Result.Restored -> R.string.sideraven_backup_imported
                    SideravenBackup.Result.NotABackup -> R.string.sideraven_backup_not_a_backup
                    SideravenBackup.Result.TooNew -> R.string.sideraven_backup_too_new
                },
            )
        }
    }

    private fun toast(
        context: Context,
        message: Int,
    ) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}
