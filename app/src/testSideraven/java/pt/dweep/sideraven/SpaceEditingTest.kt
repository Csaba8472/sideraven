/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.action.TabListAction
import mozilla.components.browser.state.state.createTab
import mozilla.components.browser.state.store.BrowserStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pt.dweep.sideraven.theme.SpaceAccents
import pt.dweep.sideraven.theme.zenColorScheme

/** Edit, reorder and delete Spaces; the accent palette. */
@RunWith(AndroidJUnit4::class)
class SpaceEditingTest {
    private val spaces = Spaces(InMemorySpacesRepository())
    private val store = BrowserStore(middleware = listOf(NewTabPositionMiddleware(), SpacesMiddleware { spaces }))
    private var undo: (() -> Unit)? = null

    /** Mirrors the app: close the Space's tabs, delete it, keep an Undo that restores both. */
    private val handler =
        SidebarIntentHandler(
            selectTab = { store.dispatch(TabListAction.SelectTabAction(it)) },
            openNewTab = {},
            editSpace = spaces::edit,
            moveSpace = spaces::move,
            deleteSpace = { id ->
                val closing = store.state.tabs.filter { !it.content.private && spaces.data.spaceOf(it.id) == id }
                val snapshot = spaces.delete(id)
                if (snapshot != null) {
                    store.dispatch(TabListAction.RemoveTabsAction(closing.map { it.id }))
                    undo = {
                        spaces.restore(snapshot)
                        store.dispatch(TabListAction.AddMultipleTabsAction(closing))
                    }
                }
            },
        )

    private fun add(id: String) = store.dispatch(TabListAction.AddTabAction(createTab("https://$id.example", id = id)))

    private fun state() =
        store.state.toSidebarUiState(isPrivate = false, tier = SidebarTier.Tablet, screen = SidebarScreen.Browser, spaces = spaces.data)

    @Test
    fun `WHEN a Space is edited THEN its name, icon and accent change`() {
        val home = spaces.data.current.id

        handler.handle(SidebarIntent.EditSpace(home, "Personal", "🌱", "teal"), state())

        val space = state().currentSpace!!
        assertEquals(listOf("Personal", "🌱", "teal"), listOf(space.name, space.icon, space.accent))
    }

    @Test
    fun `WHEN a Space icon is dragged THEN the order changes`() {
        val home = spaces.data.current.id
        spaces.create("Work", "💼")
        spaces.create("Reading", "📚")

        handler.handle(SidebarIntent.MoveSpace(home, 2), state())

        assertEquals(listOf("Work", "Reading", "Home"), state().spaces.map { it.name })
    }

    @Test
    fun `WHEN the current Space is deleted THEN its tabs close, its neighbour becomes current, and Undo brings both back`() {
        add("h1")
        val home = spaces.data.current.id
        val work = spaces.create("Work", "💼")
        add("w1")
        add("w2")

        handler.handle(SidebarIntent.DeleteSpace(work.id), state())

        assertEquals(listOf("Home"), state().spaces.map { it.name })
        assertEquals(home, state().currentSpace?.id)
        assertEquals(listOf("h1"), store.state.tabs.map { it.id })

        undo!!()

        assertEquals(listOf("Home", "Work"), state().spaces.map { it.name })
        assertEquals(work.id, state().currentSpace?.id)
        assertEquals(listOf("w2", "w1"), state().tabs.map { it.id })
    }

    @Test
    fun `GIVEN only one Space WHEN Delete is requested THEN nothing happens`() {
        add("h1")

        handler.handle(SidebarIntent.DeleteSpace(spaces.data.current.id), state())

        assertNull(undo)
        assertEquals(1, state().spaces.size)
        assertEquals(listOf("h1"), state().tabs.map { it.id })
    }

    @Test
    fun `THEN every accent keeps 4,5 to 1 against the Sidebar surface in both themes, and white text on the light one`() {
        val lightSurface = zenColorScheme(dark = false).surface
        val darkSurface = zenColorScheme(dark = true).surface
        SpaceAccents.all.forEach { accent ->
            assertTrue("${accent.key} light", contrast(accent.light, lightSurface) >= 4.5f)
            assertTrue("${accent.key} on-light", contrast(accent.light, Color.White) >= 4.5f)
            assertTrue("${accent.key} dark", contrast(accent.dark, darkSurface) >= 4.5f)
        }
    }

    @Test
    fun `THEN an unknown accent key falls back to the default instead of failing`() {
        assertEquals(DEFAULT_SPACE_ACCENT, SpaceAccents.of("from-a-newer-version").key)
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val l1 = a.luminance() + 0.05f
        val l2 = b.luminance() + 0.05f
        return maxOf(l1, l2) / minOf(l1, l2)
    }
}
