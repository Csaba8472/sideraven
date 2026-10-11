/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.SecurityInfo
import mozilla.components.browser.state.state.TabSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.mediasession.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Spec 0009 ticket 03: the Sidebar follows only what it shows. */
@RunWith(AndroidJUnit4::class)
class SidebarViewTest {
    private object Controller : MediaSession.Controller {
        override fun pause() = Unit

        override fun stop() = Unit

        override fun play() = Unit

        override fun seekTo(
            time: Double,
            fast: Boolean,
        ) = Unit

        override fun seekForward() = Unit

        override fun seekBackward() = Unit

        override fun nextTrack() = Unit

        override fun previousTrack() = Unit

        override fun skipAd() = Unit

        override fun muteAudio(mute: Boolean) = Unit
    }

    private val icon: Bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
    private val now = 1_000_000L

    private val browser =
        BrowserState(
            tabs =
                listOf(
                    createTab("https://one.example/a", id = "one", title = "One", lastAccess = now),
                    createTab("https://two.example/b", id = "two", title = "Two", lastAccess = now - 10_000_000L, createdAt = now - 10_000_000L)
                        .copy(mediaSessionState = MediaSessionState(Controller, playbackState = MediaSession.PlaybackState.PLAYING)),
                    createTab("https://three.example", id = "three", title = "", parentId = "one", lastAccess = now),
                    createTab("https://essential.example", id = "ess", title = "Essential", lastAccess = now),
                    createTab("https://private.example", id = "p", private = true, lastAccess = now),
                ),
            selectedTabId = "one",
            restoreComplete = true,
        )

    private fun BrowserState.change(
        id: String = "one",
        block: (TabSessionState) -> TabSessionState,
    ) = copy(tabs = tabs.map { if (it.id == id) block(it) else it })

    @Test
    fun `WHEN a page loads, changes security or history THEN the Sidebar's view is unchanged`() {
        val view = browser.sidebarView()
        listOf<(TabSessionState) -> TabSessionState>(
            { it.copy(content = it.content.copy(progress = 42, loading = true)) },
            { it.copy(content = it.content.copy(securityInfo = SecurityInfo.Secure(host = "one.example"))) },
            { it.copy(content = it.content.copy(canGoBack = true, canGoForward = true)) },
            { it.copy(content = it.content.copy(firstContentfulPaint = true, searchTerms = "q")) },
            { it.copy(mediaSessionState = MediaSessionState(Controller, playbackState = MediaSession.PlaybackState.PLAYING, timestamp = 5)) },
        ).forEachIndexed { i, change ->
            val id = if (i == 4) "two" else "one"
            assertEquals("change $i", view, browser.change(id, change).sidebarView())
        }
    }

    @Test
    fun `WHEN something the Sidebar shows changes THEN its view changes`() {
        val view = browser.sidebarView()
        listOf<BrowserState>(
            browser.change { it.copy(content = it.content.copy(title = "Renamed")) },
            browser.change { it.copy(content = it.content.copy(icon = icon)) },
            browser.change { it.copy(content = it.content.copy(url = "https://one.example/next")) },
            browser.change { it.copy(lastAccess = now + 1) },
            browser.change { it.copy(parentId = "two") },
            browser.change("two") { it.copy(mediaSessionState = it.mediaSessionState?.copy(muted = true)) },
            browser.copy(selectedTabId = "two"),
            browser.copy(restoreComplete = false),
            browser.copy(tabs = browser.tabs.dropLast(1)),
        ).forEachIndexed { i, changed -> assertNotEquals("change $i", view, changed.sidebarView()) }
    }

    @Test
    fun `THEN the Sidebar built from its view is the one built from the whole state`() {
        val spaces = SpacesData.initial()
        val essentials = listOf(Essential("https://essential.example", "Essential"))
        fun build(
            state: BrowserState,
            private: Boolean,
        ) = state.toSidebarUiState(
            isPrivate = private,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            chosenMode = SidebarMode.Persistent,
            essentials = essentials,
            essentialLinks = mapOf("https://essential.example" to "ess"),
            inactiveTabsEnabled = true,
            maxActiveTime = 5_000_000L,
            now = now,
            addressBarLayout = AddressBarLayout.Sidebar,
            spaces = spaces,
        )
        val loading =
            browser.change {
                it.copy(content = it.content.copy(progress = 60, loading = true, canGoBack = true, securityInfo = SecurityInfo.Secure(host = "one.example")))
            }

        listOf(false, true).forEach { private ->
            val whole = build(loading, private)
            val fromView = build(loading.sidebarView(), private).withLiveAddress(loading.selectedPageView(), SidebarScreen.Browser)
            assertEquals("private=$private", whole, fromView)
        }
        // The fixture exercises what the view must keep.
        val whole = build(loading, private = false)
        assertEquals(listOf("two"), whole.inactive?.tabs?.map { it.id })
        assertEquals(TabAudio.Playing, whole.inactive?.tabs?.single()?.audio)
        assertEquals(60, whole.address?.progress)
    }

    @Test
    fun `WHEN only the page's progress changes THEN only the selected page view changes`() {
        val loading = browser.change { it.copy(content = it.content.copy(progress = 10, loading = true)) }
        val further = loading.change { it.copy(content = it.content.copy(progress = 80)) }

        assertEquals(loading.sidebarView(), further.sidebarView())
        assertNotEquals(loading.selectedPageView(), further.selectedPageView())
    }
}
