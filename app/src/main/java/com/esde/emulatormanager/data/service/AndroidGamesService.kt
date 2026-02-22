package com.esde.emulatormanager.data.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.util.Log
import com.esde.emulatormanager.data.model.AndroidGame
import com.esde.emulatormanager.data.model.AndroidTab
import com.esde.emulatormanager.data.model.AppCategory
import com.esde.emulatormanager.data.model.ConfigResult
import com.esde.emulatormanager.data.model.StaleAndroidEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing Android games/apps for ES-DE.
 * Scans installed apps and creates shortcut files that ES-DE can use to launch them.
 */
@Singleton
class AndroidGamesService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esdeConfigService: EsdeConfigService
) {
    companion object {
        private const val TAG = "AndroidGamesService"

        // Possible ES-DE android system folder names
        private val ANDROID_SYSTEM_NAMES = listOf("androidgames", "android")

        // Shortcut file extension for Android apps
        private const val ANDROID_EXTENSION = ".app"

        // Packages to exclude (system apps, launchers, system UI components, etc.)
        private val EXCLUDED_PACKAGES = setOf(
            "com.android.vending",           // Play Store
            "com.google.android.gms",        // Google Play Services
            "com.google.android.gsf",        // Google Services Framework
            "com.android.providers",         // System providers
            "com.android.systemui",          // System UI
            "com.android.settings",          // Settings
            "com.android.launcher",          // Launchers
            "com.google.android.launcher",
            // System UI components (navigation bars, cutouts, etc.)
            "com.android.internal.systemui",
            "com.android.systemui.navigation",
            "com.android.overlay",
            "com.android.theme"
        )

        // Package name patterns to always exclude (system UI, overlays, etc.)
        private val EXCLUDED_PACKAGE_PATTERNS = listOf(
            "navigationbar",
            "navigation.bar",
            "cutout",
            "gestural",
            "transparent.navigation",
            ".overlay.",
            "systemui.plugin"
        )

        // Keywords that indicate an app is likely a game (be more specific to avoid false positives)
        private val GAME_KEYWORDS = listOf(
            "arcade", "puzzle", "rpg", "racing", "shooter",
            "action", "adventure", "strategy", "simulation", "sports",
            "casino", "card", "board", "trivia"
        )

        // Packages that are known to be games (for common games that don't self-identify)
        private val KNOWN_GAME_PACKAGES = setOf(
            // Sonic games
            "com.sega.sonicmania", "com.sega.sonicmaniapluscustom",
            "com.sega.sonic1", "com.sega.sonic2", "com.sega.soniccd",
            "com.sega.sonic4ep1", "com.sega.sonic4ep2",
            // Netflix games (they use com.netflix.NGP.* prefix)
            "com.netflix.ngp.sonicmania", "com.netflix.ngp.worldofgoohd",
            // Other known games
            "com.twodboy.worldofgoo", "com.2dboy.worldofgoo",
            "com.innersloth.spacemafia",  // Among Us
            "com.mojang.minecraftpe",     // Minecraft
            "com.supercell.clashofclans",
            "com.king.candycrushsaga",
            "com.rovio.angrybirds"
        )

        // Package patterns that indicate a game (for Netflix games, etc.)
        private val GAME_PACKAGE_PATTERNS = listOf(
            "com.netflix.ngp."  // Netflix Games Platform games
        )

        // Packages that should NOT be categorized as games (launchers, tools, etc.)
        private val NON_GAME_PACKAGES = setOf(
            // ES-DE and related
            "es.dedes.frontend", "org.es_de.esde", "org.es_de.frontend",
            // Game launchers/stores (not games themselves)
            "com.epicgames.launcher", "com.epicgames.portal",
            "com.valve.steam", "com.valvesoftware.android.steam.community",
            "com.gog.galaxy",
            // Game assistants and utilities
            "com.lenovo.minigamelauncher", "com.lenovo.gameassistant",
            // Music/media players (often have "play" in category)
            "org.musicpd", "org.videolan.vlc",
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            // Utility apps miscategorized as games
            "com.lightlight.noir",         // Artemis (utility app)
            "com.legion.xtouch"            // Ultra Control (controller utility)
        )

        // Patterns in package names that indicate NOT a game
        private val NON_GAME_PATTERNS = listOf(
            "gameassistant", "gamelauncher", "gameoptimizer", "gamebooster",
            "musicplayer", "mediaplayer", "firmware", "update"
        )

        // Known emulator package names and keywords
        private val EMULATOR_PACKAGES = setOf(
            // RetroArch
            "com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32",
            // Standalone emulators
            "org.ppsspp.ppsspp", "org.ppsspp.ppssppgold",  // PPSSPP
            "org.dolphinemu.dolphinemu", "org.dolphinemu.dolphinemu.mmj",  // Dolphin
            "org.citra.emu", "org.citra.citra_emu",  // Citra
            "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.ea",  // Yuzu
            "skyline.emu", "emu.skyline",  // Skyline
            "com.drastic.drastic",  // DraStic
            "com.dsemu.drastic",
            "com.explusalpha.Snes9xPlus", "com.snes9x.snes9x",  // SNES9x
            "com.explusalpha.NesEmu", "com.explusalpha.GbaEmu", "com.explusalpha.GbcEmu",
            "com.explusalpha.MasterEmu", "com.explusalpha.Md.emu", "com.explusalpha.PceEmu",
            "com.explusalpha.A2600Emu", "com.explusalpha.MsxEmu", "com.explusalpha.NgpEmu",
            "com.explusalpha.C64Emu", "com.explusalpha.SwanEmu", "com.explusalpha.SaturnEmu",
            "ru.nickcreature.pizza", // Pizza Boy GBA/GBC
            "it.dbtecno.pizzaboygba", "it.dbtecno.pizzaboygbc",
            "com.fastemulator.gba", "com.fastemulator.gbc",  // My Boy/OldBoy
            "com.bsemu.bstone", "com.bsemu.gcube",  // ClassicBoy
            "com.epsxe.ePSXe",  // ePSXe
            "com.fpse.emulator", "com.fpse64.emulator",  // FPse
            "com.dsemu.desmume", "com.nds4droid",  // DS emulators
            "com.reicast.emulator", "com.flycast.emulator",  // Dreamcast
            "org.mupen64plusae.v3.alpha", "org.mupen64plusae.v3.fzurita",  // Mupen64Plus
            "com.emu.project64", "com.m64plusfz",  // N64
            "paulscode.android.mupen64plus.free",
            "com.play.emulator", "xyz.aethersx2.android",  // PS2
            "com.redream", "org.reicast.emulator",  // Dreamcast
            "io.recompiled.redream",  // Redream
            "com.lemonemu.lemonemu",  // Lemon
            "org.scummvm.scummvm",  // ScummVM
            "org.dosbox.dosbox",  // DOSBox
            "com.magicbox.dosbox",
            "com.github.nicholasb", "com.limbo.emu",  // Limbo/PC emulators
            "net.sourceforge.uqm",  // Various ports
            "com.seleuco.mame4droid", "com.seleuco.mame4droid.x",  // MAME
            "com.fms.fba4droid",  // FBA
            "org.mamedev.mame",
            "com.joselugg.android.emu", // AetherSX2
            "net.nekotachi.puyopuyo", "org.citra.citra_emu.canary",
            "io.github.lime3ds.android", // Lime3DS
            "io.github.mandarine3ds.android", // Mandarine3DS
            "lemuroid.touchlay",  // Lemuroid
            "com.iware.ducks", "com.mgba.emu", // mGBA
            "org.melonds.melondsandroid",  // MelonDS
            "me.magnum.melonds",  // MelonDS (other package)
            "com.provenance.emu", "com.emubox.emubox",
            "com.mstaremu.mstar", "com.happychick.emu",
            "com.nostalgiaemulators.nssnes", "com.nostalgiaemulators.nsgbc",
            "com.nostalgiaemulators.nsnes", "com.nostalgiaemulators.nsgba",
            "com.nostalgiaemulators.nsn64", "com.nostalgiaemulators.nsmd",
            // DuckStation
            "com.github.stenzek.duckstation",
            // GameHub Lite and similar Windows game launchers (act as emulators)
            "com.nickmafra.gamehublite", "gamehub.lite",
            "com.nickmafra.gamehub", "app.gamenative", "com.nickmafra.gamenative",
            "com.winlator", "com.winlator.debug",
            "com.xtr3d.mobox",
            // 3DS emulators - Azahar/Citra forks
            "org.panda3ds.panda3ds", "io.github.panda3ds",
            "org.azahar.azahar_emu", "com.panda3ds.pandroid",
            // Switch emulators
            "org.sudachi.sudachi_emu", "org.suyu.suyu_emu",
            "org.citron.citron_emu", "org.uzuy.uzuy_emu",
            "com.sumi.SumiEmulator", "org.Ziunx.Ziunx_emu",
            "dev.eden.eden_emulator", "dev.eden.eden_nightly",
            // Vita
            "org.vita3k.emulator",
            // Wii U
            "info.cemu.Cemu", "info.cemu.cemu",
            // Lemuroid/other frontend emulators
            "com.swordfish.lemuroid"
        )

        // Additional emulator patterns (for forks and variants)
        private val EMULATOR_PACKAGE_PATTERNS = listOf(
            "azahar", "panda3ds", "sudachi", "citron", "suyu", "uzuy",
            "vita3k", "cemu"
        )

        // Keywords that indicate an app is an emulator
        private val EMULATOR_KEYWORDS = listOf(
            "emulator", "emu", "retroarch", "ppsspp", "dolphin", "citra", "yuzu",
            "drastic", "desmume", "snes9x", "mupen64", "epsxe", "pcsx", "fpse",
            "reicast", "flycast", "redream", "mame", "dosbox", "scummvm",
            "aethersx2", "melonds", "mgba", "pizza boy", "myboy", "my boy",
            "lemuroid", "skyline"
        )
    }

    // SharedPreferences for persistent storage
    private val prefs = context.getSharedPreferences("android_games_prefs", Context.MODE_PRIVATE)
    private val PREF_GAMES_PATH = "custom_games_path"
    private val PREF_APPS_PATH = "custom_apps_path"
    private val PREF_EMULATORS_PATH = "custom_emulators_path"
    private val PREF_CLASSIFICATION_OVERRIDES = "classification_overrides"

    // Default system folder names for each category
    private val DEFAULT_GAMES_FOLDER = "androidgames"
    private val DEFAULT_APPS_FOLDER = "android"
    private val DEFAULT_EMULATORS_FOLDER = "emulators"

    /**
     * Get/set custom path for Android games
     */
    var customGamesPath: String?
        get() = prefs.getString(PREF_GAMES_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(PREF_GAMES_PATH, value)
                else remove(PREF_GAMES_PATH)
                apply()
            }
        }

    /**
     * Get/set custom path for Android apps
     */
    var customAppsPath: String?
        get() = prefs.getString(PREF_APPS_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(PREF_APPS_PATH, value)
                else remove(PREF_APPS_PATH)
                apply()
            }
        }

    /**
     * Get/set custom path for emulators
     */
    var customEmulatorsPath: String?
        get() = prefs.getString(PREF_EMULATORS_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(PREF_EMULATORS_PATH, value)
                else remove(PREF_EMULATORS_PATH)
                apply()
            }
        }

    /**
     * Set a custom path for a specific category.
     */
    fun setCustomPath(tab: AndroidTab, path: String?) {
        when (tab) {
            AndroidTab.GAMES -> customGamesPath = path
            AndroidTab.APPS -> customAppsPath = path
            AndroidTab.EMULATORS -> customEmulatorsPath = path
        }
    }

    /**
     * Get the ES-DE ROM path for a specific category.
     */
    fun getPathForTab(tab: AndroidTab): String? {
        return when (tab) {
            AndroidTab.GAMES -> getGamesPath()
            AndroidTab.APPS -> getAppsPath()
            AndroidTab.EMULATORS -> getEmulatorsPath()
        }
    }

    /**
     * Get the system folder name for a specific category.
     */
    fun getSystemNameForTab(tab: AndroidTab): String {
        val path = getPathForTab(tab)
        return if (path != null) File(path).name else when (tab) {
            AndroidTab.GAMES -> DEFAULT_GAMES_FOLDER
            AndroidTab.APPS -> DEFAULT_APPS_FOLDER
            AndroidTab.EMULATORS -> DEFAULT_EMULATORS_FOLDER
        }
    }

    private fun getGamesPath(): String? {
        customGamesPath?.let { if (it.isNotBlank()) return it }
        return findOrCreatePath(DEFAULT_GAMES_FOLDER, listOf("androidgames", "android"))
    }

    private fun getAppsPath(): String? {
        customAppsPath?.let { if (it.isNotBlank()) return it }
        return findOrCreatePath(DEFAULT_APPS_FOLDER, listOf("android", "androidapps"))
    }

    private fun getEmulatorsPath(): String? {
        customEmulatorsPath?.let { if (it.isNotBlank()) return it }
        return findOrCreatePath(DEFAULT_EMULATORS_FOLDER, listOf("emulators"))
    }

    private fun findOrCreatePath(defaultFolder: String, searchFolders: List<String>): String? {
        // Try to get from ES-DE config first
        for (folder in searchFolders) {
            val configuredPath = esdeConfigService.getSystemRomPath(folder)
            if (configuredPath != null) {
                Log.d(TAG, "Found ES-DE configured path for $folder: $configuredPath")
                return configuredPath
            }
        }

        // Build list of all possible paths to check
        val possiblePaths = mutableListOf<File>()
        for (folder in searchFolders) {
            possiblePaths.addAll(listOf(
                File(Environment.getExternalStorageDirectory(), "ROMs/$folder"),
                File(Environment.getExternalStorageDirectory(), "Roms/$folder"),
                File(Environment.getExternalStorageDirectory(), "ES-DE/ROMs/$folder"),
                File(Environment.getExternalStorageDirectory(), "ES-DE/Roms/$folder")
            ))
        }

        // First, look for paths that contain .app files (actual shortcuts)
        for (path in possiblePaths) {
            if (path.exists() && path.isDirectory) {
                val hasAppFiles = path.listFiles()?.any { it.name.endsWith(ANDROID_EXTENSION) } == true
                if (hasAppFiles) {
                    Log.d(TAG, "Found path with .app files: ${path.absolutePath}")
                    return path.absolutePath
                }
            }
        }

        // Then look for any existing directory
        for (path in possiblePaths) {
            if (path.exists()) {
                Log.d(TAG, "Found existing path: ${path.absolutePath}")
                return path.absolutePath
            }
        }

        // Return default
        val defaultPath = File(Environment.getExternalStorageDirectory(), "ROMs/$defaultFolder").absolutePath
        Log.d(TAG, "Using default path: $defaultPath")
        return defaultPath
    }

    // ========== Classification Override Methods ==========

    /**
     * Get all user classification overrides as a map of packageName -> AppCategory
     */
    fun getClassificationOverrides(): Map<String, AppCategory> {
        val overridesJson = prefs.getString(PREF_CLASSIFICATION_OVERRIDES, null) ?: return emptyMap()
        return try {
            // Simple format: "pkg1:GAME,pkg2:APP,pkg3:EMULATOR"
            overridesJson.split(",")
                .filter { it.contains(":") }
                .associate { entry ->
                    val parts = entry.split(":")
                    parts[0] to AppCategory.valueOf(parts[1])
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing classification overrides", e)
            emptyMap()
        }
    }

    /**
     * Set a classification override for an app
     */
    fun setClassificationOverride(packageName: String, category: AppCategory) {
        val current = getClassificationOverrides().toMutableMap()
        current[packageName] = category
        saveClassificationOverrides(current)
        Log.d(TAG, "Set classification override: $packageName -> $category")
    }

    /**
     * Remove a classification override for an app (revert to auto-detection)
     */
    fun removeClassificationOverride(packageName: String) {
        val current = getClassificationOverrides().toMutableMap()
        current.remove(packageName)
        saveClassificationOverrides(current)
        Log.d(TAG, "Removed classification override for: $packageName")
    }

    /**
     * Check if an app has a user override
     */
    fun hasClassificationOverride(packageName: String): Boolean {
        return getClassificationOverrides().containsKey(packageName)
    }

    /**
     * Get the user's classification for an app, or null if not overridden
     */
    fun getClassificationFor(packageName: String): AppCategory? {
        return getClassificationOverrides()[packageName]
    }

    private fun saveClassificationOverrides(overrides: Map<String, AppCategory>) {
        val json = overrides.entries.joinToString(",") { "${it.key}:${it.value.name}" }
        prefs.edit().putString(PREF_CLASSIFICATION_OVERRIDES, json).apply()
    }

    // Legacy compatibility - returns games path by default
    fun getEsdeAndroidPath(): String? = getGamesPath()

    /**
     * Get the system name (folder name) for the current tab.
     * Derives this from the actual ROM path being used.
     */
    private fun getAndroidSystemName(): String {
        val androidPath = getGamesPath() ?: return DEFAULT_GAMES_FOLDER
        return File(androidPath).name
    }

    /**
     * Get all installed apps that could be added to ES-DE.
     * Filters out system apps and known non-game packages.
     */
    fun getInstalledApps(): List<AndroidGame> {
        val pm = context.packageManager
        val apps = mutableListOf<AndroidGame>()

        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        // Get existing shortcuts with their tab locations
        val existingShortcutsWithTabs = getExistingShortcutPackagesWithTabs()

        // Get user classification overrides
        val overrides = getClassificationOverrides()

        for (appInfo in installedApps) {
            // Skip system apps (unless they're games)
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Skip excluded packages
            if (EXCLUDED_PACKAGES.any { appInfo.packageName.startsWith(it) }) {
                continue
            }

            // Skip packages matching exclusion patterns (system UI components)
            val packageLower = appInfo.packageName.lowercase()
            if (EXCLUDED_PACKAGE_PATTERNS.any { packageLower.contains(it) }) {
                continue
            }

            // Check for user classification override first
            val userOverride = overrides[appInfo.packageName]
            val hasUserOverride = userOverride != null

            // Determine category based on user override or auto-detection
            val (isGame, isEmulator) = when (userOverride) {
                AppCategory.GAME -> Pair(true, false)
                AppCategory.EMULATOR -> Pair(false, true)
                AppCategory.APP -> Pair(false, false)
                null -> {
                    // Auto-detect
                    val autoIsEmulator = isAppAnEmulator(appInfo)
                    val autoIsGame = if (autoIsEmulator) false else isAppAGame(appInfo)
                    Pair(autoIsGame, autoIsEmulator)
                }
            }

            // Skip pure system apps that aren't games or emulators (unless user overrode)
            if (!hasUserOverride && isSystemApp && !isUpdatedSystemApp && !isGame && !isEmulator) {
                continue
            }

            try {
                val appName = pm.getApplicationLabel(appInfo).toString()
                val icon = try {
                    pm.getApplicationIcon(appInfo.packageName)
                } catch (e: Exception) {
                    null
                }

                val inEsdeTabs = existingShortcutsWithTabs[appInfo.packageName] ?: emptySet()
                val isInEsde = inEsdeTabs.isNotEmpty()

                apps.add(
                    AndroidGame(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = icon,
                        isGame = isGame,
                        isEmulator = isEmulator,
                        isInEsde = isInEsde,
                        inEsdeTabs = inEsdeTabs,
                        shortcutFilePath = null, // Will be set per-tab when needed
                        hasUserOverride = hasUserOverride
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting app info for ${appInfo.packageName}", e)
            }
        }

        // Sort: games first, then alphabetically by name
        return apps.sortedWith(compareByDescending<AndroidGame> { it.isGame }.thenBy { it.appName.lowercase() })
    }

    /**
     * Check if an app is categorized as a game.
     */
    private fun isAppAGame(appInfo: ApplicationInfo): Boolean {
        val packageLower = appInfo.packageName.lowercase()

        // First check if this is a known NON-game package (launchers, utilities, etc.)
        if (NON_GAME_PACKAGES.any { packageLower.startsWith(it.lowercase()) }) {
            return false
        }

        // Check for patterns that indicate NOT a game
        if (NON_GAME_PATTERNS.any { packageLower.contains(it) }) {
            return false
        }

        // Check if it's a known game package
        if (KNOWN_GAME_PACKAGES.any { packageLower.startsWith(it.lowercase()) }) {
            return true
        }

        // Check if package matches game patterns (e.g., Netflix games)
        if (GAME_PACKAGE_PATTERNS.any { packageLower.startsWith(it.lowercase()) }) {
            return true
        }

        // Check Android's game category flag (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                return true
            }
        }

        // Check the FLAG_IS_GAME flag (deprecated but still useful)
        @Suppress("DEPRECATION")
        if ((appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0) {
            return true
        }

        // Check package name for game-related keywords (more restrictive now)
        if (GAME_KEYWORDS.any { packageLower.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Check if an app is a known emulator.
     */
    private fun isAppAnEmulator(appInfo: ApplicationInfo): Boolean {
        val packageName = appInfo.packageName.lowercase()

        // First exclude system UI components (they might match "emu" in "emulator")
        if (EXCLUDED_PACKAGE_PATTERNS.any { packageName.contains(it) }) {
            return false
        }

        // Check against known emulator packages
        if (EMULATOR_PACKAGES.any { packageName.startsWith(it.lowercase()) }) {
            return true
        }

        // Check for emulator package patterns (for forks and variants)
        if (EMULATOR_PACKAGE_PATTERNS.any { packageName.contains(it) }) {
            return true
        }

        // Check package name for emulator-related keywords
        if (EMULATOR_KEYWORDS.any { packageName.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Get packages that already have shortcuts in ES-DE across all categories.
     * Returns a map of package name to which tab(s) it's in.
     */
    private fun getExistingShortcutPackagesWithTabs(): Map<String, Set<AndroidTab>> {
        val result = mutableMapOf<String, MutableSet<AndroidTab>>()

        for (tab in AndroidTab.values()) {
            val path = getPathForTab(tab) ?: continue
            val dir = File(path)
            if (!dir.exists()) continue

            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (file.name.endsWith(ANDROID_EXTENSION)) {
                    try {
                        val content = file.readText().trim()
                        if (content.isNotBlank()) {
                            result.getOrPut(content) { mutableSetOf() }.add(tab)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading shortcut file: ${file.name}", e)
                    }
                }
            }
        }

        return result
    }

    /**
     * Get packages that already have shortcuts in ES-DE (legacy - checks all paths).
     */
    private fun getExistingShortcutPackages(): Set<String> {
        return getExistingShortcutPackagesWithTabs().keys
    }

    /**
     * Create a shortcut file for an Android app in ES-DE folder for a specific tab.
     * The .app file contains the package name.
     * Also saves the app icon as cover artwork if no artwork exists.
     */
    fun createShortcutForTab(game: AndroidGame, tab: AndroidTab): ConfigResult<String> {
        val path = getPathForTab(tab)
            ?: return ConfigResult.Error("Could not determine ES-DE path for ${tab.name}")

        val dir = File(path)
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return ConfigResult.Error("Could not create ${tab.name} ROM directory")
            }
        }

        // Use app name for the filename (sanitized)
        val safeFileName = game.appName
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(100) // Limit filename length

        val shortcutFile = File(dir, "$safeFileName$ANDROID_EXTENSION")

        return try {
            // The .app file just contains the package name
            shortcutFile.writeText(game.packageName)
            Log.d(TAG, "Created Android shortcut: ${shortcutFile.absolutePath} for package: ${game.packageName}")

            // Save app icon as cover artwork if no artwork exists
            saveAppIconAsArtworkForTab(game, safeFileName, tab)

            ConfigResult.Success(shortcutFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Android shortcut", e)
            ConfigResult.Error("Failed to create shortcut: ${e.message}", e)
        }
    }

    /**
     * Legacy method - creates shortcut in Games tab folder.
     */
    fun createShortcut(game: AndroidGame): ConfigResult<String> {
        return createShortcutForTab(game, AndroidTab.GAMES)
    }

    /**
     * Save an Android app's icon as cover artwork for a specific tab.
     */
    private fun saveAppIconAsArtworkForTab(game: AndroidGame, safeFileName: String, tab: AndroidTab) {
        val systemName = getSystemNameForTab(tab)
        saveAppIconAsArtworkImpl(game, safeFileName, systemName)
    }

    /**
     * Save an Android app's icon as cover artwork in ES-DE media folder.
     * Only saves if artwork doesn't already exist for this game.
     * Writes debug log to Downloads/esde_artwork_debug.log for troubleshooting.
     */
    private fun saveAppIconAsArtwork(game: AndroidGame, safeFileName: String) {
        val systemName = getAndroidSystemName()
        saveAppIconAsArtworkImpl(game, safeFileName, systemName)
    }

    private fun saveAppIconAsArtworkImpl(game: AndroidGame, safeFileName: String, systemName: String) {
        val logMessages = mutableListOf<String>()
        fun log(msg: String) {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())
            logMessages.add("$timestamp $msg")
            Log.d(TAG, msg)
        }

        try {
            log("=== saveAppIconAsArtwork START ===")
            log("App: ${game.appName}, Package: ${game.packageName}, SafeFileName: $safeFileName")
            log("System name: $systemName")

            // Get the covers directory for this system
            val coversDir = esdeConfigService.getMediaDirectory(systemName, "covers")
            if (coversDir == null) {
                log("ERROR: Could not get covers directory - getMediaDirectory returned null")
                writeDebugLog(logMessages)
                return
            }
            log("Covers dir: ${coversDir.absolutePath}")
            log("Covers dir exists: ${coversDir.exists()}, canWrite: ${coversDir.canWrite()}, isDirectory: ${coversDir.isDirectory}")

            // Check if artwork already exists
            val possibleExtensions = listOf(".png", ".jpg", ".jpeg", ".webp")
            var existingArtwork = false
            for (ext in possibleExtensions) {
                val f = File(coversDir, "$safeFileName$ext")
                if (f.exists()) {
                    log("Found existing artwork: ${f.absolutePath}")
                    existingArtwork = true
                    break
                }
            }

            if (existingArtwork) {
                log("Artwork already exists, skipping")
                writeDebugLog(logMessages)
                return
            }
            log("No existing artwork found, proceeding to create")

            // Fetch the icon directly from package manager
            log("Fetching icon from PackageManager...")
            val icon: Drawable? = try {
                val ic = context.packageManager.getApplicationIcon(game.packageName)
                log("Got icon: ${ic.javaClass.name}")
                ic
            } catch (e: PackageManager.NameNotFoundException) {
                log("PackageManager.NameNotFoundException: ${e.message}")
                log("Trying stored icon from game object...")
                game.icon
            }

            if (icon == null) {
                log("ERROR: No icon available (both PackageManager and stored icon are null)")
                writeDebugLog(logMessages)
                return
            }

            // Convert drawable to bitmap
            log("Converting drawable to bitmap...")
            val bitmap = drawableToBitmap(icon, logMessages)
            if (bitmap == null) {
                log("ERROR: drawableToBitmap returned null")
                writeDebugLog(logMessages)
                return
            }
            log("Bitmap created: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

            // Ensure directory exists
            if (!coversDir.exists()) {
                log("Creating covers directory...")
                val created = coversDir.mkdirs()
                log("mkdirs() result: $created")
                if (!created && !coversDir.exists()) {
                    log("ERROR: Failed to create covers directory")
                    writeDebugLog(logMessages)
                    return
                }
            }

            // Save as PNG
            val outputFile = File(coversDir, "$safeFileName.png")
            log("Output file: ${outputFile.absolutePath}")

            FileOutputStream(outputFile).use { out ->
                log("Writing PNG...")
                val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                log("compress() result: $compressed")
            }

            if (outputFile.exists()) {
                log("SUCCESS! File saved: ${outputFile.absolutePath}, size: ${outputFile.length()} bytes")
            } else {
                log("ERROR: File does not exist after write!")
            }

        } catch (e: Exception) {
            log("EXCEPTION: ${e.javaClass.name}: ${e.message}")
            log("Stack trace:\n${e.stackTraceToString()}")
        }

        log("=== saveAppIconAsArtwork END ===")
        writeDebugLog(logMessages)
    }

    /**
     * Write debug log to ES-DE folder for easy access.
     * Tries multiple locations: ES-DE folder, ROMs/android folder, app's external files dir.
     */
    private fun writeDebugLog(messages: List<String>) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        val content = buildString {
            append("\n========== $timestamp ==========\n")
            messages.forEach { append("$it\n") }
        }

        // Try multiple locations
        val possibleLocations = mutableListOf<File>()

        // 1. ES-DE folder (same place where ROMs are stored - user can access this)
        val esdeDir = File(Environment.getExternalStorageDirectory(), "ES-DE")
        if (esdeDir.exists()) {
            possibleLocations.add(File(esdeDir, "artwork_debug.log"))
        }

        // 2. ROMs/android folder (where shortcuts are created)
        getEsdeAndroidPath()?.let { androidPath ->
            possibleLocations.add(File(androidPath, "artwork_debug.log"))
        }

        // 3. App's external files directory (accessible via file manager at Android/data/com.esde.emulatormanager/files/)
        context.getExternalFilesDir(null)?.let {
            possibleLocations.add(File(it, "artwork_debug.log"))
        }

        for (logFile in possibleLocations) {
            try {
                logFile.parentFile?.mkdirs()
                logFile.appendText(content)
                Log.d(TAG, "Debug log written to: ${logFile.absolutePath}")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to write log to ${logFile.absolutePath}: ${e.message}")
            }
        }

        Log.e(TAG, "Failed to write debug log to any location")
    }

    /**
     * Convert a Drawable to a Bitmap.
     * Handles adaptive icons (Android 8+) and regular drawables.
     */
    private fun drawableToBitmap(drawable: Drawable, logMessages: MutableList<String>? = null): Bitmap? {
        fun log(msg: String) {
            logMessages?.add("  [drawableToBitmap] $msg")
            Log.d(TAG, "drawableToBitmap: $msg")
        }

        return try {
            log("Input drawable type: ${drawable.javaClass.name}")

            // If it's already a bitmap drawable, extract the bitmap
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                log("Using existing BitmapDrawable bitmap")
                return drawable.bitmap
            }

            // For adaptive icons and other drawables, render to a bitmap
            val size = 512
            log("Creating new bitmap ${size}x${size}")

            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)

            log("Rendered successfully")
            bitmap
        } catch (e: Exception) {
            logMessages?.add("  [drawableToBitmap] EXCEPTION: ${e.javaClass.name}: ${e.message}")
            Log.e(TAG, "Error converting drawable to bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Remove a shortcut file from ES-DE folder for a specific tab.
     */
    fun removeShortcutForTab(game: AndroidGame, tab: AndroidTab): ConfigResult<Unit> {
        val path = getPathForTab(tab)
            ?: return ConfigResult.Error("Could not determine ES-DE path for ${tab.name}")

        val dir = File(path)
        if (!dir.exists()) return ConfigResult.Success(Unit)

        // Find the file that contains this package name
        val files = dir.listFiles() ?: return ConfigResult.Success(Unit)

        for (file in files) {
            if (file.name.endsWith(ANDROID_EXTENSION)) {
                try {
                    val content = file.readText().trim()
                    if (content == game.packageName) {
                        if (file.delete()) {
                            Log.d(TAG, "Deleted Android shortcut: ${file.absolutePath}")
                            return ConfigResult.Success(Unit)
                        } else {
                            return ConfigResult.Error("Could not delete shortcut file")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading shortcut file: ${file.name}", e)
                }
            }
        }

        // File not found, consider it removed
        return ConfigResult.Success(Unit)
    }

    /**
     * Legacy method - removes shortcut from Games tab folder.
     */
    fun removeShortcut(game: AndroidGame): ConfigResult<Unit> {
        return removeShortcutForTab(game, AndroidTab.GAMES)
    }

    /**
     * Add multiple apps to ES-DE at once for a specific tab.
     */
    fun addAllGamesForTab(games: List<AndroidGame>, tab: AndroidTab): ConfigResult<Int> {
        var addedCount = 0
        var lastError: String? = null

        for (game in games) {
            if (!game.isInEsdeForTab(tab)) {
                when (val result = createShortcutForTab(game, tab)) {
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

    /**
     * Legacy method - adds games to Games tab folder.
     */
    fun addAllGames(games: List<AndroidGame>): ConfigResult<Int> {
        return addAllGamesForTab(games, AndroidTab.GAMES)
    }

    /**
     * Check all existing Android game shortcuts and generate missing artwork.
     * Returns the number of icons successfully generated.
     * Legacy method - uses Games tab by default.
     */
    fun generateMissingArtwork(): ConfigResult<Int> {
        return generateMissingArtworkForTab(AndroidTab.GAMES)
    }

    /**
     * Check existing shortcuts for a specific tab and generate missing artwork.
     * Returns the number of icons successfully generated.
     */
    fun generateMissingArtworkForTab(tab: AndroidTab): ConfigResult<Int> {
        val androidPath = getPathForTab(tab)
            ?: return ConfigResult.Error("Could not determine ES-DE path for ${tab.name.lowercase()}")

        val androidDir = File(androidPath)
        if (!androidDir.exists()) {
            return ConfigResult.Success(0)
        }

        val systemName = getSystemNameForTab(tab)
        val coversDir = esdeConfigService.getMediaDirectory(systemName, "covers")
            ?: return ConfigResult.Error("Could not get covers directory")

        val pm = context.packageManager
        var generatedCount = 0
        var errorCount = 0
        val files = androidDir.listFiles() ?: return ConfigResult.Success(0)

        Log.d(TAG, "Checking ${files.size} files for missing artwork...")

        for (file in files) {
            if (!file.name.endsWith(ANDROID_EXTENSION)) continue

            try {
                val packageName = file.readText().trim()
                if (packageName.isBlank()) continue

                // Get the safe filename (same logic as when creating shortcuts)
                val safeFileName = file.nameWithoutExtension

                // Check if artwork already exists
                val possibleExtensions = listOf(".png", ".jpg", ".jpeg", ".webp")
                val hasArtwork = possibleExtensions.any { ext ->
                    File(coversDir, "$safeFileName$ext").exists()
                }

                if (hasArtwork) {
                    Log.d(TAG, "Artwork exists for $safeFileName, skipping")
                    continue
                }

                // Try to get icon from package manager
                val icon = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    Log.w(TAG, "Package not found: $packageName")
                    continue
                }

                // Convert and save
                val bitmap = drawableToBitmap(icon, null)
                if (bitmap == null) {
                    Log.w(TAG, "Could not convert icon for $packageName")
                    errorCount++
                    continue
                }

                val outputFile = File(coversDir, "$safeFileName.png")
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                Log.d(TAG, "Generated artwork for $safeFileName: ${outputFile.absolutePath}")
                generatedCount++

            } catch (e: Exception) {
                Log.e(TAG, "Error processing ${file.name}: ${e.message}")
                errorCount++
            }
        }

        Log.d(TAG, "Generated $generatedCount artwork files, $errorCount errors")

        return if (errorCount > 0 && generatedCount == 0) {
            ConfigResult.Error("Failed to generate artwork ($errorCount errors)")
        } else {
            ConfigResult.Success(generatedCount)
        }
    }

    /**
     * Get all existing Android shortcuts in ES-DE.
     */
    fun getExistingShortcuts(): List<Pair<String, String>> {
        val androidPath = getEsdeAndroidPath() ?: return emptyList()
        val androidDir = File(androidPath)

        if (!androidDir.exists()) return emptyList()

        val shortcuts = mutableListOf<Pair<String, String>>()
        val files = androidDir.listFiles() ?: return emptyList()

        for (file in files) {
            if (file.name.endsWith(ANDROID_EXTENSION)) {
                try {
                    val packageName = file.readText().trim()
                    val displayName = file.nameWithoutExtension.replace("_", " ")
                    shortcuts.add(displayName to packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading shortcut file: ${file.name}", e)
                }
            }
        }

        return shortcuts
    }

    /**
     * Get stale entries for a specific tab - shortcuts in ES-DE for apps that are no longer installed.
     */
    fun getStaleEntriesForTab(tab: AndroidTab): List<StaleAndroidEntry> {
        val pm = context.packageManager
        val staleEntries = mutableListOf<StaleAndroidEntry>()

        val path = getPathForTab(tab) ?: return emptyList()
        val dir = File(path)
        if (!dir.exists()) return emptyList()

        val files = dir.listFiles() ?: return emptyList()

        for (file in files) {
            if (file.name.endsWith(ANDROID_EXTENSION)) {
                try {
                    val packageName = file.readText().trim()
                    if (packageName.isNotBlank()) {
                        // Check if the app is still installed
                        val isInstalled = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getApplicationInfo(packageName, 0)
                            }
                            true
                        } catch (e: PackageManager.NameNotFoundException) {
                            false
                        }

                        if (!isInstalled) {
                            val displayName = file.nameWithoutExtension.replace("_", " ")
                            staleEntries.add(
                                StaleAndroidEntry(
                                    fileName = file.name,
                                    displayName = displayName,
                                    packageName = packageName,
                                    filePath = file.absolutePath
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading shortcut file: ${file.name}", e)
                }
            }
        }

        return staleEntries.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Legacy method - gets stale entries for games tab.
     */
    fun getStaleEntries(): List<StaleAndroidEntry> {
        return getStaleEntriesForTab(AndroidTab.GAMES)
    }

    /**
     * Remove a stale entry from ES-DE.
     */
    fun removeStaleEntry(entry: StaleAndroidEntry): ConfigResult<Unit> {
        return try {
            val file = File(entry.filePath)
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted stale entry: ${entry.filePath}")
                    ConfigResult.Success(Unit)
                } else {
                    ConfigResult.Error("Could not delete file: ${entry.fileName}")
                }
            } else {
                // File already gone
                ConfigResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting stale entry: ${entry.filePath}", e)
            ConfigResult.Error("Failed to delete: ${e.message}", e)
        }
    }

    /**
     * Remove all stale entries from ES-DE.
     */
    fun removeAllStaleEntries(entries: List<StaleAndroidEntry>): ConfigResult<Int> {
        var removedCount = 0
        var lastError: String? = null

        for (entry in entries) {
            when (val result = removeStaleEntry(entry)) {
                is ConfigResult.Success -> removedCount++
                is ConfigResult.Error -> lastError = result.message
            }
        }

        return if (removedCount > 0 || lastError == null) {
            ConfigResult.Success(removedCount)
        } else {
            ConfigResult.Error(lastError)
        }
    }
}
