/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Zen's data-URI favicons, kept out of the stored Essentials, Pinned Tabs, Folders and Spaces
 * (spec 0009). A synced item's `extra` keeps Zen's record so it can be written back unchanged;
 * its favicon (up to hundreds of KB of text) is stored here instead, by content hash, and the
 * record keeps a short reference. [restore] puts the data back for uploads, so Zen receives the
 * record exactly as it arrived.
 */
object ZenBlobs {
    private const val PREFIX = "sideraven-blob:"

    /** Shorter strings stay inline: a reference wouldn't save anything. */
    private const val MIN_LENGTH = 512

    /**
     * Where the data lives. Until the app points it at its files ([useFiles]) nothing is stashed:
     * favicons stay inline rather than land somewhere that won't survive a restart.
     */
    @Volatile var store: BlobStore? = null

    /** Points the store at the app's files; once, from whichever of startup or Zen Sync comes first. */
    fun useFiles(context: Context) {
        if (store == null) {
            synchronized(this) {
                if (store == null) store = FileBlobStore(File(context.applicationContext.filesDir, "sideraven_zen_blobs"))
            }
        }
    }

    /**
     * [data] with each large `data:` string field replaced by a reference to the stored data. A
     * field whose data couldn't be stored stays inline.
     */
    fun stash(data: JSONObject): JSONObject {
        val target = store ?: return data
        val large = largeFields(data)
        if (large.isEmpty()) return data
        val slim = JSONObject(data.toString())
        large.forEach { key ->
            val value = data.getString(key)
            val hash = sha256(value)
            if (target.put(hash, value)) slim.put(key, PREFIX + hash)
        }
        return slim
    }

    /**
     * A copy of [data] with every reference replaced by its stored data. If that data is gone, the
     * field is taken from [fallback] (the record as Zen has it now) or else left out, so neither a
     * reference nor an unwanted change reaches Zen.
     */
    fun restore(
        data: JSONObject,
        fallback: JSONObject? = null,
    ): JSONObject {
        val copy = JSONObject(data.toString())
        data.keys().asSequence().toList().forEach { key ->
            val value = data.opt(key) as? String ?: return@forEach
            if (value.startsWith(PREFIX)) {
                val stored = store?.get(value.removePrefix(PREFIX)) ?: (fallback?.opt(key) as? String)?.takeUnless { it.startsWith(PREFIX) }
                if (stored != null) copy.put(key, stored) else copy.remove(key)
            }
        }
        return copy
    }

    /** Whether [extra] still holds a data URI [stash] would move (stored before spec 0009). */
    fun holdsInline(extra: String?): Boolean =
        extra != null && extra.contains("\"data:") && ZenOrigin.decode(extra)?.data?.let { largeFields(it).isNotEmpty() } == true

    private fun largeFields(data: JSONObject): List<String> =
        data.keys().asSequence().filter { key -> (data.opt(key) as? String)?.let { it.length >= MIN_LENGTH && it.startsWith("data:") } == true }.toList()

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

/** Text stored by key; [ZenBlobs]'s storage. */
interface BlobStore {
    /** Stores [value] under [key]; false if it couldn't be stored. */
    fun put(
        key: String,
        value: String,
    ): Boolean

    fun get(key: String): String?
}

class InMemoryBlobStore : BlobStore {
    private val values = ConcurrentHashMap<String, String>()

    override fun put(
        key: String,
        value: String,
    ): Boolean {
        values[key] = value
        return true
    }

    override fun get(key: String): String? = values[key]
}

/**
 * One file per key in [dir]; written to its own temporary file and renamed, so a file is never half
 * written and two writers of the same key don't share one.
 */
class FileBlobStore(
    private val dir: File,
) : BlobStore {
    override fun put(
        key: String,
        value: String,
    ): Boolean {
        val file = File(dir, key)
        if (file.exists()) return true
        return runCatching {
            dir.mkdirs()
            val temp = File.createTempFile(key, ".tmp", dir)
            temp.writeText(value)
            temp.renameTo(file).also { if (!it) temp.delete() } || file.exists()
        }.getOrDefault(false)
    }

    override fun get(key: String): String? = File(dir, key).takeIf { it.exists() }?.readText()
}
