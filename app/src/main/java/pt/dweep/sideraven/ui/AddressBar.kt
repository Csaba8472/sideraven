/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package pt.dweep.sideraven.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mozilla.fenix.R
import pt.dweep.sideraven.AddressPin
import pt.dweep.sideraven.AddressSecurity
import pt.dweep.sideraven.SidebarAddress
import pt.dweep.sideraven.SidebarIntent
import pt.dweep.sideraven.theme.SideravenMotion
import mozilla.components.ui.icons.R as iconsR

/**
 * The Sidebar Address Bar (CONTEXT.md): the selected tab's domain with its security icon and a
 * loading line, and back, forward and reload/stop. Tapping the address opens the Search Pop-up.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AddressBar(
    address: SidebarAddress,
    onIntent: (SidebarIntent) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val backLabel = stringResource(R.string.sideraven_address_back)
    val historyLabel = stringResource(R.string.sideraven_address_history)
    val unavailableLabel = stringResource(R.string.sideraven_address_unavailable)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceContainer)
                .testTag(SidebarTestTag.ADDRESS_BAR),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                AddressPill(address, onIntent, Modifier.weight(1f))
                // Fenix's own menu: in the Sidebar layout its toolbar (and its ⋮) is hidden.
                BarButton(iconsR.drawable.mozac_ic_ellipsis_vertical_24, stringResource(R.string.sideraven_address_menu), true, SidebarTestTag.ADDRESS_MENU) {
                    onIntent(SidebarIntent.OpenMenu)
                }
            }
            // The home page has nowhere to go back or forward to.
            if (!address.isHome) Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                // Back: long-press for the tab's history.
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            // Long-press opens the tab's history even when there's nothing to go back to.
                            .combinedClickable(
                                enabled = !address.isHome,
                                role = Role.Button,
                                onClick = { if (address.canGoBack) onIntent(SidebarIntent.GoBack) },
                                onLongClick = { onIntent(SidebarIntent.ShowTabHistory) },
                            )
                            .semantics {
                                contentDescription = backLabel
                                onLongClick(label = historyLabel) {
                                    onIntent(SidebarIntent.ShowTabHistory)
                                    true
                                }
                                if (!address.canGoBack) stateDescription = unavailableLabel
                            }
                            .testTag(SidebarTestTag.ADDRESS_BACK),
                    contentAlignment = Alignment.Center,
                ) {
                    BarIcon(iconsR.drawable.mozac_ic_back_24, enabled = address.canGoBack)
                }
                BarButton(
                    iconRes = iconsR.drawable.mozac_ic_forward_24,
                    label = stringResource(R.string.sideraven_address_forward),
                    enabled = address.canGoForward,
                    testTag = SidebarTestTag.ADDRESS_FORWARD,
                ) { onIntent(SidebarIntent.GoForward) }
                if (!address.isHome) {
                    if (address.loading) {
                        BarButton(iconsR.drawable.mozac_ic_cross_24, stringResource(R.string.sideraven_address_stop), true, SidebarTestTag.ADDRESS_STOP) {
                            onIntent(SidebarIntent.StopLoading)
                        }
                    } else {
                        BarButton(iconsR.drawable.mozac_ic_arrow_clockwise_24, stringResource(R.string.sideraven_address_reload), true, SidebarTestTag.ADDRESS_RELOAD) {
                            onIntent(SidebarIntent.Reload)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // An Essential Tab or Pinned Tab: Reset once it has left its pinned URL, and Close.
                address.pin?.let { pin ->
                    if (pin.navigatedAway) {
                        BarButton(iconsR.drawable.mozac_ic_arrow_counter_clockwise_24, stringResource(R.string.sideraven_reset_essential), true, SidebarTestTag.ADDRESS_RESET) {
                            onIntent(pin.resetIntent)
                        }
                    }
                    BarButton(iconsR.drawable.mozac_ic_cross_24, stringResource(R.string.sideraven_close_tab), true, SidebarTestTag.ADDRESS_CLOSE) {
                        onIntent(pin.closeIntent)
                    }
                }
            }
        }
        // The loading line along the bottom edge.
        if (address.loading) {
            val progress by animateFloatAsState(
                targetValue = address.progress.coerceIn(0, 100) / 100f,
                animationSpec = LocalSidebarMotion.current.move(SideravenMotion.ITEM_PLACEMENT_MS),
                label = "loading",
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        // Scaled, not resized: no layout pass per frame.
                        .graphicsLayer {
                            scaleX = progress.coerceAtLeast(0.05f)
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .height(2.dp)
                        .background(colors.primary)
                        .testTag(SidebarTestTag.ADDRESS_LOADING),
            )
        }
    }
}

@Composable
private fun AddressPill(
    address: SidebarAddress,
    onIntent: (SidebarIntent) -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val placeholder = stringResource(R.string.sideraven_address_placeholder)
    val securityLabel =
        when (address.security) {
            AddressSecurity.Secure -> stringResource(R.string.sideraven_address_secure)
            AddressSecurity.Insecure -> stringResource(R.string.sideraven_address_insecure)
            AddressSecurity.None -> null
        }
    val addressLabel = stringResource(R.string.sideraven_address)
    Row(
        modifier =
            modifier
                .fillMaxHeight()
                // A field inside the bar, as Fenix's own search box.
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp)
                .clip(MaterialTheme.shapes.small)
                .background(colors.surfaceContainerHighest)
                .combinedClickable(role = Role.Button, onClick = { onIntent(SidebarIntent.OpenSearch(newTab = false)) })
                .semantics {
                    contentDescription = if (address.isHome) placeholder else "$addressLabel: ${address.domain}"
                    securityLabel?.let { stateDescription = it }
                }
                .testTag(SidebarTestTag.ADDRESS_PILL)
                .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val siteInfoLabel = stringResource(R.string.sideraven_address_site_info)
        if (address.security != AddressSecurity.None) {
            // Tap the lock for Fenix's site panel (permissions, trackers, certificate).
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .combinedClickable(role = Role.Button, onClick = { onIntent(SidebarIntent.OpenSitePanel) })
                        .semantics { contentDescription = siteInfoLabel }
                        .testTag(SidebarTestTag.ADDRESS_SITE_INFO),
                contentAlignment = Alignment.Center,
            ) {
                when (address.security) {
                    AddressSecurity.Secure -> SecurityIcon(iconsR.drawable.mozac_ic_lock_24, colors.onSurfaceVariant)
                    else -> SecurityIcon(iconsR.drawable.mozac_ic_warning_fill_24, colors.error)
                }
            }
        }
        if (address.security != AddressSecurity.None) Spacer(Modifier.width(6.dp))
        if (address.isHome) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_search_24),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = if (address.isHome) placeholder else address.domain,
            style = MaterialTheme.typography.bodyMedium,
            color = if (address.isHome) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SecurityIcon(
    iconRes: Int,
    tint: androidx.compose.ui.graphics.Color,
) {
    Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
}

@Composable
internal fun BarButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small)
                .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label }
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        BarIcon(iconRes, enabled)
    }
}

@Composable
private fun BarIcon(
    iconRes: Int,
    enabled: Boolean,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(18.dp).alpha(if (enabled) 1f else 0.38f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
