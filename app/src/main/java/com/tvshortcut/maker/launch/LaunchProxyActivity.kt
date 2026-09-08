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
package com.tvshortcut.maker.launch

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.tvshortcut.maker.R
import com.tvshortcut.maker.TvShortcutApplication

/**
 * Invisible trampoline used by every shortcut this app creates.
 *
 * Flow: launcher → shortcut intent → this activity → real app.
 *
 * Why not point the shortcut at the target app directly?
 *  - The target's launcher activity can be renamed by an update, which would
 *    silently break a hard-coded ComponentName.
 *  - We want to retry with a different entry point (leanback vs. phone) and to
 *    show a readable error when the app exposes none.
 *
 * The activity is declared with `noHistory` and a transparent theme, so the user
 * never sees it: the target app appears to start straight from the home screen.
 */
class LaunchProxyActivity : Activity() {

    companion object {
        const val ACTION_LAUNCH_APP = "com.tvshortcut.maker.LAUNCH_APP"
        const val EXTRA_PACKAGE_NAME = "com.tvshortcut.maker.extra.PACKAGE_NAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME)
        if (packageName.isNullOrBlank()) {
            finish()
            return
        }

        val repository = TvShortcutApplication.instance.container.appRepository
        val launchIntent = repository.resolveLaunchIntent(packageName)

        if (launchIntent == null) {
            // The app was uninstalled or has no launchable activity.
            Toast.makeText(this, R.string.msg_launch_failed, Toast.LENGTH_LONG).show()
        } else {
            runCatching { startActivity(launchIntent) }
                .onFailure { Toast.makeText(this, R.string.msg_launch_failed, Toast.LENGTH_LONG).show() }
        }

        // Never stay on the back stack — pressing "back" must return to the launcher.
        finish()
        overridePendingTransition(0, 0)
    }
}
