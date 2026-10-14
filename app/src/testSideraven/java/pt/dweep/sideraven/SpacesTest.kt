/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.RestoreCompleteAction
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Home Space, assignment, per-Space Tab List, switching and creating. */
@RunWith(AndroidJUnit4::class)
class SpacesTest {
    private val repository = InMemorySpacesRepository()
    private val spaces = Spaces(repository)
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private val opened = mutableListOf<Boolean>()
    private var nextId = 0

    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = { private ->
                opened += private
                store.dispatch(TabListAction.AddTabAction(createTab("about:home", id = "new${nextId++}", private = private), select = true))
            },
            switchSpace = { id ->
                spaces.switchTo(id)
                spaces.data.switchTarget(id, store.state.tabs)
            },
            createSpace = { name, icon, accent, local -> spaces.create(name, icon, accent, local) },
        )

    private fun add(
        id: String,
        select: Boolean = false,
        parent: String? = null,
        private: Boolean = false,
    ) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", id = id, parentId = parent, private = private), select = select))

    private fun state(isPrivate: Boolean = false) =
        store.state.toSidebarUiState(
            isPrivate = isPrivate,
            tier = SidebarTier.Tablet,
            screen = SidebarScreen.Browser,
            spaces = spaces.data,
        )

    private fun listed(isPrivate: Boolean = false) = state(isPrivate).tabs.map { it.id }

    @Test
    fun `GIVEN tabs restored before Spaces existed WHEN restore completes THEN they are all in Home`() {
        store.dispatch(TabListAction.AddMultipleTabsAction(listOf(createTab("https://a.example", id = "a"), createTab("https://b.example", id = "b"))))
        store.dispatch(RestoreCompleteAction)

        val data = spaces.data
        assertEquals(listOf("Home"), data.spaces.map { it.name })
        assertEquals(data.current.id, data.assignments["a"])
        assertEquals(data.current.id, data.assignments["b"])
        assertEquals(listOf("b", "a"), listed())
    }

    @Test
    fun `WHEN a Space is created THEN it becomes current with a New Tab, and the Tab List shows only its tabs`() {
        add("a", select = true)

        handler.handle(SidebarIntent.CreateSpace("Work", "💼"), state())

        val ui = state()
        assertEquals("Work", ui.currentSpace?.name)
        assertEquals(listOf("Home", "Work"), ui.spaces.map { it.name })
        assertEquals(listOf("new0"), ui.tabs.map { it.id })
        assertEquals(listOf(false), opened)
    }

    @Test
    fun `GIVEN Zen Sync is on WHEN a Space is created THEN it starts as a Local Space, and only then`() {
        handler.handle(SidebarIntent.CreateSpace("Work", "💼"), state().copy(zenSyncOn = true))
        handler.handle(SidebarIntent.CreateSpace("Play", "🎮"), state())

        assertEquals(listOf(false, true, false), spaces.data.spaces.map { it.isLocal })
    }

    @Test
    fun `WHEN tabs are opened THEN new ones join the current Space and child tabs join their parent's Space`() {
        add("home1", select = true)
        handler.handle(SidebarIntent.CreateSpace("Work", "💼"), state())
        add("work1")
        add("child", parent = "home1")

        assertEquals(listOf("work1", "new0"), listed())
        val home = spaces.data.spaces.first().id
        handler.handle(SidebarIntent.SwitchSpace(home), state())
        assertEquals(listOf("child", "home1"), listed())
    }

    @Test
    fun `WHEN switching back to a Space THEN its last selected tab is selected again`() {
        add("h1")
        add("h2", select = true)
        add("h3")
        store.dispatch(TabListAction.SelectTabAction("h2"))
        val home = spaces.data.current.id
        handler.handle(SidebarIntent.CreateSpace("Work", "💼"), state())

        handler.handle(SidebarIntent.SwitchSpace(home), state())

        assertEquals("h2", store.state.selectedTabId)
        assertEquals(home, state().currentSpace?.id)
    }

    @Test
    fun `WHEN switching to a Space with no tabs THEN a New Tab opens there`() {
        add("h1", select = true)
        val work = spaces.create("Work", "💼")
        spaces.switchTo(spaces.data.spaces.first().id)

        handler.handle(SidebarIntent.SwitchSpace(work.id), state())

        assertEquals(listOf(false), opened)
        assertEquals(listOf("new0"), listed())
    }

    @Test
    fun `WHEN the selected tab is closed THEN selection stays inside its Space`() {
        add("h1")
        val home = spaces.data.current.id
        val work = spaces.create("Work", "💼")
        add("w1")
        add("w2", select = true)
        spaces.switchTo(home)
        add("h2")
        spaces.switchTo(work.id)
        store.dispatch(TabListAction.SelectTabAction("w2"))

        store.dispatch(TabListAction.RemoveTabAction("w2"))

        assertEquals("w1", store.state.selectedTabId)
    }

    @Test
    fun `THEN private tabs belong to no Space and Private Mode shows them all with no Space row`() {
        add("h1", select = true)
        spaces.create("Work", "💼")
        add("p1", private = true)
        add("p2", private = true)

        assertNull(spaces.data.assignments["p1"])
        val ui = state(isPrivate = true)
        assertEquals(listOf("p2", "p1"), ui.tabs.map { it.id })
        assertTrue(ui.spaces.isEmpty())
    }

    @Test
    fun `WHEN restore completes THEN Spaces forget tabs that no longer exist`() {
        add("a")
        repository.save(spaces.data.copy(assignments = spaces.data.assignments + ("gone" to spaces.data.current.id)))

        store.dispatch(RestoreCompleteAction)

        assertEquals(setOf("a"), spaces.data.assignments.keys)
    }

    @Test
    fun `THEN Spaces survive storage unchanged`() {
        add("a")
        spaces.create("Work", "💼")
        val data = spaces.data.copy(spaces = spaces.data.spaces.map { it.copy(extra = """{"theme":{"type":"gradient"}}""") })

        assertEquals(data, SpacesCodec.decode(SpacesCodec.encode(data)))
    }
}
