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
package com.tvshortcut.maker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.draw.clip
import androidx.tv.material3.Icon
import com.tvshortcut.maker.ui.components.collectIsFocusedAsStateCompat
import com.tvshortcut.maker.ui.components.tvClickable
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.foundation.layout.widthIn
import com.tvshortcut.maker.data.model.AppSort
import com.tvshortcut.maker.ui.components.TvActionButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.R
import com.tvshortcut.maker.data.model.AppFilter
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.ui.components.AppCard
import com.tvshortcut.maker.ui.components.EmptyState
import com.tvshortcut.maker.ui.components.LoadingState
import com.tvshortcut.maker.ui.components.MessageBar
import com.tvshortcut.maker.ui.components.TvNavigationRail
import com.tvshortcut.maker.ui.theme.TvColors
import com.tvshortcut.maker.viewmodel.AppListUiState
import kotlinx.coroutines.delay

/**
 * Home screen: a navigation rail on the left, a grid of apps on the right.
 *
 * Layout notes for a 10-foot screen:
 *  - the grid column count follows the available width, from five on a TV down
 *    to two on a phone in portrait;
 *  - horizontal padding keeps content inside the 5 % overscan margin some
 *    televisions still crop;
 *  - focus returns to the card the user was last on rather than to the top of
 *    the list, which matters when creating several shortcuts in a row.
 */
