package com.esde.emulatormanager.data.repository

import com.esde.emulatormanager.data.KnownEmulators
import com.esde.emulatormanager.data.model.*
import com.esde.emulatormanager.data.service.CustomEmulatorService
import com.esde.emulatormanager.data.service.EmulatorDetectionService
import com.esde.emulatormanager.data.service.EsdeConfigService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.os.Environment

/**
 * Repository for managing emulator configurations
 */
@Singleton
class EmulatorRepository @Inject constructor(
    private val emulatorDetectionService: EmulatorDetectionService,
    private val esdeConfigService: EsdeConfigService,
    private val customEmulatorService: CustomEmulatorService
) {
    /**
     * Get all systems with their emulator configurations
     * Reads state from ES-DE XML files, not internal storage
     */
    suspend fun getSystemConfigurations(): ConfigResult<List<SystemEmulatorConfig>> =
        withContext(Dispatchers.IO) {
            try {
                // Get all installed emulators (known ones)
                val installedEmulators = emulatorDetectionService.getInstalledEmulators()

                // Read current configuration from ES-DE XML files
                val customSystemsResult = esdeConfigService.readCustomSystems()
                val customSystems = when (customSystemsResult) {
                    is ConfigResult.Success -> customSystemsResult.data
                    is ConfigResult.Error -> emptyList()
                }

                val customRulesResult = esdeConfigService.readCustomFindRules()
                val customRules = when (customRulesResult) {
                    is ConfigResult.Success -> customRulesResult.data
                    is ConfigResult.Error -> emptyList()
                }

                // Build a map of configured emulator packages per system from XML files
                val configuredPackagesBySystem = buildConfiguredPackagesFromXml(customSystems, customRules)

                // Get bundled system definitions
                val systems = getBundledSystems()

                // Map each system to its configuration
                val configs = systems.map { system ->
                    // Find known emulators that support this system
                    val compatibleKnownEmulators = installedEmulators.filter { installed ->
                        val knownEmulator = KnownEmulators.findByPackageNameFuzzy(installed.packageName, installed.appName)
                        knownEmulator?.supportedSystems?.contains(system.name) == true
                    }

                    // Find custom emulators (from XML) that support this system
                    val configuredPackages = configuredPackagesBySystem[system.name] ?: emptySet()
                    val compatibleCustomEmulators = configuredPackages
                        .mapNotNull { pkg ->
                            emulatorDetectionService.getAppInfo(pkg)
                        }
                        .filter { app -> KnownEmulators.findByPackageNameFuzzy(app.packageName, app.appName) == null } // Exclude known emulators

                    // Combine both lists, avoiding duplicates
                    val allCompatibleEmulators = (compatibleKnownEmulators + compatibleCustomEmulators)
                        .distinctBy { it.packageName }

                    // Get configured emulators from the XML custom systems
                    val customSystemConfig = customSystems.find { it.name == system.name }
                    val configuredEmulatorNames = customSystemConfig?.commands?.mapNotNull { it.label } ?: emptyList()

                    SystemEmulatorConfig(
                        system = system,
                        configuredEmulators = configuredEmulatorNames,
                        availableEmulators = allCompatibleEmulators
                    )
                }

                ConfigResult.Success(configs)
            } catch (e: Exception) {
                ConfigResult.Error("Failed to load system configurations: ${e.message}", e)
            }
        }

    /**
     * Get the ROM path for a specific system
     */
    suspend fun getSystemPath(systemId: String): String? = withContext(Dispatchers.IO) {
        val customSystemsResult = esdeConfigService.readCustomSystems()
        val customSystems = when (customSystemsResult) {
            is ConfigResult.Success -> customSystemsResult.data
            is ConfigResult.Error -> emptyList()
        }

        val customSystem = customSystems.find { it.name == systemId }
        if (customSystem != null && customSystem.path.isNotEmpty()) {
            val esdeDir = esdeConfigService.findEsdeDirectory()
            return@withContext if (esdeDir != null) {
                File(esdeDir.parent, customSystem.path).absolutePath
            } else {
                customSystem.path
            }
        }

        val bundledSystem = getBundledSystems().find { it.name == systemId }
        if (bundledSystem != null && bundledSystem.path.isNotEmpty()) {
            val esdeDir = esdeConfigService.findEsdeDirectory()
            val romPath = if (esdeDir != null) {
                bundledSystem.path.replace("%ROMPATH%", esdeDir.absolutePath)
            } else {
                bundledSystem.path.replace("%ROMPATH%", Environment.getExternalStorageDirectory().absolutePath)
            }
            return@withContext File(romPath).absolutePath
        }

        return@withContext null
    }


    /**
     * Build a map of system IDs to configured package names by parsing XML files
     */
    private fun buildConfiguredPackagesFromXml(
        customSystems: List<GameSystem>,
        customRules: List<EmulatorRule>
    ): Map<String, Set<String>> {
        // Create a map of emulator name to package names from find rules
        val emulatorNameToPackages = mutableMapOf<String, MutableSet<String>>()
        for (rule in customRules) {
            val packages = mutableSetOf<String>()
            for (entry in rule.entries) {
                // Extract package name from entry (format: "package/activity" or "package")
                val packageName = entry.substringBefore("/")
                packages.add(packageName)
            }
            emulatorNameToPackages[rule.name] = packages
        }

        // Now map systems to packages based on their commands
        val result = mutableMapOf<String, MutableSet<String>>()
        for (system in customSystems) {
            val packages = mutableSetOf<String>()
            for (command in system.commands) {
                // Extract emulator name from command (format: %EMULATOR_NAME% ...)
                val match = Regex("%EMULATOR_([^%]+)%").find(command.command)
                if (match != null) {
                    val emulatorName = match.groupValues[1]
                    // Get packages for this emulator
                    emulatorNameToPackages[emulatorName]?.let { packages.addAll(it) }
                }
            }
            result[system.name] = packages
        }

        return result
    }

    /**
     * Add an emulator to a system
     */
    suspend fun addEmulatorToSystem(
        systemId: String,
        emulator: InstalledEmulator
    ): ConfigResult<Unit> = withContext(Dispatchers.IO) {
        // Find the system definition
        val system = getBundledSystems().find { it.name == systemId }
            ?: return@withContext ConfigResult.Error("System definition not found for ID: $systemId")

        // Check if this is a known emulator with specific configuration
        // Use fuzzy matching to handle variant package names (forks, different versions)
        val knownEmulator = KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName)
        val launchArguments = knownEmulator?.launchCommand
        val emulationActivity = knownEmulator?.emulationActivity

        esdeConfigService.addEmulatorToSystem(
            system = system,
            emulator = emulator,
            label = emulator.appName,
            launchArguments = launchArguments,
            emulationActivity = emulationActivity
        )
    }

    /**
     * Remove an emulator from a system
     */
    suspend fun removeEmulatorFromSystem(
        systemId: String,
        emulatorPackage: String
    ): ConfigResult<Unit> = withContext(Dispatchers.IO) {
        esdeConfigService.removeEmulatorFromSystem(systemId, emulatorPackage)
    }

    /**
     * Get installed emulators
     */
    suspend fun getInstalledEmulators(): List<InstalledEmulator> =
        withContext(Dispatchers.IO) {
            emulatorDetectionService.getInstalledEmulators()
        }

    /**
     * Get installed emulators for a specific system (including custom ones)
     */
    suspend fun getInstalledEmulatorsForSystem(systemId: String): List<InstalledEmulator> =
        withContext(Dispatchers.IO) {
            // Get known emulators
            val knownEmulators = emulatorDetectionService.getInstalledEmulatorsForSystem(systemId)

            // Get custom emulators for this system
            val customEmulators = customEmulatorService.getCustomEmulatorsForSystem(systemId)
                .mapNotNull { mapping ->
                    emulatorDetectionService.getAppInfo(mapping.packageName)
                }

            // Combine and deduplicate
            (knownEmulators + customEmulators).distinctBy { it.packageName }
        }

    /**
     * Get all installed apps on the device
     */
    suspend fun getAllInstalledApps(): List<InstalledEmulator> =
        withContext(Dispatchers.IO) {
            emulatorDetectionService.getAllInstalledApps()
        }

    /**
     * Add a custom emulator mapping - writes directly to ES-DE XML files
     */
    suspend fun addCustomEmulator(
        emulator: InstalledEmulator,
        supportedSystems: List<String>
    ) = withContext(Dispatchers.IO) {
        // For each supported system, add the emulator to the ES-DE config
        for (systemId in supportedSystems) {
            val system = getBundledSystems().find { it.name == systemId } ?: continue
            esdeConfigService.addEmulatorToSystem(
                system = system,
                emulator = emulator,
                label = emulator.appName,
                launchArguments = null, // Custom emulators use default fallback
                emulationActivity = emulator.activityName
            )
        }
    }

    /**
     * Remove a custom emulator - removes from ES-DE XML files for all systems
     */
    suspend fun removeCustomEmulator(packageName: String) =
        withContext(Dispatchers.IO) {
            // Remove from all systems
            for (system in getBundledSystems()) {
                esdeConfigService.removeEmulatorFromSystem(system.name, packageName)
            }
        }

    /**
     * Get all custom emulator mappings from ES-DE XML files
     */
    fun getCustomEmulators(): List<CustomEmulatorMapping> {
        val customSystemsResult = esdeConfigService.readCustomSystems()
        val customRulesResult = esdeConfigService.readCustomFindRules()

        val customSystems = when (customSystemsResult) {
            is ConfigResult.Success -> customSystemsResult.data
            is ConfigResult.Error -> return emptyList()
        }

        val customRules = when (customRulesResult) {
            is ConfigResult.Success -> customRulesResult.data
            is ConfigResult.Error -> return emptyList()
        }

        // Build mappings from XML data
        val mappings = mutableMapOf<String, CustomEmulatorMapping>()

        // Get package-to-emulatorName mapping from rules
        val packageToEmulatorName = mutableMapOf<String, String>()
        for (rule in customRules) {
            for (entry in rule.entries) {
                val packageName = entry.substringBefore("/")
                packageToEmulatorName[packageName] = rule.name
            }
        }

        // Build system lists for each package
        for (system in customSystems) {
            for (command in system.commands) {
                // Extract emulator name from command
                val match = Regex("%EMULATOR_([^%]+)%").find(command.command) ?: continue
                val emulatorName = match.groupValues[1]

                // Find package name for this emulator
                val rule = customRules.find { it.name == emulatorName } ?: continue
                for (entry in rule.entries) {
                    val packageName = entry.substringBefore("/")
                    val appName = command.label ?: emulatorName

                    // Skip known emulators (use fuzzy matching)
                    if (KnownEmulators.findByPackageNameFuzzy(packageName, appName) != null) continue

                    val existing = mappings[packageName]
                    if (existing != null) {
                        // Add system to existing mapping
                        if (!existing.supportedSystems.contains(system.name)) {
                            mappings[packageName] = existing.copy(
                                supportedSystems = existing.supportedSystems + system.name
                            )
                        }
                    } else {
                        // Create new mapping
                        val activityName = if (entry.contains("/")) {
                            entry.substringAfter("/")
                        } else null

                        mappings[packageName] = CustomEmulatorMapping(
                            packageName = packageName,
                            appName = appName,
                            activityName = activityName,
                            supportedSystems = listOf(system.name)
                        )
                    }
                }
            }
        }

        return mappings.values.toList()
    }

    /**
     * Check if an emulator is configured in ES-DE XML files (and not a known emulator)
     * Note: Uses fuzzy matching by looking at package name patterns
     */
    fun isCustomEmulator(packageName: String): Boolean {
        // Try to determine if this is a known emulator by checking package name patterns
        // We use empty string for appName since we don't have it here - the fuzzy matcher
        // will check package name patterns first
        val packageLower = packageName.lowercase()

        // Check common known emulator package patterns
        val knownPatterns = listOf(
            "retroarch", "yuzu", "eden", "sudachi", "citron", "suyu",
            "dolphin", "ppsspp", "duckstation", "drastic", "citra", "lime3ds",
            "aethersx2", "nethersx2", "mupen64", "melonds", "redream", "flycast",
            "vita3k", "cemu"
        )

        if (knownPatterns.any { packageLower.contains(it) }) {
            return false // It's a known emulator
        }

        // Check if it's in the ES-DE XML files
        val customRulesResult = esdeConfigService.readCustomFindRules()
        val customRules = when (customRulesResult) {
            is ConfigResult.Success -> customRulesResult.data
            is ConfigResult.Error -> return false
        }

        return customRules.any { rule ->
            rule.entries.any { entry ->
                entry.substringBefore("/") == packageName
            }
        }
    }

    /**
     * Check if an emulator (by package name) is configured for a specific system
     * Reads from ES-DE XML files
     */
    fun isEmulatorConfiguredForSystem(packageName: String, systemId: String): Boolean {
        val customSystemsResult = esdeConfigService.readCustomSystems()
        val customRulesResult = esdeConfigService.readCustomFindRules()

        val customSystems = when (customSystemsResult) {
            is ConfigResult.Success -> customSystemsResult.data
            is ConfigResult.Error -> return false
        }

        val customRules = when (customRulesResult) {
            is ConfigResult.Success -> customRulesResult.data
            is ConfigResult.Error -> return false
        }

        // Find emulator name(s) for this package from rules
        val emulatorNames = customRules
            .filter { rule -> rule.entries.any { it.substringBefore("/") == packageName } }
            .map { it.name }

        if (emulatorNames.isEmpty()) return false

        // Check if any of these emulator names are used in the system's commands
        val system = customSystems.find { it.name == systemId } ?: return false
        return system.commands.any { command ->
            emulatorNames.any { name -> command.command.contains("%EMULATOR_${name}%") }
        }
    }

    /**
     * Get bundled systems list
     */
    fun getAllSystems(): List<GameSystem> = getBundledSystems()

    /**
     * Check if ES-DE is configured
     */
    fun isEsdeConfigured(): Boolean = esdeConfigService.isEsdeConfigured()

    /**
     * Get ES-DE config path
     */
    fun getEsdeConfigPath(): String? = esdeConfigService.getEsdeConfigPath()

    /**
     * Backup configuration
     */
    suspend fun backupConfiguration(): ConfigResult<String> =
        withContext(Dispatchers.IO) {
            when (val result = esdeConfigService.backupCustomConfig()) {
                is ConfigResult.Success -> ConfigResult.Success(result.data.absolutePath)
                is ConfigResult.Error -> result
            }
        }

    /**
     * Get bundled system definitions (common retro systems)
     */
    private fun getBundledSystems(): List<GameSystem> {
        return listOf(
            // Nintendo Systems
            GameSystem("nes", "Nintendo Entertainment System", "%ROMPATH%/nes", ".nes .NES .zip .ZIP .7z .7Z", emptyList(), "nes", "nes"),
            GameSystem("snes", "Super Nintendo", "%ROMPATH%/snes", ".smc .SMC .sfc .SFC .zip .ZIP .7z .7Z", emptyList(), "snes", "snes"),
            GameSystem("n64", "Nintendo 64", "%ROMPATH%/n64", ".n64 .N64 .z64 .Z64 .v64 .V64 .zip .ZIP .7z .7Z", emptyList(), "n64", "n64"),
            GameSystem("gc", "Nintendo GameCube", "%ROMPATH%/gc", ".iso .ISO .gcm .GCM .ciso .CISO .rvz .RVZ", emptyList(), "gc", "gc"),
            GameSystem("wii", "Nintendo Wii", "%ROMPATH%/wii", ".iso .ISO .wbfs .WBFS .rvz .RVZ", emptyList(), "wii", "wii"),
            GameSystem("wiiu", "Nintendo Wii U", "%ROMPATH%/wiiu", ".elf .ELF .rpx .RPX .tmd .TMD .wua .WUA .wud .WUD .wuhb .WUHB .wux .WUX", emptyList(), "wiiu", "wiiu"),
            GameSystem("switch", "Nintendo Switch", "%ROMPATH%/switch", ".nsp .NSP .xci .XCI", emptyList(), "switch", "switch"),
            GameSystem("gb", "Game Boy", "%ROMPATH%/gb", ".gb .GB .zip .ZIP .7z .7Z", emptyList(), "gb", "gb"),
            GameSystem("gbc", "Game Boy Color", "%ROMPATH%/gbc", ".gbc .GBC .zip .ZIP .7z .7Z", emptyList(), "gbc", "gbc"),
            GameSystem("gba", "Game Boy Advance", "%ROMPATH%/gba", ".gba .GBA .zip .ZIP .7z .7Z", emptyList(), "gba", "gba"),
            GameSystem("nds", "Nintendo DS", "%ROMPATH%/nds", ".nds .NDS .zip .ZIP .7z .7Z", emptyList(), "nds", "nds"),
            GameSystem("3ds", "Nintendo 3DS", "%ROMPATH%/3ds", ".3ds .3DS .cia .CIA .app .APP", emptyList(), "3ds", "3ds"),
            GameSystem("fds", "Famicom Disk System", "%ROMPATH%/fds", ".fds .FDS .zip .ZIP .7z .7Z", emptyList(), "fds", "fds"),
            GameSystem("virtualboy", "Virtual Boy", "%ROMPATH%/virtualboy", ".vb .VB .zip .ZIP .7z .7Z", emptyList(), "virtualboy", "virtualboy"),

            // Sony Systems
            GameSystem("psx", "Sony PlayStation", "%ROMPATH%/psx", ".bin .BIN .cue .CUE .chd .CHD .iso .ISO .pbp .PBP", emptyList(), "psx", "psx"),
            GameSystem("ps2", "Sony PlayStation 2", "%ROMPATH%/ps2", ".iso .ISO .chd .CHD .cso .CSO .bin .BIN", emptyList(), "ps2", "ps2"),
            GameSystem("ps3", "Sony PlayStation 3", "%ROMPATH%/ps3", ".desktop .ps3 .PS3 .iso .ISO .sfb .SFB", emptyList(), "ps3", "ps3"),
            GameSystem("psp", "Sony PSP", "%ROMPATH%/psp", ".iso .ISO .cso .CSO .pbp .PBP", emptyList(), "psp", "psp"),
            GameSystem("psvita", "Sony PS Vita", "%ROMPATH%/psvita", ".vpk .VPK", emptyList(), "psvita", "psvita"),

            // Microsoft Systems
            GameSystem("xbox360", "Microsoft Xbox 360", "%ROMPATH%/xbox360", ".iso .ISO .xex .XEX .god .GOD", emptyList(), "xbox360", "xbox360"),

            // Sega Systems
            GameSystem("genesis", "Sega Genesis", "%ROMPATH%/genesis", ".bin .BIN .gen .GEN .md .MD .smd .SMD .zip .ZIP .7z .7Z", emptyList(), "genesis", "genesis"),
            GameSystem("megadrive", "Sega Mega Drive", "%ROMPATH%/megadrive", ".bin .BIN .gen .GEN .md .MD .smd .SMD .zip .ZIP .7z .7Z", emptyList(), "megadrive", "megadrive"),
            GameSystem("mastersystem", "Sega Master System", "%ROMPATH%/mastersystem", ".sms .SMS .zip .ZIP .7z .7Z", emptyList(), "mastersystem", "mastersystem"),
            GameSystem("gamegear", "Sega Game Gear", "%ROMPATH%/gamegear", ".gg .GG .zip .ZIP .7z .7Z", emptyList(), "gamegear", "gamegear"),
            GameSystem("saturn", "Sega Saturn", "%ROMPATH%/saturn", ".bin .BIN .cue .CUE .chd .CHD .iso .ISO", emptyList(), "saturn", "saturn"),
            GameSystem("dreamcast", "Sega Dreamcast", "%ROMPATH%/dreamcast", ".cdi .CDI .gdi .GDI .chd .CHD", emptyList(), "dreamcast", "dreamcast"),
            GameSystem("segacd", "Sega CD", "%ROMPATH%/segacd", ".bin .BIN .cue .CUE .chd .CHD .iso .ISO", emptyList(), "segacd", "segacd"),
            GameSystem("sega32x", "Sega 32X", "%ROMPATH%/sega32x", ".32x .32X .bin .BIN .zip .ZIP .7z .7Z", emptyList(), "sega32x", "sega32x"),

            // Atari Systems
            GameSystem("atari2600", "Atari 2600", "%ROMPATH%/atari2600", ".a26 .A26 .bin .BIN .zip .ZIP .7z .7Z", emptyList(), "atari2600", "atari2600"),
            GameSystem("atari5200", "Atari 5200", "%ROMPATH%/atari5200", ".a52 .A52 .bin .BIN .zip .ZIP .7z .7Z", emptyList(), "atari5200", "atari5200"),
            GameSystem("atari7800", "Atari 7800", "%ROMPATH%/atari7800", ".a78 .A78 .bin .BIN .zip .ZIP .7z .7Z", emptyList(), "atari7800", "atari7800"),
            GameSystem("atarilynx", "Atari Lynx", "%ROMPATH%/atarilynx", ".lnx .LNX .zip .ZIP .7z .7Z", emptyList(), "atarilynx", "atarilynx"),

            // Arcade
            GameSystem("arcade", "Arcade", "%ROMPATH%/arcade", ".zip .ZIP .7z .7Z", emptyList(), "arcade", "arcade"),
            GameSystem("mame", "MAME", "%ROMPATH%/mame", ".zip .ZIP .7z .7Z", emptyList(), "mame", "mame"),
            GameSystem("fbneo", "FinalBurn Neo", "%ROMPATH%/fbneo", ".zip .ZIP .7z .7Z", emptyList(), "fbneo", "fbneo"),
            GameSystem("neogeo", "Neo Geo", "%ROMPATH%/neogeo", ".zip .ZIP .7z .7Z", emptyList(), "neogeo", "neogeo"),

            // Computers
            GameSystem("dos", "DOS", "%ROMPATH%/dos", ".bat .BAT .com .COM .exe .EXE .dosz .DOSZ", emptyList(), "dos", "dos"),
            GameSystem("windows", "Windows", "%ROMPATH%/windows", ".exe .EXE .bat .BAT .lnk .LNK .msi .MSI", emptyList(), "windows", "windows"),
            GameSystem("pc", "PC", "%ROMPATH%/pc", ".exe .EXE .bat .BAT .lnk .LNK .msi .MSI", emptyList(), "pc", "pc"),
            GameSystem("amiga", "Commodore Amiga", "%ROMPATH%/amiga", ".adf .ADF .adz .ADZ .dms .DMS .hdf .HDF .ipf .IPF", emptyList(), "amiga", "amiga"),
            GameSystem("c64", "Commodore 64", "%ROMPATH%/c64", ".d64 .D64 .t64 .T64 .prg .PRG .crt .CRT .tap .TAP", emptyList(), "c64", "c64"),
            GameSystem("scummvm", "ScummVM", "%ROMPATH%/scummvm", ".scummvm .SCUMMVM", emptyList(), "scummvm", "scummvm"),

            // Other Handhelds
            GameSystem("wonderswan", "WonderSwan", "%ROMPATH%/wonderswan", ".ws .WS .zip .ZIP .7z .7Z", emptyList(), "wonderswan", "wonderswan"),
            GameSystem("wonderswancolor", "WonderSwan Color", "%ROMPATH%/wonderswancolor", ".wsc .WSC .zip .ZIP .7z .7Z", emptyList(), "wonderswancolor", "wonderswancolor"),
            GameSystem("ngp", "Neo Geo Pocket", "%ROMPATH%/ngp", ".ngp .NGP .zip .ZIP .7z .7Z", emptyList(), "ngp", "ngp"),
            GameSystem("ngpc", "Neo Geo Pocket Color", "%ROMPATH%/ngpc", ".ngc .NGC .ngpc .NGPC .zip .ZIP .7z .7Z", emptyList(), "ngpc", "ngpc"),

            // NEC
            GameSystem("pcengine", "PC Engine", "%ROMPATH%/pcengine", ".pce .PCE .zip .ZIP .7z .7Z", emptyList(), "pcengine", "pcengine"),
            GameSystem("tg16", "TurboGrafx-16", "%ROMPATH%/tg16", ".pce .PCE .zip .ZIP .7z .7Z", emptyList(), "tg16", "tg16")
        )
    }

    /**
     * Configure GameHub Lite for Windows system with proper launch command
     */
    suspend fun configureGameHubLiteForWindows(windowsRomPath: String): ConfigResult<Unit> =
        withContext(Dispatchers.IO) {
            esdeConfigService.configureGameHubLiteForWindows(windowsRomPath)
        }

    /**
     * Check if GameHub Lite is properly configured for Windows
     */
    suspend fun isGameHubLiteConfiguredForWindows(): Boolean =
        withContext(Dispatchers.IO) {
            esdeConfigService.isGameHubLiteConfiguredForWindows()
        }

    /**
     * Check if GameNative is properly configured for Windows
     */
    suspend fun isGameNativeConfiguredForWindows(): Boolean =
        withContext(Dispatchers.IO) {
            esdeConfigService.isGameNativeConfiguredForWindows()
        }

    /**
     * Get the media directory for a system and media type
     */
    fun getMediaDirectory(systemName: String, mediaType: String): File? =
        esdeConfigService.getMediaDirectory(systemName, mediaType)

    /**
     * Get the base media directory path (for display/debugging)
     */
    fun getMediaBasePath(): String? = esdeConfigService.getMediaBasePath()

    /**
     * Get the path to the es_settings.xml file (for display/debugging)
     */
    fun getSettingsFilePath(): String? = esdeConfigService.getSettingsFilePath()

}
