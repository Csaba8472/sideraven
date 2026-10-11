/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import org.mozilla.fenix.R

/**
 * Zen Browser's selectable Space and Folder icons (MPL 2.0, vendored as vector drawables).
 *
 * Zen stores one of these as `chrome://browser/skin/zen-icons/selectable/<name>.svg`; Sideraven
 * stores the same string so Spaces round-trip through Zen Sync unchanged. Any other icon string is
 * an emoji (or, from a newer Zen, something unknown, which renders a neutral fallback and is kept).
 */
object ZenIcons {
    const val PREFIX = "chrome://browser/skin/zen-icons/selectable/"
    private const val SUFFIX = ".svg"

    /** Icon name -> drawable, in picker order. */
    val all: Map<String, Int> =
        linkedMapOf(
        "airplane" to R.drawable.sideraven_zen_airplane,
        "american-football" to R.drawable.sideraven_zen_american_football,
        "baseball" to R.drawable.sideraven_zen_baseball,
        "basket" to R.drawable.sideraven_zen_basket,
        "bed" to R.drawable.sideraven_zen_bed,
        "bell" to R.drawable.sideraven_zen_bell,
        "book" to R.drawable.sideraven_zen_book,
        "bookmark" to R.drawable.sideraven_zen_bookmark,
        "briefcase" to R.drawable.sideraven_zen_briefcase,
        "brush" to R.drawable.sideraven_zen_brush,
        "bug" to R.drawable.sideraven_zen_bug,
        "build" to R.drawable.sideraven_zen_build,
        "cafe" to R.drawable.sideraven_zen_cafe,
        "call" to R.drawable.sideraven_zen_call,
        "card" to R.drawable.sideraven_zen_card,
        "chat" to R.drawable.sideraven_zen_chat,
        "checkbox" to R.drawable.sideraven_zen_checkbox,
        "circle" to R.drawable.sideraven_zen_circle,
        "cloud" to R.drawable.sideraven_zen_cloud,
        "code" to R.drawable.sideraven_zen_code,
        "coins" to R.drawable.sideraven_zen_coins,
        "construct" to R.drawable.sideraven_zen_construct,
        "cutlery" to R.drawable.sideraven_zen_cutlery,
        "egg" to R.drawable.sideraven_zen_egg,
        "extension-puzzle" to R.drawable.sideraven_zen_extension_puzzle,
        "eye" to R.drawable.sideraven_zen_eye,
        "fast-food" to R.drawable.sideraven_zen_fast_food,
        "fish" to R.drawable.sideraven_zen_fish,
        "flag" to R.drawable.sideraven_zen_flag,
        "flame" to R.drawable.sideraven_zen_flame,
        "flask" to R.drawable.sideraven_zen_flask,
        "folder" to R.drawable.sideraven_zen_folder,
        "game-controller" to R.drawable.sideraven_zen_game_controller,
        "globe" to R.drawable.sideraven_zen_globe,
        "globe-1" to R.drawable.sideraven_zen_globe_1,
        "grid-2x2" to R.drawable.sideraven_zen_grid_2x2,
        "grid-3x3" to R.drawable.sideraven_zen_grid_3x3,
        "heart" to R.drawable.sideraven_zen_heart,
        "ice-cream" to R.drawable.sideraven_zen_ice_cream,
        "image" to R.drawable.sideraven_zen_image,
        "inbox" to R.drawable.sideraven_zen_inbox,
        "key" to R.drawable.sideraven_zen_key,
        "layers" to R.drawable.sideraven_zen_layers,
        "leaf" to R.drawable.sideraven_zen_leaf,
        "lightning" to R.drawable.sideraven_zen_lightning,
        "location" to R.drawable.sideraven_zen_location,
        "lock-closed" to R.drawable.sideraven_zen_lock_closed,
        "logo-github" to R.drawable.sideraven_zen_logo_github,
        "logo-rss" to R.drawable.sideraven_zen_logo_rss,
        "logo-usd" to R.drawable.sideraven_zen_logo_usd,
        "mail" to R.drawable.sideraven_zen_mail,
        "map" to R.drawable.sideraven_zen_map,
        "megaphone" to R.drawable.sideraven_zen_megaphone,
        "moon" to R.drawable.sideraven_zen_moon,
        "music" to R.drawable.sideraven_zen_music,
        "navigate" to R.drawable.sideraven_zen_navigate,
        "nuclear" to R.drawable.sideraven_zen_nuclear,
        "page" to R.drawable.sideraven_zen_page,
        "palette" to R.drawable.sideraven_zen_palette,
        "paw" to R.drawable.sideraven_zen_paw,
        "people" to R.drawable.sideraven_zen_people,
        "pizza" to R.drawable.sideraven_zen_pizza,
        "planet" to R.drawable.sideraven_zen_planet,
        "present" to R.drawable.sideraven_zen_present,
        "rocket" to R.drawable.sideraven_zen_rocket,
        "school" to R.drawable.sideraven_zen_school,
        "shapes" to R.drawable.sideraven_zen_shapes,
        "shirt" to R.drawable.sideraven_zen_shirt,
        "skull" to R.drawable.sideraven_zen_skull,
        "square" to R.drawable.sideraven_zen_square,
        "squares" to R.drawable.sideraven_zen_squares,
        "star" to R.drawable.sideraven_zen_star,
        "star-1" to R.drawable.sideraven_zen_star_1,
        "stats-chart" to R.drawable.sideraven_zen_stats_chart,
        "sun" to R.drawable.sideraven_zen_sun,
        "tada" to R.drawable.sideraven_zen_tada,
        "terminal" to R.drawable.sideraven_zen_terminal,
        "ticket" to R.drawable.sideraven_zen_ticket,
        "time" to R.drawable.sideraven_zen_time,
        "trash" to R.drawable.sideraven_zen_trash,
        "triangle" to R.drawable.sideraven_zen_triangle,
        "video" to R.drawable.sideraven_zen_video,
        "volume-high" to R.drawable.sideraven_zen_volume_high,
        "wallet" to R.drawable.sideraven_zen_wallet,
        "warning" to R.drawable.sideraven_zen_warning,
        "water" to R.drawable.sideraven_zen_water,
        "weight" to R.drawable.sideraven_zen_weight,
        )

    /** The stored form of the Zen icon [name]. */
    fun iconFor(name: String): String = PREFIX + name + SUFFIX

    /** The Zen icon name in [icon], or null when [icon] isn't a Zen icon reference. */
    fun nameOf(icon: String): String? =
        icon.trim().takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)

    /** The drawable for [icon] when it's a known Zen icon. */
    fun drawableFor(icon: String): Int? = nameOf(icon)?.let { all[it] }

    /** Whether [icon] references Zen's icon set at all (known or not). */
    fun isZenIcon(icon: String): Boolean = nameOf(icon) != null
}
