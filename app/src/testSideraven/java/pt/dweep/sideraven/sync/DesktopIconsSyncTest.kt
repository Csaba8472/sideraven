/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.sync

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import pt.dweep.sideraven.Essentials
import pt.dweep.sideraven.IconStore
import pt.dweep.sideraven.InMemoryEssentialsRepository
import pt.dweep.sideraven.InMemorySpacesRepository
import pt.dweep.sideraven.Spaces
import java.io.ByteArrayOutputStream
import java.util.Base64

/** Spec 0008 ticket 05: desktop's favicons arrive with Zen Sync for items not open here. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesktopIconsSyncTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `GIVEN desktop records with icons THEN Essentials, Pinned Tabs and Desktop Tabs get them`() {
        val server = FakeSyncServer()
        val icons = IconStore(folder.root)
        val zenSync =
            ZenSync(
                { server.credentials },
                server,
                Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap())),
                Essentials(InMemoryEssentialsRepository(), MutableStateFlow(emptyMap())),
                InMemorySyncBaseRepository(),
                InMemoryZenSyncSettingsStore(),
                icons = icons,
            )
        val out = ByteArrayOutputStream()
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, out)
        val icon = "data:image/png;base64," + Base64.getEncoder().encodeToString(out.toByteArray())
        fun withIcon(record: org.json.JSONObject) = record.apply { getJSONObject("data").put("icon", icon) }
        server.put(withIcon(ZenRecords.essential("e", "https://essential.example/")))
        server.put(ZenRecords.space("w", "Work", children = listOf("p", "n")))
        server.put(withIcon(ZenRecords.pinned("p", "https://pinned.example/", "w")))
        server.put(withIcon(ZenRecords.normal("n", "https://open.example/", "w")))
        server.put(ZenRecords.layout(listOf("w"), mapOf("default" to listOf("e"))))

        runBlocking { zenSync.firstSync(FirstSyncChoice.Replace) }

        listOf("https://essential.example/", "https://pinned.example/", "https://open.example/").forEach { assertNotNull(it, icons.get(it)) }
    }
}
