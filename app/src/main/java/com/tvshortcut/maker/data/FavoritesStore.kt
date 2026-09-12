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
package com.tvshortcut.maker.data

import android.content.Context
import androidx.core.content.edit

/**
 * Tiny persistence layer for the user's favourite packages.
 *
 * SharedPreferences is intentional: the payload is a handful of strings that we
 * read exactly once per scan, so DataStore would add a dependency without any
 * practical benefit here.
 */
class FavoritesStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFavorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty()

    fun isFavorite(packageName: String): Boolean = packageName in getFavorites()

    /**
     * Adds or removes [packageName].
     * @return the new state (`true` = the app is now a favourite).
     */
    fun toggle(packageName: String): Boolean {
        val current = getFavorites().toMutableSet()
        val added = if (packageName in current) {
            current.remove(packageName); false
        } else {
            current.add(packageName); true
        }
        // A defensive copy is required: SharedPreferences must not be handed a
        // mutable set instance that we keep modifying afterwards.
        prefs.edit { putStringSet(KEY_FAVORITES, HashSet(current)) }
        return added
    }

    private companion object {
        const val PREFS_NAME = "tv_shortcut_prefs"
        const val KEY_FAVORITES = "favorite_packages"
    }
}
