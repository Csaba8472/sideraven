/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import pt.dweep.sideraven.allowDiskReads

/**
 * What a record looked like at the last successful sync: a digest of its cleartext and its server
 * timestamp. Comparing against it tells Zen Sync which side changed since then.
 */
data class BaseEntry(
    val digest: String,
    val serverModified: Double,
)

/**
 * The last-synced state of the `spaces` collection, kept apart from the user's data.
 *
 * @property entries Record id -> its state at the last sync.
 * @property collectionModified The collection's server timestamp at the last sync.
 */
data class SyncBase(
    val entries: Map<String, BaseEntry> = emptyMap(),
    val collectionModified: Double? = null,
)

interface SyncBaseRepository {
    val base: StateFlow<SyncBase>

    fun save(base: SyncBase)
}

class InMemorySyncBaseRepository(initial: SyncBase = SyncBase()) : SyncBaseRepository {
    private val state = MutableStateFlow(initial)
    override val base: StateFlow<SyncBase> = state.asStateFlow()

    override fun save(base: SyncBase) {
        state.value = base
    }
}

internal object SyncBaseCodec {
    fun encode(base: SyncBase): String {
        val entries = JSONObject()
        base.entries.forEach { (id, entry) -> entries.put(id, JSONObject().put("d", entry.digest).put("m", entry.serverModified)) }
        return JSONObject()
            .put("entries", entries)
            .apply { base.collectionModified?.let { put("collectionModified", it) } }
            .toString()
    }

    fun decode(raw: String?): SyncBase {
        if (raw == null) return SyncBase()
        return runCatching {
            val json = JSONObject(raw)
            val entries = json.getJSONObject("entries")
            SyncBase(
                entries = entries.keys().asSequence().associateWith { id ->
                    entries.getJSONObject(id).let { BaseEntry(it.getString("d"), it.getDouble("m")) }
                },
                collectionModified = if (json.has("collectionModified")) json.getDouble("collectionModified") else null,
            )
        }.getOrDefault(SyncBase())
    }
}

class PreferencesSyncBaseRepository private constructor(context: Context) : SyncBaseRepository {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(SyncBaseCodec.decode(prefs.getString(KEY, null)))
    override val base: StateFlow<SyncBase> = state.asStateFlow()

    override fun save(base: SyncBase) {
        prefs.edit().putString(KEY, SyncBaseCodec.encode(base)).apply()
        state.value = base
    }

    companion object {
        private const val PREFS = "sideraven_zen_sync"
        private const val KEY = "base"

        @Volatile private var instance: PreferencesSyncBaseRepository? = null

        fun get(context: Context): PreferencesSyncBaseRepository =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesSyncBaseRepository(context.applicationContext) }.also { instance = it }
            }
    }
}