@Composable
fun AppDrawerScreen(
    state: AppListUiState,
    onFilterChange: (AppFilter) -> Unit,
    onAppSelected: (AppInfo) -> Unit,
    onAppLaunch: (AppInfo) -> Unit,
    onDismissMessage: () -> Unit,
    onDonateClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (AppSort) -> Unit,
    onRemoveOrphans: () -> Unit,
    onCardFocused: (AppInfo) -> Unit,
    /** Package to return focus to; read once per restore, never observed. */
    lastFocusedPackage: () -> String?,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val focusTarget = remember { FocusRequester() }

    val screenWidth = LocalConfiguration.current.screenWidthDp
    val columns = when {
        screenWidth >= 960 -> GRID_COLUMNS
        screenWidth >= 720 -> 4
        screenWidth >= 520 -> 3
        screenWidth >= 360 -> 2
        else -> 1
    }
    val compact = screenWidth < 720
    val sideInset = if (compact) 16.dp else 32.dp

    // Index of the card focus should land on: the one the user was last on,
    // falling back to the first.
    val targetIndex = remember(state.visibleApps, state.filter, state.query) {
        val wanted = lastFocusedPackage()
        state.visibleApps.indexOfFirst { it.packageName == wanted }.coerceAtLeast(0)
    }

    // The drawer slides over the content and is dismissed explicitly: by the
    // hamburger, by BACK, by tapping the scrim, or by choosing a filter. No
    // focus heuristics involved — those proved unreliable on TV firmwares.
    var drawerOpen by remember { mutableStateOf(false) }

    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(state.filter, state.query, state.isLoading) { focusRestored = false }

    LaunchedEffect(state.isLoading, state.visibleApps.size, targetIndex, focusRestored) {
        if (!state.isLoading && state.visibleApps.isNotEmpty() && !focusRestored) {
            // The item must be composed before it can take focus, hence the
            // scroll first and the short wait after it.
            runCatching { gridState.scrollToItem(targetIndex) }
            delay(FOCUS_RESTORE_DELAY_MS)
            runCatching { focusTarget.requestFocus() }
            focusRestored = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(TvColors.SurfaceElevated.copy(alpha = 0.45f), TvColors.Background)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ---- Top bar: hamburger on the left, tab title in the middle ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = sideInset,
                        end = sideInset,
                        top = if (compact) 12.dp else 22.dp,
                        bottom = 6.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(state.filter.titleRes()),
                        style = MaterialTheme.typography.titleLarge,
                        color = TvColors.TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!state.isLoading) {
                        Text(
                            text = stringResource(
                                R.string.msg_app_count,
                                state.visibleApps.size
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = TvColors.TextTertiary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                MenuButton(
                    onClick = { drawerOpen = true },
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }

            // Only on the shortcuts tab, and only when there is something dead to
            // remove: a button that is usually disabled is just noise.
            if (state.filter == AppFilter.SHORTCUTS && state.orphanShortcuts.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = sideInset, end = sideInset, top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    TvActionButton(
                        label = stringResource(
                            R.string.action_remove_orphans,
                            state.orphanShortcuts.size
                        ),
                        icon = Icons.Default.DeleteSweep,
                        destructive = true,
                        onClick = onRemoveOrphans,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )
                }
            }

            when {
                state.isLoading -> LoadingState(stringResource(R.string.state_loading))

                state.visibleApps.isEmpty() -> EmptyState(
                    title = when {
                        state.query.isNotBlank() ->
                            stringResource(R.string.search_empty, state.query)
                        state.filter == AppFilter.SHORTCUTS ->
                            stringResource(R.string.shortcuts_empty)
                        else -> stringResource(R.string.state_empty)
                    },
                    hint = when {
                        state.query.isNotBlank() ->
                            stringResource(R.string.search_empty_hint)
                        state.filter == AppFilter.SHORTCUTS ->
                            stringResource(R.string.shortcuts_empty_hint)
                        else -> stringResource(R.string.state_empty_hint)
                    }
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = sideInset,
                        end = sideInset,
                        top = 12.dp,
                        bottom = 48.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = state.visibleApps,
                        // A stable key preserves focus/scroll across filter changes.
                        key = { _, app -> app.packageName }
                    ) { index, app ->
                        AppCard(
                            app = app,
                            onFocused = { onCardFocused(app) },
                            // Short press opens the detail panel; long press
                            // launches straight away for power users.
                            onClick = { onAppSelected(app) },
                            onLongClick = { onAppLaunch(app) },
                            modifier = if (index == targetIndex) {
                                Modifier.focusRequester(focusTarget)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
        }

        // ---- Sliding drawer, drawn above everything -------------------------
        NavigationDrawerOverlay(
            open = drawerOpen,
            onDismiss = { drawerOpen = false },
            state = state,
            onFilterChange = {
                onFilterChange(it)
                drawerOpen = false
            },
            onQueryChange = onQueryChange,
            onDonateClick = {
                drawerOpen = false
                onDonateClick()
            },
            onSortChange = onSortChange
        )

        MessageBar(
            message = state.message,
            onDismiss = onDismissMessage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

/** Five columns is the sweet spot for a 1080p/4K television. */
private const val GRID_COLUMNS = 5

/** Time for the scrolled-to item to compose before it can accept focus. */
private const val FOCUS_RESTORE_DELAY_MS = 60L

/** Hamburger button: opens the navigation drawer. */
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(if (focused) TvColors.Primary else TvColors.Surface)
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvColors.BorderFocused else TvColors.Border
                ),
                shape
            )
            .tvClickable(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = stringResource(R.string.menu_open),
            tint = if (focused) TvColors.OnPrimary else TvColors.TextPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * The drawer itself: a scrim over the content plus the panel sliding in from
 * the left. Dismissal is explicit — BACK, the scrim, or picking a filter — so
 * the panel never disappears while the user is still using it.
 */
@Composable
private fun NavigationDrawerOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    state: AppListUiState,
    onFilterChange: (AppFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onDonateClick: () -> Unit,
    onSortChange: (AppSort) -> Unit
) {
    BackHandler(enabled = open, onBack = onDismiss)

    val panelFocus = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) {
            delay(FOCUS_RESTORE_DELAY_MS)
            runCatching { panelFocus.requestFocus() }
        }
    }

    AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.Scrim)
                .tvClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        ) {
            AnimatedVisibility(
                visible = open,
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                TvNavigationRail(
                    currentFilter = state.filter,
                    countFor = state::countFor,
                    query = state.query,
                    onQueryChange = onQueryChange,
                    onFilterChange = onFilterChange,
                    onDonateClick = onDonateClick,
                    currentSort = state.sort,
                    onSortChange = onSortChange,
                    modifier = Modifier.focusRequester(panelFocus)
                )
            }
        }
    }
}

/** Localised title for a filter, as a resource id. */
private fun AppFilter.titleRes(): Int = when (this) {
    AppFilter.HIDDEN -> R.string.filter_hidden
    AppFilter.ALL -> R.string.filter_all
    AppFilter.TV -> R.string.filter_tv
    AppFilter.FAVORITES -> R.string.filter_favorites
    AppFilter.SHORTCUTS -> R.string.filter_shortcuts
}
