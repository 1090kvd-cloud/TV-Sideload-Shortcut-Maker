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
package com.tvshortcut.maker.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.annotation.StringRes
import com.tvshortcut.maker.AppContainer
import com.tvshortcut.maker.R
import com.tvshortcut.maker.TvShortcutApplication
import com.tvshortcut.maker.data.model.AppFilter
import com.tvshortcut.maker.data.model.AppInfo
import com.tvshortcut.maker.data.model.AppSort
import com.tvshortcut.maker.data.apk.ShortcutResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Immutable snapshot rendered by the UI.
 *
 * [visibleApps] is derived rather than stored, which keeps the state impossible
 * to desynchronise: changing the filter can never leave a stale list behind.
 */
data class AppListUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppInfo> = emptyList(),
    val filter: AppFilter = AppFilter.HIDDEN,
    val query: String = "",
    val sort: AppSort = AppSort.NAME,
    val selectedApp: AppInfo? = null,
    /** Whether the donation panel with the QR code is open. */
    val showDonate: Boolean = false,
    val pinningSupported: Boolean = true,
    /** One-shot user feedback; cleared by [AppListViewModel.consumeMessage]. */
    val message: UiMessage? = null
) {
    val visibleApps: List<AppInfo>
        get() = allApps
            .filter { app ->
                when (filter) {
                    // Generated wrappers are excluded everywhere except their own
                    // tab: they are plumbing, not apps the user chose to install.
                    AppFilter.HIDDEN -> app.isHiddenOnTv && !app.isGeneratedShortcut
                    AppFilter.TV -> app.hasLeanbackEntry && !app.isGeneratedShortcut
                    AppFilter.FAVORITES -> app.isFavorite
                    AppFilter.ALL -> !app.isGeneratedShortcut
                    AppFilter.SHORTCUTS -> app.isGeneratedShortcut
                }
            }
            .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    AppSort.NAME -> list.sortedBy { it.label.lowercase() }
                    AppSort.RECENT -> list.sortedByDescending { it.installedAt }
                }
            }

    /** Shortcuts whose target app is gone — safe to delete in bulk. */
    val orphanShortcuts: List<AppInfo>
        get() = allApps.filter { it.isGeneratedShortcut && it.shortcutTargetMissing }

    /** Number of apps per filter, shown as a badge in the top bar. */
    fun countFor(target: AppFilter): Int = allApps.count { app ->
        when (target) {
            AppFilter.HIDDEN -> app.isHiddenOnTv && !app.isGeneratedShortcut
            AppFilter.TV -> app.hasLeanbackEntry && !app.isGeneratedShortcut
            AppFilter.FAVORITES -> app.isFavorite
            AppFilter.ALL -> !app.isGeneratedShortcut
            AppFilter.SHORTCUTS -> app.isGeneratedShortcut
        }
    }
}

/** Transient message displayed as a toast-like snack bar. */
data class UiMessage(val text: String, val isError: Boolean = false)

class AppListViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    /**
     * Package of the card the D-Pad was last on.
     *
     * Deliberately a plain field rather than part of [AppListUiState]: focus
     * changes on every remote keypress, and routing that through a StateFlow
     * would recompose the whole grid dozens of times a second for information
     * only ever read once, when focus needs restoring.
     */
    var lastFocusedPackage: String? = null
        private set

    fun rememberFocus(packageName: String) {
        lastFocusedPackage = packageName
    }

    /**
     * Clears the remembered focus when it points at a package that is gone,
     * so focus restoration falls back to the first card instead of silently
     * doing nothing.
     */
    private fun forgetFocusIfMissing(apps: List<AppInfo>) {
        val remembered = lastFocusedPackage ?: return
        if (apps.none { it.packageName == remembered }) lastFocusedPackage = null
    }

    init {
        refresh()
        observePackageChanges()
    }

    /**
     * Rescans only when the system reports that packages actually changed.
     *
     * The alternative — rescanning in onResume — cost a full walk of every
     * installed package plus icon decoding each time the user came back from
     * the installer, which is precisely the moment the UI should feel snappy.
     */
    private fun observePackageChanges() {
        viewModelScope.launch {
            container.packageEvents.events().collectLatest {
                container.appRepository.invalidateCache()
                refresh()
            }
        }
    }

    /**
     * Loads the app list, using the cached scan when one is available.
     *
     * The loading state is only raised on a cold start: flipping the whole grid
     * to a spinner for a cache read would flicker for no reason.
     */
    fun refresh() {
        viewModelScope.launch {
            val cold = !container.appRepository.hasCache()
            if (cold) _uiState.update { it.copy(isLoading = true) }
            val apps = container.appRepository.loadInstalledApps()
            forgetFocusIfMissing(apps)
            _uiState.update { current ->
                // Drop a selection that no longer exists — after an uninstall the
                // detail panel would otherwise stay open on a deleted package,
                // which reads as "the app refuses to go back to the list".
                // A surviving selection is re-read from the fresh scan so the
                // panel shows current data rather than a stale copy.
                val selected = current.selectedApp?.let { previous ->
                    apps.firstOrNull { it.packageName == previous.packageName }
                }
                current.copy(
                    isLoading = false,
                    allApps = apps,
                    selectedApp = selected,
                    pinningSupported = container.shortcutHelper.isPinningSupported()
                )
            }
        }
    }

    fun setFilter(filter: AppFilter) = _uiState.update { it.copy(filter = filter) }

    fun setQuery(query: String) = _uiState.update { it.copy(query = query) }

    fun setSort(sort: AppSort) = _uiState.update { it.copy(sort = sort) }

    fun select(app: AppInfo?) = _uiState.update { it.copy(selectedApp = app) }

    fun setDonateVisible(visible: Boolean) = _uiState.update { it.copy(showDonate = visible) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ---------------------------------------------------------------------
    //  Actions
    // ---------------------------------------------------------------------

    /** Starts [app] through the repository-resolved intent. */
    fun launch(app: AppInfo) {
        val intent = container.appRepository.resolveLaunchIntent(app.packageName)
        if (intent == null) {
            postMessage(R.string.msg_launch_failed, isError = true)
            return
        }
        // The intent carries FLAG_ACTIVITY_NEW_TASK, so starting it from the
        // application context is safe.
        runCatching { TvShortcutApplication.instance.startActivity(intent) }
            .onFailure { postMessage(R.string.msg_launch_error, app.label, isError = true) }
    }

    /**
     * Creates a home-screen shortcut for [app].
     *
     * Strategy: generate and install a tiny APK. Unlike `requestPinShortcut()`,
     * which most TV launchers silently ignore, an installed package always shows
     * up. If the generated-APK path is unavailable, fall back to pinning so the
     * feature still works on launchers that do support it.
     */
    fun createShortcut(app: AppInfo) {
        viewModelScope.launch {
            postMessage(R.string.msg_shortcut_building, app.label)

            if (!container.shortcutApkService.isSupported()) {
                createPinnedShortcut(app)
                return@launch
            }

            val artwork = withContext(Dispatchers.Default) {
                val raw = container.bannerFactory.extractIcon(app.packageName, size = 512)
                    ?: app.icon
                val banner = container.bannerFactory.createBanner(raw)
                val square = container.bannerFactory.createIconArtwork(raw)
                banner to square
            }
            val (banner, icon) = artwork

            when (val result = container.shortcutApkService.createShortcut(
                targetPackage = app.packageName,
                label = app.label,
                banner = banner,
                icon = icon
            )) {
                is ShortcutResult.InstallStarted ->
                    postMessage(R.string.msg_shortcut_install_started, app.label)

                is ShortcutResult.PermissionRequired -> {
                    postMessage(R.string.msg_shortcut_permission, isError = true)
                    container.shortcutApkService.requestInstallPermission()
                }

                is ShortcutResult.TemplateMissing -> {
                    // Packaging problem — still try the legacy path rather than
                    // leaving the user with nothing.
                    createPinnedShortcut(app)
                }

                is ShortcutResult.Failed ->
                    postMessage(R.string.msg_shortcut_failed, result.reason, isError = true)
            }
        }
    }

    /** Legacy path: ask the launcher to pin a shortcut. */
    private suspend fun createPinnedShortcut(app: AppInfo) {
        val pinned = withContext(Dispatchers.Default) {
            container.shortcutHelper.pinShortcut(app)
        }
        if (pinned) {
            postMessage(R.string.msg_pin_ok, app.label)
        } else {
            postMessage(R.string.msg_pin_unsupported, isError = true)
        }
    }

    fun toggleFavorite(app: AppInfo) {
        val nowFavorite = container.appRepository.toggleFavorite(app.packageName)
        // Patch the cached list in place instead of rescanning: a full rescan
        // would take ~1 s and visibly reset the D-Pad focus.
        _uiState.update { state ->
            val updated = state.allApps.map {
                if (it.packageName == app.packageName) it.copy(isFavorite = nowFavorite) else it
            }
            state.copy(
                allApps = updated,
                selectedApp = state.selectedApp?.takeIf { it.packageName == app.packageName }
                    ?.copy(isFavorite = nowFavorite) ?: state.selectedApp
            )
        }
    }

    /**
     * Asks the system to remove every shortcut whose target app is gone.
     *
     * Each uninstall shows its own confirmation dialog — that is enforced by
     * Android and cannot be batched away. The value here is not skipping the
     * dialogs but not having to hunt the dead shortcuts down one by one.
     */
    fun removeOrphanShortcuts() {
        val orphans = _uiState.value.orphanShortcuts
        if (orphans.isEmpty()) return
        orphans.forEach { container.appRepository.requestUninstall(it.packageName) }
        postMessage(R.string.msg_orphans_removing, orphans.size)
    }

    fun openSystemSettings(app: AppInfo) = container.appRepository.openSystemSettings(app.packageName)

    fun requestUninstall(app: AppInfo) = container.appRepository.requestUninstall(app.packageName)

    /** Renders a preview of the artwork exactly as it will be installed. */
    suspend fun renderBannerPreview(app: AppInfo): Bitmap =
        withContext(Dispatchers.Default) {
            val icon = container.bannerFactory.extractIcon(app.packageName, size = 384) ?: app.icon
            container.bannerFactory.createBanner(icon)
        }

    /**
     * Resolves [resId] against the application context so that every user-facing
     * string comes from res/values-*, keeping the app fully localisable.
     */
    private fun postMessage(@StringRes resId: Int, vararg args: Any, isError: Boolean = false) {
        val text = TvShortcutApplication.instance.getString(resId, *args)
        _uiState.update { it.copy(message = UiMessage(text, isError)) }
    }

    companion object {
        /** Factory wiring the hand-rolled service locator into the ViewModel. */
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AppListViewModel(TvShortcutApplication.instance.container)
            }
        }
    }
}
