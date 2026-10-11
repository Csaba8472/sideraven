/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import java.net.URL
import java.security.SecureRandom
import java.util.Base64

/**
 * Hawk request signing for Sync storage (ported from Z-Sync, MIT). Signs the verbatim request-line
 * resource, percent-encoding preserved.
 */
object Hawk {
    private val random = SecureRandom()

    /** The ten-line normalised string Hawk signs. */
    fun normalized(
        method: String,
        url: URL,
        resource: String,
        timestamp: Long,
        nonce: String,
        payloadHash: String?,
    ): String {
        val port = if (url.port != -1) url.port else if (url.protocol == "https") 443 else 80
        val path = if (resource.startsWith("/")) resource else "/$resource"
        return listOf(
            "hawk.1.header",
            timestamp.toString(),
            nonce,
            method.uppercase(),
            path,
            url.host.lowercase(),
            port.toString(),
            payloadHash.orEmpty(),
            "",
            "",
        ).joinToString("\n")
    }

    /** The `Authorization` header value. [timestamp] and [nonce] are fixed only in tests. */
    fun authorization(
        method: String,
        url: URL,
        resource: String,
        id: String,
        key: ByteArray,
        payloadHash: String? = null,
        timestamp: Long = System.currentTimeMillis() / 1000,
        nonce: String = randomNonce(),
    ): String {
        val mac = Base64.getEncoder().encodeToString(
            RecordCrypto.hmacSha256(key, normalized(method, url, resource, timestamp, nonce, payloadHash).toByteArray(Charsets.UTF_8)),
        )
        val parts = mutableListOf("id=\"$id\"", "ts=\"$timestamp\"", "nonce=\"$nonce\"", "mac=\"$mac\"")
        if (!payloadHash.isNullOrEmpty()) parts += "hash=\"$payloadHash\""
        return "Hawk " + parts.joinToString(", ")
    }

    /** base64(SHA-256("hawk.1.payload\n" + content type + "\n" + body + "\n")). */
    fun payloadHash(
        body: ByteArray,
        contentType: String = "application/json",
    ): String {
        val type = contentType.substringBefore(';').trim()
        val data = "hawk.1.payload\n$type\n".toByteArray(Charsets.UTF_8) + body + byteArrayOf(0x0A)
        return Base64.getEncoder().encodeToString(RecordCrypto.sha256(data))
    }

    private fun randomNonce(): String {
        val bytes = ByteArray(8).also { random.nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes).filterNot { it == '+' || it == '/' || it == '=' }
    }
}

/** BSO ids in request paths: percent-encoded over UTF-8 with only the RFC 3986 unreserved set kept. */
object BsoIds {
    private const val UNRESERVED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    fun encode(id: String): String =
        buildString {
            id.toByteArray(Charsets.UTF_8).forEach { byte ->
                val c = (byte.toInt() and 0xFF).toChar()
                if (byte >= 0 && c in UNRESERVED) append(c) else append("%%%02X".format(byte.toInt() and 0xFF))
            }
        }
}
