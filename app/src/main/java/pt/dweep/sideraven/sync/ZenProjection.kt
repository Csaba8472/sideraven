/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONObject
import pt.dweep.sideraven.DEFAULT_FOLDER_ICON
import pt.dweep.sideraven.DEFAULT_SPACE_ACCENT
import pt.dweep.sideraven.Essential
import pt.dweep.sideraven.Folder
import pt.dweep.sideraven.PinnedTab
import pt.dweep.sideraven.Space

/**
 * Where a synced item came from: its record id in the `spaces` collection and the record's raw
 * `data`, kept in the item's `extra` so everything Sideraven doesn't model is written back as it
 * arrived (containers, live-folder config, themes when colours aren't shared, …).
 */
data class ZenOrigin(
    val recordId: String,
    val data: JSONObject,
    /** For a Space: its accent when it last synced. A different accent now is the user's change. */
    val accent: String? = null,
) {
    /** Stored form; Zen's data-URI favicons go to [ZenBlobs], leaving references (spec 0009). */
    fun encode(): String = JSONObject().put("rid", recordId).put("data", ZenBlobs.stash(data)).apply { accent?.let { put("accent", it) } }.toString()

    /** Stored form with the favicons inline: a backup carries them, since the next install's store is empty. */
    fun encodeInline(): String = JSONObject().put("rid", recordId).put("data", fullData()).apply { accent?.let { put("accent", it) } }.toString()

    /**
     * A copy of the record's data as Zen sent it, favicons included: what uploads start from. A
     * favicon whose stored data is gone comes from [remote], the record as Zen has it now.
     */
    fun fullData(remote: JSONObject? = null): JSONObject = ZenBlobs.restore(data, remote)

    companion object {
        fun decode(extra: String?): ZenOrigin? =
            extra?.let { runCatching { JSONObject(it) }.getOrNull() }?.let { json ->
                val rid = json.opt("rid") as? String ?: return null
                val data = json.optJSONObject("data") ?: return null
                ZenOrigin(rid, data, json.opt("accent") as? String)
            }
    }
}

/** Desktop's Spaces, Pinned Tabs, Folders and Essentials as Sideraven items. */
data class ZenImport(
    /** In Zen's order. */
    val spaces: List<Space>,
    val pinned: List<PinnedTab>,
    val folders: List<Folder>,
    /** Every container, `default` first. */
    val essentials: List<Essential>,
)

/** Mapping between Zen's records and Sideraven's model (spec 0003, "Record mapping summary"). */
object ZenProjection {
    const val DEFAULT_BUCKET = "default"

