/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** The Sync collection Zen writes Spaces to, and its single layout record. */
const val SPACES_COLLECTION = "spaces"
const val LAYOUT_RECORD_ID = "layout"

/**
 * One decoded record of Zen's `spaces` collection (record shapes from Zen's
 * `ZenSpacesSyncModel`; decoding rules from the Z-Sync contract). [data] is the record's raw
 * `data` object, kept so fields Sideraven doesn't model are written back unchanged.
 */
sealed interface ZenRecord {
    val id: String
    val data: JSONObject
}

data class ZenSpace(
    override val id: String,
    val uuid: String,
    val name: String?,
    val icon: String?,
    val theme: ZenTheme?,
    val containerGuid: String?,
    val children: List<String>,
    override val data: JSONObject,
) : ZenRecord

/**
 * A tab record: an Essential ([isEssential]), a Pinned Tab, or a normal tab ([isNormalTab],
 * which Sideraven never syncs). Absent/null `pinned` means pinned.
 */
data class ZenTab(
    override val id: String,
    val tabId: String,
    val url: String,
    val title: String?,
    val icon: String?,
    val containerGuid: String?,
    val essential: Boolean?,
    val pinned: Boolean?,
    val workspaceUuid: String?,
    val folderId: String?,
    val staticLabel: String?,
    override val data: JSONObject,
) : ZenRecord {
    val isNormalTab: Boolean get() = pinned == false
    val isEssential: Boolean get() = essential == true
}

data class ZenFolder(
    override val id: String,
    val folderId: String,
    val name: String?,
    val icon: String?,
    val workspaceUuid: String?,
    val parentFolderId: String?,
    val children: List<String>,
    override val data: JSONObject,
) : ZenRecord

data class ZenSplit(
    override val id: String,
    val splitId: String,
    val gridType: String?,
    val pinned: Boolean?,
    val tabs: List<String>,
    val workspaceUuid: String?,
    val folderId: String?,
    override val data: JSONObject,
) : ZenRecord {
    val isNormalSplit: Boolean get() = pinned == false
}

/** Space order, and Essentials grouped by container bucket ("default" = no container). */
data class ZenLayout(
    override val id: String,
    val spaces: List<String>,
    val essentials: Map<String, List<String>>,
    override val data: JSONObject,
) : ZenRecord

/** A Space's theme; [dots] are its gradient colours as lowercase `#rrggbb`. */
data class ZenTheme(
    val type: String?,
    val dots: List<ZenDot>,
    val opacity: Double?,
    val texture: Double?,
) {
    val gradientColors: List<String> get() = dots.map { it.hex }

    /** The colour the Space reads as: the primary dot, else the first. */
    val primaryHex: String? get() = (dots.firstOrNull { it.isPrimary } ?: dots.firstOrNull())?.hex
}

data class ZenDot(
    val hex: String,
    val isPrimary: Boolean = false,
)

/** Decoding and encoding of `spaces` collection cleartexts (`{id, kind, data}` or a tombstone). */
object ZenWire {
    private val KINDS = setOf("container", "space", "tab", "folder", "split", "layout")

    /** Whether [cleartext] is a tombstone: `deleted` must be the JSON boolean true. */
    fun isTombstone(cleartext: JSONObject): Boolean = (cleartext.opt("deleted") as? Boolean) == true

    /**
     * The record in [cleartext], or null when it must be dropped: tombstones, unknown or missing
     * kinds, containers (never applied), missing or non-object data, missing required fields.
     */
    fun decode(cleartext: JSONObject): ZenRecord? {
        if (isTombstone(cleartext)) return null
        val kind = cleartext.opt("kind") as? String ?: return null
        if (kind !in KINDS || kind == "container") return null
        val data = cleartext.opt("data") as? JSONObject ?: return null
        val id = cleartext.opt("id") as? String ?: return null
        return runCatching {
            when (kind) {
                "space" -> space(id, data)
                "tab" -> tab(id, data)
                "folder" -> folder(id, data)
                "split" -> split(id, data)
                "layout" -> layout(id, data)
                else -> null
            }
        }.getOrNull()
    }

    private fun space(
        id: String,
        data: JSONObject,
    ): ZenSpace? {
        val uuid = string(data, "uuid")?.takeIf { it.isNotEmpty() } ?: return null
        return ZenSpace(
            id = id,
            uuid = uuid,
            name = string(data, "name"),
            icon = string(data, "icon"),
            theme = (data.opt("theme") as? JSONObject)?.let(::theme),
            containerGuid = string(data, "containerGuid"),
            children = strings(data, "children"),
            data = data,
        )
    }

    private fun tab(
        id: String,
        data: JSONObject,
    ): ZenTab? {
        val tabId = string(data, "tabId")?.takeIf { it.isNotEmpty() } ?: return null
        val url = string(data, "url")?.takeIf { it.isNotEmpty() } ?: return null
        return ZenTab(
            id = id,
            tabId = tabId,
            url = url,
            title = string(data, "title"),
            icon = string(data, "icon"),
            containerGuid = string(data, "containerGuid"),
            essential = flag(data.opt("essential")),
            pinned = flag(data.opt("pinned")),
            workspaceUuid = string(data, "workspaceUuid"),
            folderId = string(data, "folderId"),
            staticLabel = string(data, "staticLabel"),
            data = data,
        )
    }

