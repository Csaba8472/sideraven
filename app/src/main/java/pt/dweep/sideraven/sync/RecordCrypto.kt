/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Why a Zen Sync step failed. Messages for crypto failures match the Z-Sync contract's error strings. */
sealed class SyncError(message: String) : Exception(message) {
    class Crypto(message: String) : SyncError(message)

    /** A Sync storage or token server answered with an unexpected HTTP status. */
    class Http(val status: Int, message: String = "HTTP $status") : SyncError(message)

    /** Not signed in, or the account couldn't provide a Sync token or key. */
    class Auth(message: String) : SyncError(message)

    /** The account has no `crypto/keys`; Sideraven never creates them. */
    class KeysMissing : SyncError("sync keys missing")

    /** The server or data broke the protocol (e.g. an insecure endpoint). */
    class Protocol(message: String) : SyncError(message)
}

/** An AES-256 encryption key and an HMAC-SHA256 key, as Sync 1.5 pairs them. */
class KeyBundle(val encryptionKey: ByteArray, val hmacKey: ByteArray) {
    companion object {
        /**
         * The Sync key bundle from Fenix's `oldsync` scoped key: its `k` is the base64url encoding
         * of 64 bytes, the encryption key followed by the HMAC key.
         */
        fun fromScopedKey(k: String): KeyBundle {
            val bytes =
                runCatching { Base64.getUrlDecoder().decode(k.trimEnd('=')) }.getOrNull()
                    ?: throw SyncError.Crypto("scoped key base64")
            if (bytes.size != 64) throw SyncError.Crypto("scoped key length")
            return KeyBundle(bytes.copyOfRange(0, 32), bytes.copyOfRange(32, 64))
        }

        /** A bundle from the two base64 strings `crypto/keys` stores per collection. */
        fun fromBase64Pair(encryption: String, hmac: String): KeyBundle =
            KeyBundle(
                RecordCrypto.b64decode(encryption) ?: throw SyncError.Crypto("bso base64"),
                RecordCrypto.b64decode(hmac) ?: throw SyncError.Crypto("bso base64"),
            )
    }
}

/**
 * Sync 1.5 record envelope encryption (ported from Z-Sync, MIT): AES-256-CBC with PKCS#7, and an
 * HMAC-SHA256 computed over the base64 ciphertext string. The envelope is `{ciphertext, IV, hmac}`.
 */
object RecordCrypto {
    private val random = SecureRandom()

    /** Verifies and decrypts an envelope (the JSON string stored in a record's `payload`). */
    fun decrypt(
        payload: String,
        keys: KeyBundle,
    ): ByteArray {
        val envelope = runCatching { JSONObject(payload) }.getOrNull() ?: throw SyncError.Crypto("bso json")
        val ciphertextB64 = envelope.optString("ciphertext")
        val ivB64 = envelope.optString("IV")
        val expected = runCatching { unhex(envelope.optString("hmac")) }.getOrNull() ?: throw SyncError.Crypto("bso hmac mismatch")
        val actual = hmacSha256(keys.hmacKey, ciphertextB64.toByteArray(Charsets.UTF_8))
        // Constant-time, and before any decryption.
        if (!MessageDigest.isEqual(actual, expected)) throw SyncError.Crypto("bso hmac mismatch")
        val ciphertext = b64decode(ciphertextB64) ?: throw SyncError.Crypto("bso base64")
        val iv = b64decode(ivB64) ?: throw SyncError.Crypto("bso base64")
        if (iv.size != 16) throw SyncError.Crypto("bso iv length")
        if (ciphertext.isEmpty() || ciphertext.size % 16 != 0) throw SyncError.Crypto("bso ciphertext length")
        return try {
            aes(ciphertext, keys.encryptionKey, iv, Cipher.DECRYPT_MODE)
        } catch (_: GeneralSecurityException) {
            throw SyncError.Crypto("bso cipher")
        }
    }

    /** Encrypts [plaintext] into an envelope string, with a fresh random IV unless [iv] is given. */
    fun encrypt(
        plaintext: ByteArray,
        keys: KeyBundle,
        iv: ByteArray = ByteArray(16).also { random.nextBytes(it) },
    ): String {
        val ciphertext =
            try {
                aes(plaintext, keys.encryptionKey, iv, Cipher.ENCRYPT_MODE)
            } catch (_: GeneralSecurityException) {
                throw SyncError.Crypto("bso cipher")
            }
        val ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext)
        return JSONObject()
            .put("ciphertext", ciphertextB64)
            .put("IV", Base64.getEncoder().encodeToString(iv))
            .put("hmac", hex(hmacSha256(keys.hmacKey, ciphertextB64.toByteArray(Charsets.UTF_8))))
            .toString()
    }

    private fun aes(
        data: ByteArray,
        key: ByteArray,
        iv: ByteArray,
        mode: Int,
    ): ByteArray =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(data)
        }

    fun hmacSha256(
        key: ByteArray,
        data: ByteArray,
    ): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    /** Strict standard base64 (the contract's target; no MIME leniency). */
    fun b64decode(s: String): ByteArray? = runCatching { Base64.getDecoder().decode(s) }.getOrNull()

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun unhex(hex: String): ByteArray {
        if (hex.length % 2 != 0) throw SyncError.Crypto("invalid hex")
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: throw SyncError.Crypto("invalid hex")
        }
    }
}
