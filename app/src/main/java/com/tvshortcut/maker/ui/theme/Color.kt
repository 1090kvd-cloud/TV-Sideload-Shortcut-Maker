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
package com.tvshortcut.maker.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * KVD palette: charcoal surfaces and warm orange accents matching the avatar.
 * Focus states remain distinct for remote-control navigation.
 */
object TvColors {
    val Background = Color(0xFF070709)
    val Surface = Color(0xFF0D0D10)
    val SurfaceElevated = Color(0xFF191719)
    val SurfaceFocused = Color(0xFF2D2115)

    val Primary = Color(0xFFFFA52C)        // KVD orange
    val PrimaryDim = Color(0xFF713E12)
    val Secondary = Color(0xFFFFCB70)      // warm highlight
    val OnPrimary = Color(0xFF1A1005)

    val TextPrimary = Color(0xFFF4EEE6)
    val TextSecondary = Color(0xFFB9ADA0)
    val TextTertiary = Color(0xFF9A8B7C)

    val Border = Color(0xFF3A2D20)
    val BorderFocused = Color(0xFFFFE0A3)
    val Danger = Color(0xFFFF6B6B)
    val Success = Color(0xFF4ADE80)

    val Scrim = Color(0xE6070709)
}
