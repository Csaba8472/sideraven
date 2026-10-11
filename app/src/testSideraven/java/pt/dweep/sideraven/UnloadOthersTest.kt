/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.junit.Assert.assertEquals
import org.junit.Test

/** Spec 0006 ticket 10: Unload Others puts every other tab of the pressed one's kind to sleep. */
class UnloadOthersTest {
    private fun tab(
        id: String,
        selected: Boolean = false,
        sleeping: Boolean = false,
    ) = SidebarTab(id, id, "https://$id.example", icon = null, isSelected = selected, sleeping = sleeping)

    private fun essential(
        url: String,
        tabId: String?,
        selected: Boolean = false,
        sleeping: Boolean = false,
    ) = SidebarEssential(url, url, icon = null, linkedTabId = tabId, isSelected = selected, sleeping = sleeping)

    private fun pin(
        id: String,
        tabId: String?,
        selected: Boolean = false,
        sleeping: Boolean = false,
    ) = SidebarPinnedTab(id, id, "https://$id.example", icon = null, linkedTabId = tabId, isSelected = selected, sleeping = sleeping)

    // The Sidebar state holds only the current Space's tabs and pins, in the current Browsing Mode.
    private val state =
        SidebarUiState(
            isVisible = true,
            isPrivate = false,
            tabs = listOf(tab("a", selected = true), tab("b"), tab("c"), tab("asleep", sleeping = true)),
            inactive = SidebarInactiveSection(listOf(tab("old")), expanded = false),
            essentials = listOf(essential("mail", "tm"), essential("docs", "td"), essential("ghost", null), essential("cal", "tc", sleeping = true)),
            pinned = listOf(pin("p1", "tp1"), pin("p2", "tp2", selected = false), pin("p3", null)),
        )

    @Test
    fun `GIVEN a normal tab THEN the other awake normal tabs sleep, never the pressed one or the one on screen`() {
        assertEquals(listOf("c", "old"), unloadOthersTargets(state, UnloadOthersFrom.Tab("b")))
        // From the tab on screen: everything else awake.
        assertEquals(listOf("b", "c", "old"), unloadOthersTargets(state, UnloadOthersFrom.Tab("a")))
    }

    @Test
    fun `GIVEN an Essential THEN the other Essential Tabs sleep, not Ghosts or sleeping ones`() {
        assertEquals(listOf("td"), unloadOthersTargets(state, UnloadOthersFrom.Essential("mail")))
    }

    @Test
    fun `GIVEN a Pinned Tab THEN the other Pinned Tabs of the Space sleep`() {
        assertEquals(listOf("tp2"), unloadOthersTargets(state, UnloadOthersFrom.Pinned("p1")))
    }

    @Test
    fun `THEN the handler puts the targets to sleep`() {
        val slept = mutableListOf<List<String>>()
        val handler = SidebarIntentHandler(selectTab = {}, openNewTab = {}, sleepTabs = { slept += it })

        handler.handle(SidebarIntent.UnloadOthers(UnloadOthersFrom.Tab("b")), state)

        assertEquals(listOf(listOf("c", "old")), slept)
    }
}
