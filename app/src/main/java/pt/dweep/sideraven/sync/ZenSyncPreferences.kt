/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.fxa.manager.SCOPE_SYNC
import org.mozilla.fenix.R
import org.mozilla.fenix.ext.components

/** Zen Sync's view of the Firefox Account Sideraven is signed into (Fenix's account manager). */
class FenixZenAccount(
    private val accountManager: () -> FxaAccountManager,
) : ZenAccount {
    override suspend fun credentials(): SyncCredentials? {
        val account = accountManager().authenticatedAccount() ?: return null
        val token = account.getAccessToken(SCOPE_SYNC) ?: throw SyncError.Auth("no sync token")
        val key = token.key ?: throw SyncError.Auth("no sync key")
        val tokenServer = account.getTokenServerEndpointURL() ?: throw SyncError.Auth("no token server")
        return SyncCredentials(tokenServer, token.token, key.kid, key.k)
    }
}

/** How a [ZenSyncStatus] reads in settings. */
object ZenSyncText {
    /** The "Sync now" line: when it last synced, or the last problem. */
    fun run(
        context: Context,
        status: ZenSyncRunStatus,
    ): String =
        when {
            status.running -> context.getString(R.string.sideraven_zen_first_sync_running)
            status.problem != null -> describe(context, ZenSyncStatus.Failed(status.problem))
            status.lastSynced != null ->
                context.getString(
                    R.string.sideraven_zen_last_synced,
                    android.text.format.DateUtils.getRelativeTimeSpanString(status.lastSynced, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS),
                )
            else -> context.getString(R.string.sideraven_zen_not_synced_yet)
        }

    fun describe(
        context: Context,
        status: ZenSyncStatus?,
    ): String =
        when (status) {
            null -> context.getString(R.string.sideraven_zen_checking)
            ZenSyncStatus.SignedOut -> context.getString(R.string.sideraven_zen_signed_out)
            ZenSyncStatus.NothingFromDesktop -> context.getString(R.string.sideraven_zen_nothing_from_desktop)
            is ZenSyncStatus.Connected ->
                context.getString(
                    R.string.sideraven_zen_desktop_has,
                    status.summary.spaces,
                    status.summary.pinnedTabs,
                    status.summary.essentials,
                )
            is ZenSyncStatus.Failed ->
                context.getString(
                    when (status.reason) {
                        ZenSyncStatus.Reason.KeysMissing -> R.string.sideraven_zen_error_keys
                        ZenSyncStatus.Reason.Network -> R.string.sideraven_zen_error_network
                        ZenSyncStatus.Reason.SignIn -> R.string.sideraven_zen_error_sign_in
                        ZenSyncStatus.Reason.NewerVersion -> R.string.sideraven_zen_error_newer
                        ZenSyncStatus.Reason.Other -> R.string.sideraven_zen_error_other
                    },
                )
        }
}

/**
 * The Zen Sync section of Settings → Sync (added through one `SIDERAVEN-HOOK` in the account
 * settings screen), and its entry row in Customize → Sidebar for when nobody is signed in.
 */
object ZenSyncPreferences {
    const val CATEGORY_KEY = "sideraven_zen_sync_category"
    const val STATUS_KEY = "sideraven_zen_sync_status"
    const val SWITCH_KEY = "sideraven_zen_sync_switch"
    const val SYNC_NOW_KEY = "sideraven_zen_sync_now"
    const val SPACES_KEY = "sideraven_zen_sync_spaces"
    const val ESSENTIALS_KEY = "sideraven_zen_sync_essentials"
    const val COLOURS_KEY = "sideraven_zen_sync_colours"
    const val AUTOMATIC_KEY = "sideraven_zen_sync_automatic"
    const val SPACES_LIST_KEY = "sideraven_zen_sync_space_list"
    const val DESKTOP_TABS_HINT_KEY = "sideraven_zen_sync_desktop_tabs_hint"
    const val SPACE_KEY_PREFIX = "sideraven_zen_space."

