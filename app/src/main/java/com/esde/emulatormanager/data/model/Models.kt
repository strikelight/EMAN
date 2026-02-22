package com.esde.emulatormanager.data.model

/**
 * Represents an emulator entry in es_find_rules.xml
 */
data class EmulatorRule(
    val name: String,
    val entries: List<String> // Package/Activity entries like "com.retroarch.aarch64/..."
)

/**
 * Represents a gaming system in es_systems.xml
 */
data class GameSystem(
    val name: String,           // Short identifier (e.g., "nes", "snes")
    val fullName: String,       // Display name (e.g., "Nintendo Entertainment System")
    val path: String,           // ROM path
    val extensions: String,     // Supported file extensions
    val commands: List<EmulatorCommand>, // Launch commands for different emulators
    val platform: String,       // Platform classification
    val theme: String           // Theme identifier
)

/**
 * Represents a command entry for launching an emulator
 */
data class EmulatorCommand(
    val label: String?,         // Optional label for UI
    val command: String         // The actual command string
)

/**
 * Represents an installed emulator on the device
 */
data class InstalledEmulator(
    val packageName: String,
    val appName: String,
    val activityName: String?,
    val icon: android.graphics.drawable.Drawable?
)

/**
 * Known emulator definitions with their package info and supported systems
 */
data class KnownEmulator(
    val id: String,                     // Unique identifier
    val displayName: String,            // User-friendly name
    val packageNames: List<String>,     // Possible package names
    val activityName: String?,          // Main activity (if known)
    val supportedSystems: List<String>, // System IDs this emulator supports
    val isRetroArch: Boolean = false,   // Whether this is RetroArch
    val retroArchCore: String? = null,  // RetroArch core name if applicable
    val launchCommand: String? = null,  // Specific launch command override
    val emulationActivity: String? = null, // Activity for es_find_rules.xml (handles game launching)
    val supportedExtensions: String? = null // File extensions this emulator handles (e.g., ".steam" for GameHub Lite)
)

/**
 * Represents a system with its configured emulators and available alternatives
 */
data class SystemEmulatorConfig(
    val system: GameSystem,
    val configuredEmulators: List<String>,      // Currently configured emulator names
    val availableEmulators: List<InstalledEmulator>, // Installed emulators that support this system
    val isModified: Boolean = false
)

/**
 * Result of parsing ES-DE configuration files
 */
sealed class ConfigResult<out T> {
    data class Success<T>(val data: T) : ConfigResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : ConfigResult<Nothing>()
}

/**
 * UI state for the main screen
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val systems: List<SystemEmulatorConfig> = emptyList(),
    val error: String? = null,
    val successMessage: String? = null,
    val hasStoragePermission: Boolean = false,
    val esdeConfigPath: String? = null,
    val searchQuery: String = "",
    val selectedCategory: SystemCategory? = null
)

/**
 * Categories for organizing systems
 */
enum class SystemCategory(val displayName: String, val platforms: List<String>) {
    NINTENDO("Nintendo", listOf("nes", "snes", "n64", "gc", "wii", "wiiu", "switch", "gb", "gbc", "gba", "nds", "3ds", "virtualboy", "fds", "famicom", "superfamicom", "satellaview", "sufami")),
    SONY("Sony", listOf("psx", "ps2", "ps3", "psp", "psvita")),
    MICROSOFT("Microsoft", listOf("xbox", "xbox360")),
    SEGA("Sega", listOf("genesis", "megadrive", "mastersystem", "gamegear", "saturn", "dreamcast", "segacd", "sega32x", "sg-1000")),
    ATARI("Atari", listOf("atari2600", "atari5200", "atari7800", "atarilynx", "atarijaguar", "atarist")),
    ARCADE("Arcade", listOf("arcade", "mame", "fba", "fbneo", "neogeo", "neogeocd", "cps1", "cps2", "cps3", "naomi")),
    COMPUTERS("Computers", listOf("amiga", "amstradcpc", "c64", "dos", "msx", "msx2", "pc", "pc88", "pc98", "windows", "x68000", "zxspectrum")),
    HANDHELDS("Handhelds", listOf("wonderswan", "wonderswancolor", "ngp", "ngpc", "pokemini", "supervision")),
    OTHER("Other", emptyList())
}

/**
 * Represents a custom emulator mapping added by the user
 * Maps an app to specific systems it can emulate
 */
data class CustomEmulatorMapping(
    val packageName: String,
    val appName: String,
    val activityName: String?,
    val supportedSystems: List<String>,  // System IDs this emulator supports
    val displayLabel: String? = null     // Custom label for ES-DE menu
)

/**
 * Storage format for custom emulator mappings (serializable to JSON)
 */
data class CustomEmulatorConfig(
    val version: Int = 1,
    val mappings: List<CustomEmulatorMapping> = emptyList()
)

/**
 * Represents a Windows game shortcut for ES-DE
 */
data class WindowsGameShortcut(
    val id: String,                    // Unique identifier (filename without extension)
    val name: String,                  // Display name of the game
    val executablePath: String,        // Path to the executable or shortcut target
    val launcherPackage: String,       // Package name of the launcher (e.g., gamehub, winlator)
    val launcherName: String,          // Display name of the launcher
    val iconPath: String? = null,      // Path to game icon if available
    val isInEsde: Boolean = false,     // Whether shortcut exists in ES-DE ROM folder
    val shortcutFilePath: String? = null // Path to the .desktop/.gamehub file in ES-DE
)

