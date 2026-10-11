/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import io.mockk.mockk
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.MediaSessionState
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.mediasession.MediaSession
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ticket 15: audio state flows from the browser store into Tab List items. */
class AudioIndicatorTest {
    private fun media(
        playback: MediaSession.PlaybackState,
        muted: Boolean = false,
    ) = MediaSessionState(controller = mockk(relaxed = true), playbackState = playback, muted = muted)

    private val store =
        BrowserStore().apply {
            dispatch(TabListAction.AddTabAction(createTab("https://silent.example", id = "silent"), select = true))
            dispatch(
                TabListAction.AddTabAction(
                    createTab("https://music.example", id = "music").copy(mediaSessionState = media(MediaSession.PlaybackState.PLAYING)),
                ),
            )
            dispatch(
                TabListAction.AddTabAction(
                    createTab("https://muted.example", id = "muted")
                        .copy(mediaSessionState = media(MediaSession.PlaybackState.PLAYING, muted = true)),
                ),
            )
            dispatch(
                TabListAction.AddTabAction(
                    createTab("https://paused.example", id = "paused").copy(mediaSessionState = media(MediaSession.PlaybackState.PAUSED)),
                ),
            )
        }

    @Test
    fun `THEN playing, muted and silent tabs are told apart`() {
        val audio =
            store.state
                .toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser)
                .tabs
                .associate { it.id to it.audio }

        assertEquals(TabAudio.None, audio["silent"])
        assertEquals(TabAudio.Playing, audio["music"])
        assertEquals(TabAudio.Muted, audio["muted"])
        assertEquals(TabAudio.None, audio["paused"])
    }

    @Test
    fun `WHEN the audio indicator is tapped THEN that tab is muted or unmuted`() {
        val toggled = mutableListOf<String>()
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, toggleMute = { toggled += it })

        handler.handle(
            SidebarIntent.ToggleMute("music"),
            store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser),
        )

        assertEquals(listOf("music"), toggled)
    }
}
