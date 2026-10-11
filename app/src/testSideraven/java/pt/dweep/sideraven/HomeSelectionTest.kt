/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.sync.DesktopTab

/** Spec 0008 ticket 01: choosing something in the Sidebar shows it, even from Fenix's home screen. */
@RunWith(AndroidJUnit4::class)
class HomeSelectionTest {
    private val shown = mutableListOf<String>()
    private val handler =
        SidebarIntentHandler(
            selectTab = { shown += "select:$it" },
            openNewTab = { shown += "new" },
            openEssential = { url, _ -> shown += "essential:$url" },
            openPinnedTab = { id, _ -> shown += "pin:$id" },
            openUrl = { shown += "url:$it" },
            showBrowser = { shown += "browser" },
        )

    private val state =
        SidebarUiState(
            isVisible = true,
            isPrivate = false,
            tabs = listOf(SidebarTab("t", "Tab", "https://t.example", icon = null, isSelected = false)),
            mode = SidebarMode.Persistent,
            essentials = listOf(SidebarEssential("https://e.example", "E", icon = null, linkedTabId = null, isSelected = false)),
            pinned = listOf(SidebarPinnedTab("p", "P", "https://p.example", icon = null, linkedTabId = "pt", isSelected = false)),
            desktopTabs = SidebarDesktopTabs(listOf(DesktopTab("https://d.example", "D")), expanded = true),
        )

    @Test
    fun `WHEN a tab, Essential, Pinned Tab or Desktop Tab is chosen THEN the browser shows it`() {
        handler.handle(SidebarIntent.SelectTab("t"), state)
        handler.handle(SidebarIntent.SelectEssential("https://e.example"), state)
        handler.handle(SidebarIntent.SelectPinnedTab("p"), state)
        handler.handle(SidebarIntent.OpenDesktopTab("https://d.example"), state)

        assertEquals(
            listOf("select:t", "browser", "essential:https://e.example", "browser", "select:pt", "browser", "url:https://d.example", "browser"),
            shown,
        )
    }

    @Test
    fun `WHEN a new tab is opened THEN New Tab Behaviour decides, not the browser`() {
        handler.handle(SidebarIntent.NewTab, state)

        assertEquals(false, "browser" in shown)
    }
}
