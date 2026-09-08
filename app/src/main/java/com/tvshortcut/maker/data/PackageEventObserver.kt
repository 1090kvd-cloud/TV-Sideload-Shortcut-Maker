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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce

/**
 * Emits whenever a package is installed, removed or replaced on the device.
 *
 * This replaces the previous approach of rescanning everything in `onResume`.
 * A full scan walks 100+ packages and rasterises every icon, which is a visible
 * stall on TV hardware — and it happened every single time the user came back
 * from the installer or from a launched app, almost always for nothing.
 *
 * The system already knows when the app list actually changed, so we listen
 * instead of polling.
 */
class PackageEventObserver(private val context: Context) {

    /**
     * Cold flow of package changes. Registration happens on collection and is
     * torn down automatically when the collector goes away, so there is no
     * receiver to leak.
     *
     * Events are debounced: installing one APK can fire ADDED and REPLACED
     * back to back, and a batch install fires a burst — rescanning once at the
     * end is both cheaper and less jarring than rescanning per event.
     */
    fun events(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // EXTRA_REPLACING marks the "removed" half of an update; the
                // matching ADDED arrives right after, so ignoring it here avoids
                // a pointless intermediate scan.
                val replacing = intent?.getBooleanExtra(Intent.EXTRA_REPLACING, false) == true
                if (intent?.action == Intent.ACTION_PACKAGE_REMOVED && replacing) return
                trySend(Unit)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            // Package broadcasts carry the package name as data, so the filter
            // must declare the scheme or nothing is delivered.
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.debounce(DEBOUNCE_MS)

    private companion object {
        /** Long enough to swallow an install burst, short enough to feel instant. */
        const val DEBOUNCE_MS = 400L
    }
}
