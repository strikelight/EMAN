package com.esde.emulatormanager.data.model

/**
 * Represents a device fingerprint for profile association.
 * Uses Build properties to uniquely identify a device.
 */
data class DeviceFingerprint(
    val manufacturer: String,
    val model: String,
    val device: String,
    val hardware: String,
    val fingerprint: String  // SHA-256 hash of combined properties
)

/**
 * Lightweight shortcut data for Android apps (no Drawable icons for serialization)
 */
data class AndroidShortcutData(
    val packageName: String,
    val appName: String,
    val fileName: String  // e.g., "Game Name.app"
)

/**
 * Lightweight shortcut data for Windows games
 */
data class WindowsShortcutData(
    val id: String,
    val name: String,
    val executablePath: String,
    val launcherPackage: String,
    val launcherName: String,
    val fileName: String  // e.g., "Game Name.desktop" or "Game Name.gamehub"
)

/**
 * Lightweight shortcut data for Steam games
 */
data class SteamShortcutData(
    val appId: Int,
    val name: String,
    val launcherPackage: String,
    val fileName: String  // e.g., "Game Name.steam"
)

/**
 * Lightweight shortcut data for GOG games (.gog files)
 */
data class GogShortcutData(
    val productId: Long,
    val name: String,
    val fileName: String  // e.g., "Game Name.gog"
)

/**
 * Lightweight shortcut data for Epic games (.epic files)
 */
data class EpicShortcutData(
    val name: String,
    val internalId: String,  // content of the .epic file (internal ID)
    val fileName: String     // e.g., "Game Name.epic"
)

/**
 * The actual configuration data stored in a profile.
 * Only stores user-specified custom paths, not ES-DE resolved paths.
 * This allows profiles to work across devices with different ES-DE configurations.
 */
data class ProfileConfiguration(
    val customEmulators: List<CustomEmulatorMapping> = emptyList(),
    val appClassificationOverrides: Map<String, AppCategory> = emptyMap(),
    // User-specified custom paths (override ES-DE config)
    // These are only set if the user explicitly configured a custom path in the app
    val customGamesPath: String? = null,
    val customAppsPath: String? = null,
    val customEmulatorsPath: String? = null,
    val customWindowsPath: String? = null,
    // IGDB / Twitch API credentials for metadata fetching
    val igdbClientId: String? = null,
    val igdbClientSecret: String? = null,
    // Shortcuts
    val androidGameShortcuts: List<AndroidShortcutData> = emptyList(),
    val androidAppShortcuts: List<AndroidShortcutData> = emptyList(),
    val androidEmulatorShortcuts: List<AndroidShortcutData> = emptyList(),
    val windowsGameShortcuts: List<WindowsShortcutData> = emptyList(),
    val steamGameShortcuts: List<SteamShortcutData> = emptyList(),
    val gogGameShortcuts: List<GogShortcutData> = emptyList(),
    val epicGameShortcuts: List<EpicShortcutData> = emptyList()
) {
    /** Total count of all shortcuts */
    val totalShortcuts: Int
        get() = androidGameShortcuts.size + androidAppShortcuts.size +
                androidEmulatorShortcuts.size + windowsGameShortcuts.size +
                steamGameShortcuts.size + gogGameShortcuts.size + epicGameShortcuts.size
}

/**
 * Represents a saved profile configuration
 */
data class Profile(
    val id: String,
    val name: String,
    val deviceFingerprint: DeviceFingerprint? = null,
    val createdAt: Long,
    val modifiedAt: Long,
    val autoSave: Boolean = false,
    val configuration: ProfileConfiguration
) {
    /** Get a summary of what's in this profile */
    fun getSummary(): String {
        val parts = mutableListOf<String>()
        val config = configuration

        if (config.androidGameShortcuts.isNotEmpty()) {
            parts.add("${config.androidGameShortcuts.size} games")
        }
        if (config.androidAppShortcuts.isNotEmpty()) {
            parts.add("${config.androidAppShortcuts.size} apps")
        }
        if (config.androidEmulatorShortcuts.isNotEmpty()) {
            parts.add("${config.androidEmulatorShortcuts.size} emulators")
        }
        if (config.windowsGameShortcuts.isNotEmpty() || config.steamGameShortcuts.isNotEmpty() ||
            config.gogGameShortcuts.isNotEmpty() || config.epicGameShortcuts.isNotEmpty()) {
            val windowsTotal = config.windowsGameShortcuts.size + config.steamGameShortcuts.size +
                    config.gogGameShortcuts.size + config.epicGameShortcuts.size
            parts.add("$windowsTotal Windows games")
        }

        return if (parts.isEmpty()) "Empty profile" else parts.joinToString(", ")
    }
}

/**
 * Container for all profiles (JSON serialization format)
 */
data class ProfilesContainer(
    val version: Int = 1,
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null
)

/**
 * Result of loading a profile, with details about what was changed
 */
data class ProfileLoadResult(
    val success: Boolean,
    val profileName: String,
    val androidGamesCleared: Int = 0,
    val androidAppsCleared: Int = 0,
    val androidEmulatorsCleared: Int = 0,
    val windowsGamesCleared: Int = 0,
    val steamGamesCleared: Int = 0,
    val gogGamesCleared: Int = 0,
    val epicGamesCleared: Int = 0,
    val androidGamesRestored: Int = 0,
    val androidAppsRestored: Int = 0,
    val androidEmulatorsRestored: Int = 0,
    val windowsGamesRestored: Int = 0,
    val steamGamesRestored: Int = 0,
    val gogGamesRestored: Int = 0,
    val epicGamesRestored: Int = 0,
    val errorMessage: String? = null
) {
    fun getSummary(): String {
        if (!success) return errorMessage ?: "Failed to load profile"

        val cleared = androidGamesCleared + androidAppsCleared + androidEmulatorsCleared +
                windowsGamesCleared + steamGamesCleared + gogGamesCleared + epicGamesCleared
        val restored = androidGamesRestored + androidAppsRestored + androidEmulatorsRestored +
                windowsGamesRestored + steamGamesRestored + gogGamesRestored + epicGamesRestored

        return "Loaded '$profileName': cleared $cleared shortcuts, restored $restored shortcuts"
    }
}

/**
 * UI state for profile management
 */
data class ProfilesUiState(
    val isLoading: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    val activeProfile: Profile? = null,
    val currentDeviceFingerprint: DeviceFingerprint? = null,
    val currentDeviceName: String = "",
    val error: String? = null,
    val successMessage: String? = null,
    // Dialogs
    val showCreateDialog: Boolean = false,
    val showRenameDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val showLoadConfirmDialog: Boolean = false,
    val profileToEdit: Profile? = null,
    // Device switch detection
    val showDeviceSwitchPrompt: Boolean = false,
    val matchingProfiles: List<Profile> = emptyList(),
    // ES-DE status
    val esdeConfigured: Boolean = true
)
