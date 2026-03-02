package com.esde.emulatormanager.data.service

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.esde.emulatormanager.data.model.*
import com.esde.emulatormanager.data.parser.EsdeConfigParser
import com.esde.emulatormanager.data.parser.EsdeConfigWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing ES-DE configuration files
 */
@Singleton
class EsdeConfigService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: EsdeConfigParser,
    private val writer: EsdeConfigWriter
) {
    companion object {
        private const val ESDE_FOLDER = "ES-DE"
        private const val CUSTOM_SYSTEMS_FOLDER = "custom_systems"
        private const val ES_SYSTEMS_FILE = "es_systems.xml"
        private const val ES_FIND_RULES_FILE = "es_find_rules.xml"
        private const val ES_SETTINGS_FILE = "es_settings.xml"
    }

    /**
     * Find the ES-DE configuration directory
     */
    fun findEsdeDirectory(): File? {
        val possibleLocations = listOf(
            // Primary external storage
            File(Environment.getExternalStorageDirectory(), ESDE_FOLDER),
            // Android/data location
            File(context.getExternalFilesDir(null)?.parentFile?.parentFile, "ES-DE/$ESDE_FOLDER"),
            // Download folder
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ESDE_FOLDER),
            // Documents folder
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ESDE_FOLDER)
        )

        return possibleLocations.firstOrNull { it.exists() && it.isDirectory }
    }

    /**
     * Get the custom_systems directory, creating it if necessary
     */
    fun getCustomSystemsDirectory(): File? {
        val esdeDir = findEsdeDirectory() ?: return null
        val customDir = File(esdeDir, CUSTOM_SYSTEMS_FOLDER)

        if (!customDir.exists()) {
            if (!customDir.mkdirs()) {
                return null
            }
        }

        return customDir
    }

    /**
     * Read current custom es_systems.xml if it exists
     */
    fun readCustomSystems(): ConfigResult<List<GameSystem>> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        val systemsFile = File(customDir, ES_SYSTEMS_FILE)
        if (!systemsFile.exists()) {
            return ConfigResult.Success(emptyList())
        }

        return try {
            FileInputStream(systemsFile).use { inputStream ->
                parser.parseSystemsXml(inputStream)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to read es_systems.xml: ${e.message}", e)
        }
    }

    /**
     * Read current custom es_find_rules.xml if it exists
     */
    fun readCustomFindRules(): ConfigResult<List<EmulatorRule>> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        val rulesFile = File(customDir, ES_FIND_RULES_FILE)
        if (!rulesFile.exists()) {
            return ConfigResult.Success(emptyList())
        }

        return try {
            FileInputStream(rulesFile).use { inputStream ->
                parser.parseFindRulesXml(inputStream)
            }
        } catch (e: Exception) {
            ConfigResult.Error("Failed to read es_find_rules.xml: ${e.message}", e)
        }
    }

    /**
     * Write custom systems configuration
     */
    fun writeCustomSystems(systems: List<GameSystem>): ConfigResult<Unit> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        val systemsFile = File(customDir, ES_SYSTEMS_FILE)

        return try {
            val content = writer.generateSystemsXml(systems)
            FileOutputStream(systemsFile).use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            ConfigResult.Success(Unit)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to write es_systems.xml: ${e.message}", e)
        }
    }

    /**
     * Write custom find rules configuration
     */
    fun writeCustomFindRules(rules: List<EmulatorRule>): ConfigResult<Unit> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        val rulesFile = File(customDir, ES_FIND_RULES_FILE)

        return try {
            val content = writer.generateFindRulesXml(rules)
            FileOutputStream(rulesFile).use { outputStream ->
                outputStream.write(content.toByteArray(Charsets.UTF_8))
            }
            ConfigResult.Success(Unit)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to write es_find_rules.xml: ${e.message}", e)
        }
    }

    /**
     * Add an emulator to a system's configuration
     */
    fun addEmulatorToSystem(
        system: GameSystem,
        emulator: InstalledEmulator,
        label: String,
        launchArguments: String? = null,
        emulationActivity: String? = null
    ): ConfigResult<Unit> {
        // Read existing custom systems
        val existingSystemsResult = readCustomSystems()
        val existingSystems = when (existingSystemsResult) {
            is ConfigResult.Success -> existingSystemsResult.data.toMutableList()
            is ConfigResult.Error -> return existingSystemsResult
        }

        // Read existing find rules
        val existingRulesResult = readCustomFindRules()
        val existingRules = when (existingRulesResult) {
            is ConfigResult.Success -> existingRulesResult.data.toMutableList()
            is ConfigResult.Error -> return existingRulesResult
        }

        // 1. Update es_find_rules.xml
        // Use hyphens instead of underscores in emulator name (ES-DE convention)
        val emulatorName = emulator.appName.uppercase()
            .replace(" ", "-")
            .replace("_", "-")
            .replace(".", "-")

        // Use emulationActivity if provided, otherwise fall back to stored activityName
        val activityToUse = emulationActivity ?: emulator.activityName
        // Entry format is always package/activity (with slash separator)
        val entry = if (activityToUse != null) {
            "${emulator.packageName}/$activityToUse"
        } else {
            // If no activity is known, just use package name
            emulator.packageName
        }

        val existingRuleIndex = existingRules.indexOfFirst { it.name == emulatorName }
        if (existingRuleIndex >= 0) {
            val existingRule = existingRules[existingRuleIndex]
            if (!existingRule.entries.contains(entry)) {
                existingRules[existingRuleIndex] = existingRule.copy(
                    entries = existingRule.entries + entry
                )
            }
        } else {
            existingRules.add(EmulatorRule(name = emulatorName, entries = listOf(entry)))
        }

        val writeRulesResult = writeCustomFindRules(existingRules)
        if (writeRulesResult is ConfigResult.Error) {
            return writeRulesResult
        }

        // 2. Update es_systems.xml
        // Use custom launch arguments if provided (from KnownEmulators.launchCommand)
        val commandString = if (launchArguments != null) {
            "%EMULATOR_${emulatorName}% $launchArguments"
        } else {
            // Fallback for unknown emulators - simple DATA command
            "%EMULATOR_${emulatorName}% %DATA%=%ROMSAF%"
        }

        val newCommand = EmulatorCommand(label = label, command = commandString)

        val existingSystemIndex = existingSystems.indexOfFirst { it.name == system.name }
        if (existingSystemIndex >= 0) {
            val existingSystem = existingSystems[existingSystemIndex]
            // Add command if it doesn't exist
            if (existingSystem.commands.none { it.label == label }) {
                existingSystems[existingSystemIndex] = existingSystem.copy(
                    commands = existingSystem.commands + newCommand
                )
            }
        } else {
            // Create new system entry with only the new command
            existingSystems.add(system.copy(commands = listOf(newCommand)))
        }

        return writeCustomSystems(existingSystems)
    }

    /**
     * Remove an emulator from a system's configuration
     */
    fun removeEmulatorFromSystem(
        systemId: String,
        emulatorPackage: String
    ): ConfigResult<Unit> {
        // 1. Update es_find_rules.xml
        val existingRulesResult = readCustomFindRules()
        val existingRules = when (existingRulesResult) {
            is ConfigResult.Success -> existingRulesResult.data.toMutableList()
            is ConfigResult.Error -> return existingRulesResult
        }

        // Remove entries containing this package
        // Also identify the emulator name(s) that are being removed
        val removedEmulatorNames = mutableListOf<String>()
        
        val updatedRules = existingRules.mapNotNull { rule ->
            val filteredEntries = rule.entries.filter { entry ->
                !entry.startsWith(emulatorPackage)
            }
            
            if (filteredEntries.isEmpty()) {
                removedEmulatorNames.add(rule.name)
                null
            } else if (filteredEntries.size != rule.entries.size) {
                rule.copy(entries = filteredEntries)
            } else {
                rule
            }
        }

        val writeRulesResult = writeCustomFindRules(updatedRules)
        if (writeRulesResult is ConfigResult.Error) {
            return writeRulesResult
        }

        // 2. Update es_systems.xml
        // We need to remove commands that reference the removed emulator rules
        if (removedEmulatorNames.isNotEmpty()) {
            val existingSystemsResult = readCustomSystems()
            val existingSystems = when (existingSystemsResult) {
                is ConfigResult.Success -> existingSystemsResult.data.toMutableList()
                is ConfigResult.Error -> return existingSystemsResult
            }

            val updatedSystems = existingSystems.mapNotNull { system ->
                val updatedCommands = system.commands.filter { cmd ->
                    // Check if command uses one of the removed emulator variables
                    removedEmulatorNames.none { emuName -> 
                        cmd.command.contains("%EMULATOR_${emuName}%")
                    }
                }

                if (updatedCommands.isEmpty()) {
                    // If system has no commands left, remove the system?
                    // Maybe, or keep it. Usually if we added it, we should remove it.
                    null 
                } else {
                    system.copy(commands = updatedCommands)
                }
            }
            
            return writeCustomSystems(updatedSystems)
        }

        return ConfigResult.Success(Unit)
    }

    /**
     * Check if ES-DE is properly set up
     */
    fun isEsdeConfigured(): Boolean {
        return findEsdeDirectory() != null
    }

    /**
     * Get the ES-DE configuration path for display
     */
    fun getEsdeConfigPath(): String? {
        return findEsdeDirectory()?.absolutePath
    }

    /**
     * Find the es_settings.xml file.
     * ES-DE on Android stores this in different locations depending on the installation.
     */
    private fun findSettingsFile(): File? {
        // Check multiple possible locations for es_settings.xml
        val possibleLocations = mutableListOf<File>()

        // 1. Check ES-DE Android app data folder (most common on Android)
        val androidDataPaths = listOf(
            File(Environment.getExternalStorageDirectory(), "Android/data/org.es_de.frontend/files"),
            File(Environment.getExternalStorageDirectory(), "Android/data/org.es_de.frontend.debug/files"),
            File(Environment.getExternalStorageDirectory(), "Android/data/org.es_de.frontend.beta/files")
        )
        androidDataPaths.forEach { path ->
            possibleLocations.add(File(path, ES_SETTINGS_FILE))
        }

        // 2. Check main ES-DE folder in external storage
        val esdeDir = findEsdeDirectory()
        if (esdeDir != null) {
            possibleLocations.add(File(esdeDir, ES_SETTINGS_FILE))
        }

        // 3. Check common ES-DE locations
        possibleLocations.add(File(Environment.getExternalStorageDirectory(), "ES-DE/$ES_SETTINGS_FILE"))
        possibleLocations.add(File(Environment.getExternalStorageDirectory(), "ES-DE/settings/$ES_SETTINGS_FILE"))

        for (file in possibleLocations) {
            android.util.Log.d("EsdeConfigService", "Checking for settings at: ${file.absolutePath}")
            if (file.exists()) {
                android.util.Log.d("EsdeConfigService", "Found settings file at: ${file.absolutePath}")
                return file
            }
        }

        android.util.Log.d("EsdeConfigService", "Settings file not found in any location")
        return null
    }

    /**
     * Read the MediaDirectory setting from ES-DE's es_settings.xml.
     * Returns the configured media directory path, or null if not found.
     */
    fun getMediaDirectoryFromSettings(): String? {
        val settingsFile = findSettingsFile()

        if (settingsFile == null) {
            android.util.Log.d("EsdeConfigService", "Settings file not found in any location")
            return null
        }

        try {
            val content = settingsFile.readText()
            android.util.Log.d("EsdeConfigService", "Read settings file, length: ${content.length}")

            // Try multiple regex patterns to handle different ES-DE settings formats
            // Format 1: <string name="MediaDirectory" value="/path" />
            // Format 2: <string name="MediaDirectory">/path</string>
            // Note: ES-DE uses "MediaDirectory" as the setting name

            val patterns = listOf(
                // Standard attribute format with value attribute
                Regex("""<string\s+name\s*=\s*"MediaDirectory"\s+value\s*=\s*"([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE),
                // Alternate attribute order
                Regex("""<string\s+value\s*=\s*"([^"]+)"\s+name\s*=\s*"MediaDirectory"[^>]*>""", RegexOption.IGNORE_CASE),
                // Content between tags format
                Regex("""<string\s+name\s*=\s*"MediaDirectory"[^>]*>([^<]+)</string>""", RegexOption.IGNORE_CASE)
            )

            for (pattern in patterns) {
                val match = pattern.find(content)
                if (match != null) {
                    val path = match.groupValues[1].trim()
                    if (path.isNotBlank()) {
                        android.util.Log.d("EsdeConfigService", "Found MediaDirectory: $path")
                        // Handle ~ for home directory
                        val resolvedPath = if (path.startsWith("~")) {
                            path.replaceFirst("~", Environment.getExternalStorageDirectory().absolutePath)
                        } else {
                            path
                        }
                        android.util.Log.d("EsdeConfigService", "Resolved MediaDirectory: $resolvedPath")
                        return resolvedPath
                    }
                }
            }

            android.util.Log.d("EsdeConfigService", "MediaDirectory not found in settings")
        } catch (e: Exception) {
            android.util.Log.e("EsdeConfigService", "Failed to read settings: ${e.message}", e)
        }
        return null
    }

    /**
     * Get the downloaded_media directory for a specific system.
     * First checks ES-DE's es_settings.xml for MediaDirectory setting,
     * then falls back to the default location in ES-DE folder.
     * Creates the directory structure if it doesn't exist.
     *
     * @param systemName The system name (e.g., "windows")
     * @param mediaType The media type subdirectory (e.g., "covers", "marquees", "screenshots")
     * @return The File representing the media directory, or null if ES-DE is not configured
     */
    fun getMediaDirectory(systemName: String, mediaType: String): File? {
        // First try to get from ES-DE settings
        val settingsMediaDir = getMediaDirectoryFromSettings()
        android.util.Log.d("EsdeConfigService", "getMediaDirectory: settingsMediaDir=$settingsMediaDir")

        val baseMediaDir = if (settingsMediaDir != null && settingsMediaDir.isNotBlank()) {
            val dir = File(settingsMediaDir)
            android.util.Log.d("EsdeConfigService", "Using MediaDirectory from settings: ${dir.absolutePath}")
            dir
        } else {
            // Fall back to default location
            val esdeDir = findEsdeDirectory()
            if (esdeDir == null) {
                android.util.Log.e("EsdeConfigService", "ES-DE directory not found")
                return null
            }
            val dir = File(esdeDir, "downloaded_media")
            android.util.Log.d("EsdeConfigService", "Using default media directory: ${dir.absolutePath}")
            dir
        }

        val mediaDir = File(baseMediaDir, "$systemName/$mediaType")
        android.util.Log.d("EsdeConfigService", "Final media directory: ${mediaDir.absolutePath}")

        if (!mediaDir.exists()) {
            val created = mediaDir.mkdirs()
            android.util.Log.d("EsdeConfigService", "Created media directory: $created")
            if (!created && !mediaDir.exists()) {
                android.util.Log.e("EsdeConfigService", "Failed to create media directory: ${mediaDir.absolutePath}")
            }
        }
        return mediaDir
    }

    /**
     * Get the base media directory path (for display/debugging purposes).
     * Returns the path that would be used for downloaded media.
     */
    fun getMediaBasePath(): String? {
        val settingsMediaDir = getMediaDirectoryFromSettings()
        if (settingsMediaDir != null && settingsMediaDir.isNotBlank()) {
            return settingsMediaDir
        }
        val esdeDir = findEsdeDirectory() ?: return null
        return File(esdeDir, "downloaded_media").absolutePath
    }

    /**
     * Get the path to the es_settings.xml file (for display/debugging).
     */
    fun getSettingsFilePath(): String? {
        return findSettingsFile()?.absolutePath
    }

    /**
     * Get the gamelists directory for a specific system.
     * ES-DE stores gamelist.xml files in ES-DE/gamelists/<system>/gamelist.xml
     * Creates the directory structure if it doesn't exist.
     *
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @return The File representing the gamelists directory for the system, or null if ES-DE is not configured
     */
    fun getGamelistDirectory(systemName: String): File? {
        val esdeDir = findEsdeDirectory()
        if (esdeDir == null) {
            android.util.Log.e("EsdeConfigService", "ES-DE directory not found")
            return null
        }

        val gamelistDir = File(esdeDir, "gamelists/$systemName")
        android.util.Log.d("EsdeConfigService", "Gamelist directory: ${gamelistDir.absolutePath}")

        if (!gamelistDir.exists()) {
            val created = gamelistDir.mkdirs()
            android.util.Log.d("EsdeConfigService", "Created gamelist directory: $created")
            if (!created && !gamelistDir.exists()) {
                android.util.Log.e("EsdeConfigService", "Failed to create gamelist directory: ${gamelistDir.absolutePath}")
            }
        }
        return gamelistDir
    }

    /**
     * Get the ROM path for a specific system from ES-DE's bundled or custom configuration.
     * First checks custom_systems, then falls back to the bundled es_systems.xml.
     * Returns null if the system is not found.
     */
    fun getSystemRomPath(systemName: String): String? {
        // First check custom systems
        when (val customResult = readCustomSystems()) {
            is ConfigResult.Success -> {
                val customSystem = customResult.data.find { it.name == systemName }
                if (customSystem != null && customSystem.path.isNotBlank()) {
                    return resolveRomPath(customSystem.path)
                }
            }
            is ConfigResult.Error -> { /* Continue to check bundled */ }
        }

        // Then check bundled es_systems.xml in ES-DE resources folder
        val esdeDir = findEsdeDirectory() ?: return null
        val bundledSystemsFile = File(esdeDir, "resources/systems/android/$ES_SYSTEMS_FILE")

        if (bundledSystemsFile.exists()) {
            try {
                FileInputStream(bundledSystemsFile).use { inputStream ->
                    when (val result = parser.parseSystemsXml(inputStream)) {
                        is ConfigResult.Success -> {
                            val system = result.data.find { it.name == systemName }
                            if (system != null && system.path.isNotBlank()) {
                                return resolveRomPath(system.path)
                            }
                        }
                        is ConfigResult.Error -> { /* System not found */ }
                    }
                }
            } catch (e: Exception) {
                // Failed to read bundled systems
            }
        }

        return null
    }

    /**
     * Resolve a ROM path from ES-DE format to an actual file path.
     * ES-DE uses placeholders like %ROMPATH% which need to be resolved.
     */
    private fun resolveRomPath(path: String): String {
        var resolvedPath = path

        // ES-DE uses %ROMPATH% to refer to the ROMs directory
        // Common patterns: %ROMPATH%/windows, ~/ROMs/windows
        if (resolvedPath.contains("%ROMPATH%")) {
            // Try to find the ROMs directory
            val possibleRomPaths = listOf(
                File(Environment.getExternalStorageDirectory(), "ROMs"),
                File(Environment.getExternalStorageDirectory(), "Roms"),
                File(Environment.getExternalStorageDirectory(), "roms")
            )
            val romsDir = possibleRomPaths.firstOrNull { it.exists() }
                ?: File(Environment.getExternalStorageDirectory(), "ROMs")

            resolvedPath = resolvedPath.replace("%ROMPATH%", romsDir.absolutePath)
        }

        // Handle ~ (home directory) - on Android this typically means external storage
        if (resolvedPath.startsWith("~")) {
            resolvedPath = resolvedPath.replaceFirst("~", Environment.getExternalStorageDirectory().absolutePath)
        }

        return resolvedPath
    }

    /**
     * Configure the Windows system in ES-DE to use GameHub Lite and GameNative with correct launch commands.
     * This updates both es_systems.xml and es_find_rules.xml in custom_systems.
     *
     * GameHub Lite handles .steam files (Steam games)
     * GameNative handles .gog, .epic, and .amazon files (GOG, Epic, and Amazon games)
     *
     * @param windowsRomPath The path to the Windows ROMs folder
     * @return Result indicating success or failure
     */
    fun configureGameHubLiteForWindows(windowsRomPath: String): ConfigResult<Unit> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        // First, backup existing config
        backupCustomConfig()

        // Read existing custom systems
        val existingSystemsResult = readCustomSystems()
        val existingSystems = when (existingSystemsResult) {
            is ConfigResult.Success -> existingSystemsResult.data.toMutableList()
            is ConfigResult.Error -> mutableListOf()
        }

        // Read existing find rules
        val existingRulesResult = readCustomFindRules()
        val existingRules = when (existingRulesResult) {
            is ConfigResult.Success -> existingRulesResult.data.toMutableList()
            is ConfigResult.Error -> mutableListOf()
        }

        // GameHub Lite emulator configuration (for .steam files)
        val gameHubLiteEmulatorName = "GAMEHUB-LITE"
        val gameHubLiteCommand = EmulatorCommand(
            label = "GameHub Lite",
            command = "%EMULATOR_$gameHubLiteEmulatorName% %ACTION%=gamehub.lite.LAUNCH_GAME %EXTRA_steamAppId%=%BASENAME% %EXTRA_autoStartGame%=true"
        )

        // GameNative emulator configurations — one per platform so game_source is correct.
        // %EXTRAINTEGER_app_id% reads the file content as an integer (GameNative uses getIntExtra).
        // %INJECT%=%ROM% tells ES-DE to read the ROM file content and use it as the extra value.
        // GameNative can also launch Steam games — the .steam file contains the Steam App ID integer.
        // game_source defaults to STEAM in GameNative if omitted, but we set it explicitly.
        val gameNativeSteamEmulatorName = "GAMENATIVE-STEAM"
        val gameNativeSteamCommand = EmulatorCommand(
            label = "GameNative (Steam)",
            command = "%EMULATOR_$gameNativeSteamEmulatorName% %ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=STEAM"
        )

        val gameNativeGogEmulatorName = "GAMENATIVE-GOG"
        val gameNativeGogCommand = EmulatorCommand(
            label = "GameNative (GOG)",
            command = "%EMULATOR_$gameNativeGogEmulatorName% %ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=GOG"
        )

        val gameNativeEpicEmulatorName = "GAMENATIVE-EPIC"
        val gameNativeEpicCommand = EmulatorCommand(
            label = "GameNative (Epic)",
            command = "%EMULATOR_$gameNativeEpicEmulatorName% %ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=EPIC"
        )

        val gameNativeAmazonEmulatorName = "GAMENATIVE-AMAZON"
        val gameNativeAmazonCommand = EmulatorCommand(
            label = "GameNative (Amazon)",
            command = "%EMULATOR_$gameNativeAmazonEmulatorName% %ACTION%=app.gamenative.LAUNCH_GAME %EXTRAINTEGER_app_id%=%INJECT%=%ROM% %EXTRA_game_source%=AMAZON"
        )

        // Update or create the Windows system entry
        // The first command in the list is the default in ES-DE, so we put GameHub Lite first
        val windowsSystemIndex = existingSystems.indexOfFirst { it.name == "windows" }
        if (windowsSystemIndex >= 0) {
            val existingSystem = existingSystems[windowsSystemIndex]
            // Remove any existing GameHub Lite or GameNative commands
            val otherCommands = existingSystem.commands.filter { cmd ->
                cmd.label != "GameHub Lite" && !cmd.command.contains("GAMEHUB-LITE") &&
                !cmd.command.contains("GAMENATIVE")
            }
            // Ensure extensions include .gog, .epic and .amazon
            val requiredExtensions = listOf(".steam", ".gog", ".epic", ".amazon")
            var updatedExtensions = existingSystem.extensions
            for (ext in requiredExtensions) {
                if (!updatedExtensions.contains(ext)) {
                    updatedExtensions = "$updatedExtensions $ext"
                }
            }
            existingSystems[windowsSystemIndex] = existingSystem.copy(
                commands = listOf(gameHubLiteCommand, gameNativeSteamCommand, gameNativeGogCommand, gameNativeEpicCommand, gameNativeAmazonCommand) + otherCommands,
                extensions = updatedExtensions
            )
        } else {
            // Create new Windows system entry with all launchers
            val windowsSystem = GameSystem(
                name = "windows",
                fullName = "Windows",
                path = windowsRomPath,
                extensions = ".bat .cmd .exe .game .lnk .ps1 .steam .gog .epic .amazon .desktop",
                commands = listOf(gameHubLiteCommand, gameNativeSteamCommand, gameNativeGogCommand, gameNativeEpicCommand, gameNativeAmazonCommand),
                platform = "pc",
                theme = "windows"
            )
            existingSystems.add(windowsSystem)
        }

        // Update or create the find rules entry for GameHub Lite
        val gameHubLiteEntry = "gamehub.lite/com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
        val existingGameHubLiteRuleIndex = existingRules.indexOfFirst { it.name == gameHubLiteEmulatorName }
        if (existingGameHubLiteRuleIndex >= 0) {
            val existingRule = existingRules[existingGameHubLiteRuleIndex]
            if (!existingRule.entries.contains(gameHubLiteEntry)) {
                existingRules[existingGameHubLiteRuleIndex] = existingRule.copy(
                    entries = existingRule.entries + gameHubLiteEntry
                )
            }
        } else {
            existingRules.add(EmulatorRule(name = gameHubLiteEmulatorName, entries = listOf(gameHubLiteEntry)))
        }

        // Update or create find rules for GameNative GOG, Epic, and Amazon.
        // All emulator names point to the same app package — ES-DE resolves the package via find rules.
        val gameNativeEntry = "app.gamenative/app.gamenative.MainActivity"
        val legacyGameNativeEntry = "com.nickmafra.gamenative/com.nickmafra.gamenative.MainActivity"

        fun upsertGameNativeRule(emulatorName: String) {
            val idx = existingRules.indexOfFirst { it.name == emulatorName }
            if (idx >= 0) {
                val existing = existingRules[idx]
                val entries = existing.entries.toMutableList()
                if (!entries.contains(gameNativeEntry)) entries.add(gameNativeEntry)
                if (!entries.contains(legacyGameNativeEntry)) entries.add(legacyGameNativeEntry)
                existingRules[idx] = existing.copy(entries = entries)
            } else {
                existingRules.add(EmulatorRule(name = emulatorName, entries = listOf(gameNativeEntry, legacyGameNativeEntry)))
            }
        }

        upsertGameNativeRule(gameNativeSteamEmulatorName)
        upsertGameNativeRule(gameNativeGogEmulatorName)
        upsertGameNativeRule(gameNativeEpicEmulatorName)
        upsertGameNativeRule(gameNativeAmazonEmulatorName)

        // Remove any old single GAMENATIVE rule that may exist from a previous config version
        existingRules.removeAll { it.name == "GAMENATIVE" }

        // Write updated configurations
        val writeRulesResult = writeCustomFindRules(existingRules)
        if (writeRulesResult is ConfigResult.Error) {
            return writeRulesResult
        }

        val writeSystemsResult = writeCustomSystems(existingSystems)
        if (writeSystemsResult is ConfigResult.Error) {
            return writeSystemsResult
        }

        return ConfigResult.Success(Unit)
    }

    /**
     * Check if GameHub Lite is properly configured for Windows in ES-DE.
     * Returns true only if GameHub Lite is configured as the first (default) command.
     */
    fun isGameHubLiteConfiguredForWindows(): Boolean {
        val customResult = readCustomSystems()
        if (customResult is ConfigResult.Success) {
            val windowsSystem = customResult.data.find { it.name == "windows" }
            if (windowsSystem != null && windowsSystem.commands.isNotEmpty()) {
                // Check if GameHub Lite is the first (default) command
                val firstCommand = windowsSystem.commands.first()
                return firstCommand.command.contains("GAMEHUB-LITE") &&
                       firstCommand.command.contains("autoStartGame")
            }
        }
        return false
    }

    /**
     * Check if GameNative is properly configured for Windows in ES-DE.
     * Returns true if GameNative is configured as one of the available commands.
     */
    fun isGameNativeConfiguredForWindows(): Boolean {
        val customResult = readCustomSystems()
        if (customResult is ConfigResult.Success) {
            val windowsSystem = customResult.data.find { it.name == "windows" }
            if (windowsSystem != null && windowsSystem.commands.isNotEmpty()) {
                // Check if GameNative is configured as any command
                return windowsSystem.commands.any { command ->
                    command.command.contains("GAMENATIVE") &&
                    command.command.contains("app.gamenative.LAUNCH_GAME")
                }
            }
        }
        return false
    }

    /**
     * Backup current custom configuration
     */
    fun backupCustomConfig(): ConfigResult<File> {
        val customDir = getCustomSystemsDirectory()
            ?: return ConfigResult.Error("ES-DE custom_systems directory not found")

        val backupDir = File(customDir, "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val backupFolder = File(backupDir, "backup_$timestamp")
        backupFolder.mkdirs()

        return try {
            // Backup systems file
            val systemsFile = File(customDir, ES_SYSTEMS_FILE)
            if (systemsFile.exists()) {
                systemsFile.copyTo(File(backupFolder, ES_SYSTEMS_FILE))
            }

            // Backup rules file
            val rulesFile = File(customDir, ES_FIND_RULES_FILE)
            if (rulesFile.exists()) {
                rulesFile.copyTo(File(backupFolder, ES_FIND_RULES_FILE))
            }

            ConfigResult.Success(backupFolder)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to create backup: ${e.message}", e)
        }
    }
}
