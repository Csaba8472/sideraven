/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URL
import java.util.Base64

/**
 * The protocol port against Z-Sync's contract fixtures (vendored under
 * `zen-contract/`, Contract-Version 1). Expected values come from the fixtures, not the code.
 */
@RunWith(AndroidJUnit4::class)
class ZenContractTest {
    private fun fixture(path: String): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("zen-contract/$path.json") ?: error("missing fixture $path")
        return JSONObject(stream.bufferedReader().readText())
    }

    private fun input(path: String) = fixture(path).getJSONObject("input")

    private fun expect(path: String) = fixture(path).getJSONObject("expect")

    private fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }

    private fun nullableString(json: JSONObject, key: String): String? = if (json.isNull(key)) null else json.getString(key)

    private fun nullableBool(json: JSONObject, key: String): Boolean? = if (json.isNull(key)) null else json.getBoolean(key)

    // Crypto

    @Test
    fun `a valid envelope decrypts to its plaintext and re-encrypts to the same ciphertext with its IV`() {
        val input = input("crypto/crypto-bso-envelope-valid")
        val expect = expect("crypto/crypto-bso-envelope-valid")
        val keys = KeyBundle(RecordCrypto.unhex(input.getString("encryptionKeyHex")), RecordCrypto.unhex(input.getString("hmacKeyHex")))
        val envelope = input.getJSONObject("envelope")

        assertEquals(expect.getString("plaintextUtf8"), String(RecordCrypto.decrypt(envelope.toString(), keys)))

        val reencrypted = JSONObject(RecordCrypto.encrypt(expect.getString("plaintextUtf8").toByteArray(), keys, Base64.getDecoder().decode(envelope.getString("IV"))))
        assertEquals(expect.getString("recomputedCiphertext"), reencrypted.getString("ciphertext"))
        assertEquals(envelope.getString("hmac"), reencrypted.getString("hmac"))
    }

    @Test
    fun `a tampered HMAC fails before decryption`() {
        val input = input("crypto/crypto-bso-envelope-tampered-hmac")
        val keys = KeyBundle(RecordCrypto.unhex(input.getString("encryptionKeyHex")), RecordCrypto.unhex(input.getString("hmacKeyHex")))
        try {
            RecordCrypto.decrypt(input.getJSONObject("envelope").toString(), keys)
            fail("expected a failure")
        } catch (e: SyncError.Crypto) {
            assertEquals(expect("crypto/crypto-bso-envelope-tampered-hmac").getString("error"), e.message)
        }
    }

    @Test
    fun `Fenix's scoped key splits into the encryption and HMAC keys`() {
        val bytes = ByteArray(64) { it.toByte() }
        val keys = KeyBundle.fromScopedKey(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes))
        assertEquals(RecordCrypto.hex(bytes.copyOfRange(0, 32)), RecordCrypto.hex(keys.encryptionKey))
        assertEquals(RecordCrypto.hex(bytes.copyOfRange(32, 64)), RecordCrypto.hex(keys.hmacKey))
    }

    // Hawk and BSO ids

    @Test
    fun `Hawk signs the verbatim resource as the fixture expects`() {
        val input = input("crypto/hawk-authorization-resource")
        val expect = expect("crypto/hawk-authorization-resource")
        val url = URL(input.getString("url"))
        val ts = input.getLong("fixedTimestamp")
        val nonce = input.getString("fixedNonce")

        assertEquals(expect.getString("normalized"), Hawk.normalized(input.getString("method"), url, input.getString("resource"), ts, nonce, null))
        assertEquals(
            expect.getString("authorization"),
            Hawk.authorization(input.getString("method"), url, input.getString("resource"), input.getString("id"), RecordCrypto.unhex(input.getString("keyHex")), null, ts, nonce),
        )
    }

    @Test
    fun `the Hawk payload hash matches`() {
        val input = input("crypto/hawk-payload-hash")
        assertEquals(
            expect("crypto/hawk-payload-hash").getString("hashBase64"),
            Hawk.payloadHash(input.getString("bodyUtf8").toByteArray(), input.getString("contentType")),
        )
    }

    @Test
    fun `BSO ids are percent-encoded with only the unreserved set kept`() {
        val cases = fixture("auth/bso-ids-percent-encoding").getJSONArray("cases")
        (0 until cases.length()).map { cases.getJSONObject(it) }.forEach { case ->
            assertEquals(case.getString("id"), case.getJSONObject("expect").getString("encoded"), BsoIds.encode(case.getJSONObject("input").getString("id")))
        }
    }

    // Wire

    private fun decode(path: String) = ZenWire.decode(input(path))

    @Test
    fun `space records decode, with every colour form as a hex dot`() {
        val basic = decode("wire/wire-space-basic") as ZenSpace
        val e = expect("wire/wire-space-basic")
        assertEquals(e.getString("uuid"), basic.uuid)
        assertEquals(e.getString("name"), basic.name)
        assertEquals(e.getString("icon"), basic.icon)
        assertEquals(strings(e.getJSONArray("children")), basic.children)
        assertEquals(strings(e.getJSONArray("gradientColors")), basic.theme!!.gradientColors)
        assertEquals(nullableString(e, "containerGuid"), basic.containerGuid)

        val dots = decode("wire/wire-space-object-dots") as ZenSpace
        val dotsExpect = expect("wire/wire-space-object-dots")
        assertEquals(strings(dotsExpect.getJSONArray("gradientColors")), dots.theme!!.gradientColors)
        assertEquals(dotsExpect.getJSONArray("dots").getJSONObject(0).getBoolean("isPrimary"), dots.theme.dots.single().isPrimary)

        val rgb = decode("wire/wire-space-rgb-dots") as ZenSpace
        assertEquals(strings(expect("wire/wire-space-rgb-dots").getJSONArray("gradientColors")), rgb.theme!!.gradientColors)
        assertEquals("#ff7850", rgb.theme.primaryHex)
    }

    @Test
    fun `tab records decode, with pinned absent meaning pinned and string flags parsed`() {
        val pinnedDefault = decode("wire/wire-tab-pinned-default") as ZenTab
        val e = expect("wire/wire-tab-pinned-default")
        assertEquals(nullableBool(e, "pinned"), pinnedDefault.pinned)
        assertEquals(e.getBoolean("isNormalTab"), pinnedDefault.isNormalTab)
        assertEquals(nullableString(e, "folderId"), pinnedDefault.folderId)
        assertEquals(e.getString("workspaceUuid"), pinnedDefault.workspaceUuid)

        listOf("wire/wire-tab-normal-pinned-false", "wire/wire-tab-pinned-string-false").forEach { path ->
            val tab = decode(path) as ZenTab
            assertEquals(path, expect(path).getBoolean("pinned"), tab.pinned)
            assertEquals(path, expect(path).getBoolean("isNormalTab"), tab.isNormalTab)
        }
    }

    @Test
    fun `folder records decode and keep unknown fields such as live config`() {
        val basic = decode("wire/wire-folder-basic") as ZenFolder
        val e = expect("wire/wire-folder-basic")
        assertEquals(e.getString("folderId"), basic.folderId)
        assertEquals(strings(e.getJSONArray("children")), basic.children)
        assertEquals(nullableString(e, "parentFolderId"), basic.parentFolderId)

        val live = decode("wire/wire-folder-live-object") as ZenFolder
        assertEquals(nullableString(expect("wire/wire-folder-live-object"), "icon"), live.icon)
        // Preserved for writing back unchanged.
        assertEquals("rss", live.data.getJSONObject("live").getString("kind"))

        // A folder without folderId is dropped (it can never be a target).
        assertNull(decode("wire/wire-folder-missing-folderid"))
    }

    @Test
    fun `split and layout records decode, dropping non-string ids but keeping valid buckets`() {
        val split = decode("wire/wire-split-basic") as ZenSplit
        val se = expect("wire/wire-split-basic")
        assertEquals(nullableBool(se, "pinned"), split.pinned)
        assertEquals(se.getBoolean("isNormalSplit"), split.isNormalSplit)
        assertEquals(strings(se.getJSONArray("tabs")), split.tabs)
        assertTrue((decode("wire/wire-split-normal-pinned-false") as ZenSplit).isNormalSplit)

        val layout = decode("wire/wire-layout-basic") as ZenLayout
        val le = expect("wire/wire-layout-basic")
        assertEquals(strings(le.getJSONArray("spaces")), layout.spaces)
        val buckets = le.getJSONObject("essentials")
        assertEquals(buckets.keys().asSequence().associateWith { strings(buckets.getJSONArray(it)) }, layout.essentials)
    }

    @Test
    fun `every ignored shape is dropped, and hostile shapes follow the strict rules`() {
        val cases = fixture("wire/wire-ignored-records").getJSONArray("cases")
        (0 until cases.length()).map { cases.getJSONObject(it) }.forEach { case ->
            assertNull(case.getString("id"), ZenWire.decode(case.getJSONObject("input")))
        }
        assertNull(decode("wire/wire-space-numeric-uuid"))
        // The string "true" is not a tombstone.
        val notDeleted = decode("wire/wire-deleted-string") as ZenTab
        assertEquals(expect("wire/wire-deleted-string").getString("url"), notDeleted.url)
        assertFalse(ZenWire.isTombstone(input("wire/wire-deleted-string")))
        assertTrue(ZenWire.isTombstone(ZenWire.tombstone("x")))
    }

    @Test
    fun `synced pref values parse tolerantly`() {
        val cases = fixture("wire/wire-prefs-normal-tabs").getJSONArray("cases")
        (0 until cases.length()).map { cases.getJSONObject(it) }.forEach { case ->
            val expected = case.getJSONObject("expect").let { if (it.isNull("prefBool")) null else it.getBoolean("prefBool") }
            assertEquals(case.getString("id"), expected, ZenWire.flag(case.getJSONObject("input").opt("value")))
        }
    }
}
