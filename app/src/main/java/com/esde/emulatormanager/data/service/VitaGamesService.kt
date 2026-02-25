package com.esde.emulatormanager.data.service

import android.content.Context
import android.os.Environment
import android.util.Log
import com.esde.emulatormanager.data.model.ConfigResult
import com.esde.emulatormanager.data.model.VitaGame
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing PS Vita game shortcuts for ES-DE.
 * Creates .psvita files containing a game's Title ID (e.g., "PCSG00123"),
 * which Vita3K reads via %INJECT%=%BASENAME%.psvita in the launch command.
 */
@Singleton
class VitaGamesService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esdeConfigService: EsdeConfigService,
    private val gamelistService: GamelistService
) {
    companion object {
        private const val TAG = "VitaGamesService"
        private const val VITA_FOLDER = "psvita"
        private const val VITA_EXT = ".psvita"
    }

    // SharedPreferences for persistent custom path storage
    private val prefs = context.getSharedPreferences("vita_games_prefs", android.content.Context.MODE_PRIVATE)
    private val PREF_CUSTOM_VITA_PATH = "custom_vita_path"

    /**
     * User-specified custom path override. When set, this takes precedence over
     * ES-DE config and fallback paths. Stored in SharedPreferences for persistence.
     */
    var customVitaPath: String?
        get() = prefs.getString(PREF_CUSTOM_VITA_PATH, null)
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(PREF_CUSTOM_VITA_PATH, value)
                else remove(PREF_CUSTOM_VITA_PATH)
                apply()
            }
        }

    /**
     * Set a custom Vita ROMs path that overrides automatic detection.
     * The path is persisted across app restarts.
     */
    fun setCustomPath(path: String?) {
        customVitaPath = path
    }

    /**
     * Get the ES-DE psvita ROM path.
     * Priority: 0) User custom path, 1) ES-DE config, 2) Common fallback paths, 3) Default path.
     */
    fun getEsdeVitaPath(): String? {
        // Priority 0: user-specified custom path
        customVitaPath?.let { return it }

        // Priority 1: try to get the path from ES-DE's configuration
        val configuredPath = esdeConfigService.getSystemRomPath(VITA_FOLDER)
        if (configuredPath != null) {
            return configuredPath
        }

        // Priority 2: fallback to searching common paths that actually exist
        val possiblePaths = listOf(
            File(Environment.getExternalStorageDirectory(), "ROMs/$VITA_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "Roms/$VITA_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "ES-DE/ROMs/$VITA_FOLDER"),
            File(Environment.getExternalStorageDirectory(), "ES-DE/Roms/$VITA_FOLDER")
        )

        for (path in possiblePaths) {
            if (path.exists()) {
                return path.absolutePath
            }
        }

        // Priority 3: return default path even if it doesn't exist yet
        return File(Environment.getExternalStorageDirectory(), "ROMs/$VITA_FOLDER").absolutePath
    }

    /**
     * Scan the psvita ROM folder for existing .psvita shortcut files.
     */
    fun scanVitaGames(): List<VitaGame> {
        val vitaPath = getEsdeVitaPath() ?: return emptyList()
        val vitaDir = File(vitaPath)

        if (!vitaDir.exists() || !vitaDir.isDirectory) return emptyList()

        val games = mutableListOf<VitaGame>()

        for (file in vitaDir.listFiles() ?: emptyArray()) {
            if (!file.isFile || !file.name.endsWith(VITA_EXT, ignoreCase = true)) continue

            try {
                val titleId = file.readText().trim()
                val displayName = file.nameWithoutExtension
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                val hasMetadata = gamelistService.hasMetadata("psvita", "./${file.name}")

                games.add(
                    VitaGame(
                        titleId = titleId,
                        displayName = displayName,
                        filePath = file.absolutePath,
                        hasMetadata = hasMetadata
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error reading .psvita file: ${file.name}", e)
            }
        }

        return games
    }

    /**
     * Create a .psvita shortcut file.
     * The file content is just the Title ID (e.g., "PCSG00123").
     */
    fun createVitaShortcut(
        titleId: String,
        displayName: String,
        vitaPath: String
    ): ConfigResult<VitaGame> {
        val vitaDir = File(vitaPath)
        if (!vitaDir.exists()) {
            if (!vitaDir.mkdirs()) {
                return ConfigResult.Error("Could not create psvita ROM directory")
            }
        }

        val safeFileName = generateSafeFileName(displayName)
        val vitaFile = File(vitaDir, "$safeFileName$VITA_EXT")

        return try {
            vitaFile.writeText(titleId.trim())
            Log.d(TAG, "Created .psvita shortcut: ${vitaFile.absolutePath} for TitleID: $titleId")
            ConfigResult.Success(
                VitaGame(
                    titleId = titleId.trim(),
                    displayName = displayName,
                    filePath = vitaFile.absolutePath,
                    hasMetadata = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating .psvita shortcut", e)
            ConfigResult.Error("Failed to create .psvita shortcut: ${e.message}", e)
        }
    }

    /**
     * Remove a .psvita shortcut file.
     */
    fun removeVitaGame(game: VitaGame): ConfigResult<Unit> {
        val file = File(game.filePath)
        return try {
            if (file.exists()) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted .psvita file: ${file.absolutePath}")
                    ConfigResult.Success(Unit)
                } else {
                    ConfigResult.Error("Could not delete .psvita file: ${file.name}")
                }
            } else {
                Log.w(TAG, "No .psvita file found to delete: ${file.absolutePath}")
                ConfigResult.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove .psvita file: ${game.filePath}", e)
            ConfigResult.Error("Failed to remove game: ${e.message}", e)
        }
    }

    /**
     * Check if a Title ID is already added as a .psvita shortcut.
     */
    fun vitaGameExists(titleId: String): Boolean {
        val vitaPath = getEsdeVitaPath() ?: return false
        val vitaDir = File(vitaPath)

        if (!vitaDir.exists()) return false

        for (file in vitaDir.listFiles() ?: emptyArray()) {
            if (file.isFile && file.name.endsWith(VITA_EXT, ignoreCase = true)) {
                try {
                    if (file.readText().trim().equals(titleId.trim(), ignoreCase = true)) {
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading .psvita file: ${file.name}", e)
                }
            }
        }

        return false
    }

    /**
     * Get count of .psvita files without metadata.
     */
    fun getGamesWithoutMetadataCount(): Int {
        val vitaPath = getEsdeVitaPath() ?: return 0
        val vitaDir = File(vitaPath)

        if (!vitaDir.exists()) return 0

        return vitaDir.listFiles { f ->
            f.isFile && f.name.endsWith(VITA_EXT, ignoreCase = true)
        }?.count { file ->
            !gamelistService.hasMetadata("psvita", "./${file.name}")
        } ?: 0
    }

    /**
     * Generate a safe filename from a display name (same pattern as WindowsGamesService).
     */
    private fun generateSafeFileName(name: String): String {
        return name
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
    }
}
