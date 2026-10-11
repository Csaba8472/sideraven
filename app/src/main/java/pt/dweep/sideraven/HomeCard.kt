/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag

/**
 * Fenix's home screen as a card (spec 0006): while the content is framed, the area between the
 * home toolbars gets a rounded, slightly different tone, so home reads like a web page inside the
 * frame. The tint is translucent, so a wallpaper still shows. Fenix's home calls this from one
 * `SIDERAVEN-HOOK`, around its content.
 */
@Composable
fun HomeCard(
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val look by SidebarGate.frameLook.collectAsState()
    Box(Modifier.fillMaxSize()) {
        if (look.framed) {
            val radius = with(LocalDensity.current) { look.radiusPx.toDp() }
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .clip(RoundedCornerShape(radius))
                    .background(if (isSystemInDarkTheme()) Color.Black.copy(alpha = DARK_TINT) else Color.White.copy(alpha = LIGHT_TINT))
                    .testTag(HOME_CARD_TAG),
            )
        }
        content()
    }
}

const val HOME_CARD_TAG = "sideraven.homeCard"

private const val DARK_TINT = 0.28f
private const val LIGHT_TINT = 0.7f
