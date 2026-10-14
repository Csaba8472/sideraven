/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import pt.dweep.sideraven.theme.SideravenTheme
import pt.dweep.sideraven.ui.SidebarContent
import pt.dweep.sideraven.ui.SidebarTestTag

/** Zen's icon set for Spaces and Folders. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1280dp-h800dp")
class ZenIconsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `THEN a Zen icon is stored the way Zen stores it and resolves to its drawable`() {
        val icon = ZenIcons.iconFor("airplane")

        assertEquals("chrome://browser/skin/zen-icons/selectable/airplane.svg", icon)
        assertEquals("airplane", ZenIcons.nameOf(icon))
        assertNotNull(ZenIcons.drawableFor(icon))
        assertTrue(ZenIcons.all.size > 80)
    }

    @Test
    fun `GIVEN an icon from a newer Zen THEN it has no drawable but is kept unchanged`() {
        val unknown = "chrome://browser/skin/zen-icons/selectable/hovercraft.svg"
        assertTrue(ZenIcons.isZenIcon(unknown))
        assertNull(ZenIcons.drawableFor(unknown))

        val spaces = Spaces(InMemorySpacesRepository(), pinLinks = MutableStateFlow(emptyMap()))
        val space = spaces.create("Future", unknown)
        assertEquals(unknown, SpacesCodec.decode(SpacesCodec.encode(spaces.data))!!.spaces.first { it.id == space.id }.icon)
    }

    @Test
    fun `THEN an emoji is not a Zen icon`() {
        assertNull(ZenIcons.nameOf("💼"))
    }

    @Test
    fun `WHEN a Zen icon is picked for a new Space THEN its stored form is sent`() {
        val intents = mutableListOf<SidebarIntent>()
        val state =
            SidebarUiState(
                isVisible = true,
                isPrivate = false,
                tabs = emptyList(),
                mode = SidebarMode.Persistent,
                spaces = listOf(SidebarSpace("h", "Home", "🏠", "ember", isCurrent = true)),
            )
        composeRule.setContent { SideravenTheme { SidebarContent(state = state, onIntent = { intents += it }) } }

        composeRule.onNodeWithTag(SidebarTestTag.NEW_SPACE).performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_NAME_FIELD).performTextInput("Travel")
        composeRule.onNodeWithTag(SidebarTestTag.ZEN_ICONS_TAB).performClick()
        composeRule.onNodeWithContentDescription("airplane").performClick()
        composeRule.onNodeWithTag(SidebarTestTag.SPACE_CONFIRM).performClick()

        assertEquals(listOf<SidebarIntent>(SidebarIntent.CreateSpace("Travel", ZenIcons.iconFor("airplane"))), intents)
    }
}