    /** Desktop's data, as the tablet will show it. */
    fun fromZen(
        snapshot: ZenSnapshot,
        shareColours: Boolean,
    ): ZenImport {
        val spaceRecords = snapshot.records.filterIsInstance<ZenSpace>()
        val tabs = snapshot.records.filterIsInstance<ZenTab>().associateBy { it.tabId }
        val folderRecords = snapshot.records.filterIsInstance<ZenFolder>()
        val layout = snapshot.layout

        val order = layout?.spaces.orEmpty()
        val orderedSpaces = spaceRecords.sortedBy { space -> order.indexOf(space.uuid).let { if (it == -1) Int.MAX_VALUE else it } }
        val spaceIds = orderedSpaces.map { it.uuid }.toSet()
        val spaces =
            orderedSpaces.map { record ->
                val accent = (if (shareColours) ZenColours.nearestAccent(record.theme?.primaryHex) else null) ?: DEFAULT_SPACE_ACCENT
                Space(
                    id = record.uuid,
                    name = record.name ?: "",
                    icon = record.icon.orEmpty(),
                    accent = accent,
                    extra = ZenOrigin(record.id, record.data, accent).encode(),
                )
            }

        val folderIds = folderRecords.filter { it.workspaceUuid in spaceIds }.map { it.folderId }.toSet()
        // Display order: walk each Space's children, descending into Folders.
        val position = mutableMapOf<String, Int>()
        val foldersById = folderRecords.associateBy { it.folderId }
        fun walk(ids: List<String>) {
            ids.forEach { id ->
                if (id !in position) {
                    position[id] = position.size
                    foldersById[id]?.let { walk(it.children) }
                }
            }
        }
        orderedSpaces.forEach { walk(it.children) }
        fun rank(id: String) = position[id] ?: Int.MAX_VALUE

        val folders =
            folderRecords
                .filter { it.workspaceUuid in spaceIds }
                .sortedBy { rank(it.folderId) }
                .map { record ->
                    Folder(
                        id = record.folderId,
                        spaceId = record.workspaceUuid!!,
                        name = record.name ?: "",
                        icon = record.icon ?: DEFAULT_FOLDER_ICON,
                        parentId = record.parentFolderId?.takeIf { it in folderIds },
                        extra = ZenOrigin(record.id, record.data).encode(),
                    )
                }

        val pinned =
            tabs.values
                .filter { !it.isNormalTab && !it.isEssential && it.workspaceUuid in spaceIds }
                .sortedBy { rank(it.tabId) }
                .map { record ->
                    PinnedTab(
                        id = record.tabId,
                        spaceId = record.workspaceUuid!!,
                        url = record.url,
                        title = record.title ?: record.url,
                        folderId = record.folderId?.takeIf { it in folderIds },
                        customName = record.staticLabel?.takeIf { it.isNotBlank() },
                        extra = ZenOrigin(record.id, record.data).encode(),
                    )
                }

        val buckets = layout?.essentials.orEmpty()
        val bucketOrder = listOfNotNull(DEFAULT_BUCKET.takeIf { it in buckets }) + buckets.keys.filter { it != DEFAULT_BUCKET }
        val seen = mutableSetOf<String>()
        val essentials =
            bucketOrder.flatMap { bucket ->
                buckets.getValue(bucket).mapNotNull { id ->
                    tabs[id]?.takeIf { it.isEssential && seen.add(it.tabId) }?.let { essential(it, bucket) }
                }
            } +
                // Essentials the layout doesn't list still show (in the default container).
                tabs.values.filter { it.isEssential && it.tabId !in seen }.map { essential(it, DEFAULT_BUCKET) }

        return ZenImport(spaces, pinned, folders, essentials.distinctBy { it.url })
    }

    private fun essential(
        record: ZenTab,
        bucket: String,
    ) = Essential(
        url = record.url,
        title = record.title ?: record.url,
        id = record.tabId,
        customName = record.staticLabel?.takeIf { it.isNotBlank() },
        extra = ZenOrigin(record.id, record.data).encode(),
        bucket = bucket.takeIf { it != DEFAULT_BUCKET },
    )

    /** The record's kind, for rebuilding its cleartext. */
    fun kindOf(record: ZenRecord): String =
        when (record) {
            is ZenSpace -> "space"
            is ZenTab -> "tab"
            is ZenFolder -> "folder"
            is ZenSplit -> "split"
            is ZenLayout -> "layout"
        }

    /**
     * The last-synced state for the records in [held]: only records the tablet actually holds are
     * remembered, so a record it never showed (a category that was off, a pin of an unknown Space,
     * a duplicate Essential) can never later look deleted here and be deleted on desktop.
     */
    fun baseOf(
        snapshot: ZenSnapshot,
        held: Set<String>,
    ): SyncBase =
        SyncBase(
            entries =
                snapshot.records.filter { it.id in held }.associate { record ->
                    record.id to BaseEntry(Canonical.digest(ZenWire.cleartext(record.id, kindOf(record), record.data)), snapshot.modified[record.id] ?: 0.0)
                },
            collectionModified = snapshot.lastModified,
        )

    /** The record ids behind what [import] puts on the tablet, for the categories that sync. */
    fun heldRecords(
        import: ZenImport,
        options: ZenSyncSettings,
    ): Set<String> {
        fun origins(extras: List<String?>) = extras.mapNotNull { ZenOrigin.decode(it)?.recordId }
        val spaces =
            if (options.syncSpaces) {
                origins(import.spaces.map { it.extra } + import.pinned.map { it.extra } + import.folders.map { it.extra })
            } else {
                emptyList()
            }
        val essentials = if (options.syncEssentials) origins(import.essentials.map { it.extra }) else emptyList()
        val layout = if (options.syncSpaces || options.syncEssentials) listOf(LAYOUT_RECORD_ID) else emptyList()
        return (spaces + essentials + layout).toSet()
    }
}