    /** Adds (or refreshes) the Zen Sync section and reads what desktop has. */
    fun install(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        val category =
            screen.findPreference(CATEGORY_KEY)
                ?: PreferenceCategory(context).apply {
                    key = CATEGORY_KEY
                    title = context.getString(R.string.sideraven_zen_sync)
                    layoutResource = R.layout.preference_cat_style
                    isIconSpaceReserved = false
                    screen.addPreference(this)
                }
        val zenSync = ZenSync.get(context)
        val settingsStore = PreferencesZenSyncSettingsStore.get(context)
        val switch =
            category.findPreference(SWITCH_KEY)
                ?: SwitchPreferenceCompat(context).apply {
                    key = SWITCH_KEY
                    title = context.getString(R.string.sideraven_zen_sync)
                    summary = context.getString(R.string.sideraven_zen_sync_summary)
                    isPersistent = false
                    isIconSpaceReserved = false
                    order = 0
                    category.addPreference(this)
                }
        switch.isChecked = settingsStore.settings.value.enabled
        val status =
            category.findPreference(STATUS_KEY)
                ?: Preference(context).apply {
                    key = STATUS_KEY
                    title = context.getString(R.string.sideraven_zen_desktop)
                    isIconSpaceReserved = false
                    category.addPreference(this)
                }
        fun check() {
            status.summary = ZenSyncText.describe(context, null)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val account = FenixZenAccount { context.components.backgroundServices.accountManager }
                val result = withContext(Dispatchers.IO) { ZenSyncCheck.run(account) }
                status.summary = ZenSyncText.describe(context, result)
            }
        }
        // Tap to read again.
        status.setOnPreferenceClickListener {
            check()
            true
        }
        val syncNow =
            category.findPreference(SYNC_NOW_KEY)
                ?: Preference(context).apply {
                    key = SYNC_NOW_KEY
                    title = context.getString(R.string.sideraven_zen_sync_now)
                    isIconSpaceReserved = false
                    category.addPreference(this)
                }
        val runner = ZenSyncRunners.get(context)
        val spaceList =
            category.findPreference(SPACES_LIST_KEY)
                ?: PreferenceCategory(context).apply {
                    key = SPACES_LIST_KEY
                    title = context.getString(R.string.sideraven_zen_spaces_title)
                    layoutResource = R.layout.preference_cat_style
                    isIconSpaceReserved = false
                    category.addPreference(this)
                }
        val desktopTabsHint =
            category.findPreference(DESKTOP_TABS_HINT_KEY)
                ?: Preference(context).apply {
                    key = DESKTOP_TABS_HINT_KEY
                    title = context.getString(R.string.sideraven_zen_desktop_tabs_off)
                    summary = context.getString(R.string.sideraven_zen_desktop_tabs_off_summary)
                    isSelectable = false
                    isIconSpaceReserved = false
                    category.addPreference(this)
                }
        fun refreshSpaces() {
            val options = settingsStore.settings.value
            spaceList.isVisible = options.enabled && options.syncSpaces
            desktopTabsHint.isVisible = spaceList.isVisible && !PreferencesDesktopViewStore.get(context).view.value.sharesOpenTabs
            if (!spaceList.isVisible) return
            bindSpaceList(context, spaceList, zenSync.spaceChoices()) { id, on ->
                zenSync.setOnTablet(id, on)
                runner.syncNow()
                // After the switch has been applied (the listener runs before the switch flips).
                android.os.Handler(android.os.Looper.getMainLooper()).post { refreshSpaces() }
            }
            // Shown once: the next visit no longer marks them new.
            zenSync.acknowledgeNewSpaces()
        }
        // What syncs: each category stops in both directions when off.
        val choices =
            listOf(
                Triple(SPACES_KEY, R.string.sideraven_zen_what_spaces, ZenSyncSettings::syncSpaces),
                Triple(ESSENTIALS_KEY, R.string.sideraven_zen_what_essentials, ZenSyncSettings::syncEssentials),
                Triple(COLOURS_KEY, R.string.sideraven_zen_what_colours, ZenSyncSettings::shareColours),
                Triple(AUTOMATIC_KEY, R.string.sideraven_zen_automatic, ZenSyncSettings::syncAutomatically),
            ).map { (key, label, read) ->
                val preference =
                    category.findPreference(key)
                        ?: SwitchPreferenceCompat(context).apply {
                            this.key = key
                            title = context.getString(label)
                            isPersistent = false
                            isIconSpaceReserved = false
                            category.addPreference(this)
                        }
                if (key == AUTOMATIC_KEY) preference.summary = context.getString(R.string.sideraven_zen_automatic_summary)
                preference.isChecked = read(settingsStore.settings.value)
                preference.setOnPreferenceChangeListener { _, value ->
                    val on = value as Boolean
                    val current = settingsStore.settings.value
                    settingsStore.save(
                        when (key) {
                            SPACES_KEY -> current.copy(syncSpaces = on)
                            ESSENTIALS_KEY -> current.copy(syncEssentials = on)
                            AUTOMATIC_KEY -> current.copy(syncAutomatically = on)
                            else -> current.copy(shareColours = on)
                        },
                    )
                    runner.syncNow()
                    refreshSpaces()
                    true
                }
                preference
            }
        fun showChoices(on: Boolean) {
            choices.forEach { it.isVisible = on }
            refreshSpaces()
        }
        showChoices(settingsStore.settings.value.enabled)
        syncNow.isVisible = settingsStore.settings.value.enabled
        syncNow.summary = ZenSyncText.run(context, runner.status.value)
        syncNow.setOnPreferenceClickListener {
            syncNow.summary = context.getString(R.string.sideraven_zen_first_sync_running)
            fragment.viewLifecycleOwner.lifecycleScope.launch {
                runner.syncNow().join()
                syncNow.summary = ZenSyncText.run(context, runner.status.value)
            }
            true
        }
        switch.setOnPreferenceChangeListener { _, on ->
            if (on as Boolean) {
                // Turning on runs the First Sync; the switch flips once it has succeeded.
                FirstSyncDialog.show(context) { choice ->
                    status.summary = context.getString(R.string.sideraven_zen_first_sync_running)
                    fragment.viewLifecycleOwner.lifecycleScope.launch {
                        val outcome =
                            try {
                                if (choice == FirstSyncChoice.Merge) {
                                    // Merge changes desktop: show what it would add and wait for a yes.
                                    val preview = withContext(Dispatchers.IO) { zenSync.previewMerge() }
                                    if (!FirstSyncDialog.confirmMerge(context, preview.summary)) throw MergeCancelled()
                                    withContext(Dispatchers.IO) { zenSync.applyMerge(preview) }
                                } else {
                                    withContext(Dispatchers.IO) { zenSync.firstSync(choice) }
                                }
                                Result.success(Unit)
                            } catch (e: MergeCancelled) {
                                switch.isChecked = false
                                status.summary = context.getString(R.string.sideraven_zen_merge_cancelled)
                                return@launch
                            } catch (e: Exception) {
                                Result.failure(e)
                            }
                        switch.isChecked = outcome.isSuccess
                        syncNow.isVisible = outcome.isSuccess
                        showChoices(outcome.isSuccess)
                        status.summary =
                            outcome.exceptionOrNull()?.let { e ->
                                ZenSyncText.describe(context, ZenSyncStatus.Failed((e as? SyncError)?.let(ZenSyncCheck::reasonOf) ?: ZenSyncStatus.Reason.Other))
                            } ?: context.getString(R.string.sideraven_zen_first_sync_done)
                    }
                }
                false
            } else {
                zenSync.turnOff()
                syncNow.isVisible = false
                showChoices(false)
                true
            }
        }
        check()
    }

    /**
     * The "Spaces on this tablet" switches, one per [choices] in order (stale ones removed). On
     * means shared with desktop; the last Space on this tablet that desktop has can't be switched off.
     */
    internal fun bindSpaceList(
        context: Context,
        group: androidx.preference.PreferenceGroup,
        choices: List<SpaceChoice>,
        onSwitch: (spaceId: String, on: Boolean) -> Unit,
    ): List<SwitchPreferenceCompat> {
        val keys = choices.map { SPACE_KEY_PREFIX + it.id }.toSet()
        (0 until group.preferenceCount).map { group.getPreference(it) }
            .filter { it.key?.startsWith(SPACE_KEY_PREFIX) == true && it.key !in keys }
            .forEach { group.removePreference(it) }
        val onTablet = choices.count { it.onTablet }
        return choices.mapIndexed { index, choice ->
            val preference =
                group.findPreference(SPACE_KEY_PREFIX + choice.id)
                    ?: SwitchPreferenceCompat(context).apply {
                        key = SPACE_KEY_PREFIX + choice.id
                        isPersistent = false
                        isIconSpaceReserved = false
                        group.addPreference(this)
                    }
            preference.order = index
            preference.title = listOf(choice.icon, choice.name).filter { it.isNotBlank() }.joinToString(" ")
            preference.summary =
                context.getString(
                    when {
                        choice.isNew -> R.string.sideraven_zen_space_new
                        !choice.onTablet -> R.string.sideraven_zen_space_desktop
                        choice.syncs && choice.onDesktop -> R.string.sideraven_zen_space_both
                        else -> R.string.sideraven_zen_space_tablet
                    },
                )
            preference.isChecked = choice.syncs
            preference.isEnabled = !(choice.syncs && choice.onTablet && choice.onDesktop && onTablet <= 1)
            preference.setOnPreferenceChangeListener { _, value ->
                onSwitch(choice.id, value as Boolean)
                true
            }
            preference
        }
    }

    const val LINK_KEY = "sideraven_zen_sync_link"

    /** Settings → Sync: a one-line link to Zen Sync in Sideraven Settings. */
    fun installLink(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val screen = fragment.preferenceScreen ?: return
        val link =
            screen.findPreference(LINK_KEY)
                ?: Preference(context).apply {
                    key = LINK_KEY
                    title = context.getString(R.string.sideraven_zen_sync)
                    summary = context.getString(R.string.sideraven_zen_sync_link_summary)
                    isIconSpaceReserved = false
                    screen.addPreference(this)
                }
        link.setOnPreferenceClickListener {
            fragment.findNavController().navigate(
                R.id.action_global_sideravenSettingsFragment,
                android.os.Bundle().apply { putString(pt.dweep.sideraven.settings.SideravenSettings.ARG_CATEGORY, pt.dweep.sideraven.settings.SettingsCategory.ZenSync.name) },
            )
            true
        }
    }
}

