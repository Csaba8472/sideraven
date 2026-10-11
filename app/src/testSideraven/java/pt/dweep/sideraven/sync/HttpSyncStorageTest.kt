/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Zen Sync ticket 01: the storage client against the contract's recorded HTTP exchanges. */
@RunWith(AndroidJUnit4::class)
class HttpSyncStorageTest {
    private val requests = mutableListOf<SyncHttpRequest>()

    private fun recording(name: String): JSONObject =
        JSONObject(javaClass.classLoader!!.getResourceAsStream("zen-contract/http/$name.json")!!.bufferedReader().readText())

    /** Answers each request with the recorded response whose path matches, and a body built here. */
    private fun storage(vararg answers: Pair<String, String>): HttpSyncStorage {
        val byPath = answers.associate { (name, body) -> recording(name).let { it.getJSONObject("input").getJSONObject("request").getString("path") to (it to body) } }
        return HttpSyncStorage("https://sync.example.com/1.5/1", "id", ByteArray(32)) { request ->
            requests += request
            val path = request.url.path.removePrefix("/1.5/1") + (request.url.query?.let { "?$it" } ?: "")
            val (rec, body) = byPath[path] ?: error("unexpected request $path")
            val response = rec.getJSONObject("expect").getJSONObject("response")
            val headers = response.getJSONObject("headers").let { h -> h.keys().asSequence().associate { it.lowercase() to h.getString(it) } }
            SyncHttpResponse(response.getInt("status"), headers, body.toByteArray())
        }
    }

    private fun page(vararg ids: String) =
        JSONArray().apply { ids.forEach { put(JSONObject().put("id", it).put("payload", "{}").put("modified", 1700000100.0)) } }.toString()

    @Test
    fun `a full read follows the next-offset header until the last page`() {
        val read = storage("http-get-pagination-page1" to page("a", "b"), "http-get-pagination-page2" to page("c")).readAll(SPACES_COLLECTION)

        assertEquals(listOf("a", "b", "c"), read.records.map { it.id })
        assertEquals(1700000100.0, read.lastModified!!, 0.0)
        assertTrue(requests.all { it.headers["Authorization"]!!.startsWith("Hawk ") })
    }

    @Test
    fun `a stuck offset stops paging instead of looping`() {
        val read = storage("http-get-pagination-page1" to page("a"), "http-get-stuck-offset" to page("b")).readAll(SPACES_COLLECTION)

        assertEquals(listOf("a", "b"), read.records.map { it.id })
        assertEquals(2, requests.size)
    }

    @Test
    fun `a conditional write that lost the race reports the current timestamp`() {
        val result = storage("http-put-412" to "").write(SPACES_COLLECTION, "{abc}", "{}", ifUnmodifiedSince = 1700000000.0)

        assertEquals(PutResult.Conflict(1700000100.12), result)
        assertEquals("1700000000.00", requests.single().headers["X-If-Unmodified-Since"])
        assertTrue(requests.single().headers["Authorization"]!!.contains("hash=\""))
    }

    @Test
    fun `a non-HTTPS endpoint is refused before any request`() {
        try {
            HttpSyncStorage("http://sync.example.com/1.5/1", "id", ByteArray(32)) { error("must not send") }
            fail("expected a refusal")
        } catch (e: SyncError.Protocol) {
            assertEquals("insecure sync endpoint", e.message)
        }
    }
}
