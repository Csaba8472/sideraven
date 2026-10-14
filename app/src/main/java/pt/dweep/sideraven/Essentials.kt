/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import mozilla.components.browser.state.state.TabSessionState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * The Essentials Cap: at or above it, adding an Essential on this device is
 * unavailable. Essentials beyond it (arriving through Zen Sync) are never hidden.
 */
const val ESSENTIALS_CAP = 12

/** A new stable id for an Essential, Pinned Tab, Folder or Space. */
fun newPinId(): String = UUID.randomUUID().toString()

/**
 * A URL pinned to the Essentials Section. Stored by URL, not tab id, in display order.
 *
 * @property id Stable identity, kept across restarts (and, with Zen Sync, across devices).
 * @property customName The user's name for it, shown instead of [title] when set.
 * @property extra Data Sideraven doesn't model (a JSON object as text), written back unchanged.
 * @property bucket The Zen container it belongs to; null is Zen's `default` (no container).
 * @property modified When the user last changed it, or its position, here (epoch ms; 0 = never).
 */
data class Essential(
    val url: String,
    val title: String,
    val id: String = newPinId(),
    val customName: String? = null,
    val extra: String? = null,
    val bucket: String? = null,
    val modified: Long = 0,
) {
    /** What the Sidebar shows: the custom name, else the title. */
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: title
}

/** JSON form of the stored Essentials list, and its migration from the pre-id format. */
internal object EssentialsCodec {
    fun encode(essentials: List<Essential>): String {
        val json = JSONArray()
        essentials.forEach { essential ->
            json.put(
                JSONObject()
                    .put("id", essential.id)
                    .put("url", essential.url)
                    .put("title", essential.title)
                    .put("modified", essential.modified)
                    .apply {
                        essential.customName?.let { put("customName", it) }
                        essential.extra?.let { put("extra", it) }
                        essential.bucket?.let { put("bucket", it) }
                    },
            )
        }
        return json.toString()
    }

    /** Decoded Essentials, and whether any needed migrating (so the caller writes them back). */
    fun decode(raw: String?): Pair<List<Essential>, Boolean> {
        if (raw == null) return emptyList<Essential>() to false
        var migrated = false
        val essentials =
            runCatching {
                val json = JSONArray(raw)
                (0 until json.length()).map { i ->
                    val item = json.getJSONObject(i)
                    val id = item.optString("id").ifBlank { migrated = true; newPinId() }
                    Essential(
                        url = item.getString("url"),
                        title = item.optString("title"),
                        id = id,
                        customName = item.optString("customName").ifBlank { null },
                        extra = item.optString("extra").ifBlank { null },
                        bucket = item.optString("bucket").ifBlank { null },
                        modified = item.optLong("modified", 0),
                    )
                }
            }.getOrDefault(emptyList())
        return essentials to migrated
    }
}

/** Outcome of trying to pin a tab. */
enum class PinResult {
    Pinned,

    /** The Essentials Cap is reached. */
    Full,

    /** Already an Essential. */
    AlreadyPinned,

    /** Private tabs never become Essentials. */
    PrivateRefused,
}

/** Persisted, ordered Essentials. */
interface EssentialsRepository {
    val essentials: StateFlow<List<Essential>>

    fun save(essentials: List<Essential>)
}

/** In-memory repository, for tests and previews. */
class InMemoryEssentialsRepository(initial: List<Essential> = emptyList()) : EssentialsRepository {
    private val state = MutableStateFlow(initial)
    override val essentials: StateFlow<List<Essential>> = state.asStateFlow()

    override fun save(essentials: List<Essential>) {
        state.value = essentials
    }
}

/** SharedPreferences-backed repository (a small JSON array). One per process, see [get]. */
class PreferencesEssentialsRepository private constructor(context: Context) : EssentialsRepository {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    override val essentials: StateFlow<List<Essential>> = state.asStateFlow()

    override fun save(essentials: List<Essential>) {
        prefs.edit().putString(KEY, EssentialsCodec.encode(essentials)).apply()
        state.value = essentials
    }

    private fun read(): List<Essential> {
        val (essentials, migrated) = EssentialsCodec.decode(prefs.getString(KEY, null))
        // Pre-id Essentials get their ids written back once, so they stay stable from now on.
        if (migrated) prefs.edit().putString(KEY, EssentialsCodec.encode(essentials)).apply()
        return essentials
    }

    companion object {
        private const val PREFS = "sideraven_essentials"
        private const val KEY = "essentials"

        @Volatile private var instance: PreferencesEssentialsRepository? = null

        fun get(context: Context): PreferencesEssentialsRepository =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesEssentialsRepository(context.applicationContext) }.also { instance = it }
            }
    }
}

/**
 * Essentials operations and Essential Tab links.
 *
 * A link (Essential URL -> tab id) is made when a tab is opened from, or pinned as, an Essential
 * and survives navigation. Links live in memory; after a restart, [relinkRestoredTabs] re-links
 * restored tabs whose URL exactly matches.
 */
