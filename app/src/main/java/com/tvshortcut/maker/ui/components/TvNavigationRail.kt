/*
 * TV Shortcut Maker — home-screen shortcuts for sideloaded Android TV apps.
 * Copyright (C) 2026 V. D. Krivitskiy
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 3 as published by the
 * Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.tvshortcut.maker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.R
import com.tvshortcut.maker.data.model.AppFilter
import com.tvshortcut.maker.data.model.AppSort
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Left navigation rail, the pattern Google TV itself uses.
 *
 * Collapsed it is a narrow strip of icons that costs the content almost no
 * width. Moving the D-Pad left out of the grid puts focus into the rail, which
 * expands to reveal labels, counts and the search box; moving right collapses
 * it again.
 *
 * Expansion is driven by focus rather than by a toggle button on purpose: with
 * a remote the user is already pressing LEFT to reach the filters, and turning
 * that into "open, then choose" would add a keypress and a mode to escape from.
 *
 * One structural rule matters here: **the set of focusable children must not
 * change between the two states.** If a node that holds focus is unmounted on
 * expansion, focus escapes the rail, the rail collapses, and the UI flickers in
 * a loop. So the same items are always present — only their labels appear.
 */
@Composable
fun TvNavigationRail(
    currentFilter: AppFilter,
    countFor: (AppFilter) -> Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onFilterChange: (AppFilter) -> Unit,
    onDonateClick: () -> Unit,
    currentSort: AppSort,
    onSortChange: (AppSort) -> Unit,
    initialFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {

    // On a phone a 300 dp panel would swallow the screen, so it is capped at
    // roughly four fifths of the available width.
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val width = minOf(DRAWER_WIDTH, screenWidth * 0.82f)

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .focusGroup()
            .background(TvColors.Surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 20.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // App identity: an icon while collapsed, the full name once open.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = TvColors.Primary,
                modifier = Modifier.size(24.dp)
            )
            RailLabel {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }

        // Search: the text box only exists while the rail is open, which is safe
        // because a collapsed rail never holds focus.
        TvSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.search_hint),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))

        AppFilter.entries.forEach { filter ->
            RailItem(
                icon = filter.icon(),
                label = stringResource(filter.titleRes()),
                count = countFor(filter),
                selected = filter == currentFilter,
                onClick = { onFilterChange(filter) },
                modifier = if (filter == currentFilter) {
                    Modifier.focusRequester(initialFocusRequester)
                } else Modifier
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.sort_title),
            style = MaterialTheme.typography.labelMedium,
            color = TvColors.TextTertiary,
            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
        )

        RailItem(
            icon = Icons.Default.SortByAlpha,
            label = stringResource(R.string.sort_name),
            count = null,
            selected = currentSort == AppSort.NAME,
            onClick = { onSortChange(AppSort.NAME) }
        )
        RailItem(
            icon = Icons.Default.Schedule,
            label = stringResource(R.string.sort_recent),
            count = null,
            selected = currentSort == AppSort.RECENT,
            onClick = { onSortChange(AppSort.RECENT) }
        )

        Spacer(Modifier.height(14.dp))

        RailItem(
            icon = Icons.Default.Favorite,
            label = stringResource(R.string.donate_action),
            count = null,
            selected = false,
            onClick = onDonateClick
        )

        RailLabel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 8.dp, top = 14.dp)
                    .padding(12.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.developer_avatar),
                    contentDescription = stringResource(R.string.about_developer_avatar),
                    modifier = Modifier.size(48.dp).clip(CircleShape)
                )
                Text(
                    text = stringResource(R.string.about_developer),
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color(0xFFE2D8C9),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

/** One focusable row of the rail: icon always, label and count when open. */
@Composable
private fun RailItem(
    icon: ImageVector,
    label: String,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(
                when {
                    focused -> TvColors.Primary
                    selected -> TvColors.SurfaceFocused
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) TvColors.BorderFocused
                    else androidx.compose.ui.graphics.Color.Transparent
                ),
                shape
            )
            .tvClickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                focused -> TvColors.OnPrimary
                selected -> TvColors.Primary
                else -> TvColors.TextSecondary
            },
            modifier = Modifier.size(22.dp)
        )

        RailLabel {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focused) TvColors.OnPrimary else TvColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (count != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focused) TvColors.OnPrimary.copy(alpha = 0.7f)
                        else TvColors.TextTertiary
                    )
                }
            }
        }
    }
}

/** Kept as a wrapper so item layout stays identical across the file. */
@Composable
private fun RailLabel(content: @Composable () -> Unit) {
    Box { content() }
}

private fun AppFilter.icon(): ImageVector = when (this) {
    AppFilter.HIDDEN -> Icons.Default.VisibilityOff
    AppFilter.ALL -> Icons.Default.Apps
    AppFilter.TV -> Icons.Default.Tv
    AppFilter.FAVORITES -> Icons.Default.Star
    AppFilter.SHORTCUTS -> Icons.Default.Widgets
}

private fun AppFilter.titleRes(): Int = when (this) {
    AppFilter.HIDDEN -> R.string.filter_hidden
    AppFilter.ALL -> R.string.filter_all
    AppFilter.TV -> R.string.filter_tv
    AppFilter.FAVORITES -> R.string.filter_favorites
    AppFilter.SHORTCUTS -> R.string.filter_shortcuts
}

/** Fits the longest filter title plus its count without truncating. */
private val DRAWER_WIDTH = 300.dp
