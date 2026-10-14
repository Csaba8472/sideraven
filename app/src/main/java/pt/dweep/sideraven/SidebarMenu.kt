/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.WeakHashMap

/** Where an Optional Control lives: a button in Sidebar Controls, or an entry in the Main Menu. */
enum class ControlPlace { InSidebar, InMenu }

/** The Sidebar Controls whose Control Place the user chooses. */
enum class OptionalControl { SyncedTabs, Toggle, LayoutButton }

/** The Control Place of each Optional Control. */
data class ControlPlaces(
    val syncedTabs: ControlPlace = ControlPlace.InMenu,
    val toggle: ControlPlace = ControlPlace.InSidebar,
    val layoutButton: ControlPlace = ControlPlace.InSidebar,
) {
    operator fun get(control: OptionalControl): ControlPlace =
        when (control) {
            OptionalControl.SyncedTabs -> syncedTabs
            OptionalControl.Toggle -> toggle
            OptionalControl.LayoutButton -> layoutButton
        }
}

/** The entries Sideraven adds to the Main Menu, each doing what its Sidebar button does. */
enum class SideravenMenuEntry { SyncedTabs, ChangeSidebarMode, MoveAddressBar, ArrangeSidebar }

/**
 * The Sidebar intent an entry hands to this window's Sidebar; null for Synced Tabs, which opens
 * Fenix's synced-tabs page itself, so it works where there's no Sidebar.
 */
val SideravenMenuEntry.sidebarIntent: SidebarIntent?
    get() =
        when (this) {
            SideravenMenuEntry.SyncedTabs -> null
            SideravenMenuEntry.ChangeSidebarMode -> SidebarIntent.ToggleMode
            SideravenMenuEntry.MoveAddressBar -> SidebarIntent.ToggleAddressBarLayout
            SideravenMenuEntry.ArrangeSidebar -> SidebarIntent.StartArranging
        }

/** What the Main Menu needs to know about the Sidebar on screen. */
data class SidebarMenuInfo(
    /** What the Toggle switches to; null when the tier has one mode. */
    val nextMode: SidebarMode?,
    /** Whether the layout button could move the address here. */
    val layoutMovable: Boolean = false,
    /** The layout the user chose, so the entry can say where the address goes. */
    val chosenLayout: AddressBarLayout = AddressBarLayout.Top,
    /** The Sidebar Mode shown (Arrange sidebar is offered in Collapsed Mode only). */
    val mode: SidebarMode? = null,
    /** Whether there's a section to arrange: Essentials or Pinned Tabs (never in private browsing). */
    val arrangeable: Boolean = false,
)

/**
 * The Main Menu's Sideraven entries for these Control Places, given the Sidebar on screen ([sidebar],
 * null when there's none). An entry is offered only where its button could act.
 */
fun sideravenMenuEntries(
    places: ControlPlaces,
    sidebar: SidebarMenuInfo?,
): List<SideravenMenuEntry> =
    buildList {
        // Synced Tabs needs no Sidebar: it opens Fenix's synced-tabs page.
        if (places.syncedTabs == ControlPlace.InMenu) add(SideravenMenuEntry.SyncedTabs)
        if (sidebar == null) return@buildList
        if (places.toggle == ControlPlace.InMenu && sidebar.nextMode != null) add(SideravenMenuEntry.ChangeSidebarMode)
        if (places.layoutButton == ControlPlace.InMenu && sidebar.layoutMovable) add(SideravenMenuEntry.MoveAddressBar)
        if (sidebar.mode == SidebarMode.Collapsed && sidebar.arrangeable) add(SideravenMenuEntry.ArrangeSidebar)
    }

/**
 * One window's link between its Main Menu (Fenix's dialog, hooked) and its Sidebar: the Sidebar
 * publishes what the menu needs to know, and the menu's Sideraven entries come back as Sidebar
 * intents. One per activity, so split-screen windows never answer each other's menus.
 */
class SidebarMenuLink {
    private val info = MutableStateFlow<SidebarMenuInfo?>(null)
    private val intents = MutableSharedFlow<SidebarIntent>(extraBufferCapacity = 8)

    /** The Sidebar on screen in this window, or null while there's none. */
    val sidebar: StateFlow<SidebarMenuInfo?> = info.asStateFlow()

    /** Main Menu entries chosen, for this window's Sidebar to handle. */
    val requests: SharedFlow<SidebarIntent> = intents.asSharedFlow()

    /** Called by the Sidebar whenever what it offers changes; null when it stops being shown. */
    fun publish(sidebar: SidebarMenuInfo?) {
        info.value = sidebar
    }

    /** Hands [entry] to this window's Sidebar; false for an entry that doesn't go through it. */
    fun choose(entry: SideravenMenuEntry): Boolean = entry.sidebarIntent?.let(intents::tryEmit) ?: false
}

/** The [SidebarMenuLink] of each window. */
object SidebarMenu {
    private val links = WeakHashMap<Activity, SidebarMenuLink>()

    /** The link of the window [context] belongs to; null outside an activity. */
    fun of(context: Context): SidebarMenuLink? =
        activityOf(context)?.let { activity -> synchronized(links) { links.getOrPut(activity) { SidebarMenuLink() } } }

    /** The activity [context] belongs to, through any wrappers (a dialog's context, for one). */
    fun activityOf(context: Context): Activity? = context.activity()

    private tailrec fun Context.activity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.activity()
            else -> null
        }
}
