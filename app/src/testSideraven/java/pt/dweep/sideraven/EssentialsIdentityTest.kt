/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spaces ticket 01: Essentials have a stable identity, custom name and preserved extra data. */
@RunWith(AndroidJUnit4::class)
class EssentialsIdentityTest {
    @Test
    fun `GIVEN Essentials stored before ids existed WHEN read THEN they keep order and get ids, flagged for write-back`() {
        val old = """[{"url":"https://mail.example","title":"Mail"},{"url":"https://docs.example","title":"Docs"}]"""

        val (essentials, migrated) = EssentialsCodec.decode(old)

        assertTrue(migrated)
        assertEquals(listOf("https://mail.example", "https://docs.example"), essentials.map { it.url })
        assertTrue(essentials.all { it.id.isNotBlank() })
        assertNotEquals(essentials[0].id, essentials[1].id)
    }

    @Test
    fun `THEN id, custom name and extra data round-trip unchanged`() {
        val extra = """{"containerGuid":"work","theme":{"type":"gradient"}}"""
        val original =
            listOf(
                Essential("https://mail.example", "Mail", id = "e1", customName = "Inbox", extra = extra),
                Essential("https://docs.example", "Docs", id = "e2"),
            )

        val (decoded, migrated) = EssentialsCodec.decode(EssentialsCodec.encode(original))

        assertFalse(migrated)
        assertEquals(original, decoded)
    }

    @Test
    fun `THEN the custom name shows instead of the title, and a blank one falls back`() {
        assertEquals("Inbox", Essential("https://mail.example", "Mail", customName = "Inbox").displayName)
        assertEquals("Mail", Essential("https://mail.example", "Mail", customName = " ").displayName)
    }

    @Test
    fun `WHEN a tab is pinned THEN its Essential keeps the same id through moves`() {
        val repository = InMemoryEssentialsRepository(listOf(Essential("https://a.example", "A", id = "a")))
        val essentials = Essentials(repository)
        repository.save(repository.essentials.value + Essential("https://b.example", "B", id = "b"))

        essentials.move("https://b.example", 0)

        assertEquals(listOf("b", "a"), repository.essentials.value.map { it.id })
    }
}
