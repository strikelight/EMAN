package com.esde.emulatormanager.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.esde.emulatormanager.data.KnownEmulators
import com.esde.emulatormanager.data.model.InstalledEmulator
import com.esde.emulatormanager.data.model.KnownEmulator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service to detect installed emulator apps on the device
 */
@Singleton
class EmulatorDetectionService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager

    /**
     * Get all installed apps that are known emulators
     * First checks exact package name matches, then uses fuzzy matching on all installed apps
     */
    fun getInstalledEmulators(): List<InstalledEmulator> {
        val installedEmulators = mutableListOf<InstalledEmulator>()
        val foundPackages = mutableSetOf<String>()

        // First pass: Check exact package name matches in KnownEmulators
        for (knownEmulator in KnownEmulators.emulators) {
            for (packageName in knownEmulator.packageNames) {
                // Skip packages already added (multiple KnownEmulator entries can share a package)
                if (foundPackages.contains(packageName)) break

                try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(
                            packageName,
                            PackageManager.ApplicationInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(packageName, 0)
                    }

                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    // Get the main activity
                    val activityName = getLaunchActivityForPackage(packageName)

                    installedEmulators.add(
                        InstalledEmulator(
                            packageName = packageName,
                            appName = appName,
                            activityName = activityName,
                            icon = icon
                        )
                    )
                    foundPackages.add(packageName)
                    break // Found this emulator, no need to check other package variants
                } catch (e: PackageManager.NameNotFoundException) {
                    // Package not installed, continue
                }
            }
        }

        // Second pass: Scan all installed apps and check for fuzzy matches
        // This catches emulator variants with different package names
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        for (app in apps) {
            val packageName = app.activityInfo.packageName
            // Skip if already found in first pass
            if (foundPackages.contains(packageName)) continue

            val appName = app.loadLabel(packageManager).toString()

            // Check if this app matches a known emulator via fuzzy matching
            val knownEmulator = KnownEmulators.findByPackageNameFuzzy(packageName, appName)
            if (knownEmulator != null) {
                val icon = try {
                    app.loadIcon(packageManager)
                } catch (e: Exception) {
                    null
                }

                installedEmulators.add(
                    InstalledEmulator(
                        packageName = packageName,
                        appName = appName,
                        activityName = app.activityInfo.name,
                        icon = icon
                    )
                )
                foundPackages.add(packageName)
            }
        }

        return installedEmulators
    }

    /**
     * Check if a specific package is installed
     */
    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Get installed emulators that support a specific system
     */
    fun getInstalledEmulatorsForSystem(systemId: String): List<InstalledEmulator> {
        val compatibleEmulators = KnownEmulators.getEmulatorsForSystem(systemId)
        val installed = mutableListOf<InstalledEmulator>()

        for (knownEmulator in compatibleEmulators) {
            for (packageName in knownEmulator.packageNames) {
                if (isPackageInstalled(packageName)) {
                    try {
                        val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getApplicationInfo(
                                packageName,
                                PackageManager.ApplicationInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getApplicationInfo(packageName, 0)
                        }

                        val appName = packageManager.getApplicationLabel(appInfo).toString()
                        val icon = try {
                            packageManager.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }

                        val activityName = getLaunchActivityForPackage(packageName)
                            ?: knownEmulator.activityName

                        installed.add(
                            InstalledEmulator(
                                packageName = packageName,
                                appName = appName,
                                activityName = activityName,
                                icon = icon
                            )
                        )
                        break
                    } catch (e: Exception) {
                        // Skip this one
                    }
                }
            }
        }

        return installed
    }

    /**
     * Get the launch activity for a package
     */
    private fun getLaunchActivityForPackage(packageName: String): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }

        val resolveInfoList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return resolveInfoList.firstOrNull()?.activityInfo?.name
    }

    /**
     * Get known emulator info for a package (using fuzzy matching)
     */
    fun getKnownEmulatorInfo(packageName: String, appName: String = ""): KnownEmulator? {
        return KnownEmulators.findByPackageNameFuzzy(packageName, appName)
    }

    /**
     * Generate the ES-DE package/activity entry format
     */
    fun generateEsdeEntry(packageName: String, activityName: String?): String {
        return if (activityName != null) {
            "$packageName/$activityName"
        } else {
            packageName
        }
    }

    /**
     * Scan for ALL installed apps that could potentially be emulators
     * This provides a fallback for emulators not in our known list
     */
    fun scanAllPotentialEmulators(): List<InstalledEmulator> {
        val emulatorKeywords = listOf(
            "emu", "emulator", "retro", "arcade", "mame", "dolphin", "citra",
            "yuzu", "ppsspp", "drastic", "duckstation", "pcsx", "snes", "nes",
            "genesis", "megadrive", "saturn", "dreamcast", "n64", "gba", "gbc",
            "scummvm", "dosbox", "flycast", "redream", "melonds", "desmume",
            "eden", "suyu", "sudachi", "citron", "lime3ds", "vita3k", "aethersx2",
            "nethersx2", "pizza", "myboy", "myoldboy", "snes9x", "mupen", "m64plus"
        )

        val potentialEmulators = mutableListOf<InstalledEmulator>()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        for (app in apps) {
            val packageName = app.activityInfo.packageName.lowercase()
            val appName = app.loadLabel(packageManager).toString().lowercase()

            // Check if package name or app name contains emulator-related keywords
            val isLikelyEmulator = emulatorKeywords.any { keyword ->
                packageName.contains(keyword) || appName.contains(keyword)
            }

            if (isLikelyEmulator) {
                // Skip if already in known emulators and detected (use fuzzy matching)
                val isKnown = KnownEmulators.findByPackageNameFuzzy(
                    app.activityInfo.packageName,
                    app.loadLabel(packageManager).toString()
                ) != null

                if (!isKnown) {
                    val icon = try {
                        app.loadIcon(packageManager)
                    } catch (e: Exception) {
                        null
                    }

                    potentialEmulators.add(
                        InstalledEmulator(
                            packageName = app.activityInfo.packageName,
                            appName = app.loadLabel(packageManager).toString(),
                            activityName = app.activityInfo.name,
                            icon = icon
                        )
                    )
                }
            }
        }

        return potentialEmulators
    }

    /**
     * Get ALL installed launchable apps on the device
     * Used for manually selecting any app as an emulator
     */
    fun getAllInstalledApps(): List<InstalledEmulator> {
        val allApps = mutableListOf<InstalledEmulator>()
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        for (app in apps) {
            val icon = try {
                app.loadIcon(packageManager)
            } catch (e: Exception) {
                null
            }

            allApps.add(
                InstalledEmulator(
                    packageName = app.activityInfo.packageName,
                    appName = app.loadLabel(packageManager).toString(),
                    activityName = app.activityInfo.name,
                    icon = icon
                )
            )
        }

        return allApps.sortedBy { it.appName.lowercase() }
    }

    /**
     * Get app info for a specific package name
     */
    fun getAppInfo(packageName: String): InstalledEmulator? {
        return try {
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }

            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val icon = try {
                packageManager.getApplicationIcon(appInfo)
            } catch (e: Exception) {
                null
            }
            val activityName = getLaunchActivityForPackage(packageName)

            InstalledEmulator(
                packageName = packageName,
                appName = appName,
                activityName = activityName,
                icon = icon
            )
        } catch (e: Exception) {
            null
        }
    }
}