class Essentials(
    private val raw: EssentialsRepository,
    private val links: MutableStateFlow<Map<String, String>> = MutableStateFlow(emptyMap()),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Saves through [stamp], so every user change records its edit time for Zen Sync. */
    private val repository: EssentialsRepository = StampingEssentialsRepository(raw) { clock() }

    /** Replaces the Essentials with what came from Zen Sync, without edit times (they aren't the tablet's changes). */
    fun applyRemote(essentials: List<Essential>) = raw.save(essentials)

    val essentials: StateFlow<List<Essential>> get() = repository.essentials
    val linkedTabs: StateFlow<Map<String, String>> get() = links

    /** Whether [tab] may be pinned right now. */
    fun canPin(tab: TabSessionState?): PinResult {
        if (tab == null) return PinResult.PrivateRefused
        val current = repository.essentials.value
        return when {
            tab.content.private -> PinResult.PrivateRefused
            current.any { it.url == tab.content.url } -> PinResult.AlreadyPinned
            current.size >= ESSENTIALS_CAP -> PinResult.Full
            else -> PinResult.Pinned
        }
    }

    /** Pins [tab]'s URL and links the tab as its Essential Tab. */
    fun pin(
        tab: TabSessionState,
        atIndex: Int? = null,
    ): PinResult {
        val result = canPin(tab)
        if (result != PinResult.Pinned) return result
        val current = repository.essentials.value.toMutableList()
        val essential = Essential(url = tab.content.url, title = tab.content.title.ifBlank { tab.content.url })
        current.add((atIndex ?: current.size).coerceIn(0, current.size), essential)
        repository.save(current)
        link(essential.url, tab.id)
        return PinResult.Pinned
    }

    /** Pins a URL (a Pinned Tab moving into Essentials), linking [tabId] when its tab is open. */
    fun pinUrl(
        url: String,
        title: String,
        tabId: String? = null,
        customName: String? = null,
    ): PinResult {
        val current = repository.essentials.value
        if (current.any { it.url == url }) return PinResult.AlreadyPinned
        if (current.size >= ESSENTIALS_CAP) return PinResult.Full
        repository.save(current + Essential(url = url, title = title, customName = customName))
        tabId?.let { link(url, it) }
        return PinResult.Pinned
    }

    /** Unpins [url]; its Essential Tab, if open, returns to the Tab List. */
    fun unpin(url: String) {
        repository.save(repository.essentials.value.filterNot { it.url == url })
        links.value = links.value - url
    }

    /** Gives the Essential at [url] a custom name; null or blank goes back to its title. */
    fun rename(
        url: String,
        name: String?,
    ) {
        val customName = name?.trim()?.ifBlank { null }
        repository.save(repository.essentials.value.map { if (it.url == url) it.copy(customName = customName) else it })
    }

    /** Moves the Essential at [url] to [toIndex]. */
    fun move(
        url: String,
        toIndex: Int,
    ) {
        val current = repository.essentials.value.toMutableList()
        val index = current.indexOfFirst { it.url == url }
        if (index == -1) return
        val essential = current.removeAt(index)
        current.add(toIndex.coerceIn(0, current.size), essential)
        repository.save(current)
    }

    fun link(
        url: String,
        tabId: String,
    ) {
        links.value = links.value + (url to tabId)
    }

    /** Moves the Essential Tab link from [oldUrl] to [newUrl] (the Essential's address changed); returns the tab. */
    fun relink(
        oldUrl: String,
        newUrl: String,
    ): String? {
        val tabId = links.value[oldUrl] ?: return null
        links.value = links.value - oldUrl + (newUrl to tabId)
        return tabId
    }

    /** The live Essential Tab id for [url], if its tab still exists in [tabs]. */
    fun linkedTabId(
        url: String,
        tabs: List<TabSessionState>,
    ): String? = links.value[url]?.takeIf { id -> tabs.any { it.id == id } }

    /**
     * After session restore: link each Essential without a live Essential Tab to the first
     * unlinked, non-private restored tab whose URL matches exactly.
     */
    fun relinkRestoredTabs(tabs: List<TabSessionState>) {
        val updated = links.value.filterValues { id -> tabs.any { it.id == id } }.toMutableMap()
        repository.essentials.value.forEach { essential ->
            if (essential.url in updated) return@forEach
            tabs.firstOrNull { tab ->
                !tab.content.private && tab.content.url == essential.url && tab.id !in updated.values
            }?.let { updated[essential.url] = it.id }
        }
        links.value = updated
    }

    companion object {
        /** Links are per process, like the tabs they point at. */
        val processLinks = MutableStateFlow<Map<String, String>>(emptyMap())

        /** Whether this process has already re-linked restored tabs. */
        @Volatile var restoreRelinked = false
    }
}

/**
 * Stamps edit times on save: an Essential that is new, changed, or moved gets `modified` = now.
 * Zen keeps Essentials' order in one layout record, so a move stamps the moved Essentials.
 */
private class StampingEssentialsRepository(
    private val delegate: EssentialsRepository,
    private val now: () -> Long,
) : EssentialsRepository by delegate {
    override fun save(essentials: List<Essential>) {
        val before = delegate.essentials.value
        val oldById = before.associateBy { it.id }
        val oldIndex = before.mapIndexed { i, e -> e.id to i }.toMap()
        val time = now()
        delegate.save(
            essentials.mapIndexed { index, essential ->
                val old = oldById[essential.id]
                val moved = oldIndex[essential.id]?.let { it != index } ?: true
                if (old == null || old.copy(modified = 0) != essential.copy(modified = 0) || moved) essential.copy(modified = time) else essential
            },
        )
    }
}
