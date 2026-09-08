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
package com.tvshortcut.maker.data.model

import android.graphics.Bitmap

/**
 * A single installed application as presented by the UI.
 *
 * @param packageName        Unique package id, e.g. `com.spotify.music`.
 * @param label              Human readable name resolved through the PackageManager.
 * @param versionName        Version string shown in the detail panel (may be empty).
 * @param isSystemApp        True for apps living in /system (pre-installed).
 * @param hasLeanbackEntry   True when the app declares CATEGORY_LEANBACK_LAUNCHER,
 *                           i.e. it is already visible in the Android TV launcher.
 * @param hasMobileEntry     True when the app declares the classic CATEGORY_LAUNCHER.
 * @param icon               Icon extracted from the APK, already down-scaled for the grid.
 * @param accentColor        Dominant ARGB colour of [icon], used for the focus glow
 *                           and for the generated banner gradient.
 * @param isFavorite         Whether the user pinned this app inside our own drawer.
 * @param isGeneratedShortcut True when this package is a shortcut wrapper this
 *                           app generated, rather than an app the user installed.
 * @param shortcutTarget     For a generated shortcut, the package it opens.
 * @param shortcutTargetMissing True when this shortcut points at an app that is
 *                           no longer installed — the shortcut is dead weight.
 * @param installedAt        First-install timestamp, used for "recent" sorting.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String = "",
    val isSystemApp: Boolean = false,
    val hasLeanbackEntry: Boolean = false,
    val hasMobileEntry: Boolean = false,
    val icon: Bitmap? = null,
    val accentColor: Int = 0xFF7C6BFF.toInt(),
    val isFavorite: Boolean = false,
    val isGeneratedShortcut: Boolean = false,
    val shortcutTarget: String? = null,
    val shortcutTargetMissing: Boolean = false,
    val installedAt: Long = 0L
) {
    /**
     * The apps this project exists for: they *can* be launched, but the TV
     * launcher refuses to show them because there is no leanback entry point.
     */
    val isHiddenOnTv: Boolean get() = hasMobileEntry && !hasLeanbackEntry

    /** Anything we are able to start at all. */
    val isLaunchable: Boolean get() = hasMobileEntry || hasLeanbackEntry
}

/** Filters offered in the top bar of the drawer. */
enum class AppFilter(val titleResSuffix: String) {
    HIDDEN("hidden"),        // sideloaded / mobile-only apps  -> default
    ALL("all"),
    TV("tv"),
    FAVORITES("favorites"),
    /** Wrappers this app generated — the only place they can be found and removed. */
    SHORTCUTS("shortcuts");
}

/** Order the app grid is presented in. */
enum class AppSort {
    /** Case-insensitive alphabetical — the default, easiest to scan with a remote. */
    NAME,

    /**
     * Newest first. Sideloading something and then looking for it is the single
     * most common reason to open this app, and alphabetical order buries it.
     */
    RECENT
}
