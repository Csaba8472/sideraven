/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.json.JSONArray
import org.json.JSONObject
import pt.dweep.sideraven.sync.ZenOrigin

/**
 * Backup (Sideraven Settings): Spaces (with their Pinned Tabs and Folders), Essentials and every
 * Sideraven setting in one JSON file. Open tabs stay out (Firefox restores those), and so do Zen
 * Sync's sign-in and sync state (they belong to the Firefox account). Zen's favicons are written
 * whole, so a backup restores on a fresh install. Importing replaces what's there.
 */
object SideravenBackup {
    private const val FORMAT = "sideraven-backup"

    /** The newest backup format this version reads and writes. */
    const val VERSION = 1

    enum class Result { Restored, NotABackup, TooNew }

    fun export(
        spaces: SpacesData,
        essentials: List<Essential>,
        settings: SidebarModeStore,
    ): String =
        JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            // No open tabs: which tab is in which Space, and each Space's last tab, stay on this device.
            .put("spaces", JSONObject(SpacesCodec.encode(spaces.withExtras(::inline).withoutTabs())))
            .put("essentials", JSONArray(EssentialsCodec.encode(essentials.map { it.copy(extra = inline(it.extra)) })))
            .put("settings", settings.exportSettings())
            .toString(2)

    /**
     * Reads [text] whole, and only if every part reads cleanly replaces the Spaces, Essentials and
     * settings. Open tabs keep their Spaces wherever those Spaces are in the backup.
     */
    fun import(
        text: String,
        spaces: Spaces,
        essentials: Essentials,
        settings: SidebarModeStore,
    ): Result {
        val json = runCatching { JSONObject(text) }.getOrNull()?.takeIf { it.optString("format") == FORMAT } ?: return Result.NotABackup
        if (json.optInt("version", 0) > VERSION) return Result.TooNew
        val savedSpaces = json.optJSONObject("spaces")?.let { SpacesCodec.decode(it.toString()) } ?: return Result.NotABackup
        val essentialsJson = json.optJSONArray("essentials") ?: return Result.NotABackup
        // The decoder gives up on the whole list at the first bad entry: a shorter list means a damaged file.
        val savedEssentials = EssentialsCodec.decode(essentialsJson.toString()).first.takeIf { it.size == essentialsJson.length() } ?: return Result.NotABackup
        val savedSettings = json.optJSONObject("settings") ?: return Result.NotABackup

        spaces.applyRemote { live -> savedSpaces.withExtras(::slim).keepingTabsOf(live) }
        essentials.applyRemote(savedEssentials.map { it.copy(extra = slim(it.extra)) })
        settings.importSettings(savedSettings)
        return Result.Restored
    }

    private fun SpacesData.withoutTabs() = copy(assignments = emptyMap(), spaces = spaces.map { it.copy(lastSelectedTabId = null) })

    /** [this] backup with [live]'s open tabs kept in their Spaces, where the backup has those Spaces. */
    private fun SpacesData.keepingTabsOf(live: SpacesData): SpacesData {
        val kept = spaces.map { it.id }.toSet()
        return copy(
            assignments = live.assignments.filterValues { it in kept },
            spaces = spaces.map { space -> space.copy(lastSelectedTabId = live.spaces.firstOrNull { it.id == space.id }?.lastSelectedTabId) },
        )
    }

    /** A synced item's Zen record with its favicons written inline. */
    private fun inline(extra: String?): String? = ZenOrigin.decode(extra)?.encodeInline() ?: extra

    /** Back to the stored form: favicons into the favicon store, references in their place. */
    private fun slim(extra: String?): String? = ZenOrigin.decode(extra)?.encode() ?: extra

    private fun SpacesData.withExtras(change: (String?) -> String?) =
        copy(
            spaces = spaces.map { it.copy(extra = change(it.extra)) },
            pinned = pinned.map { it.copy(extra = change(it.extra)) },
            folders = folders.map { it.copy(extra = change(it.extra)) },
        )
}