/**
 * Represents a detected Windows game launcher and its games
 */
data class WindowsGameLauncher(
    val packageName: String,
    val displayName: String,
    val icon: android.graphics.drawable.Drawable?,
    val games: List<WindowsGameShortcut>,
    val dataPath: String?              // Path where this launcher stores game data
)

/**
 * UI state for Windows games management
 */
data class WindowsGamesUiState(
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val launchers: List<WindowsGameLauncher> = emptyList(),
    val shortcuts: List<WindowsGameShortcut> = emptyList(),
    val esdeWindowsPath: String? = null,
    val esdeMediaPath: String? = null, // Path where downloaded media (artwork) is saved
    val esdeSettingsFilePath: String? = null, // Path to es_settings.xml (for debugging)
    val error: String? = null,
    val successMessage: String? = null,
    val showPathSelectionDialog: Boolean = false,
    // Steam game search state
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SteamGame> = emptyList(),
    val showAddGameDialog: Boolean = false,
    // GOG game search state
    val gogSearchQuery: String = "",
    val gogSearchResults: List<GogGame> = emptyList(),
    val isSearchingGog: Boolean = false,
    // Epic game import state
    val epicImportPath: String = "",
    val epicFoundGames: List<EpicGame> = emptyList(),
    val isScanningEpic: Boolean = false,
    // ES-DE configuration state
    val isGameHubLiteConfigured: Boolean = false,
    val isGameNativeConfigured: Boolean = false,
    // Metadata scraping state
    val isScraping: Boolean = false,
    val scrapeProgress: ScrapeProgress? = null,
    val gamesWithoutMetadataCount: Int = 0,
    // Scrape options dialog state
    val showScrapeOptionsDialog: Boolean = false,
    val scrapeOptions: ScrapeOptions = ScrapeOptions(),
    // Re-scrape state - holds the game waiting for user confirmation/search term
    val pendingReScrapeGame: WindowsGameShortcut? = null
)

/**
 * Represents a Steam game from the Steam API
 */
data class SteamGame(
    val appId: Int,
    val name: String
)

/**
 * Represents a manually added Steam game for ES-DE
 */
data class SteamGameEntry(
    val appId: Int,
    val name: String,
    val launcherPackage: String,    // Which launcher to use (gamehub.lite, etc.)
    val artworkPath: String? = null // Path to downloaded artwork
)

/**
 * Represents a GOG game from the GOG API
 */
data class GogGame(
    val productId: Long,
    val name: String
)

/**
 * Represents an Epic game imported from GameNative export
 */
data class EpicGame(
    val internalId: String,  // GameNative's internal ID (from file content)
    val name: String,        // Game name (from filename)
    val sourcePath: String   // Original file path
)

/**
 * Enum for Windows game platforms
 */
enum class WindowsGamePlatform {
    STEAM,
    GOG,
    EPIC
}

/**
 * Represents an installed Android app/game that can be added to ES-DE
 */
data class AndroidGame(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val isGame: Boolean,           // Whether Android categorizes this as a game
    val isEmulator: Boolean = false, // Whether this is a known emulator app
    val isInEsde: Boolean = false, // Whether this app is already added to ES-DE (in any tab)
    val inEsdeTabs: Set<AndroidTab> = emptySet(), // Which tabs this app is added to
    val shortcutFilePath: String? = null, // Path to the .app file in ES-DE
    val hasUserOverride: Boolean = false // Whether user has manually classified this app
) {
    /** Check if this app is in ES-DE for a specific tab */
    fun isInEsdeForTab(tab: AndroidTab): Boolean = inEsdeTabs.contains(tab)
}

/**
 * Tab categories for the Android section
 */
enum class AndroidTab {
    GAMES,
    APPS,
    EMULATORS
}

/**
 * Represents what category a user has manually classified an app as
 */
enum class AppCategory {
    GAME,
    APP,
    EMULATOR
}

/**
 * Represents a user's manual classification override for an app
 */
data class AppClassificationOverride(
    val packageName: String,
    val category: AppCategory
)

/**
 * Represents a stale ES-DE shortcut (app no longer installed on device)
 */
data class StaleAndroidEntry(
    val fileName: String,          // The .app filename
    val displayName: String,       // Display name (from filename)
    val packageName: String,       // Package name stored in the file
    val filePath: String           // Full path to the .app file
)

/**
 * UI state for Android games management
 */
data class AndroidGamesUiState(
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val allApps: List<AndroidGame> = emptyList(),
    val games: List<AndroidGame> = emptyList(), // Filtered to show only games or all apps
    val staleEntries: List<StaleAndroidEntry> = emptyList(), // Shortcuts for uninstalled apps
    val esdeGamesPath: String? = null,      // Path to androidgames ROM folder
    val esdeAppsPath: String? = null,       // Path to android (apps) ROM folder
    val esdeEmulatorsPath: String? = null,  // Path to emulators ROM folder
    val error: String? = null,
    val successMessage: String? = null,
    val showAllApps: Boolean = false, // Toggle between showing category-specific vs all apps
    val searchQuery: String = "",
    val selectedTab: AndroidTab = AndroidTab.GAMES, // Currently selected tab
    // Metadata scraping state
    val isScraping: Boolean = false,
    val scrapeProgress: ScrapeProgress? = null,
    val gamesWithoutMetadataCount: Int = 0,
    val hasIgdbCredentials: Boolean = false,
    // Re-scrape state - when a single game re-scrape fails, show dialog to refine search
    val pendingReScrapeGame: AndroidGame? = null
)

