/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.selector.findTab
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Spaces ticket 13: Folders in a Space's pinned block. */
@RunWith(AndroidJUnit4::class)
class FoldersTest {
    private val repository = InMemorySpacesRepository()
    private val spaces = Spaces(repository, pinLinks = MutableStateFlow(emptyMap()))
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))

    private val handler =
        SidebarIntentHandler(
            selectTab = {},
            openNewTab = {},
            pinToSpace = { id -> spaces.pinToSpace(store.state.findTab(id)!!) },
            folders = FolderActions.of(spaces),
        )

    private fun state() =
        store.state.toSidebarUiState(
            isPrivate = false,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            spaces = spaces.data,
            pinnedLinks = spaces.pinnedLinks.value,
        )

    private fun pin(vararg ids: String) {
        ids.forEach { id ->
            store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", title = id, id = id)))
            handler.handle(SidebarIntent.PinToSpace(id), state())
        }
    }

    private fun pinId(title: String) = state().pinned.first { it.title == title }.id

    private fun rows() =
        state().pinnedRows.map { row ->
            when (row) {
                is SidebarPinnedRow.FolderRow -> "${"  ".repeat(row.depth)}[${row.folder.name}]"
                is SidebarPinnedRow.PinRow -> "${"  ".repeat(row.depth)}${row.pin.title}"
            }
        }

    @Test
    fun `WHEN a Folder is created and pins are moved in THEN they show inside it, before the loose pins`() {
        pin("mail", "docs", "news")
        handler.handle(SidebarIntent.CreateFolder("Work", "💼"), state())
        val work = state().folders.single().id

        handler.handle(SidebarIntent.MoveIntoFolder(pinId("mail"), work), state())
        handler.handle(SidebarIntent.MoveIntoFolder(pinId("docs"), work), state())

        assertEquals(listOf("[Work]", "  mail", "  docs", "news"), rows())
        assertEquals(2, state().folders.single().count)
    }

    @Test
    fun `WHEN a Folder is collapsed THEN its pins are hidden, and it stays collapsed after a restart`() {
        pin("mail")
        handler.handle(SidebarIntent.CreateFolder("Work", "💼"), state())
        val work = state().folders.single().id
        handler.handle(SidebarIntent.MoveIntoFolder(pinId("mail"), work), state())

        handler.handle(SidebarIntent.ToggleFolder(work), state())

        assertEquals(listOf("[Work]"), rows())
        assertTrue(SpacesCodec.decode(SpacesCodec.encode(spaces.data))!!.collapsedFolders.contains(work))
    }

    @Test
    fun `WHEN a pin is taken out of its Folder THEN it returns to the top level`() {
        pin("mail")
        handler.handle(SidebarIntent.CreateFolder("Work", "💼"), state())
        val work = state().folders.single().id
        handler.handle(SidebarIntent.MoveIntoFolder(pinId("mail"), work), state())

        handler.handle(SidebarIntent.MoveIntoFolder(pinId("mail"), null), state())

        assertEquals(listOf("[Work]", "mail"), rows())
    }

    @Test
    fun `WHEN a Folder is deleted keeping its pins THEN they move out, and removing them unpins them`() {
        pin("mail", "docs")
        handler.handle(SidebarIntent.CreateFolder("A", "📁"), state())
        handler.handle(SidebarIntent.CreateFolder("B", "📁"), state())
        val (a, b) = state().folders.map { it.id }
        handler.handle(SidebarIntent.MoveIntoFolder(pinId("mail"), a), state())
        handler.handle(SidebarIntent.MoveIntoFolder(pinId("docs"), b), state())

        handler.handle(SidebarIntent.DeleteFolder(a, keepPins = true), state())
        handler.handle(SidebarIntent.DeleteFolder(b, keepPins = false), state())

        assertTrue(state().folders.isEmpty())
        assertEquals(listOf("mail"), state().pinned.map { it.title })
    }

    @Test
    fun `GIVEN nested Folders (from Zen Sync) THEN they show nested and count toward the cap`() {
        pin("mail", "docs")
        val outer = spaces.createFolder("Outer")
        val inner = Folder(newPinId(), spaces.data.current.id, "Inner", parentId = outer.id)
        repository.save(spaces.data.copy(folders = spaces.data.folders + inner))
        spaces.moveIntoFolder(pinId("mail"), inner.id)

        assertEquals(listOf("[Outer]", "  [Inner]", "    mail", "docs"), rows())
        assertEquals(1, state().folders.first { it.name == "Outer" }.count)
        assertEquals(2, state().spaces.single().pinnedCount)
    }

    @Test
    fun `WHEN Folders are moved THEN their order changes, and edits rename them`() {
        handler.handle(SidebarIntent.CreateFolder("A", "📁"), state())
        handler.handle(SidebarIntent.CreateFolder("B", "📁"), state())
        val b = state().folders.last().id

        handler.handle(SidebarIntent.MoveFolder(b, -1), state())
        handler.handle(SidebarIntent.EditFolder(b, "Reading", "📚"), state())

        assertEquals(listOf("Reading", "A"), state().folders.map { it.name })
    }

    @Test
    fun `THEN Folders survive storage with their extra data`() {
        spaces.createFolder("Live")
        val data = spaces.data.copy(folders = spaces.data.folders.map { it.copy(extra = """{"live":{"type":"rss"}}""") })

        assertEquals(data, SpacesCodec.decode(SpacesCodec.encode(data)))
    }
}