    private fun folder(
        id: String,
        data: JSONObject,
    ): ZenFolder? {
        val folderId = string(data, "folderId")?.takeIf { it.isNotEmpty() } ?: return null
        return ZenFolder(
            id = id,
            folderId = folderId,
            name = string(data, "name"),
            icon = string(data, "icon"),
            workspaceUuid = string(data, "workspaceUuid"),
            parentFolderId = string(data, "parentFolderId"),
            children = strings(data, "children"),
            data = data,
        )
    }

    private fun split(
        id: String,
        data: JSONObject,
    ): ZenSplit? {
        val splitId = string(data, "splitId")?.takeIf { it.isNotEmpty() } ?: return null
        return ZenSplit(
            id = id,
            splitId = splitId,
            gridType = string(data, "gridType"),
            pinned = flag(data.opt("pinned")),
            tabs = strings(data, "tabs"),
            workspaceUuid = string(data, "workspaceUuid"),
            folderId = string(data, "folderId"),
            data = data,
        )
    }

    private fun layout(
        id: String,
        data: JSONObject,
    ): ZenLayout {
        val buckets = linkedMapOf<String, List<String>>()
        (data.opt("essentials") as? JSONObject)?.let { essentials ->
            essentials.keys().forEach { key ->
                // A malformed bucket is dropped without discarding the valid ones.
                (essentials.opt(key) as? JSONArray)?.let { buckets[key] = strings(it) }
            }
        }
        return ZenLayout(id, strings(data, "spaces"), buckets, data)
    }

    private fun theme(json: JSONObject): ZenTheme {
        val dots =
            (json.opt("gradientColors") as? JSONArray)?.let { list ->
                (0 until list.length()).mapNotNull { dot(list.opt(it)) }
            }.orEmpty()
        return ZenTheme(string(json, "type"), dots, number(json.opt("opacity")), number(json.opt("texture")))
    }

    private fun dot(value: Any?): ZenDot? =
        when (value) {
            is String -> colour(value)?.let { ZenDot(it) }
            is JSONArray -> rgbArray(value)?.let { ZenDot(it) }
            is JSONObject -> {
                // An unrecognised colour falls back to grey rather than dropping the dot.
                val c = value.opt("c")
                val parsed: String? =
                    if (c is String) {
                        colour(c)
                    } else if (c is JSONArray) {
                        rgbArray(c)
                    } else {
                        null
                    }
                val hex = parsed ?: GREY
                ZenDot(hex, isPrimary = value.opt("isPrimary") as? Boolean ?: false)
            }
            else -> null
        }

    private const val GREY = "#808080"

    private fun colour(text: String): String? = hexColour(text) ?: rgbString(text)

    private fun hexColour(text: String): String? {
        var t = text.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 && t.length != 8) return null
        if (t.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
        return "#" + t.substring(0, 6).lowercase()
    }

    private fun rgbString(text: String): String? {
        val parts = Regex("[0-9.]+").findAll(text).map { it.value.toDouble() }.toList()
        return if (parts.size >= 3) hexOf(parts[0], parts[1], parts[2]) else null
    }

    private fun rgbArray(array: JSONArray): String? {
        if (array.length() < 3) return null
        val values = (0 until 3).map { (array.opt(it) as? Number)?.toDouble() ?: return null }
        return hexOf(values[0], values[1], values[2])
    }

    private fun hexOf(
        r: Double,
        g: Double,
        b: Double,
    ): String = "#%02x%02x%02x".format(byte(r), byte(g), byte(b))

    private fun byte(v: Double): Int = v.coerceIn(0.0, 255.0).roundToInt()

    /** A boolean flag sent as a JSON boolean, "true"/"false"/"1"/"0", or 1/0; anything else is null. */
    fun flag(value: Any?): Boolean? =
        when (value) {
            is Boolean -> value
            is String ->
                when (value) {
                    "true", "1" -> true
                    "false", "0" -> false
                    else -> null
                }
            is Number ->
                when (value.toDouble()) {
                    1.0 -> true
                    0.0 -> false
                    else -> null
                }
            else -> null
        }

    /** A required-type string: present, not null, and a JSON string (no coercion). */
    fun string(
        obj: JSONObject,
        key: String,
    ): String? = obj.opt(key) as? String

    private fun strings(
        obj: JSONObject,
        key: String,
    ): List<String> = (obj.opt(key) as? JSONArray)?.let(::strings).orEmpty()

    private fun strings(array: JSONArray): List<String> = (0 until array.length()).mapNotNull { array.opt(it) as? String }

    private fun number(value: Any?): Double? =
        when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }

    /** The cleartext for a record: `{id, kind, data}`. */
    fun cleartext(
        id: String,
        kind: String,
        data: JSONObject,
    ): JSONObject = JSONObject().put("id", id).put("kind", kind).put("data", data)

    /** The cleartext for a delete: `{id, deleted: true}`. Zen reads `deleted` from the decrypted cleartext. */
    fun tombstone(id: String): JSONObject = JSONObject().put("id", id).put("deleted", true)
}
