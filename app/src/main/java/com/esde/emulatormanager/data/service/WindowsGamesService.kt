package com.esde.emulatormanager.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import com.esde.emulatormanager.data.model.ConfigResult
import com.esde.emulatormanager.data.model.EpicGame
import com.esde.emulatormanager.data.model.GameSystem
import com.esde.emulatormanager.data.model.GogGame
import com.esde.emulatormanager.data.model.SteamGame
import com.esde.emulatormanager.data.model.WindowsGameLauncher
import com.esde.emulatormanager.data.model.WindowsGamePlatform
import com.esde.emulatormanager.data.model.WindowsGameShortcut
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Windows game shortcuts for ES-DE.
 * Scans for games installed via GameHub, Winlator, etc. and creates
 * shortcut files that ES-DE can use to launch them.
 */
@Singleton
class WindowsGamesService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esdeConfigService: EsdeConfigService
) {
    companion object {
        private const val TAG = "WindowsGamesService"

        // Known Windows game launcher packages and their data paths
        private val LAUNCHER_INFO = mapOf(
            "com.nickmafra.gamehub" to LauncherInfo("GameHub", "GameHub"),
            "com.nickmafra.gamehublite" to LauncherInfo("GameHub Lite", "GameHubLite"),
            "gamehub.lite" to LauncherInfo("GameHub Lite", "GameHubLite"),
            "app.gamenative" to LauncherInfo("GameNative", "GameNative"),
            "com.nickmafra.gamenative" to LauncherInfo("GameNative", "GameNative"),  // Legacy package name
            "com.winlator" to LauncherInfo("Winlator", "winlator"),
            "com.winlator.debug" to LauncherInfo("Winlator Debug", "winlator"),
            "com.xtr3d.mobox" to LauncherInfo("Mobox", "mobox")
        )

        // ES-DE windows ROM folder name
        private const val ESDE_WINDOWS_FOLDER = "windows"

        // Shortcut file extensions
        private const val SHORTCUT_EXTENSION = ".desktop"
        private const val STEAM_EXTENSION = ".steam"
        private const val GOG_EXTENSION = ".gog"
        private const val EPIC_EXTENSION = ".epic"

        // GameNative export formats (for import detection)
        private const val GAMENATIVE_GOG_EXTENSION = "zst"  // GameNative GOG files end with "zst" (no dot)
        private const val GAMENATIVE_EPIC_EXTENSION = ".epicgame"
    }

    private data class LauncherInfo(
        val displayName: String,
        val dataFolderName: String
    )

    // SharedPreferences for persistent storage
    private val prefs = context.getSharedPreferences("windows_games_prefs", Context.MODE_PRIVATE)
    private val PREF_CUSTOM_WINDOWS_PATH = "custom_windows_path"

    /**
     * User-specified custom path override. When set, this takes precedence over
     * ES-DE config and fallback paths. Stored in SharedPreferences for persistence.
     */
    var customWindowsPath: String?
        get() = prefs.getString(PREF_CUSTOM_WINDOWS_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) {
                    putString(PREF_CUSTOM_WINDOWS_PATH, value)
                } else {
                    remove(PREF_CUSTOM_WINDOWS_PATH)
                }
                apply()
            }
        }

    /**
     * Set a custom Windows ROMs path that overrides automatic detection.
     * The path is persisted across app restarts.
     */
    fun setCustomPath(path: String?) {
        customWindowsPath = path
    }

    /**
     * Get installed Windows game launchers
     */
    fun getInstalledLaunchers(): List<WindowsGameLauncher> {
        val pm = context.packageManager
        val launchers = mutableListOf<WindowsGameLauncher>()

        for ((packageName, info) in LAUNCHER_INFO) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    null
                }

                // For GameHub Lite, scan ES-DE Windows ROMs folder for .steam files
                // instead of scanning the launcher's data folder
                val isGameHubLite = packageName == "gamehub.lite" || packageName == "com.nickmafra.gamehublite"
                val dataPath: String?
                val games: List<WindowsGameShortcut>

                if (isGameHubLite) {
                    // Use ES-DE Windows ROMs path for GameHub Lite
                    dataPath = getEsdeWindowsPath()
                    games = if (dataPath != null) {
                        scanSteamGamesInFolder(packageName, info.displayName, dataPath)
                    } else {
                        emptyList()
                    }
                } else {
                    // For other launchers, use their data folder
                    dataPath = findLauncherDataPath(packageName, info.dataFolderName)
                    games = if (dataPath != null) {
                        scanLauncherGames(packageName, info.displayName, dataPath)
                    } else {
                        emptyList()
                    }
                }

                launchers.add(
                    WindowsGameLauncher(
                        packageName = packageName,
                        displayName = appName,
                        icon = icon,
                        games = games,
                        dataPath = dataPath
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Launcher not installed, skip
            }
        }

        return launchers
    }

    /**
     * Scan a folder for .steam, .gog, and .epic files (game shortcuts)
     */
    private fun scanSteamGamesInFolder(
        launcherPackage: String,
        launcherName: String,
        folderPath: String
    ): List<WindowsGameShortcut> {
        val games = mutableListOf<WindowsGameShortcut>()
        val folder = File(folderPath)

        if (!folder.exists() || !folder.isDirectory) return games

        // Scan for all supported shortcut types: .steam, .gog, .epic
        val shortcutFiles = folder.listFiles { file ->
            file.isFile && (
                file.name.endsWith(STEAM_EXTENSION, ignoreCase = true) ||
                file.name.endsWith(GOG_EXTENSION, ignoreCase = true) ||
                file.name.endsWith(EPIC_EXTENSION, ignoreCase = true)
            )
        } ?: return games

        for (shortcutFile in shortcutFiles) {
            try {
                val platformId = shortcutFile.readText().trim()

                // Determine the platform and launcher based on file extension
                val platform = detectGamePlatform(shortcutFile)
                val actualLauncherName = when (platform) {
                    WindowsGamePlatform.STEAM -> "Steam (GameHub Lite)"
                    WindowsGamePlatform.GOG -> "GOG (GameNative)"
                    WindowsGamePlatform.EPIC -> "Epic (GameNative)"
                    else -> launcherName
                }

                val actualLauncherPackage = when (platform) {
                    WindowsGamePlatform.STEAM -> launcherPackage
                    WindowsGamePlatform.GOG, WindowsGamePlatform.EPIC -> "app.gamenative"
                    else -> launcherPackage
                }

                val gameName = getGameFileName(shortcutFile)
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                games.add(
                    WindowsGameShortcut(
                        id = getGameFileName(shortcutFile),
                        name = gameName,
                        executablePath = platformId, // Store platform ID as the "executable"
                        launcherPackage = actualLauncherPackage,
                        launcherName = actualLauncherName,
                        isInEsde = true, // Already in ES-DE since it's in the ROMs folder
                        shortcutFilePath = shortcutFile.absolutePath
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading shortcut file: ${shortcutFile.name}", e)
            }
        }

        return games
    }

    /**
     * Find the data path for a launcher
     */
    private fun findLauncherDataPath(packageName: String, folderName: String): String? {
        val possiblePaths = listOf(
            // External storage paths
            File(Environment.getExternalStorageDirectory(), folderName),
            File(Environment.getExternalStorageDirectory(), "Android/data/$packageName/files"),
            File(Environment.getExternalStorageDirectory(), "Android/data/$packageName"),
            // Common game locations
            File(Environment.getExternalStorageDirectory(), "Games/$folderName"),
            File(Environment.getExternalStorageDirectory(), "$folderName/games")
        )

        for (path in possiblePaths) {
            if (path.exists() && path.isDirectory) {
                return path.absolutePath
            }
        }

        return null
    }

    /**
     * Scan a launcher's data directory for games
     */
    private fun scanLauncherGames(
        launcherPackage: String,
        launcherName: String,
        dataPath: String
    ): List<WindowsGameShortcut> {
        val games = mutableListOf<WindowsGameShortcut>()
        val dataDir = File(dataPath)

        if (!dataDir.exists()) return games

        // Look for executable files and common game indicators
        scanDirectory(dataDir, launcherPackage, launcherName, games, 0, 3)

        return games
    }

    /**
     * Recursively scan directory for games (with depth limit)
     */
    private fun scanDirectory(
        dir: File,
        launcherPackage: String,
        launcherName: String,
        games: MutableList<WindowsGameShortcut>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory) return

        val files = dir.listFiles() ?: return

        for (file in files) {
            when {
                file.isDirectory -> {
                    // Check if this directory looks like a game folder
                    val hasExe = file.listFiles()?.any {
                        it.name.endsWith(".exe", ignoreCase = true)
                    } == true

                    if (hasExe) {
                        // Found a game directory
                        val gameName = file.name
                            .replace("_", " ")
                            .replace("-", " ")
                            .trim()

                        val exeFile = file.listFiles()?.find {
                            it.name.endsWith(".exe", ignoreCase = true) &&
                            !it.name.contains("unins", ignoreCase = true) &&
                            !it.name.contains("setup", ignoreCase = true)
                        }

                        if (exeFile != null) {
                            val shortcutId = generateShortcutId(gameName)
                            val existingShortcut = findExistingShortcut(shortcutId)

                            games.add(
                                WindowsGameShortcut(
                                    id = shortcutId,
                                    name = gameName,
                                    executablePath = exeFile.absolutePath,
                                    launcherPackage = launcherPackage,
                                    launcherName = launcherName,
                                    iconPath = findGameIcon(file),
                                    isInEsde = existingShortcut != null,
                                    shortcutFilePath = existingShortcut
                                )
                            )
                        }
                    } else {
                        // Continue scanning subdirectories
                        scanDirectory(file, launcherPackage, launcherName, games, currentDepth + 1, maxDepth)
                    }
                }
                file.name.endsWith(".exe", ignoreCase = true) -> {
                    // Standalone exe file
                    if (!file.name.contains("unins", ignoreCase = true) &&
                        !file.name.contains("setup", ignoreCase = true)) {

                        val gameName = file.nameWithoutExtension
                            .replace("_", " ")
                            .replace("-", " ")
                            .trim()

                        val shortcutId = generateShortcutId(gameName)
                        val existingShortcut = findExistingShortcut(shortcutId)

                        games.add(
                            WindowsGameShortcut(
                                id = shortcutId,
                                name = gameName,
                                executablePath = file.absolutePath,
                                launcherPackage = launcherPackage,
                                launcherName = launcherName,
                                isInEsde = existingShortcut != null,
                                shortcutFilePath = existingShortcut
                            )
                        )
                    }
                }
            }
        }
    }

    /**
     * Find game icon in directory
     */
    private fun findGameIcon(gameDir: File): String? {
        val iconExtensions = listOf(".ico", ".png", ".jpg", ".jpeg", ".bmp")
        val iconNames = listOf("icon", "game", gameDir.name.lowercase())

        for (file in gameDir.listFiles() ?: emptyArray()) {
            val nameLower = file.name.lowercase()
            if (iconExtensions.any { nameLower.endsWith(it) }) {
                if (iconNames.any { nameLower.contains(it) }) {
                    return file.absolutePath
                }
            }
        }

        // Check for any icon file
        for (file in gameDir.listFiles() ?: emptyArray()) {
            if (iconExtensions.any { file.name.lowercase().endsWith(it) }) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * Generate a safe shortcut ID from game name
     */
    private fun generateShortcutId(gameName: String): String {
        return gameName
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }

    /**
     * Get the ES-DE windows ROM path.
     * Priority: 1) User-specified custom path, 2) ES-DE config, 3) Common fallback paths.
     */
    fun getEsdeWindowsPath(): String? {
        // First priority: user-specified custom path
        customWindowsPath?.let { path ->
            if (path.isNotBlank()) {
                return path
            }
        }

        // Second: try to get the path from ES-DE's configuration
        val configuredPath = esdeConfigService.getSystemRomPath(ESDE_WINDOWS_FOLDER)
        if (configuredPath != null) {
            return configuredPath
        }

        // Fallback to searching common paths
        val possiblePaths = listOf(
            File(Environment.getExternalStorageDirectory(), "ROMs/$ESDE_WINDOWS_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "Roms/$ESDE_WINDOWS_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "ES-DE/ROMs/$ESDE_WINDOWS_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "ES-DE/Roms/$ESDE_WINDOWS_FOLDER")
        )

        for (path in possiblePaths) {
            if (path.exists()) {
                return path.absolutePath
            }
        }

        // Return default path even if it doesn't exist yet
        return File(Environment.getExternalStorageDirectory(), "ROMs/$ESDE_WINDOWS_FOLDER").absolutePath
    }

    /**
     * Find existing shortcut file for a game
     */
    private fun findExistingShortcut(shortcutId: String): String? {
        val windowsPath = getEsdeWindowsPath() ?: return null
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return null

        val shortcutFile = File(windowsDir, "$shortcutId$SHORTCUT_EXTENSION")
        return if (shortcutFile.exists()) shortcutFile.absolutePath else null
    }

    /**
     * Get all existing shortcuts in ES-DE windows folder
     * Includes .desktop, .steam, .gog, and .epic files
     */
    fun getExistingShortcuts(): List<WindowsGameShortcut> {
        val windowsPath = getEsdeWindowsPath() ?: return emptyList()
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return emptyList()

        val shortcuts = mutableListOf<WindowsGameShortcut>()

        for (file in windowsDir.listFiles() ?: emptyArray()) {
            when {
                file.name.endsWith(SHORTCUT_EXTENSION) -> {
                    val shortcut = parseShortcutFile(file)
                    if (shortcut != null) {
                        shortcuts.add(shortcut)
                    }
                }
                file.name.endsWith(STEAM_EXTENSION) ||
                file.name.endsWith(GOG_EXTENSION) ||
                file.name.endsWith(EPIC_EXTENSION) -> {
                    val shortcut = parsePlatformShortcutFile(file)
                    if (shortcut != null) {
                        shortcuts.add(shortcut)
                    }
                }
            }
        }

        return shortcuts
    }

    /**
     * Parse a platform-specific shortcut file (.steam, .gog, .epic)
     */
    private fun parsePlatformShortcutFile(file: File): WindowsGameShortcut? {
        try {
            val platformId = file.readText().trim()
            val platform = detectGamePlatform(file)

            val launcherName = when (platform) {
                WindowsGamePlatform.STEAM -> "Steam (GameHub Lite)"
                WindowsGamePlatform.GOG -> "GOG (GameNative)"
                WindowsGamePlatform.EPIC -> "Epic (GameNative)"
                else -> "Unknown"
            }

            val launcherPackage = when (platform) {
                WindowsGamePlatform.STEAM -> "gamehub.lite"
                WindowsGamePlatform.GOG, WindowsGamePlatform.EPIC -> "app.gamenative"
                else -> ""
            }

            val gameName = getGameFileName(file)
                .replace("_", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            return WindowsGameShortcut(
                id = getGameFileName(file),
                name = gameName,
                executablePath = platformId,
                launcherPackage = launcherPackage,
                launcherName = launcherName,
                isInEsde = true,
                shortcutFilePath = file.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing platform shortcut file: ${file.name}", e)
            return null
        }
    }

    /**
     * Parse a .desktop shortcut file
     */
    private fun parseShortcutFile(file: File): WindowsGameShortcut? {
        try {
            val content = file.readText()
            val lines = content.lines()

            var name = file.nameWithoutExtension
            var execPath = ""
            var launcherPackage = ""
            var launcherName = ""
            var iconPath: String? = null

            for (line in lines) {
                when {
                    line.startsWith("Name=") -> name = line.substringAfter("Name=")
                    line.startsWith("Exec=") -> execPath = line.substringAfter("Exec=")
                    line.startsWith("X-Launcher-Package=") -> launcherPackage = line.substringAfter("X-Launcher-Package=")
                    line.startsWith("X-Launcher-Name=") -> launcherName = line.substringAfter("X-Launcher-Name=")
                    line.startsWith("Icon=") -> iconPath = line.substringAfter("Icon=")
                }
            }

            return WindowsGameShortcut(
                id = file.nameWithoutExtension,
                name = name,
                executablePath = execPath,
                launcherPackage = launcherPackage,
                launcherName = launcherName,
                iconPath = iconPath,
                isInEsde = true,
                shortcutFilePath = file.absolutePath
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing shortcut file: ${file.name}", e)
            return null
        }
    }

    /**
     * Create a shortcut file for a game in ES-DE windows folder
     */
    fun createShortcut(game: WindowsGameShortcut): ConfigResult<String> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) {
            if (!windowsDir.mkdirs()) {
                return ConfigResult.Error("Could not create windows ROM directory")
            }
        }

        val shortcutFile = File(windowsDir, "${game.id}$SHORTCUT_EXTENSION")

        try {
            val content = buildString {
                appendLine("[Desktop Entry]")
                appendLine("Type=Application")
                appendLine("Name=${game.name}")
                appendLine("Exec=${game.executablePath}")
                appendLine("X-Launcher-Package=${game.launcherPackage}")
                appendLine("X-Launcher-Name=${game.launcherName}")
                if (game.iconPath != null) {
                    appendLine("Icon=${game.iconPath}")
                }
                appendLine("Terminal=false")
                appendLine("Categories=Game;")
            }

            shortcutFile.writeText(content)

            return ConfigResult.Success(shortcutFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating shortcut", e)
            return ConfigResult.Error("Failed to create shortcut: ${e.message}", e)
        }
    }

    /**
     * Remove a shortcut file from ES-DE windows folder.
     * Checks for .desktop, .steam, .gog, and .epic file extensions.
     */
    fun removeShortcut(shortcutId: String): ConfigResult<Unit> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        // Try all file extensions
        val filesToCheck = listOf(
            File(windowsPath, "$shortcutId$SHORTCUT_EXTENSION"),
            File(windowsPath, "$shortcutId$STEAM_EXTENSION"),
            File(windowsPath, "$shortcutId$GOG_EXTENSION"),
            File(windowsPath, "$shortcutId$EPIC_EXTENSION")
        )

        return try {
            var deleted = false

            for (file in filesToCheck) {
                if (file.exists()) {
                    if (file.delete()) {
                        Log.d(TAG, "Deleted shortcut: ${file.absolutePath}")
                        deleted = true
                    } else {
                        return ConfigResult.Error("Could not delete shortcut file: ${file.name}")
                    }
                }
            }

            if (deleted) {
                ConfigResult.Success(Unit)
            } else {
                // No file existed - log for debugging but still return success
                Log.w(TAG, "No shortcut found to delete for ID: $shortcutId (checked: ${filesToCheck.map { it.name }})")
                ConfigResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove shortcut: $shortcutId", e)
            ConfigResult.Error("Failed to remove shortcut: ${e.message}", e)
        }
    }

    /**
     * Add all games from a launcher to ES-DE
     */
    fun addAllGamesFromLauncher(launcher: WindowsGameLauncher): ConfigResult<Int> {
        var addedCount = 0
        var lastError: String? = null

        for (game in launcher.games) {
            if (!game.isInEsde) {
                when (val result = createShortcut(game)) {
                    is ConfigResult.Success -> addedCount++
                    is ConfigResult.Error -> lastError = result.message
                }
            }
        }

        return if (addedCount > 0 || lastError == null) {
            ConfigResult.Success(addedCount)
        } else {
            ConfigResult.Error(lastError)
        }
    }

    // ==================== Steam Game Support ====================

    /**
     * Create a .steam file for a Steam game that can be launched by GameHub Lite.
     * The .steam file contains just the Steam App ID.
     *
     * @param game The Steam game to create a shortcut for
     * @param launcherPackage The package name of the launcher to use (e.g., "gamehub.lite")
     * @return Result with the path to the created file
     */
    fun createSteamShortcut(game: SteamGame, launcherPackage: String): ConfigResult<String> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) {
            if (!windowsDir.mkdirs()) {
                return ConfigResult.Error("Could not create windows ROM directory")
            }
        }

        // Create a safe filename from the game name
        val safeFileName = generateShortcutId(game.name)
        val steamFile = File(windowsDir, "$safeFileName$STEAM_EXTENSION")

        return try {
            // The .steam file just contains the Steam App ID
            steamFile.writeText(game.appId.toString())
            Log.d(TAG, "Created Steam shortcut: ${steamFile.absolutePath} for AppID: ${game.appId}")
            ConfigResult.Success(steamFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Steam shortcut", e)
            return ConfigResult.Error("Failed to create Steam shortcut: ${e.message}", e)
        }
    }

    /**
     * Check if a Steam game already exists in ES-DE
     */
    fun steamGameExistsInEsde(appId: Int): Boolean {
        val windowsPath = getEsdeWindowsPath() ?: return false
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return false

        // Check all .steam files for this app ID
        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(STEAM_EXTENSION)) {
                try {
                    val content = file.readText().trim()
                    if (content == appId.toString()) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Steam file: ${file.name}", e)
                }
            }
        }

        return false
    }

    /**
     * Check if a Steam game exists by name (filename)
     */
    fun steamGameExistsByName(gameName: String): Boolean {
        val windowsPath = getEsdeWindowsPath() ?: return false
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return false

        val safeFileName = generateShortcutId(gameName)
        val steamFile = File(windowsDir, "$safeFileName$STEAM_EXTENSION")

        return steamFile.exists()
    }

    /**
     * Remove a Steam shortcut by App ID
     */
    fun removeSteamShortcut(appId: Int): ConfigResult<Unit> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) return ConfigResult.Success(Unit)

        // Find and delete the file with this app ID
        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(STEAM_EXTENSION)) {
                try {
                    val content = file.readText().trim()
                    if (content == appId.toString()) {
                        if (file.delete()) {
                            Log.d(TAG, "Removed Steam shortcut: ${file.absolutePath}")
                            return ConfigResult.Success(Unit)
                        } else {
                            return ConfigResult.Error("Could not delete Steam shortcut file")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading/deleting Steam file: ${file.name}", e)
                }
            }
        }

        return ConfigResult.Success(Unit) // File not found, consider it removed
    }

    /**
     * Get all Steam games currently in ES-DE windows folder
     */
    fun getExistingSteamGames(): List<Pair<String, Int>> {
        val windowsPath = getEsdeWindowsPath() ?: return emptyList()
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return emptyList()

        val games = mutableListOf<Pair<String, Int>>()

        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(STEAM_EXTENSION)) {
                try {
                    val appId = file.readText().trim().toIntOrNull()
                    if (appId != null) {
                        val gameName = file.nameWithoutExtension
                            .replace("_", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        games.add(gameName to appId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Steam file: ${file.name}", e)
                }
            }
        }

        return games
    }

    /**
     * Get the list of installed Windows launchers that support Steam games
     */
    fun getSteamCompatibleLaunchers(): List<Pair<String, String>> {
        val pm = context.packageManager
        val launchers = mutableListOf<Pair<String, String>>()

        // Launchers that support Steam game launching
        val steamCompatiblePackages = listOf(
            "gamehub.lite" to "GameHub Lite",
            "com.nickmafra.gamehublite" to "GameHub Lite",
            "com.nickmafra.gamehub" to "GameHub"
        )

        for ((packageName, displayName) in steamCompatiblePackages) {
            try {
                pm.getApplicationInfo(packageName, 0)
                launchers.add(packageName to displayName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return launchers.distinctBy { it.second }
    }

    /**
     * Get the artwork path for a Windows game.
     * Looks in the ES-DE downloaded_media/windows/covers folder for artwork
     * matching the game's shortcut ID.
     *
     * @param gameId The game's shortcut ID (filename without extension)
     * @return The path to the artwork file, or null if not found
     */
    fun getArtworkPath(gameId: String): String? {
        val mediaPath = esdeConfigService.getMediaDirectory("windows", "covers")
        if (mediaPath == null || !mediaPath.exists()) {
            return null
        }

        // Check for common image extensions
        val extensions = listOf(".jpg", ".jpeg", ".png", ".webp")
        for (ext in extensions) {
            val artworkFile = File(mediaPath, "$gameId$ext")
            if (artworkFile.exists()) {
                return artworkFile.absolutePath
            }
        }

        return null
    }

    // ==================== GOG Game Support ====================

    /**
     * Create a GOG shortcut file for a GOG game that can be launched by GameNative.
     * The file has .gog extension and contains the GOG product ID.
     *
     * @param game The GOG game to create a shortcut for
     * @param launcherPackage The package name of the launcher to use
     * @return Result with the path to the created file
     */
    fun createGogShortcut(game: GogGame, launcherPackage: String): ConfigResult<String> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) {
            if (!windowsDir.mkdirs()) {
                return ConfigResult.Error("Could not create windows ROM directory")
            }
        }

        // Create a safe filename from the game name with .gog extension
        val safeFileName = generateShortcutId(game.name)
        val gogFile = File(windowsDir, "$safeFileName$GOG_EXTENSION")

        return try {
            // The GOG file just contains the product ID
            gogFile.writeText(game.productId.toString())
            Log.d(TAG, "Created GOG shortcut: ${gogFile.absolutePath} for ProductID: ${game.productId}")
            ConfigResult.Success(gogFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating GOG shortcut", e)
            ConfigResult.Error("Failed to create GOG shortcut: ${e.message}", e)
        }
    }

    /**
     * Check if a GOG game already exists in ES-DE
     */
    fun gogGameExistsInEsde(productId: Long): Boolean {
        val windowsPath = getEsdeWindowsPath() ?: return false
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return false

        // Check all files ending with "zst" for this product ID
        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(GOG_EXTENSION)) {
                try {
                    val content = file.readText().trim()
                    if (content == productId.toString()) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading GOG file: ${file.name}", e)
                }
            }
        }

        return false
    }

    /**
     * Check if a GOG game exists by name (filename)
     */
    fun gogGameExistsByName(gameName: String): Boolean {
        val windowsPath = getEsdeWindowsPath() ?: return false
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return false

        val safeFileName = generateShortcutId(gameName)
        val gogFile = File(windowsDir, "$safeFileName$GOG_EXTENSION")

        return gogFile.exists()
    }

    /**
     * Get all GOG games currently in ES-DE windows folder
     */
    fun getExistingGogGames(): List<Pair<String, Long>> {
        val windowsPath = getEsdeWindowsPath() ?: return emptyList()
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return emptyList()

        val games = mutableListOf<Pair<String, Long>>()

        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(GOG_EXTENSION)) {
                try {
                    val productId = file.readText().trim().toLongOrNull()
                    if (productId != null) {
                        // Remove .gog extension to get the game name
                        val gameName = file.name.dropLast(GOG_EXTENSION.length)
                            .replace("_", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        games.add(gameName to productId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading GOG file: ${file.name}", e)
                }
            }
        }

        return games
    }

    /**
     * Remove a GOG shortcut by product ID
     */
    fun removeGogShortcut(productId: Long): ConfigResult<Unit> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) return ConfigResult.Success(Unit)

        // Find and delete the file with this product ID
        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(GOG_EXTENSION)) {
                try {
                    val content = file.readText().trim()
                    if (content == productId.toString()) {
                        if (file.delete()) {
                            Log.d(TAG, "Removed GOG shortcut: ${file.absolutePath}")
                            return ConfigResult.Success(Unit)
                        } else {
                            return ConfigResult.Error("Could not delete GOG shortcut file")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading/deleting GOG file: ${file.name}", e)
                }
            }
        }

        return ConfigResult.Success(Unit) // File not found, consider it removed
    }

    /**
     * Get the list of installed Windows launchers that support GOG games
     */
    fun getGogCompatibleLaunchers(): List<Pair<String, String>> {
        val pm = context.packageManager
        val launchers = mutableListOf<Pair<String, String>>()

        // Launchers that support GOG game launching
        val gogCompatiblePackages = listOf(
            "app.gamenative" to "GameNative",
            "com.nickmafra.gamenative" to "GameNative"  // Legacy package name
        )

        for ((packageName, displayName) in gogCompatiblePackages) {
            try {
                pm.getApplicationInfo(packageName, 0)
                launchers.add(packageName to displayName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return launchers
    }

    // ==================== Epic Game Support ====================

    /**
     * Import an Epic game shortcut that was exported from GameNative.
     * Reads the GameNative .epicgame file and creates an .epic file in the ES-DE windows folder.
     *
     * @param sourceFile The exported .epicgame file from GameNative
     * @return Result with the EpicGame info
     */
    fun importEpicShortcut(sourceFile: File): ConfigResult<EpicGame> {
        if (!sourceFile.exists()) {
            return ConfigResult.Error("Source file does not exist: ${sourceFile.absolutePath}")
        }

        if (!sourceFile.name.endsWith(GAMENATIVE_EPIC_EXTENSION)) {
            return ConfigResult.Error("File must have .epicgame extension (GameNative export format)")
        }

        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val windowsDir = File(windowsPath)
        if (!windowsDir.exists()) {
            if (!windowsDir.mkdirs()) {
                return ConfigResult.Error("Could not create windows ROM directory")
            }
        }

        return try {
            // Read the internal ID from the file
            val internalId = sourceFile.readText().trim()

            // Get game name from filename (remove .epicgame extension)
            val gameName = sourceFile.nameWithoutExtension

            // Create a safe filename with .epic extension (ES-DE compatible)
            val safeFileName = generateShortcutId(gameName)
            val destFile = File(windowsDir, "$safeFileName$EPIC_EXTENSION")

            // Write the internal ID to the new .epic file
            destFile.writeText(internalId)

            Log.d(TAG, "Imported Epic shortcut: ${destFile.absolutePath} for ID: $internalId")

            val epicGame = EpicGame(
                internalId = internalId,
                name = gameName,
                sourcePath = destFile.absolutePath
            )

            ConfigResult.Success(epicGame)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing Epic shortcut", e)
            ConfigResult.Error("Failed to import Epic shortcut: ${e.message}", e)
        }
    }

    /**
     * Get all Epic games currently in ES-DE windows folder
     */
    fun getExistingEpicGames(): List<EpicGame> {
        val windowsPath = getEsdeWindowsPath() ?: return emptyList()
        val windowsDir = File(windowsPath)

        if (!windowsDir.exists()) return emptyList()

        val games = mutableListOf<EpicGame>()

        for (file in windowsDir.listFiles() ?: emptyArray()) {
            if (file.name.endsWith(EPIC_EXTENSION)) {
                try {
                    val internalId = file.readText().trim()
                    val gameName = file.nameWithoutExtension
                        .replace("_", " ")
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    games.add(EpicGame(
                        internalId = internalId,
                        name = gameName,
                        sourcePath = file.absolutePath
                    ))
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Epic file: ${file.name}", e)
                }
            }
        }

        return games
    }

    /**
     * Remove an Epic shortcut by filename
     */
    fun removeEpicShortcut(shortcutId: String): ConfigResult<Unit> {
        val windowsPath = getEsdeWindowsPath()
            ?: return ConfigResult.Error("Could not determine ES-DE windows path")

        val epicFile = File(windowsPath, "$shortcutId$EPIC_EXTENSION")

        return try {
            if (epicFile.exists()) {
                if (epicFile.delete()) {
                    Log.d(TAG, "Removed Epic shortcut: ${epicFile.absolutePath}")
                    ConfigResult.Success(Unit)
                } else {
                    ConfigResult.Error("Could not delete Epic shortcut file")
                }
            } else {
                ConfigResult.Success(Unit) // File not found, consider it removed
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing Epic shortcut: $shortcutId", e)
            ConfigResult.Error("Failed to remove Epic shortcut: ${e.message}", e)
        }
    }

    /**
     * Get the list of installed Windows launchers that support Epic games
     */
    fun getEpicCompatibleLaunchers(): List<Pair<String, String>> {
        val pm = context.packageManager
        val launchers = mutableListOf<Pair<String, String>>()

        // Launchers that support Epic game launching
        val epicCompatiblePackages = listOf(
            "app.gamenative" to "GameNative",
            "com.nickmafra.gamenative" to "GameNative"  // Legacy package name
        )

        for ((packageName, displayName) in epicCompatiblePackages) {
            try {
                pm.getApplicationInfo(packageName, 0)
                launchers.add(packageName to displayName)
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed
            }
        }

        return launchers
    }

    // ==================== Platform Detection ====================

    /**
     * Determine the platform of a Windows game shortcut based on file extension/suffix.
     */
    fun detectGamePlatform(file: File): WindowsGamePlatform? {
        return when {
            file.name.endsWith(STEAM_EXTENSION) -> WindowsGamePlatform.STEAM
            file.name.endsWith(GOG_EXTENSION) -> WindowsGamePlatform.GOG
            file.name.endsWith(EPIC_EXTENSION) -> WindowsGamePlatform.EPIC
            else -> null
        }
    }

    /**
     * Get the game filename (shortcut ID) for a file, stripping the platform-specific extension.
     */
    fun getGameFileName(file: File): String {
        return when {
            file.name.endsWith(STEAM_EXTENSION) -> file.name.dropLast(STEAM_EXTENSION.length)
            file.name.endsWith(GOG_EXTENSION) -> file.name.dropLast(GOG_EXTENSION.length)
            file.name.endsWith(EPIC_EXTENSION) -> file.name.dropLast(EPIC_EXTENSION.length)
            else -> file.nameWithoutExtension
        }
    }

    /**
     * Get the platform-specific ID from a shortcut file.
     * Returns the file content (Steam App ID, GOG Product ID, or Epic internal ID).
     */
    fun getGamePlatformId(file: File): String? {
        return try {
            file.readText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading game file: ${file.name}", e)
            null
        }
    }
}