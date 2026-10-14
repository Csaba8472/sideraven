/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pt.dweep.sideraven.allowDiskReads
import pt.dweep.sideraven.theme.SpaceAccents

/**
 * The user's Zen Sync choices.
 *
 * @property enabled The main switch. Off by default.
 * @property firstSyncDone Whether the First Sync has run since Zen Sync was last turned on.
 * @property syncSpaces Spaces with their Pinned Tabs and Folders.
 * @property syncEssentials Essentials.
 * @property shareColours "Share Space colours with Zen".
 * @property desktopOnly Desktop-only Spaces: desktop Spaces switched off on this device.
 * @property seenSpaces Desktop Spaces this device has already met (anything else arrives switched off).
 * @property newOnDesktop Desktop-only Spaces still marked "New on desktop".
 * @property syncAutomatically Sync on its own (app start and return, every few minutes, after
 * changes here, with Firefox's sync); off, only "Sync now" syncs.
 */
data class ZenSyncSettings(
    val enabled: Boolean = false,
    val firstSyncDone: Boolean = false,
    val syncSpaces: Boolean = true,
    val syncEssentials: Boolean = true,
    val shareColours: Boolean = true,
    val desktopOnly: Set<String> = emptySet(),
    val seenSpaces: Set<String> = emptySet(),
    val newOnDesktop: Set<String> = emptySet(),
    val syncAutomatically: Boolean = true,
)

interface ZenSyncSettingsStore {
    val settings: StateFlow<ZenSyncSettings>

    fun save(settings: ZenSyncSettings)
}

class InMemoryZenSyncSettingsStore(initial: ZenSyncSettings = ZenSyncSettings()) : ZenSyncSettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<ZenSyncSettings> = state.asStateFlow()

    override fun save(settings: ZenSyncSettings) {
        state.value = settings
    }
}

class PreferencesZenSyncSettingsStore private constructor(context: Context) : ZenSyncSettingsStore {
    private val prefs = context.getSharedPreferences("sideraven_zen_sync_settings", Context.MODE_PRIVATE)
    private val state =
        MutableStateFlow(
            ZenSyncSettings(
                enabled = prefs.getBoolean("enabled", false),
                firstSyncDone = prefs.getBoolean("firstSyncDone", false),
                syncSpaces = prefs.getBoolean("syncSpaces", true),
                syncEssentials = prefs.getBoolean("syncEssentials", true),
                shareColours = prefs.getBoolean("shareColours", true),
                desktopOnly = prefs.getStringSet("desktopOnly", null)?.toSet().orEmpty(),
                seenSpaces = prefs.getStringSet("seenSpaces", null)?.toSet().orEmpty(),
                newOnDesktop = prefs.getStringSet("newOnDesktop", null)?.toSet().orEmpty(),
                syncAutomatically = prefs.getBoolean("syncAutomatically", true),
            ),
        )
    override val settings: StateFlow<ZenSyncSettings> = state.asStateFlow()

    override fun save(settings: ZenSyncSettings) {
        prefs.edit()
            .putBoolean("enabled", settings.enabled)
            .putBoolean("firstSyncDone", settings.firstSyncDone)
            .putBoolean("syncSpaces", settings.syncSpaces)
            .putBoolean("syncEssentials", settings.syncEssentials)
            .putBoolean("shareColours", settings.shareColours)
            .putStringSet("desktopOnly", settings.desktopOnly)
            .putStringSet("seenSpaces", settings.seenSpaces)
            .putStringSet("newOnDesktop", settings.newOnDesktop)
            .putBoolean("syncAutomatically", settings.syncAutomatically)
            .apply()
        state.value = settings
    }

    companion object {
        @Volatile private var instance: PreferencesZenSyncSettingsStore? = null

        fun get(context: Context): PreferencesZenSyncSettingsStore =
            instance ?: synchronized(this) {
                instance ?: allowDiskReads { PreferencesZenSyncSettingsStore(context.applicationContext) }.also { instance = it }
            }
    }
}

/** Order-independent digests of cleartext JSON, so "unchanged" survives key reordering. */
object Canonical {
    fun digest(json: JSONObject): String = RecordCrypto.hex(RecordCrypto.sha256(canonical(json).toByteArray(Charsets.UTF_8)))

    private fun canonical(value: Any?): String =
        when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
            is String -> JSONObject.quote(value)
            is Number -> if (value.toDouble() == value.toLong().toDouble()) value.toLong().toString() else value.toDouble().toString()
            else -> value.toString()
        }
}

/** Zen gradients to Sideraven accents: the palette accent nearest the theme's main colour. */
object ZenColours {
    fun nearestAccent(hex: String?): String? {
        val target = hex?.removePrefix("#")?.toLongOrNull(16)?.toInt() ?: return null
        fun distance(colour: Color): Int {
            val c = colour.toArgb()
            val dr = ((c shr 16) and 0xFF) - ((target shr 16) and 0xFF)
            val dg = ((c shr 8) and 0xFF) - ((target shr 8) and 0xFF)
            val db = (c and 0xFF) - (target and 0xFF)
            return dr * dr + dg * dg + db * db
        }
        return SpaceAccents.all.minByOrNull { minOf(distance(it.light), distance(it.dark)) }?.key
    }

    /**
     * The gradient a Space from Zen brings (its colours, top to bottom, as lowercase `#rrggbb`), or
     * empty: for a Space made here, when Space colours aren't shared, or once its accent was changed
     * here (the desktop gradient no longer matches it).
     */
    fun gradientOf(
        space: pt.dweep.sideraven.Space,
        shareColours: Boolean,
    ): List<String> {
        if (!shareColours) return emptyList()
        val origin = ZenOrigin.decode(space.extra) ?: return emptyList()
        if (origin.accent != null && origin.accent != space.accent) return emptyList()
        return ZenWire.themeOf(origin.data)?.gradientColors.orEmpty()
    }

    /** A one-colour Zen theme for an accent changed on the tablet. */
    fun solidTheme(accent: String): JSONObject {
        val hex = "#%06x".format(SpaceAccents.of(accent).dark.toArgb() and 0xFFFFFF)
        return JSONObject()
            .put("type", "gradient")
            .put(
                "gradientColors",
                JSONArray().put(JSONObject().put("c", hex).put("isCustom", false).put("isPrimary", true).put("algorithm", "floating").put("type", "explicit-lightness")),
            ).put("opacity", 0.5)
            .put("texture", 0)
    }
}
