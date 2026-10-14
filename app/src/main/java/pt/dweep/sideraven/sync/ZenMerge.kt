/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONObject

/**
 * What one sync does: what to upload or delete on the server, and what the server holds afterwards
 * (for everything Zen Sync covers), from which the tablet's synced items are rebuilt.
 */
data class MergePlan(
    val uploads: Map<String, JSONObject>,
    val tombstones: Set<String>,
    /** In-scope records after the sync, by record id. */
    val serverAfter: Map<String, JSONObject>,
)

/**
 * Record-level three-way merge against the last-synced [SyncBase]: a record changed
 * on one side takes that side; changed on both, the later edit wins. A record is deleted on the
 * server only if it existed at the last sync and the tablet no longer has it, never just because
 * it's missing here; a local delete loses to a remote edit, a remote delete to a local edit.
 */
object ZenMerge {
    fun plan(
        base: SyncBase,
        local: Map<String, OutboundRecord>,
        remote: Map<String, JSONObject>,
        remoteModified: Map<String, Double>,
    ): MergePlan {
        val uploads = linkedMapOf<String, JSONObject>()
        val tombstones = linkedSetOf<String>()
        val after = linkedMapOf<String, JSONObject>()
        (local.keys + remote.keys).forEach { id ->
            val l = local[id]
            val r = remote[id]
            val lDigest = l?.let { Canonical.digest(it.cleartext) }
            val rDigest = r?.let(Canonical::digest)
            val bDigest = base.entries[id]?.digest
            val localChanged = lDigest != bDigest
            val remoteChanged = rDigest != bDigest
            fun takeLocal() {
                if (l != null) {
                    uploads[id] = l.cleartext
                    after[id] = l.cleartext
                } else if (bDigest != null && r != null) {
                    tombstones += id
                }
            }
            fun takeRemote() {
                if (r != null) after[id] = r
            }
            when {
                !localChanged && !remoteChanged -> takeRemote()
                localChanged && !remoteChanged -> takeLocal()
                !localChanged -> takeRemote()
                lDigest == rDigest -> takeRemote()
                l == null -> takeRemote()
                r == null -> takeLocal()
                l.localModified > ((remoteModified[id] ?: 0.0) * 1000).toLong() -> takeLocal()
                else -> takeRemote()
            }
        }
        return MergePlan(uploads, tombstones, after)
    }
}