/** The First Sync choice, asked the first time Zen Sync is turned on. */
object FirstSyncDialog {
    fun show(
        context: Context,
        onChoose: (FirstSyncChoice) -> Unit,
    ) {
        val choices = FirstSyncChoice.entries
        val labels =
            choices.map { choice ->
                when (choice) {
                    FirstSyncChoice.KeepSeparate -> context.getString(R.string.sideraven_zen_first_keep)
                    FirstSyncChoice.Replace -> context.getString(R.string.sideraven_zen_first_replace)
                    FirstSyncChoice.Merge -> context.getString(R.string.sideraven_zen_first_merge)
                }
            }
        var selected = 0
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(R.string.sideraven_zen_first_title)
            .setSingleChoiceItems(labels.toTypedArray(), selected) { _, which -> selected = which }
            .setPositiveButton(R.string.sideraven_zen_first_start) { _, _ -> onChoose(choices[selected]) }
            .setNegativeButton(R.string.sideraven_cancel, null)
            .show()
    }

    /** Asks to confirm a Merge, showing what it adds to Zen. Suspends until answered. */
    suspend fun confirmMerge(
        context: Context,
        summary: MergeSummary,
    ): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val body =
                buildString {
                    append(context.getString(R.string.sideraven_zen_merge_adds, summary.spaces, summary.pinnedTabs, summary.essentials))
                    if (summary.matches.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.sideraven_zen_merge_matches))
                        summary.matches.forEach { (tablet, desktop) -> append("\n• ").append(tablet).append(" → ").append(desktop) }
                    }
                }
            var answered = false
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle(R.string.sideraven_zen_merge_title)
                .setMessage(body)
                .setPositiveButton(R.string.sideraven_zen_merge_confirm) { _, _ ->
                    answered = true
                    continuation.resumeWith(Result.success(true))
                }
                .setNegativeButton(R.string.sideraven_cancel) { _, _ ->
                    answered = true
                    continuation.resumeWith(Result.success(false))
                }
                .setOnDismissListener { if (!answered) continuation.resumeWith(Result.success(false)) }
                .show()
        }
}

/** The user cancelled the Merge summary: nothing was changed or uploaded. */
class MergeCancelled : Exception("merge cancelled")

/** The app's one Zen Sync runner, living as long as the process. */
object ZenSyncRunners {
    @Volatile private var instance: ZenSyncRunner? = null

    fun get(context: Context): ZenSyncRunner =
        instance ?: synchronized(this) {
            instance ?: ZenSyncRunner(
                ZenSync.get(context),
                PreferencesZenSyncSettingsStore.get(context),
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main),
            ).also { instance = it }
        }
}
