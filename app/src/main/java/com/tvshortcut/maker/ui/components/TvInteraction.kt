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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared D-Pad plumbing.
 *
 * `combinedClickable` already makes the node focusable and maps DPAD_CENTER /
 * ENTER to `onClick`, plus a long press to `onLongClick`. We disable the ripple
 * indication because on TV the focus state — not a touch ripple — is the
 * affordance the user reads.
 */
fun Modifier.tvClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = this.combinedClickable(
    interactionSource = interactionSource,
    indication = null,
    enabled = enabled,
    onLongClick = onLongClick,
    onClick = onClick
)

/** Sugar around [collectIsFocusedAsState] returning the raw boolean. */
@Composable
fun InteractionSource.collectIsFocusedAsStateCompat(): Boolean = collectIsFocusedAsState().value
