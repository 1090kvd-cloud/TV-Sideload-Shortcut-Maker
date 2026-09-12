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
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import com.tvshortcut.maker.data.apk.ShortcutApkBuilder
import com.tvshortcut.maker.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the list of installed applications.
 *
 * The interesting bit is the classification: an app is "hidden on TV" when it
 * exposes `CATEGORY_LAUNCHER` but not `CATEGORY_LEANBACK_LAUNCHER`. Those are
 * exactly the sideloaded phone apps the Android TV home screen refuses to show.
 */
class AppRepository(
    private val context: Context,
    private val bannerFactory: BannerFactory,
    private val favoritesStore: FavoritesStore
) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Last scan result, kept so that returning to the app does not re-read every
     * package and re-decode every icon. Marked @Volatile because it is written
     * on an IO thread and read from the main one.
     *
     * Favourites are deliberately NOT part of the cached objects — they change
     * far more often than the app list, and re-applying them on read is cheaper
     * than throwing the whole scan away.
     */
    @Volatile
    private var cachedApps: List<AppInfo>? = null

    /**
     * Scans the device and builds the UI model.
     *
     * Runs on [Dispatchers.IO]: reading + rasterising a few hundred icons is
     * far too slow for the main thread and would drop D-Pad input frames.
     */
    suspend fun loadInstalledApps(forceRescan: Boolean = false): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val cached = cachedApps
            if (!forceRescan && cached != null) {
                return@withContext cached.withFavorites()
            }
            scanInstalledApps().also { cachedApps = it }.withFavorites()
        }

    /** True when a previous scan is still usable. */
    fun hasCache(): Boolean = cachedApps != null

    /** Drops the cache so the next load rescans. */
    fun invalidateCache() {
        cachedApps = null
    }

    /** Re-applies the current favourites onto a cached list. */
    private fun List<AppInfo>.withFavorites(): List<AppInfo> {
        val favorites = favoritesStore.getFavorites()
        return map { it.copy(isFavorite = it.packageName in favorites) }
    }

    private fun scanInstalledApps(): List<AppInfo> {
        val ownPackage = context.packageName

        // 1. Which packages have a classic phone launcher entry?
        val mobileEntries = queryLaunchable(Intent.CATEGORY_LAUNCHER)
        // 2. Which packages are already visible on the TV home screen?
        val leanbackEntries = queryLaunchable(Intent.CATEGORY_LEANBACK_LAUNCHER)

        // 3. Enumerate everything, then keep what we can actually start.
        val installed: List<ApplicationInfo> = runCatching {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        return installed.asSequence()
            .filter { it.packageName != ownPackage }
            .filter { it.packageName in mobileEntries || it.packageName in leanbackEntries }
            .map { info ->
                val icon = bannerFactory.extractIcon(info)
                val generated = info.packageName.startsWith(
                    ShortcutApkBuilder.STUB_PACKAGE_PREFIX
                )
                val target = if (generated) readShortcutTarget(info.packageName) else null
                // One PackageInfo lookup instead of two: it is a binder call per
                // package, and there can be hundreds of them.
                val packageDetails = packageDetailsOf(info.packageName)
                AppInfo(
                    packageName = info.packageName,
                    // Generated wrappers carry a space-padded label (a side effect
                    // of the fixed-length manifest patch), so trim it here.
                    label = runCatching { packageManager.getApplicationLabel(info).toString() }
                        .getOrDefault(info.packageName)
                        .trim(),
                    versionName = packageDetails.versionName,
                    installedAt = packageDetails.installedAt,
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                        (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0,
                    hasLeanbackEntry = info.packageName in leanbackEntries,
                    hasMobileEntry = info.packageName in mobileEntries,
                    icon = icon,
                    accentColor = bannerFactory.accentColorOf(icon),
                    // Filled in by withFavorites() on read.
                    isFavorite = false,
                    isGeneratedShortcut = generated,
                    shortcutTarget = target,
                    shortcutTargetMissing = generated &&
                        (target == null || !isInstalled(target))
                )
            }
            // Case-insensitive alphabetical order — easiest to scan with a remote.
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** Returns the set of package names owning an activity for [category]. */
    private fun queryLaunchable(category: String): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val resolved: List<ResolveInfo> = runCatching {
            packageManager.queryIntentActivities(intent, 0)
        }.getOrDefault(emptyList())
        return resolved.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    /**
     * Reads the target package out of a generated shortcut's own assets.
     *
     * The wrapper stores it in `assets/target.txt`; reading it back lets the UI
     * say which app a shortcut opens instead of showing an opaque hashed name.
     */
    private fun readShortcutTarget(packageName: String): String? = runCatching {
        val resources = packageManager.getResourcesForApplication(packageName)
        resources.assets.open("target.txt").bufferedReader().use { it.readText() }.trim()
            .takeIf { it.isNotBlank() }
    }.getOrNull()

    /** Version string plus install time, read in a single PackageManager call. */
    private fun packageDetailsOf(packageName: String): PackageDetails = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        PackageDetails(info.versionName.orEmpty(), info.firstInstallTime)
    }.getOrDefault(PackageDetails("", 0L))

    /** Whether [packageName] is present on the device at all. */
    private fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
    }.isSuccess

    private data class PackageDetails(val versionName: String, val installedAt: Long)

    // ---------------------------------------------------------------------
    //  Launching
    // ---------------------------------------------------------------------

    /**
     * Builds the intent that actually starts [packageName].
     *
     * Order matters: we prefer the leanback entry point when one exists (it is
     * the TV-optimised activity), otherwise we fall back to the phone launcher
     * activity, which is what every sideloaded app provides.
     *
     * Returns `null` when the package exposes no launchable activity at all.
     */
    fun resolveLaunchIntent(packageName: String): Intent? {
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

    /** Opens the system "App info" screen for [packageName]. */
    fun openSystemSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Asks the system to uninstall [packageName] (shows the standard dialog). */
    fun requestUninstall(packageName: String) {
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    // ---------------------------------------------------------------------
    //  Favourites
    // ---------------------------------------------------------------------

    fun toggleFavorite(packageName: String): Boolean = favoritesStore.toggle(packageName)
}
