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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvshortcut.maker.ui.theme.TvColors

/**
 * Full-width action button used in the detail panel.
 *
 * @param destructive paints the button in the danger colour (uninstall).
 */
@Composable
fun TvActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused = interactionSource.collectIsFocusedAsStateCompat()
    val shape = RoundedCornerShape(14.dp)
    val accent = if (destructive) TvColors.Danger else TvColors.Primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    !enabled -> TvColors.Surface.copy(alpha = 0.5f)
                    focused -> accent
                    else -> TvColors.SurfaceElevated
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) TvColors.BorderFocused else TvColors.Border
                ),
                shape
            )
            .tvClickable(
                interactionSource = interactionSource,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> TvColors.TextTertiary
                focused -> TvColors.OnPrimary
                else -> accent
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> TvColors.TextTertiary
                focused -> TvColors.OnPrimary
                else -> TvColors.TextPrimary
            }
        )
    }
}
