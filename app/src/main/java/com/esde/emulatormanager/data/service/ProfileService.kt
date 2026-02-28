package com.esde.emulatormanager.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.esde.emulatormanager.data.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing profile CRUD operations.
 * Profiles are stored in ES-DE/profiles/profiles.json on external storage,
 * making them accessible across devices sharing the same SD card.
 */
@Singleton
class ProfileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esdeConfigService: EsdeConfigService,
    private val customEmulatorService: CustomEmulatorService,
    private val androidGamesService: AndroidGamesService,
    private val windowsGamesService: WindowsGamesService,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val igdbService: IgdbService,
    private val vitaGamesService: VitaGamesService
) {
    companion object {
        private const val TAG = "ProfileService"
        private const val PROFILES_FOLDER = "profiles"
        private const val PROFILES_FILE = "profiles.json"
        private const val BACKUP_FOLDER = "backups"
        // Android shortcut extensions - ES-DE recognizes both .app and .android
        private const val ANDROID_EXTENSION_APP = ".app"
        private const val ANDROID_EXTENSION_ANDROID = ".android"
        private const val DESKTOP_EXTENSION = ".desktop"
        private const val GAMEHUB_EXTENSION = ".gamehub"
        private const val STEAM_EXTENSION = ".steam"
        private const val GOG_EXTENSION = ".gog"
        private const val EPIC_EXTENSION = ".epic"
        private const val VITA_EXTENSION = ".psvita"

        /** Check if a filename is an Android shortcut file */
        private fun isAndroidShortcut(fileName: String): Boolean {
            return fileName.endsWith(ANDROID_EXTENSION_APP) || fileName.endsWith(ANDROID_EXTENSION_ANDROID)
        }
    }

    private var cachedContainer: ProfilesContainer? = null

    /**
     * Check if a package is installed on this device.
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    // ==================== Directory Management ====================

    /**
     * Get the profiles directory, creating if necessary.
     */
    private fun getProfilesDirectory(): File? {
        val esdeDir = esdeConfigService.findEsdeDirectory() ?: return null
        val profilesDir = File(esdeDir, PROFILES_FOLDER)
        if (!profilesDir.exists()) {
            if (!profilesDir.mkdirs()) {
                Log.e(TAG, "Could not create profiles directory: ${profilesDir.absolutePath}")
                return null
            }
        }
        return profilesDir
    }

    /**
     * Get the profiles file.
     */
    private fun getProfilesFile(): File? {
        val profilesDir = getProfilesDirectory() ?: return null
        return File(profilesDir, PROFILES_FILE)
    }

    /**
     * Check if ES-DE is configured (directory exists).
     */
    fun isEsdeConfigured(): Boolean {
        return esdeConfigService.findEsdeDirectory() != null
    }

    // ==================== Profile CRUD Operations ====================

    /**
     * Load all profiles from storage.
     */
    fun loadProfiles(): ProfilesContainer {
        cachedContainer?.let { return it }

        val file = getProfilesFile()
        if (file == null || !file.exists()) {
            return ProfilesContainer().also { cachedContainer = it }
        }

        return try {
            val json = file.readText()
            parseProfilesContainer(json).also { cachedContainer = it }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading profiles", e)
            ProfilesContainer().also { cachedContainer = it }
        }
    }

    /**
     * Save profiles container to storage.
     */
    private fun saveProfiles(container: ProfilesContainer) {
        val file = getProfilesFile()
        if (file == null) {
            Log.e(TAG, "Could not get profiles file for saving")
            return
        }

        try {
            val json = serializeProfilesContainer(container)
            file.writeText(json)
            cachedContainer = container
            Log.d(TAG, "Saved profiles to: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving profiles", e)
        }
    }

    /**
     * Create a new profile from current configuration.
     */
    fun createProfile(name: String, associateWithDevice: Boolean = true): ConfigResult<Profile> {
        if (!isEsdeConfigured()) {
            return ConfigResult.Error("ES-DE is not configured. Please set up ES-DE first.")
        }

        val config = captureCurrentConfiguration()
        val fingerprint = if (associateWithDevice) {
            deviceIdentificationService.getDeviceFingerprint()
        } else null

        val now = System.currentTimeMillis()
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name,
            deviceFingerprint = fingerprint,
            createdAt = now,
            modifiedAt = now,
            configuration = config
        )

        val container = loadProfiles()
        val updatedContainer = container.copy(
            profiles = container.profiles + profile
        )
        saveProfiles(updatedContainer)

        Log.d(TAG, "Created profile: $name with ${config.totalShortcuts} shortcuts")
        return ConfigResult.Success(profile)
    }

    /**
     * Load/apply a profile (replaces current configuration).
     * Returns detailed results showing what was cleared and restored.
     */
    fun loadProfile(profileId: String): ConfigResult<ProfileLoadResult> {
        val container = loadProfiles()
        val profile = container.profiles.find { it.id == profileId }
            ?: return ConfigResult.Error("Profile not found")

        // Backup current configuration before loading
        backupCurrentConfiguration()

        // Apply profile configuration and collect results
        val applyResult = applyConfiguration(profile.configuration)

        // Update active profile
        val updatedContainer = container.copy(activeProfileId = profileId)
        saveProfiles(updatedContainer)

        Log.d(TAG, "Loaded profile: ${profile.name}")

        val loadResult = ProfileLoadResult(
            success = true,
            profileName = profile.name,
            androidGamesCleared = applyResult.gamesResult.cleared,
            androidAppsCleared = applyResult.appsResult.cleared,
            androidEmulatorsCleared = applyResult.emulatorsResult.cleared,
            windowsGamesCleared = applyResult.windowsResult.cleared,
            steamGamesCleared = applyResult.steamResult.cleared,
            gogGamesCleared = applyResult.gogResult.cleared,
            epicGamesCleared = applyResult.epicResult.cleared,
            vitaGamesCleared = applyResult.vitaResult.cleared,
            androidGamesRestored = applyResult.gamesResult.restored,
            androidAppsRestored = applyResult.appsResult.restored,
            androidEmulatorsRestored = applyResult.emulatorsResult.restored,
            windowsGamesRestored = applyResult.windowsResult.restored,
            steamGamesRestored = applyResult.steamResult.restored,
            gogGamesRestored = applyResult.gogResult.restored,
            epicGamesRestored = applyResult.epicResult.restored,
            vitaGamesRestored = applyResult.vitaResult.restored
        )

        return ConfigResult.Success(loadResult)
    }

    /**
     * Save current configuration to an existing profile.
     */
    fun saveToProfile(profileId: String): ConfigResult<Unit> {
        val container = loadProfiles()
        val profileIndex = container.profiles.indexOfFirst { it.id == profileId }

        if (profileIndex < 0) {
            return ConfigResult.Error("Profile not found")
        }

        val config = captureCurrentConfiguration()
        val updatedProfile = container.profiles[profileIndex].copy(
            configuration = config,
            modifiedAt = System.currentTimeMillis()
        )

        val updatedProfiles = container.profiles.toMutableList()
        updatedProfiles[profileIndex] = updatedProfile

        saveProfiles(container.copy(profiles = updatedProfiles))
        Log.d(TAG, "Saved current config to profile: ${updatedProfile.name}")
        return ConfigResult.Success(Unit)
    }

    /**
     * Rename a profile.
     */
    fun renameProfile(profileId: String, newName: String): ConfigResult<Unit> {
        val container = loadProfiles()
        val profileIndex = container.profiles.indexOfFirst { it.id == profileId }

        if (profileIndex < 0) {
            return ConfigResult.Error("Profile not found")
        }

        val updatedProfile = container.profiles[profileIndex].copy(
            name = newName,
            modifiedAt = System.currentTimeMillis()
        )

        val updatedProfiles = container.profiles.toMutableList()
        updatedProfiles[profileIndex] = updatedProfile

        saveProfiles(container.copy(profiles = updatedProfiles))
        return ConfigResult.Success(Unit)
    }

    /**
     * Delete a profile.
     */
    fun deleteProfile(profileId: String): ConfigResult<Unit> {
        val container = loadProfiles()
        val updatedProfiles = container.profiles.filter { it.id != profileId }

        val updatedActiveId = if (container.activeProfileId == profileId) null
        else container.activeProfileId

        saveProfiles(container.copy(
            profiles = updatedProfiles,
            activeProfileId = updatedActiveId
        ))
        return ConfigResult.Success(Unit)
    }

    /**
     * Associate/disassociate a profile with the current device.
     */
    fun setProfileDeviceAssociation(profileId: String, associate: Boolean): ConfigResult<Unit> {
        val container = loadProfiles()
        val profileIndex = container.profiles.indexOfFirst { it.id == profileId }

        if (profileIndex < 0) {
            return ConfigResult.Error("Profile not found")
        }

        val fingerprint = if (associate) {
            deviceIdentificationService.getDeviceFingerprint()
        } else null

        val updatedProfile = container.profiles[profileIndex].copy(
            deviceFingerprint = fingerprint,
            modifiedAt = System.currentTimeMillis()
        )

        val updatedProfiles = container.profiles.toMutableList()
        updatedProfiles[profileIndex] = updatedProfile

        saveProfiles(container.copy(profiles = updatedProfiles))
        return ConfigResult.Success(Unit)
    }

    /**
     * Toggle auto-save for a profile.
     */
    fun setProfileAutoSave(profileId: String, autoSave: Boolean): ConfigResult<Unit> {
        val container = loadProfiles()
        val profileIndex = container.profiles.indexOfFirst { it.id == profileId }

        if (profileIndex < 0) {
            return ConfigResult.Error("Profile not found")
        }

        val updatedProfile = container.profiles[profileIndex].copy(autoSave = autoSave)
        val updatedProfiles = container.profiles.toMutableList()
        updatedProfiles[profileIndex] = updatedProfile

        saveProfiles(container.copy(profiles = updatedProfiles))
        return ConfigResult.Success(Unit)
    }

    // ==================== Profile Queries ====================

    /**
     * Get profiles associated with the current device.
     */
    fun getProfilesForCurrentDevice(): List<Profile> {
        val container = loadProfiles()
        val currentFingerprint = deviceIdentificationService.getDeviceFingerprint()

        return container.profiles.filter { profile ->
            deviceIdentificationService.fingerprintsMatch(
                profile.deviceFingerprint,
                currentFingerprint
            )
        }
    }

    /**
     * Get the active profile (if any).
     */
    fun getActiveProfile(): Profile? {
        val container = loadProfiles()
        return container.activeProfileId?.let { id ->
            container.profiles.find { it.id == id }
        }
    }

    /**
     * Check if there are profiles for a different device.
     */
    fun hasProfilesForDifferentDevice(): Boolean {
        val container = loadProfiles()
        val currentFingerprint = deviceIdentificationService.getDeviceFingerprint()

        return container.profiles.any { profile ->
            profile.deviceFingerprint != null &&
                    !deviceIdentificationService.fingerprintsMatch(
                        profile.deviceFingerprint,
                        currentFingerprint
                    )
        }
    }

    // ==================== Configuration Capture ====================

    /**
     * Capture the current app configuration including all shortcuts.
     * Only saves user-specified custom paths, not ES-DE resolved paths.
     * This allows profiles to work across devices with different ES-DE configurations.
     */
    fun captureCurrentConfiguration(): ProfileConfiguration {
        // Only capture user-specified custom paths (not ES-DE defaults)
        val customGames = androidGamesService.customGamesPath
        val customApps = androidGamesService.customAppsPath
        val customEmulators = androidGamesService.customEmulatorsPath
        val customWindows = windowsGamesService.customWindowsPath
        val customVita = vitaGamesService.customVitaPath

        // Capture IGDB credentials (if configured)
        val igdbClientId = igdbService.getClientId()
        val igdbClientSecret = if (igdbService.hasCredentials()) {
            // Read the secret via shared prefs directly — same prefs key as IgdbService uses
            context.getSharedPreferences("igdb_prefs", Context.MODE_PRIVATE)
                .getString("client_secret", null)
        } else null

        Log.d(TAG, "Capturing configuration with custom paths only:")
        Log.d(TAG, "  Custom Games: $customGames")
        Log.d(TAG, "  Custom Apps: $customApps")
        Log.d(TAG, "  Custom Emulators: $customEmulators")
        Log.d(TAG, "  Custom Windows: $customWindows")
        Log.d(TAG, "  Custom Vita: $customVita")
        Log.d(TAG, "  IGDB Client ID: ${if (igdbClientId != null) "(set)" else "(not set)"}")

        return ProfileConfiguration(
            customEmulators = customEmulatorService.getAllCustomEmulators(),
            appClassificationOverrides = androidGamesService.getClassificationOverrides(),
            // Only save user-specified custom paths (not ES-DE defaults)
            customGamesPath = customGames,
            customAppsPath = customApps,
            customEmulatorsPath = customEmulators,
            customWindowsPath = customWindows,
            // IGDB credentials
            igdbClientId = igdbClientId,
            igdbClientSecret = igdbClientSecret,
            // Shortcuts - captured from current paths (custom or ES-DE config)
            androidGameShortcuts = captureAndroidShortcuts(AndroidTab.GAMES),
            androidAppShortcuts = captureAndroidShortcuts(AndroidTab.APPS),
            androidEmulatorShortcuts = captureAndroidShortcuts(AndroidTab.EMULATORS),
            windowsGameShortcuts = captureWindowsShortcuts(),
            steamGameShortcuts = captureSteamShortcuts(),
            gogGameShortcuts = captureGogShortcuts(),
            epicGameShortcuts = captureEpicShortcuts(),
            // PS Vita
            customVitaPath = customVita,
            vitaGameShortcuts = captureVitaShortcuts()
        )
    }

    /**
     * Capture Android shortcuts for a specific tab.
     * Uses AndroidGamesService.getInstalledApps() which already knows which apps
     * are added to ES-DE for each tab - this ensures the profile matches the UI.
     */
    private fun captureAndroidShortcuts(tab: AndroidTab): List<AndroidShortcutData> {
        // Get the list of installed apps with their ES-DE status from AndroidGamesService
        val installedApps = androidGamesService.getInstalledApps()

        // Filter to only apps that are in ES-DE for this specific tab
        val appsInTab = installedApps.filter { it.isInEsdeForTab(tab) }

        // Convert to shortcut data
        val shortcuts = appsInTab.map { app ->
            // Generate filename the same way AndroidGamesService does
            val safeFileName = app.appName
                .replace(Regex("[^a-zA-Z0-9\\s]"), "")
                .replace(Regex("\\s+"), "_")
                .trim('_')

            AndroidShortcutData(
                packageName = app.packageName,
                appName = app.appName,
                fileName = "$safeFileName.app"
            )
        }

        Log.d(TAG, "Captured ${shortcuts.size} Android shortcuts for ${tab.name} (from installed apps)")
        return shortcuts
    }

    /**
     * Capture Windows game shortcuts (.desktop files).
     * Uses WindowsGamesService.getEsdeWindowsPath() for proper path resolution.
     */
    private fun captureWindowsShortcuts(): List<WindowsShortcutData> {
        val path = windowsGamesService.getEsdeWindowsPath()
        if (path == null) {
            Log.w(TAG, "Could not find Windows path for shortcut capture")
            return emptyList()
        }

        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Log.d(TAG, "Windows shortcuts directory does not exist: $path")
            return emptyList()
        }

        val shortcuts = mutableListOf<WindowsShortcutData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(DESKTOP_EXTENSION) || file.name.endsWith(GAMEHUB_EXTENSION)) {
                try {
                    val content = file.readText()
                    val shortcut = parseDesktopFile(content, file.name)
                    if (shortcut != null) {
                        shortcuts.add(shortcut)
                        Log.d(TAG, "Captured Windows shortcut: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Windows shortcut: ${file.name}", e)
                }
            }
        }

        Log.d(TAG, "Captured ${shortcuts.size} Windows shortcuts from $path")
        return shortcuts
    }

    /**
     * Capture Steam game shortcuts (.steam files).
     * Uses WindowsGamesService.getEsdeWindowsPath() for proper path resolution.
     */
    private fun captureSteamShortcuts(): List<SteamShortcutData> {
        val path = windowsGamesService.getEsdeWindowsPath()
        if (path == null) {
            Log.w(TAG, "Could not find Windows path for Steam shortcut capture")
            return emptyList()
        }

        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Log.d(TAG, "Steam shortcuts directory does not exist: $path")
            return emptyList()
        }

        val shortcuts = mutableListOf<SteamShortcutData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(STEAM_EXTENSION)) {
                try {
                    val appId = file.readText().trim().toIntOrNull()
                    if (appId != null) {
                        shortcuts.add(SteamShortcutData(
                            appId = appId,
                            name = file.nameWithoutExtension,
                            launcherPackage = "com.nickmafra.gamehublite", // Default launcher
                            fileName = file.name
                        ))
                        Log.d(TAG, "Captured Steam shortcut: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Steam shortcut: ${file.name}", e)
                }
            }
        }

        Log.d(TAG, "Captured ${shortcuts.size} Steam shortcuts from $path")
        return shortcuts
    }

    /**
     * Capture GOG game shortcuts (.gog files).
     */
    private fun captureGogShortcuts(): List<GogShortcutData> {
        val path = windowsGamesService.getEsdeWindowsPath() ?: return emptyList()
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val shortcuts = mutableListOf<GogShortcutData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(GOG_EXTENSION)) {
                try {
                    val productId = file.readText().trim().toLongOrNull()
                    if (productId != null) {
                        shortcuts.add(GogShortcutData(
                            productId = productId,
                            name = file.nameWithoutExtension,
                            fileName = file.name
                        ))
                        Log.d(TAG, "Captured GOG shortcut: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading GOG shortcut: ${file.name}", e)
                }
            }
        }
        Log.d(TAG, "Captured ${shortcuts.size} GOG shortcuts from $path")
        return shortcuts
    }

    /**
     * Capture Epic game shortcuts (.epic files).
     */
    private fun captureEpicShortcuts(): List<EpicShortcutData> {
        val path = windowsGamesService.getEsdeWindowsPath() ?: return emptyList()
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val shortcuts = mutableListOf<EpicShortcutData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(EPIC_EXTENSION)) {
                try {
                    val internalId = file.readText().trim()
                    shortcuts.add(EpicShortcutData(
                        name = file.nameWithoutExtension,
                        internalId = internalId,
                        fileName = file.name
                    ))
                    Log.d(TAG, "Captured Epic shortcut: ${file.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Epic shortcut: ${file.name}", e)
                }
            }
        }
        Log.d(TAG, "Captured ${shortcuts.size} Epic shortcuts from $path")
        return shortcuts
    }

    /**
     * Capture PS Vita game shortcuts (.psvita files).
     */
    private fun captureVitaShortcuts(): List<VitaShortcutData> {
        val path = vitaGamesService.getEsdeVitaPath() ?: return emptyList()
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        val shortcuts = mutableListOf<VitaShortcutData>()
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(VITA_EXTENSION, ignoreCase = true)) {
                try {
                    val titleId = file.readText().trim()
                    if (titleId.isNotBlank()) {
                        shortcuts.add(VitaShortcutData(
                            titleId = titleId,
                            displayName = file.nameWithoutExtension,
                            fileName = file.name
                        ))
                        Log.d(TAG, "Captured PS Vita shortcut: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading PS Vita shortcut: ${file.name}", e)
                }
            }
        }
        Log.d(TAG, "Captured ${shortcuts.size} PS Vita shortcuts from $path")
        return shortcuts
    }

    /**
     * Parse a .desktop file to extract shortcut data.
     */
    private fun parseDesktopFile(content: String, fileName: String): WindowsShortcutData? {
        var name = fileName.removeSuffix(DESKTOP_EXTENSION).removeSuffix(GAMEHUB_EXTENSION)
        var execPath = ""
        var launcherPackage = ""
        var launcherName = ""

        content.lines().forEach { line ->
            when {
                line.startsWith("Name=") -> name = line.substringAfter("Name=")
                line.startsWith("Exec=") -> execPath = line.substringAfter("Exec=")
                line.startsWith("X-Launcher-Package=") -> launcherPackage = line.substringAfter("X-Launcher-Package=")
                line.startsWith("X-Launcher-Name=") -> launcherName = line.substringAfter("X-Launcher-Name=")
            }
        }

        if (execPath.isBlank()) return null

        return WindowsShortcutData(
            id = fileName.substringBeforeLast("."),
            name = name,
            executablePath = execPath,
            launcherPackage = launcherPackage,
            launcherName = launcherName,
            fileName = fileName
        )
    }

    /**
     * Find default ROM path for a system folder.
     */
    private fun findDefaultPath(systemFolder: String): String? {
        val esdeDir = esdeConfigService.findEsdeDirectory() ?: return null
        val romsDir = File(esdeDir, "roms")
        if (!romsDir.exists()) return null

        val systemDir = File(romsDir, systemFolder)
        return if (systemDir.exists()) systemDir.absolutePath else null
    }

    // ==================== Configuration Application ====================

    /**
     * Data class to hold results from applying a configuration
     */
    data class ApplyResult(
        val gamesResult: RestoreResult,
        val appsResult: RestoreResult,
        val emulatorsResult: RestoreResult,
        val windowsResult: RestoreResult,
        val steamResult: RestoreResult,
        val gogResult: RestoreResult,
        val epicResult: RestoreResult,
        val vitaResult: RestoreResult
    )

    /**
     * Apply a profile configuration (restore).
     * Returns detailed results of what was cleared and restored.
     *
     * Custom paths from the profile are applied first, then shortcuts are restored
     * to the appropriate locations (using custom paths if set, or ES-DE config paths).
     */
    private fun applyConfiguration(config: ProfileConfiguration): ApplyResult {
        Log.d(TAG, "=== applyConfiguration START ===")
        Log.d(TAG, "Profile shortcuts: Games=${config.androidGameShortcuts.size}, Apps=${config.androidAppShortcuts.size}, Emulators=${config.androidEmulatorShortcuts.size}")
        Log.d(TAG, "Windows shortcuts: ${config.windowsGameShortcuts.size}, Steam: ${config.steamGameShortcuts.size}, GOG: ${config.gogGameShortcuts.size}, Epic: ${config.epicGameShortcuts.size}")
        Log.d(TAG, "PS Vita shortcuts: ${config.vitaGameShortcuts.size}")

        // Start fresh diagnostic log
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        writeDiagnosticLog("========== Profile Load: $timestamp ==========", append = false)
        writeDiagnosticLog("Profile contains:")
        writeDiagnosticLog("  Android Games: ${config.androidGameShortcuts.size}")
        writeDiagnosticLog("  Android Apps: ${config.androidAppShortcuts.size}")
        writeDiagnosticLog("  Android Emulators: ${config.androidEmulatorShortcuts.size}")
        writeDiagnosticLog("  Windows Games: ${config.windowsGameShortcuts.size}")
        writeDiagnosticLog("  Steam Games: ${config.steamGameShortcuts.size}")
        writeDiagnosticLog("  GOG Games: ${config.gogGameShortcuts.size}")
        writeDiagnosticLog("  Epic Games: ${config.epicGameShortcuts.size}")
        writeDiagnosticLog("  PS Vita Games: ${config.vitaGameShortcuts.size}")
        writeDiagnosticLog("Profile custom paths (user-specified only):")
        writeDiagnosticLog("  Custom Games: ${config.customGamesPath ?: "(not set)"}")
        writeDiagnosticLog("  Custom Apps: ${config.customAppsPath ?: "(not set)"}")
        writeDiagnosticLog("  Custom Emulators: ${config.customEmulatorsPath ?: "(not set)"}")
        writeDiagnosticLog("  Custom Windows: ${config.customWindowsPath ?: "(not set)"}")
        writeDiagnosticLog("  Custom Vita: ${config.customVitaPath ?: "(not set)"}")

        // Apply custom paths first (before restoring shortcuts)
        // Only set if the profile has custom paths defined
        Log.d(TAG, "Applying custom paths from profile...")
        androidGamesService.customGamesPath = config.customGamesPath
        androidGamesService.customAppsPath = config.customAppsPath
        androidGamesService.customEmulatorsPath = config.customEmulatorsPath
        windowsGamesService.customWindowsPath = config.customWindowsPath
        vitaGamesService.customVitaPath = config.customVitaPath

        // Restore IGDB credentials if the profile has them
        val profileClientId = config.igdbClientId
        val profileClientSecret = config.igdbClientSecret
        if (!profileClientId.isNullOrBlank() && !profileClientSecret.isNullOrBlank()) {
            Log.d(TAG, "Restoring IGDB credentials from profile")
            igdbService.setCredentials(profileClientId, profileClientSecret)
        } else {
            Log.d(TAG, "Profile has no IGDB credentials — leaving existing credentials unchanged")
        }

        // Clear and restore custom emulators
        val existingCustom = customEmulatorService.getAllCustomEmulators()
        existingCustom.forEach { customEmulatorService.removeCustomEmulator(it.packageName) }
        config.customEmulators.forEach { customEmulatorService.addCustomEmulator(it) }

        // Clear and restore classification overrides
        val existingOverrides = androidGamesService.getClassificationOverrides()
        existingOverrides.keys.forEach { androidGamesService.removeClassificationOverride(it) }
        config.appClassificationOverrides.forEach { (pkg, category) ->
            androidGamesService.setClassificationOverride(pkg, category)
        }

        // Restore shortcuts (wipe and recreate), collecting results
        // Paths are determined by: 1) custom path if set, 2) current device's ES-DE config
        // We don't pass profile resolved paths - let the current device determine paths
        Log.d(TAG, "Restoring Android GAMES shortcuts...")
        val gamesResult = restoreAndroidShortcuts(AndroidTab.GAMES, config.androidGameShortcuts)
        Log.d(TAG, "Restoring Android APPS shortcuts...")
        val appsResult = restoreAndroidShortcuts(AndroidTab.APPS, config.androidAppShortcuts)
        Log.d(TAG, "Restoring Android EMULATORS shortcuts...")
        val emulatorsResult = restoreAndroidShortcuts(AndroidTab.EMULATORS, config.androidEmulatorShortcuts)
        Log.d(TAG, "Restoring Windows shortcuts...")
        val windowsResult = restoreWindowsShortcuts(config.windowsGameShortcuts)
        Log.d(TAG, "Restoring Steam shortcuts...")
        val steamResult = restoreSteamShortcuts(config.steamGameShortcuts)
        Log.d(TAG, "Restoring GOG shortcuts...")
        val gogResult = restoreGogShortcuts(config.gogGameShortcuts)
        Log.d(TAG, "Restoring Epic shortcuts...")
        val epicResult = restoreEpicShortcuts(config.epicGameShortcuts)
        Log.d(TAG, "Restoring PS Vita shortcuts...")
        val vitaResult = restoreVitaShortcuts(config.vitaGameShortcuts)

        Log.d(TAG, "=== applyConfiguration END - Applied ${config.totalShortcuts} total shortcuts ===")

        return ApplyResult(gamesResult, appsResult, emulatorsResult, windowsResult, steamResult, gogResult, epicResult, vitaResult)
    }

    /**
     * Get the ES-DE configured path for an Android tab.
     * Uses the same approach as WindowsGamesService: ES-DE config first, then fallbacks.
     */
    private fun getEsdeAndroidPath(tab: AndroidTab): String? {
        // System names as they appear in ES-DE config
        val systemNames = when (tab) {
            AndroidTab.GAMES -> listOf("androidgames", "android")
            AndroidTab.APPS -> listOf("android", "androidapps")
            AndroidTab.EMULATORS -> listOf("emulators")
        }

        // First priority: user-specified custom path
        val customPath = when (tab) {
            AndroidTab.GAMES -> androidGamesService.customGamesPath
            AndroidTab.APPS -> androidGamesService.customAppsPath
            AndroidTab.EMULATORS -> androidGamesService.customEmulatorsPath
        }
        if (!customPath.isNullOrBlank()) {
            Log.d(TAG, "Using custom path for ${tab.name}: $customPath")
            return customPath
        }

        // Second: try to get the path from ES-DE's configuration (same as Windows games)
        for (systemName in systemNames) {
            val configuredPath = esdeConfigService.getSystemRomPath(systemName)
            if (configuredPath != null) {
                Log.d(TAG, "Found ES-DE configured path for $systemName: $configuredPath")
                return configuredPath
            }
        }

        // Fallback: use AndroidGamesService's path resolution
        val fallbackPath = androidGamesService.getPathForTab(tab)
        Log.d(TAG, "Using fallback path for ${tab.name}: $fallbackPath")
        return fallbackPath
    }

    /**
     * Get all possible paths where Android shortcuts might be stored for a tab.
     * Primary source is ES-DE configuration (same method as Windows games).
     */
    private fun getAllPossibleAndroidPaths(tab: AndroidTab): List<File> {
        val paths = mutableListOf<File>()

        // Primary: Get the ES-DE configured path (this is what Windows games uses)
        val esdePath = getEsdeAndroidPath(tab)
        if (esdePath != null) {
            paths.add(File(esdePath))
        }

        // Also add the path from AndroidGamesService in case it differs
        val servicePath = androidGamesService.getPathForTab(tab)
        if (servicePath != null && servicePath != esdePath) {
            paths.add(File(servicePath))
        }

        val result = paths.filter { it.exists() && it.isDirectory }.distinctBy { it.absolutePath }
        Log.d(TAG, "getAllPossibleAndroidPaths for ${tab.name}: found ${result.size} existing directories")
        result.forEach { Log.d(TAG, "  - ${it.absolutePath}") }
        return result
    }

    /**
     * Result of restoring shortcuts for one category
     */
    data class RestoreResult(val cleared: Int, val restored: Int)

    /**
     * Write diagnostic information to a file the user can check.
     * File is written to ES-DE/profiles/profile_restore_log.txt
     */
    private fun writeDiagnosticLog(message: String, append: Boolean = true) {
        try {
            val profilesDir = getProfilesDirectory() ?: return
            val logFile = File(profilesDir, "profile_restore_log.txt")
            if (append) {
                logFile.appendText("$message\n")
            } else {
                logFile.writeText("$message\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing diagnostic log", e)
        }
    }

    /**
     * Restore Android shortcuts for a specific tab.
     * Wipes existing shortcuts from ALL possible locations, then creates new ones.
     * Uses current device's ES-DE config (or custom path if set) to determine paths.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreAndroidShortcuts(tab: AndroidTab, shortcuts: List<AndroidShortcutData>): RestoreResult {
        Log.d(TAG, "=== restoreAndroidShortcuts START for ${tab.name} ===")
        Log.d(TAG, "Profile contains ${shortcuts.size} shortcuts for ${tab.name}")
        writeDiagnosticLog("\n=== restoreAndroidShortcuts for ${tab.name} ===")
        writeDiagnosticLog("Profile contains ${shortcuts.size} shortcuts to restore")

        // Get paths from current device's ES-DE config (respects custom paths if set)
        val allPaths = getAllPossibleAndroidPaths(tab)

        Log.d(TAG, "Found ${allPaths.size} directories to clear for ${tab.name}")
        writeDiagnosticLog("Found ${allPaths.size} directories to scan:")
        allPaths.forEach { writeDiagnosticLog("  - ${it.absolutePath}") }

        var totalDeleted = 0
        for (dir in allPaths) {
            Log.d(TAG, "Checking directory: ${dir.absolutePath}")
            val files = dir.listFiles() ?: continue
            val shortcutFiles = files.filter { isAndroidShortcut(it.name) }
            writeDiagnosticLog("Directory ${dir.absolutePath}: ${files.size} total files, ${shortcutFiles.size} shortcuts")

            for (file in shortcutFiles) {
                // Check for both .app and .android extensions
                writeDiagnosticLog("  Deleting: ${file.name}")
                Log.d(TAG, "Deleting Android shortcut: ${file.absolutePath}")
                if (file.delete()) {
                    totalDeleted++
                } else {
                    writeDiagnosticLog("    FAILED to delete!")
                }
            }
        }
        Log.d(TAG, "Cleared $totalDeleted total Android shortcuts (.app and .android) from all locations for ${tab.name}")
        writeDiagnosticLog("Total deleted for ${tab.name}: $totalDeleted")

        // Get path from current device's ES-DE config (respects custom paths if set)
        val primaryPath = getEsdeAndroidPath(tab)

        if (primaryPath == null) {
            Log.w(TAG, "Could not find path for ${tab.name}, skipping shortcut creation")
            writeDiagnosticLog("ERROR: Could not find path to create shortcuts for ${tab.name}")
            return RestoreResult(totalDeleted, 0)
        }
        writeDiagnosticLog("Creating ${shortcuts.size} shortcuts in: $primaryPath")

        val dir = File(primaryPath)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create directory: $primaryPath")
                return RestoreResult(totalDeleted, 0)
            }
        }

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                file.writeText(shortcut.packageName)
                createdCount++
                Log.d(TAG, "Restored Android shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Android shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount Android shortcuts for ${tab.name} in $primaryPath")
        Log.d(TAG, "=== restoreAndroidShortcuts END for ${tab.name} ===")

        return RestoreResult(totalDeleted, createdCount)
    }

    /**
     * Restore Windows game shortcuts.
     * Wipes existing .desktop files and creates new ones from profile data.
     * Uses current device's ES-DE config (or custom path if set) to determine paths.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreWindowsShortcuts(shortcuts: List<WindowsShortcutData>): RestoreResult {
        // Get path from current device's ES-DE config (respects custom paths if set)
        val path = windowsGamesService.getEsdeWindowsPath()

        if (path == null) {
            Log.w(TAG, "Could not find Windows path, skipping shortcut restore")
            return RestoreResult(0, 0)
        }

        Log.d(TAG, "Using Windows path: $path")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create Windows directory: $path")
                return RestoreResult(0, 0)
            }
        }

        // Wipe existing .desktop and .gamehub files
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(DESKTOP_EXTENSION) || file.name.endsWith(GAMEHUB_EXTENSION)) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted existing Windows shortcut: ${file.name}")
                }
            }
        }
        Log.d(TAG, "Cleared $deletedCount existing Windows shortcuts")

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                val content = buildString {
                    appendLine("[Desktop Entry]")
                    appendLine("Type=Application")
                    appendLine("Name=${shortcut.name}")
                    appendLine("Exec=${shortcut.executablePath}")
                    appendLine("X-Launcher-Package=${shortcut.launcherPackage}")
                    appendLine("X-Launcher-Name=${shortcut.launcherName}")
                    appendLine("Terminal=false")
                    appendLine("Categories=Game;")
                }
                file.writeText(content)
                createdCount++
                Log.d(TAG, "Restored Windows shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Windows shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount Windows shortcuts")

        return RestoreResult(deletedCount, createdCount)
    }

    /**
     * Restore Steam game shortcuts.
     * Wipes existing .steam files and creates new ones from profile data.
     * Uses current device's ES-DE config (or custom path if set) to determine paths.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreSteamShortcuts(shortcuts: List<SteamShortcutData>): RestoreResult {
        // Get path from current device's ES-DE config (respects custom paths if set)
        val path = windowsGamesService.getEsdeWindowsPath()

        if (path == null) {
            Log.w(TAG, "Could not find Windows path, skipping Steam shortcut restore")
            return RestoreResult(0, 0)
        }

        Log.d(TAG, "Using Steam path: $path")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create Steam directory: $path")
                return RestoreResult(0, 0)
            }
        }

        // Wipe existing .steam files
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(STEAM_EXTENSION)) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted existing Steam shortcut: ${file.name}")
                }
            }
        }
        Log.d(TAG, "Cleared $deletedCount existing Steam shortcuts")

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                file.writeText(shortcut.appId.toString())
                createdCount++
                Log.d(TAG, "Restored Steam shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Steam shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount Steam shortcuts")

        return RestoreResult(deletedCount, createdCount)
    }

    /**
     * Restore GOG game shortcuts.
     * Wipes existing .gog files and creates new ones from profile data.
     * Uses current device's ES-DE config (or custom path if set) to determine paths.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreGogShortcuts(shortcuts: List<GogShortcutData>): RestoreResult {
        val path = windowsGamesService.getEsdeWindowsPath()

        if (path == null) {
            Log.w(TAG, "Could not find Windows path, skipping GOG shortcut restore")
            return RestoreResult(0, 0)
        }

        Log.d(TAG, "Using GOG path: $path")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create GOG directory: $path")
                return RestoreResult(0, 0)
            }
        }

        // Wipe existing .gog files
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(GOG_EXTENSION)) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted existing GOG shortcut: ${file.name}")
                }
            }
        }
        Log.d(TAG, "Cleared $deletedCount existing GOG shortcuts")

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                file.writeText(shortcut.productId.toString())
                createdCount++
                Log.d(TAG, "Restored GOG shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating GOG shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount GOG shortcuts")

        return RestoreResult(deletedCount, createdCount)
    }

    /**
     * Restore Epic game shortcuts.
     * Wipes existing .epic files and creates new ones from profile data.
     * Uses current device's ES-DE config (or custom path if set) to determine paths.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreEpicShortcuts(shortcuts: List<EpicShortcutData>): RestoreResult {
        val path = windowsGamesService.getEsdeWindowsPath()

        if (path == null) {
            Log.w(TAG, "Could not find Windows path, skipping Epic shortcut restore")
            return RestoreResult(0, 0)
        }

        Log.d(TAG, "Using Epic path: $path")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create Epic directory: $path")
                return RestoreResult(0, 0)
            }
        }

        // Wipe existing .epic files
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(EPIC_EXTENSION)) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted existing Epic shortcut: ${file.name}")
                }
            }
        }
        Log.d(TAG, "Cleared $deletedCount existing Epic shortcuts")

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                file.writeText(shortcut.internalId)
                createdCount++
                Log.d(TAG, "Restored Epic shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating Epic shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount Epic shortcuts")

        return RestoreResult(deletedCount, createdCount)
    }

    /**
     * Restore PS Vita game shortcuts.
     * Wipes existing .psvita files and creates new ones from profile data.
     * Uses current device's Vita path (custom or auto-detected) to determine location.
     * Returns counts of cleared and restored shortcuts.
     */
    private fun restoreVitaShortcuts(shortcuts: List<VitaShortcutData>): RestoreResult {
        val path = vitaGamesService.getEsdeVitaPath()

        if (path == null) {
            Log.w(TAG, "Could not find PS Vita path, skipping shortcut restore")
            return RestoreResult(0, 0)
        }

        Log.d(TAG, "Using PS Vita path: $path")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "Could not create PS Vita directory: $path")
                return RestoreResult(0, 0)
            }
        }

        // Wipe existing .psvita files
        var deletedCount = 0
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(VITA_EXTENSION, ignoreCase = true)) {
                if (file.delete()) {
                    deletedCount++
                    Log.d(TAG, "Deleted existing PS Vita shortcut: ${file.name}")
                }
            }
        }
        Log.d(TAG, "Cleared $deletedCount existing PS Vita shortcuts")

        // Create shortcuts from profile
        var createdCount = 0
        shortcuts.forEach { shortcut ->
            try {
                val file = File(dir, shortcut.fileName)
                file.writeText(shortcut.titleId)
                createdCount++
                Log.d(TAG, "Restored PS Vita shortcut: ${shortcut.fileName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating PS Vita shortcut: ${shortcut.fileName}", e)
            }
        }
        Log.d(TAG, "Restored $createdCount PS Vita shortcuts")

        return RestoreResult(deletedCount, createdCount)
    }

    // ==================== Backup ====================

    /**
     * Backup current configuration before loading a profile.
     */
    private fun backupCurrentConfiguration() {
        val profilesDir = getProfilesDirectory() ?: return
        val backupDir = File(profilesDir, BACKUP_FOLDER)
        if (!backupDir.exists()) backupDir.mkdirs()

        val timestamp = System.currentTimeMillis()
        val backupFile = File(backupDir, "backup_$timestamp.json")

        try {
            val config = captureCurrentConfiguration()
            val json = serializeConfigurationToJson(config).toString(2)
            backupFile.writeText(json)
            Log.d(TAG, "Backed up configuration to: ${backupFile.absolutePath}")

            // Keep only the last 5 backups
            cleanupOldBackups(backupDir)
        } catch (e: Exception) {
            Log.e(TAG, "Error backing up configuration", e)
        }
    }

    /**
     * Remove old backups, keeping only the last 5.
     */
    private fun cleanupOldBackups(backupDir: File) {
        val backups = backupDir.listFiles()
            ?.filter { it.name.startsWith("backup_") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.name }
            ?: return

        if (backups.size > 5) {
            backups.drop(5).forEach { it.delete() }
        }
    }

    // ==================== JSON Serialization ====================

    /**
     * Helper to get nullable string from JSONObject.
     * Android's optString with null default can return "null" string, not actual null.
     */
    private fun JSONObject.optStringOrNull(key: String): String? {
        return if (has(key) && !isNull(key)) getString(key) else null
    }

    private fun parseProfilesContainer(json: String): ProfilesContainer {
        val jsonObject = JSONObject(json)
        val version = jsonObject.optInt("version", 1)
        val activeProfileId = jsonObject.optStringOrNull("activeProfileId")
        val profilesArray = jsonObject.optJSONArray("profiles") ?: JSONArray()

        val profiles = mutableListOf<Profile>()
        for (i in 0 until profilesArray.length()) {
            profiles.add(parseProfile(profilesArray.getJSONObject(i)))
        }

        return ProfilesContainer(
            version = version,
            profiles = profiles,
            activeProfileId = activeProfileId
        )
    }

    private fun parseProfile(json: JSONObject): Profile {
        val fingerprintJson = json.optJSONObject("deviceFingerprint")
        val fingerprint = fingerprintJson?.let {
            DeviceFingerprint(
                manufacturer = it.optString("manufacturer", ""),
                model = it.optString("model", ""),
                device = it.optString("device", ""),
                hardware = it.optString("hardware", ""),
                fingerprint = it.optString("fingerprint", "")
            )
        }

        return Profile(
            id = json.getString("id"),
            name = json.getString("name"),
            deviceFingerprint = fingerprint,
            createdAt = json.optLong("createdAt", 0),
            modifiedAt = json.optLong("modifiedAt", 0),
            autoSave = json.optBoolean("autoSave", false),
            configuration = parseConfiguration(json.getJSONObject("configuration"))
        )
    }

    private fun parseConfiguration(json: JSONObject): ProfileConfiguration {
        // Parse custom emulators
        val customEmulators = mutableListOf<CustomEmulatorMapping>()
        val emulatorsArray = json.optJSONArray("customEmulators") ?: JSONArray()
        for (i in 0 until emulatorsArray.length()) {
            val emJson = emulatorsArray.getJSONObject(i)
            val systemsArray = emJson.optJSONArray("supportedSystems") ?: JSONArray()
            val systems = mutableListOf<String>()
            for (j in 0 until systemsArray.length()) {
                systems.add(systemsArray.getString(j))
            }
            customEmulators.add(CustomEmulatorMapping(
                packageName = emJson.getString("packageName"),
                appName = emJson.getString("appName"),
                activityName = emJson.optStringOrNull("activityName"),
                supportedSystems = systems,
                displayLabel = emJson.optStringOrNull("displayLabel")
            ))
        }

        // Parse classification overrides
        val overrides = mutableMapOf<String, AppCategory>()
        val overridesJson = json.optJSONObject("appClassificationOverrides")
        overridesJson?.keys()?.forEach { key ->
            try {
                overrides[key] = AppCategory.valueOf(overridesJson.getString(key))
            } catch (e: Exception) {
                // Skip invalid entries
            }
        }

        // Parse Android shortcuts
        val androidGameShortcuts = parseAndroidShortcuts(json.optJSONArray("androidGameShortcuts"))
        val androidAppShortcuts = parseAndroidShortcuts(json.optJSONArray("androidAppShortcuts"))
        val androidEmulatorShortcuts = parseAndroidShortcuts(json.optJSONArray("androidEmulatorShortcuts"))

        // Parse Windows shortcuts
        val windowsGameShortcuts = parseWindowsShortcuts(json.optJSONArray("windowsGameShortcuts"))
        val steamGameShortcuts = parseSteamShortcuts(json.optJSONArray("steamGameShortcuts"))
        val gogGameShortcuts = parseGogShortcuts(json.optJSONArray("gogGameShortcuts"))
        val epicGameShortcuts = parseEpicShortcuts(json.optJSONArray("epicGameShortcuts"))

        // Parse PS Vita shortcuts
        val vitaGameShortcuts = parseVitaShortcuts(json.optJSONArray("vitaGameShortcuts"))

        return ProfileConfiguration(
            customEmulators = customEmulators,
            appClassificationOverrides = overrides,
            // Custom paths (user overrides only)
            customGamesPath = json.optStringOrNull("customGamesPath"),
            customAppsPath = json.optStringOrNull("customAppsPath"),
            customEmulatorsPath = json.optStringOrNull("customEmulatorsPath"),
            customWindowsPath = json.optStringOrNull("customWindowsPath"),
            // IGDB credentials
            igdbClientId = json.optStringOrNull("igdbClientId"),
            igdbClientSecret = json.optStringOrNull("igdbClientSecret"),
            // Shortcuts
            androidGameShortcuts = androidGameShortcuts,
            androidAppShortcuts = androidAppShortcuts,
            androidEmulatorShortcuts = androidEmulatorShortcuts,
            windowsGameShortcuts = windowsGameShortcuts,
            steamGameShortcuts = steamGameShortcuts,
            gogGameShortcuts = gogGameShortcuts,
            epicGameShortcuts = epicGameShortcuts,
            // PS Vita
            customVitaPath = json.optStringOrNull("customVitaPath"),
            vitaGameShortcuts = vitaGameShortcuts
        )
    }

    private fun parseAndroidShortcuts(array: JSONArray?): List<AndroidShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<AndroidShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(AndroidShortcutData(
                packageName = json.getString("packageName"),
                appName = json.getString("appName"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun parseWindowsShortcuts(array: JSONArray?): List<WindowsShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<WindowsShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(WindowsShortcutData(
                id = json.getString("id"),
                name = json.getString("name"),
                executablePath = json.getString("executablePath"),
                launcherPackage = json.getString("launcherPackage"),
                launcherName = json.getString("launcherName"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun parseSteamShortcuts(array: JSONArray?): List<SteamShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<SteamShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(SteamShortcutData(
                appId = json.getInt("appId"),
                name = json.getString("name"),
                launcherPackage = json.getString("launcherPackage"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun parseGogShortcuts(array: JSONArray?): List<GogShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<GogShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(GogShortcutData(
                productId = json.getLong("productId"),
                name = json.getString("name"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun parseEpicShortcuts(array: JSONArray?): List<EpicShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<EpicShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(EpicShortcutData(
                name = json.getString("name"),
                internalId = json.getString("internalId"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun parseVitaShortcuts(array: JSONArray?): List<VitaShortcutData> {
        if (array == null) return emptyList()
        val shortcuts = mutableListOf<VitaShortcutData>()
        for (i in 0 until array.length()) {
            val json = array.getJSONObject(i)
            shortcuts.add(VitaShortcutData(
                titleId = json.getString("titleId"),
                displayName = json.getString("displayName"),
                fileName = json.getString("fileName")
            ))
        }
        return shortcuts
    }

    private fun serializeProfilesContainer(container: ProfilesContainer): String {
        val json = JSONObject()
        json.put("version", container.version)
        container.activeProfileId?.let { json.put("activeProfileId", it) }

        val profilesArray = JSONArray()
        container.profiles.forEach { profile ->
            profilesArray.put(serializeProfile(profile))
        }
        json.put("profiles", profilesArray)

        return json.toString(2)
    }

    private fun serializeProfile(profile: Profile): JSONObject {
        val json = JSONObject()
        json.put("id", profile.id)
        json.put("name", profile.name)
        json.put("createdAt", profile.createdAt)
        json.put("modifiedAt", profile.modifiedAt)
        json.put("autoSave", profile.autoSave)

        profile.deviceFingerprint?.let { fp ->
            val fpJson = JSONObject()
            fpJson.put("manufacturer", fp.manufacturer)
            fpJson.put("model", fp.model)
            fpJson.put("device", fp.device)
            fpJson.put("hardware", fp.hardware)
            fpJson.put("fingerprint", fp.fingerprint)
            json.put("deviceFingerprint", fpJson)
        }

        json.put("configuration", serializeConfigurationToJson(profile.configuration))
        return json
    }

    private fun serializeConfigurationToJson(config: ProfileConfiguration): JSONObject {
        val json = JSONObject()

        // Custom paths (user overrides only - not ES-DE resolved paths)
        config.customGamesPath?.let { json.put("customGamesPath", it) }
        config.customAppsPath?.let { json.put("customAppsPath", it) }
        config.customEmulatorsPath?.let { json.put("customEmulatorsPath", it) }
        config.customWindowsPath?.let { json.put("customWindowsPath", it) }
        config.customVitaPath?.let { json.put("customVitaPath", it) }

        // IGDB credentials
        config.igdbClientId?.let { json.put("igdbClientId", it) }
        config.igdbClientSecret?.let { json.put("igdbClientSecret", it) }

        // Custom emulators
        val emulatorsArray = JSONArray()
        config.customEmulators.forEach { em ->
            val emJson = JSONObject()
            emJson.put("packageName", em.packageName)
            emJson.put("appName", em.appName)
            em.activityName?.let { emJson.put("activityName", it) }
            em.displayLabel?.let { emJson.put("displayLabel", it) }
            val systemsArray = JSONArray()
            em.supportedSystems.forEach { systemsArray.put(it) }
            emJson.put("supportedSystems", systemsArray)
            emulatorsArray.put(emJson)
        }
        json.put("customEmulators", emulatorsArray)

        // Classification overrides
        val overridesJson = JSONObject()
        config.appClassificationOverrides.forEach { (pkg, cat) ->
            overridesJson.put(pkg, cat.name)
        }
        json.put("appClassificationOverrides", overridesJson)

        // Android shortcuts
        json.put("androidGameShortcuts", serializeAndroidShortcuts(config.androidGameShortcuts))
        json.put("androidAppShortcuts", serializeAndroidShortcuts(config.androidAppShortcuts))
        json.put("androidEmulatorShortcuts", serializeAndroidShortcuts(config.androidEmulatorShortcuts))

        // Windows shortcuts
        json.put("windowsGameShortcuts", serializeWindowsShortcuts(config.windowsGameShortcuts))
        json.put("steamGameShortcuts", serializeSteamShortcuts(config.steamGameShortcuts))
        json.put("gogGameShortcuts", serializeGogShortcuts(config.gogGameShortcuts))
        json.put("epicGameShortcuts", serializeEpicShortcuts(config.epicGameShortcuts))

        // PS Vita shortcuts
        json.put("vitaGameShortcuts", serializeVitaShortcuts(config.vitaGameShortcuts))

        return json
    }

    private fun serializeAndroidShortcuts(shortcuts: List<AndroidShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("packageName", shortcut.packageName)
            json.put("appName", shortcut.appName)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    private fun serializeWindowsShortcuts(shortcuts: List<WindowsShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("id", shortcut.id)
            json.put("name", shortcut.name)
            json.put("executablePath", shortcut.executablePath)
            json.put("launcherPackage", shortcut.launcherPackage)
            json.put("launcherName", shortcut.launcherName)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    private fun serializeSteamShortcuts(shortcuts: List<SteamShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("appId", shortcut.appId)
            json.put("name", shortcut.name)
            json.put("launcherPackage", shortcut.launcherPackage)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    private fun serializeGogShortcuts(shortcuts: List<GogShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("productId", shortcut.productId)
            json.put("name", shortcut.name)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    private fun serializeEpicShortcuts(shortcuts: List<EpicShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("name", shortcut.name)
            json.put("internalId", shortcut.internalId)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    private fun serializeVitaShortcuts(shortcuts: List<VitaShortcutData>): JSONArray {
        val array = JSONArray()
        shortcuts.forEach { shortcut ->
            val json = JSONObject()
            json.put("titleId", shortcut.titleId)
            json.put("displayName", shortcut.displayName)
            json.put("fileName", shortcut.fileName)
            array.put(json)
        }
        return array
    }

    /**
     * Invalidate the cached container (force reload on next access).
     */
    fun invalidateCache() {
        cachedContainer = null
    }
}
