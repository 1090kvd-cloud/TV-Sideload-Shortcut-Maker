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
package com.tvshortcut.maker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvshortcut.maker.ui.screens.AppDetailPanel
import com.tvshortcut.maker.ui.screens.AppDrawerScreen
import com.tvshortcut.maker.ui.screens.DonatePanel
import com.tvshortcut.maker.ui.theme.TvColors
import com.tvshortcut.maker.ui.theme.TvShortcutTheme
import com.tvshortcut.maker.viewmodel.AppListViewModel

/**
 * The single activity of the app.
 *
 * Everything is Compose; there is no fragment or Leanback fragment involved.
 * The activity itself only wires the ViewModel to the two screens and rescans
 * the device on resume (an app may have been installed or removed while we
 * were in the background).
 */
class MainActivity : ComponentActivity() {

    private var viewModelRef: AppListViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvShortcutTheme {
                val viewModel: AppListViewModel = viewModel(factory = AppListViewModel.Factory)
                viewModelRef = viewModel
                TvShortcutApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Deliberately NOT a rescan: PackageEventObserver already reacts to
        // installs and removals, so this only re-reads the cached list (and the
        // favourites on top of it), which is effectively free.
        viewModelRef?.refresh()
    }
}

/** Root composable: the drawer with the detail panel layered on top. */
@Composable
private fun TvShortcutApp(viewModel: AppListViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
    ) {
        AppDrawerScreen(
            state = state,
            onFilterChange = viewModel::setFilter,
            onAppSelected = { viewModel.select(it) },
            onAppLaunch = { viewModel.launch(it) },
            onDismissMessage = viewModel::consumeMessage,
            onDonateClick = { viewModel.setDonateVisible(true) },
            onQueryChange = viewModel::setQuery,
            onSortChange = viewModel::setSort,
            onRemoveOrphans = viewModel::removeOrphanShortcuts,
            onCardFocused = { viewModel.rememberFocus(it.packageName) },
            lastFocusedPackage = { viewModel.lastFocusedPackage }
        )

        AppDetailPanel(
            app = state.selectedApp,
            pinningSupported = state.pinningSupported,
            onLaunch = { viewModel.launch(it) },
            onCreateShortcut = {
                viewModel.createShortcut(it)
                viewModel.select(null)
            },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onOpenSettings = { viewModel.openSystemSettings(it) },
            onUninstall = {
                viewModel.requestUninstall(it)
                // Back to the list straight away: the system dialog takes over
                // from here, and there is nothing left to do in the panel.
                viewModel.select(null)
            },
            onDismiss = { viewModel.select(null) },
            renderBanner = viewModel::renderBannerPreview
        )

        DonatePanel(
            visible = state.showDonate,
            onDismiss = { viewModel.setDonateVisible(false) }
        )
    }
}
