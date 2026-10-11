/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One stored record (BSO): its id, encrypted envelope, and server timestamp (seconds). */
data class StoredRecord(
    val id: String,
    val payload: String,
    val modified: Double,
)

/** A full read of a collection, and the collection's last-modified timestamp. */
data class CollectionRead(
    val records: List<StoredRecord>,
    val lastModified: Double?,
)

/** Outcome of a conditional write. */
sealed interface PutResult {
    data class Ok(val modified: Double) : PutResult

    /** 412: the record changed on the server since [ifUnmodifiedSince]; [current] is its timestamp. */
    data class Conflict(val current: Double?) : PutResult
}

/** The subset of Sync 1.5 storage Zen Sync uses. Blocking; never call on the main thread. */
interface SyncStorage {
    /** Every record of [collection] (paged); empty when the collection doesn't exist. */
    fun readAll(collection: String): CollectionRead

    /** One record, or null when it doesn't exist. */
    fun read(
        collection: String,
        id: String,
    ): StoredRecord?

    /**
     * Writes one record. With [ifUnmodifiedSince], the write only happens if the record's server
     * timestamp still equals it (0 = create only if absent).
     */
    fun write(
        collection: String,
        id: String,
        payload: String,
        ifUnmodifiedSince: Double? = null,
    ): PutResult
}

/** Server timestamps as the Sync headers carry them: decimal seconds with two places. */
fun formatSyncTimestamp(seconds: Double): String = String.format(Locale.ROOT, "%.2f", seconds)

/** One HTTP request/response; the seam tests replace. */
data class SyncHttpRequest(
    val method: String,
    val url: URL,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

data class SyncHttpResponse(
    val status: Int,
    /** Lowercased names. */
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase()]
}

fun interface SyncHttpTransport {
    fun execute(request: SyncHttpRequest): SyncHttpResponse
}

/** Production transport: one HttpURLConnection per request; redirects are never followed. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 30_000,
    private val readTimeoutMs: Int = 60_000,
) : SyncHttpTransport {
    override fun execute(request: SyncHttpRequest): SyncHttpResponse {
        val connection = request.url.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            request.body?.let { body ->
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = connection.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull().orEmpty() }
            return SyncHttpResponse(status, headers, body)
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Sync 1.5 storage over HTTP with Hawk credentials from the token server.
 *
 * @param endpoint The token server's `api_endpoint` (must be HTTPS).
 */
class HttpSyncStorage(
    private val endpoint: String,
    private val hawkId: String,
    private val hawkKey: ByteArray,
    private val transport: SyncHttpTransport = UrlConnectionTransport(),
) : SyncStorage {
    init {
        if (!endpoint.startsWith("https://")) throw SyncError.Protocol("insecure sync endpoint")
    }

    override fun readAll(collection: String): CollectionRead {
        val records = mutableListOf<StoredRecord>()
        var lastModified: Double? = null
        var offset: String? = null
        repeat(MAX_PAGES) {
            val query = "full=1&limit=$PAGE_SIZE" + (offset?.let { "&offset=" + BsoIds.encode(it) } ?: "")
            val response = send("GET", "/storage/$collection?$query", null, emptyMap())
            when (response.status) {
                404 -> return CollectionRead(emptyList(), null)
                in 200..299 -> Unit
                else -> throw SyncError.Http(response.status)
            }
            lastModified = response.header("X-Last-Modified")?.toDoubleOrNull() ?: lastModified
            val page = JSONArray(String(response.body, Charsets.UTF_8))
            if (page.length() == 0) return CollectionRead(records, lastModified)
            (0 until page.length()).forEach { records += record(page.getJSONObject(it)) }
            val next = response.header("X-Weave-Next-Offset")
            // Stop when there's no next page, or the server repeats the offset (stuck token).
            if (next.isNullOrEmpty() || next == offset) return CollectionRead(records, lastModified)
            offset = next
        }
        return CollectionRead(records, lastModified)
    }

    override fun read(
        collection: String,
        id: String,
    ): StoredRecord? {
        val response = send("GET", "/storage/$collection/${BsoIds.encode(id)}", null, emptyMap())
        return when (response.status) {
            404 -> null
            in 200..299 -> {
                val json = JSONObject(String(response.body, Charsets.UTF_8))
                if (!json.has("payload")) null else record(json)
            }
            else -> throw SyncError.Http(response.status)
        }
    }

    override fun write(
        collection: String,
        id: String,
        payload: String,
        ifUnmodifiedSince: Double?,
    ): PutResult {
        val body = JSONObject().put("payload", payload).toString().toByteArray(Charsets.UTF_8)
        val headers = ifUnmodifiedSince?.let { mapOf("X-If-Unmodified-Since" to formatSyncTimestamp(it)) }.orEmpty()
        val response = send("PUT", "/storage/$collection/${BsoIds.encode(id)}", body, headers)
        return when (response.status) {
            412 -> PutResult.Conflict(response.header("X-Last-Modified")?.toDoubleOrNull())
            in 200..299 ->
                PutResult.Ok(
                    response.header("X-Last-Modified")?.toDoubleOrNull()
                        ?: String(response.body, Charsets.UTF_8).trim().toDoubleOrNull()
                        ?: 0.0,
                )
            else -> throw SyncError.Http(response.status)
        }
    }

    private fun send(
        method: String,
        path: String,
        body: ByteArray?,
        extraHeaders: Map<String, String>,
    ): SyncHttpResponse {
        val url = URL(endpoint.trimEnd('/') + path)
        // Sign the verbatim request-line resource (percent-encoding preserved).
        val resource = url.path + (url.query?.let { "?$it" } ?: "")
        val hash = body?.let { Hawk.payloadHash(it) }
        val headers =
            buildMap {
                put("Authorization", Hawk.authorization(method, url, resource, hawkId, hawkKey, hash))
                put("Accept", "application/json")
                if (body != null) put("Content-Type", "application/json")
                putAll(extraHeaders)
            }
        return transport.execute(SyncHttpRequest(method, url, headers, body))
    }

    private fun record(json: JSONObject) =
        StoredRecord(
            id = json.getString("id"),
            payload = json.getString("payload"),
            modified = json.optDouble("modified", 0.0),
        )

    private companion object {
        const val PAGE_SIZE = 2500
        const val MAX_PAGES = 50
    }
}
