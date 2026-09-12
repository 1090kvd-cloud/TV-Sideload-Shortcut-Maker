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
package com.tvshortcut.stub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/**
 * The entire runtime of a generated shortcut.
 *
 * It reads the package name it should open from `assets/target.txt` — a file the
 * patcher rewrites for every shortcut — resolves a launch intent and forwards to
 * it. The activity is translucent and `noHistory`, so the user only ever sees the
 * target app appear.
 *
 * Reading the target from assets rather than from the manifest is what keeps the
 * patcher simple: assets are ordinary zip entries and can change length freely,
 * while manifest strings cannot.
 */
class StubLauncherActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val target = readTargetPackage()
        if (target.isNullOrBlank()) {
            toast(getString(R.string.stub_not_configured))
            finish()
            return
        }

        val intent = resolveLaunchIntent(target)
        if (intent == null) {
            // Most likely the target app was uninstalled after the shortcut was made.
            toast(getString(R.string.stub_not_installed, target))
        } else {
            runCatching { startActivity(intent) }
                .onFailure { toast(getString(R.string.stub_cannot_open, target)) }
        }

        finish()
        overridePendingTransition(0, 0)
    }

    /** Reads and trims the single line stored in `assets/target.txt`. */
    private fun readTargetPackage(): String? = runCatching {
        assets.open(TARGET_ASSET).bufferedReader().use { it.readText() }.trim()
    }.getOrNull()

    /**
     * Prefers the leanback entry point when the target declares one, otherwise
     * falls back to the classic phone launcher activity.
     */
    private fun resolveLaunchIntent(packageName: String): Intent? {
        val leanback = runCatching {
            packageManager.getLeanbackLaunchIntentForPackage(packageName)
        }.getOrNull()
        val standard = runCatching {
            packageManager.getLaunchIntentForPackage(packageName)
        }.getOrNull()

        return (leanback ?: standard)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val TARGET_ASSET = "target.txt"
    }
}
