package com.esde.emulatormanager.data.service

import android.util.Log
import com.esde.emulatormanager.data.model.AndroidGame
import com.esde.emulatormanager.data.model.AndroidTab
import com.esde.emulatormanager.data.model.EpicGame
import com.esde.emulatormanager.data.model.GameMetadata
import com.esde.emulatormanager.data.model.GogGame
import com.esde.emulatormanager.data.model.MetadataResult
import com.esde.emulatormanager.data.model.PendingMetadataSearch
import com.esde.emulatormanager.data.model.ScrapeOptions
import com.esde.emulatormanager.data.model.ScrapeProgress
import com.esde.emulatormanager.data.model.SteamGame
import com.esde.emulatormanager.data.model.VitaGame
import com.esde.emulatormanager.data.model.WindowsGamePlatform
import com.esde.emulatormanager.data.model.WindowsGameShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified service for scraping and managing game metadata.
 * Coordinates between different metadata sources (Steam, GOG, IGDB) and the gamelist service.
 */
@Singleton
class MetadataService @Inject constructor(
    private val steamApiService: SteamApiService,
    private val gogApiService: GogApiService,
    private val igdbService: IgdbService,
    private val gamelistService: GamelistService,
    private val esdeConfigService: EsdeConfigService,
    private val windowsGamesService: WindowsGamesService,
    private val androidGamesService: AndroidGamesService,
    private val screenScraperService: ScreenScraperService,
    private val vitaGamesService: VitaGamesService
) {
    companion object {
        private const val TAG = "MetadataService"
    }

    private val _scrapeProgress = MutableStateFlow<ScrapeProgress?>(null)
    val scrapeProgress: StateFlow<ScrapeProgress?> = _scrapeProgress

    /**
     * Check if IGDB credentials are configured.
     */
    fun hasIgdbCredentials(): Boolean = igdbService.hasCredentials()

    /**
     * Set IGDB credentials.
     */
    fun setIgdbCredentials(clientId: String, clientSecret: String) {
        igdbService.setCredentials(clientId, clientSecret)
    }

    /**
     * Get current IGDB client ID (for display).
     */
    fun getIgdbClientId(): String? = igdbService.getClientId()

    /**
     * Clear IGDB credentials.
     */
    fun clearIgdbCredentials() = igdbService.clearCredentials()

    // ==================== Steam Games ====================

    /**
     * Scrape and save metadata for a Steam game.
     * @param appId Steam App ID
     * @param gameFileName The game file name (without extension)
     * @return True if metadata was successfully scraped and saved
     */
    suspend fun scrapeAndSaveSteamMetadata(appId: Int, gameFileName: String, options: ScrapeOptions = ScrapeOptions()): Boolean = withContext(Dispatchers.IO) {
        val gamePath = "./$gameFileName.steam"

        when (val result = steamApiService.getGameMetadata(appId, gamePath)) {
            is MetadataResult.Success -> {
                var metadata = result.metadata
                var hasArtwork = false

                if (options.scrapeArtwork) {
                    // Download cover artwork if we have an image URL
                    if (!metadata.image.isNullOrBlank()) {
                        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
                        if (coversDir != null) {
                            val coverPath = steamApiService.downloadCoverArtwork(appId, gameFileName, coversDir)
                            if (coverPath != null) {
                                // Update metadata with local cover path (relative to ROM folder)
                                val relativePath = "./media/covers/$gameFileName.jpg"
                                metadata = metadata.copy(image = relativePath)
                                hasArtwork = true
                            }
                        }
                    }

                    // Download screenshots if available
                    val screenshotUrls = result.metadata.screenshotUrls
                    var hasScreenshot = false
                    if (!screenshotUrls.isNullOrEmpty()) {
                        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
                        if (screenshotsDir != null) {
                            val screenshotPath = steamApiService.downloadScreenshots(screenshotUrls, screenshotsDir, gameFileName)
                            if (screenshotPath != null) {
                                val relativePath = "./media/screenshots/$gameFileName.jpg"
                                metadata = metadata.copy(titlescreen = relativePath)
                                hasScreenshot = true
                                Log.d(TAG, "Downloaded screenshots for Steam game: $gameFileName")
                            }
                        }
                    }

                    // Generate miximage if we have artwork
                    if (hasArtwork || hasScreenshot) {
                        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
                        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
                        val miximagesDir = esdeConfigService.getMediaDirectory("windows", "miximages")
                        if (coversDir != null && screenshotsDir != null && miximagesDir != null) {
                            val miximagePath = steamApiService.generateMiximage(coversDir, screenshotsDir, miximagesDir, gameFileName)
                            if (miximagePath != null) {
                                Log.d(TAG, "Generated miximage for Steam game: $gameFileName")
                            }
                        }
                    }
                } else {
                    // Clear artwork fields if not scraping artwork
                    metadata = metadata.copy(image = null, titlescreen = null)
                }

                if (options.scrapeVideos) {
                    // Download video if available
                    val videoUrl = result.metadata.videoUrl
                    if (!videoUrl.isNullOrBlank()) {
                        val videosDir = esdeConfigService.getMediaDirectory("windows", "videos")
                        if (videosDir != null) {
                            val videoPath = steamApiService.downloadVideo(videoUrl, videosDir, gameFileName)
                            if (videoPath != null) {
                                val relativePath = "./media/videos/$gameFileName.mp4"
                                metadata = metadata.copy(video = relativePath)
                                Log.d(TAG, "Downloaded video for Steam game: $gameFileName")
                            }
                        }
                    }
                } else {
                    metadata = metadata.copy(video = null)
                }

                // If not scraping metadata, only save if we scraped artwork or video
                if (!options.scrapeMetadata) {
                    metadata = metadata.copy(
                        desc = null, rating = null, releasedate = null,
                        developer = null, publisher = null, genre = null, players = null
                    )
                }

                // Clear temporary URLs before saving (not needed in gamelist.xml)
                metadata = metadata.copy(screenshotUrls = null, videoUrl = null)

                // Save to gamelist.xml using system name
                gamelistService.writeGameMetadata("windows", metadata)
                Log.d(TAG, "Saved Steam metadata for: ${metadata.name}")
                true
            }
            is MetadataResult.NotFound -> {
                Log.w(TAG, "No metadata found for Steam game: $appId")
                false
            }
            is MetadataResult.Error -> {
                Log.e(TAG, "Error scraping Steam metadata: ${result.message}")
                false
            }
        }
    }

    /**
     * Re-scrape metadata and artwork for a single Windows/Steam game.
     * This will overwrite any existing metadata and artwork.
     * @param appId Steam App ID
     * @param gameFileName The game file name (without extension)
     * @return True if re-scrape was successful
     */
    suspend fun reScrapeWindowsGame(appId: Int, gameFileName: String, options: ScrapeOptions = ScrapeOptions()): Boolean = withContext(Dispatchers.IO) {
        // Delete existing cover artwork to force re-download
        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
        if (coversDir != null) {
            listOf(".png", ".jpg", ".jpeg", ".webp").forEach { ext ->
                File(coversDir, "$gameFileName$ext").delete()
            }
        }

        // Delete existing screenshots
        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
        if (screenshotsDir != null) {
            for (i in 0..5) {
                val suffix = if (i == 0) "" else "_$i"
                File(screenshotsDir, "$gameFileName$suffix.jpg").delete()
            }
        }

        // Delete existing miximages
        val miximagesDir = esdeConfigService.getMediaDirectory("windows", "miximages")
        if (miximagesDir != null) {
            listOf(".png", ".jpg").forEach { ext ->
                File(miximagesDir, "$gameFileName$ext").delete()
            }
        }

        // Delete existing videos
        val videosDir = esdeConfigService.getMediaDirectory("windows", "videos")
        if (videosDir != null) {
            listOf(".mp4", ".webm", ".avi").forEach { ext ->
                File(videosDir, "$gameFileName$ext").delete()
            }
        }

        // Now scrape fresh
        scrapeAndSaveSteamMetadata(appId, gameFileName, options)
    }

    /**
     * Scrape metadata for all Windows games that don't have it.
     * @param onProgress Callback for progress updates
     * @return Number of games successfully scraped
     */
    suspend fun scrapeAllMissingSteamMetadata(options: ScrapeOptions = ScrapeOptions(), onProgress: ((ScrapeProgress) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val windowsPath = windowsGamesService.getEsdeWindowsPath()
        if (windowsPath == null) {
            Log.e(TAG, "Could not get Windows path")
            return@withContext 0
        }

        val windowsDir = File(windowsPath)

        // Get all shortcut files that need scraping
        data class PendingGame(val file: File, val platform: String)
        val allFiles = mutableListOf<PendingGame>()

        // .steam files (Steam games)
        windowsDir.listFiles()?.filter { it.extension == "steam" }?.forEach {
            if (!gamelistService.hasMetadata("windows", "./${it.name}")) {
                allFiles.add(PendingGame(it, "steam"))
            }
        }

        // .gog files (GOG games)
        windowsDir.listFiles()?.filter { it.extension == "gog" }?.forEach {
            if (!gamelistService.hasMetadata("windows", "./${it.name}")) {
                allFiles.add(PendingGame(it, "gog"))
            }
        }

        // .epic files (Epic games)
        windowsDir.listFiles()?.filter { it.extension == "epic" }?.forEach {
            if (!gamelistService.hasMetadata("windows", "./${it.name}")) {
                allFiles.add(PendingGame(it, "epic"))
            }
        }

        var successCount = 0
        val total = allFiles.size

        allFiles.forEachIndexed { index, pending ->
            val progress = ScrapeProgress(
                total = total,
                completed = index,
                successful = successCount,
                failed = index - successCount,
                currentGame = pending.file.nameWithoutExtension
            )
            _scrapeProgress.value = progress
            onProgress?.invoke(progress)

            try {
                val scraped = when (pending.platform) {
                    "steam" -> {
                        val appId = pending.file.readText().trim().toIntOrNull()
                        if (appId != null) scrapeAndSaveSteamMetadata(appId, pending.file.nameWithoutExtension, options) else false
                    }
                    "gog" -> {
                        val productId = pending.file.readText().trim().toLongOrNull()
                        if (productId != null) scrapeAndSaveGogMetadata(productId, pending.file.nameWithoutExtension, options) else false
                    }
                    "epic" -> {
                        val gameName = pending.file.nameWithoutExtension
                            .replace("_", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                        scrapeAndSaveEpicMetadata(gameName, pending.file.nameWithoutExtension, options)
                    }
                    else -> false
                }
                if (scraped) successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping metadata for ${pending.file.name}", e)
            }

            // Small delay to avoid rate limiting
            kotlinx.coroutines.delay(500)
        }

        val finalProgress = ScrapeProgress(
            total = total,
            completed = total,
            successful = successCount,
            failed = total - successCount,
            currentGame = null
        )
        _scrapeProgress.value = finalProgress
        onProgress?.invoke(finalProgress)

        Log.d(TAG, "Scraped Windows metadata: $successCount/$total successful")
        successCount
    }

    // ==================== GOG Games ====================

    /**
     * Scrape and save metadata for a GOG game.
     * @param productId GOG Product ID
     * @param gameFileName The game file name (without extension/suffix)
     * @return True if metadata was successfully scraped and saved
     */
    suspend fun scrapeAndSaveGogMetadata(productId: Long, gameFileName: String, options: ScrapeOptions = ScrapeOptions()): Boolean = withContext(Dispatchers.IO) {
        val gamePath = "./$gameFileName.gog"

        when (val result = gogApiService.getGameMetadata(productId, gamePath)) {
            is MetadataResult.Success -> {
                var metadata = result.metadata
                var hasArtwork = false

                if (options.scrapeArtwork) {
                    // Download cover artwork if we have an image URL
                    if (!metadata.image.isNullOrBlank()) {
                        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
                        if (coversDir != null) {
                            val coverPath = gogApiService.downloadCoverArtwork(metadata.image!!, coversDir, gameFileName)
                            if (coverPath != null) {
                                val relativePath = "./media/covers/$gameFileName.jpg"
                                metadata = metadata.copy(image = relativePath)
                                hasArtwork = true
                            }
                        }
                    }

                    // Download screenshots if available
                    val screenshotUrls = result.metadata.screenshotUrls
                    var hasScreenshot = false
                    if (!screenshotUrls.isNullOrEmpty()) {
                        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
                        if (screenshotsDir != null) {
                            val screenshotPath = gogApiService.downloadScreenshots(screenshotUrls, screenshotsDir, gameFileName)
                            if (screenshotPath != null) {
                                val relativePath = "./media/screenshots/$gameFileName.jpg"
                                metadata = metadata.copy(titlescreen = relativePath)
                                hasScreenshot = true
                                Log.d(TAG, "Downloaded screenshots for GOG game: $gameFileName")
                            }
                        }
                    }

                    // Generate miximage if we have artwork
                    if (hasArtwork || hasScreenshot) {
                        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
                        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
                        val miximagesDir = esdeConfigService.getMediaDirectory("windows", "miximages")
                        if (coversDir != null && screenshotsDir != null && miximagesDir != null) {
                            val miximagePath = steamApiService.generateMiximage(coversDir, screenshotsDir, miximagesDir, gameFileName)
                            if (miximagePath != null) {
                                Log.d(TAG, "Generated miximage for GOG game: $gameFileName")
                            }
                        }
                    }
                } else {
                    metadata = metadata.copy(image = null, titlescreen = null)
                }

                if (options.scrapeVideos) {
                    // GOG videos are YouTube URLs, so try to find and download from Steam instead
                    val videosDir = esdeConfigService.getMediaDirectory("windows", "videos")
                    if (videosDir != null) {
                        val videoPath = gogApiService.downloadVideoViaSteam(metadata.name, videosDir, gameFileName)
                        if (videoPath != null) {
                            val relativePath = "./media/videos/$gameFileName.mp4"
                            metadata = metadata.copy(video = relativePath)
                            Log.d(TAG, "Downloaded Steam video for GOG game: $gameFileName")
                        }
                    }
                } else {
                    metadata = metadata.copy(video = null)
                }

                if (!options.scrapeMetadata) {
                    metadata = metadata.copy(
                        desc = null, rating = null, releasedate = null,
                        developer = null, publisher = null, genre = null, players = null
                    )
                }

                // Clear temporary URLs before saving
                metadata = metadata.copy(screenshotUrls = null, videoUrl = null)

                // Save to gamelist.xml
                gamelistService.writeGameMetadata("windows", metadata)
                Log.d(TAG, "Saved GOG metadata for: ${metadata.name}")
                true
            }
            is MetadataResult.NotFound -> {
                Log.w(TAG, "No metadata found for GOG game: $productId")
                false
            }
            is MetadataResult.Error -> {
                Log.e(TAG, "Error scraping GOG metadata: ${result.message}")
                false
            }
        }
    }

    /**
     * Re-scrape metadata and artwork for a single GOG game.
     */
    suspend fun reScrapeGogGame(productId: Long, gameFileName: String, options: ScrapeOptions = ScrapeOptions()): Boolean = withContext(Dispatchers.IO) {
        // Delete existing artwork
        deleteExistingMedia(gameFileName)
        // Scrape fresh
        scrapeAndSaveGogMetadata(productId, gameFileName, options)
    }

    // ==================== Epic Games ====================

    /**
     * Scrape and save metadata for an Epic game using IGDB (search by name).
     * Since Epic's internal ID from GameNative isn't usable for API lookups,
     * we use the game name to search IGDB.
     *
     * @param gameName The game name (from filename)
     * @param gameFileName The game file name (without extension)
     * @return True if metadata was successfully scraped and saved
     */
    suspend fun scrapeAndSaveEpicMetadata(gameName: String, gameFileName: String, options: ScrapeOptions = ScrapeOptions(), fileExtension: String = ".epic"): Boolean = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            Log.w(TAG, "IGDB credentials not configured for Epic/Amazon game scraping")
            return@withContext false
        }

        val gamePath = "./$gameFileName$fileExtension"

        // Search IGDB by game name using PC platform filter
        when (val result = igdbService.searchWindowsGame(gameName)) {
            is MetadataResult.Success -> {
                var metadata = result.metadata.copy(path = gamePath)
                var hasArtwork = false

                if (options.scrapeArtwork) {
                    // Download cover artwork if available
                    if (!metadata.image.isNullOrBlank()) {
                        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
                        if (coversDir != null) {
                            val coverPath = igdbService.downloadCoverImage(metadata.image!!, "windows", gameFileName)
                            if (coverPath != null) {
                                val relativePath = "./media/covers/$gameFileName.jpg"
                                metadata = metadata.copy(image = relativePath)
                                hasArtwork = true
                            }
                        }
                    }

                    // Download screenshots if available
                    val screenshotUrls = result.metadata.screenshotUrls
                    var hasScreenshot = false
                    if (!screenshotUrls.isNullOrEmpty()) {
                        val screenshotPath = igdbService.downloadScreenshots(screenshotUrls, "windows", gameFileName)
                        if (screenshotPath != null) {
                            val relativePath = "./media/screenshots/$gameFileName.jpg"
                            metadata = metadata.copy(titlescreen = relativePath)
                            hasScreenshot = true
                            Log.d(TAG, "Downloaded screenshots for Epic game: $gameFileName")
                        }
                    }

                    // Generate miximage if we have artwork
                    if (hasArtwork || hasScreenshot) {
                        val miximagePath = igdbService.generateMiximage("windows", gameFileName)
                        if (miximagePath != null) {
                            Log.d(TAG, "Generated miximage for Epic game: $gameFileName")
                        }
                    }
                } else {
                    metadata = metadata.copy(image = null, titlescreen = null)
                }

                if (options.scrapeVideos) {
                    // Download video via Steam store search (Epic doesn't provide downloadable videos)
                    val videosDir = esdeConfigService.getMediaDirectory("windows", "videos")
                    if (videosDir != null) {
                        val videoPath = gogApiService.downloadVideoViaSteam(metadata.name, videosDir, gameFileName)
                        if (videoPath != null) {
                            val relativePath = "./media/videos/$gameFileName.mp4"
                            metadata = metadata.copy(video = relativePath)
                            Log.d(TAG, "Downloaded Steam video for Epic game: $gameFileName")
                        }
                    }
                } else {
                    metadata = metadata.copy(video = null)
                }

                if (!options.scrapeMetadata) {
                    metadata = metadata.copy(
                        desc = null, rating = null, releasedate = null,
                        developer = null, publisher = null, genre = null, players = null
                    )
                }

                // Clear temporary URLs before saving
                metadata = metadata.copy(screenshotUrls = null, videoUrl = null)

                // Save to gamelist.xml
                gamelistService.writeGameMetadata("windows", metadata)
                Log.d(TAG, "Saved Epic metadata for: ${metadata.name}")
                true
            }
            is MetadataResult.NotFound -> {
                Log.w(TAG, "No metadata found for Epic game: $gameName")
                false
            }
            is MetadataResult.Error -> {
                Log.e(TAG, "Error scraping Epic metadata: ${result.message}")
                false
            }
        }
    }

    /**
     * Re-scrape metadata and artwork for a single Epic game.
     */
    suspend fun reScrapeEpicGame(gameName: String, gameFileName: String, options: ScrapeOptions = ScrapeOptions(), fileExtension: String = ".epic"): Boolean = withContext(Dispatchers.IO) {
        // Delete existing artwork
        deleteExistingMedia(gameFileName)
        // Scrape fresh
        scrapeAndSaveEpicMetadata(gameName, gameFileName, options, fileExtension)
    }

    // ==================== Utility Methods for Windows Games ====================

    /**
     * Delete existing media files for a Windows game.
     */
    private fun deleteExistingMedia(gameFileName: String) {
        // Delete existing cover artwork
        val coversDir = esdeConfigService.getMediaDirectory("windows", "covers")
        if (coversDir != null) {
            listOf(".png", ".jpg", ".jpeg", ".webp").forEach { ext ->
                File(coversDir, "$gameFileName$ext").delete()
            }
        }

        // Delete existing screenshots
        val screenshotsDir = esdeConfigService.getMediaDirectory("windows", "screenshots")
        if (screenshotsDir != null) {
            for (i in 0..5) {
                val suffix = if (i == 0) "" else "_$i"
                File(screenshotsDir, "$gameFileName$suffix.jpg").delete()
            }
        }

        // Delete existing miximages
        val miximagesDir = esdeConfigService.getMediaDirectory("windows", "miximages")
        if (miximagesDir != null) {
            listOf(".png", ".jpg").forEach { ext ->
                File(miximagesDir, "$gameFileName$ext").delete()
            }
        }

        // Delete existing videos
        val videosDir = esdeConfigService.getMediaDirectory("windows", "videos")
        if (videosDir != null) {
            listOf(".mp4", ".webm", ".avi").forEach { ext ->
                File(videosDir, "$gameFileName$ext").delete()
            }
        }
    }

    // ==================== Android Games ====================

    /**
     * Scrape and save metadata for an Android game.
     * @param game The Android game to scrape metadata for
     * @param tab The Android tab (games, apps, emulators)
     * @param customSearchTerm Optional custom search term to use instead of app name
     * @return MetadataResult indicating success, not found, or error
     */
    suspend fun scrapeAndSaveAndroidMetadata(
        game: AndroidGame,
        tab: AndroidTab,
        customSearchTerm: String? = null
    ): MetadataResult = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            Log.w(TAG, "IGDB credentials not configured")
            return@withContext MetadataResult.Error("IGDB credentials not configured")
        }

        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        // Generate filename same way as when creating shortcut (must match AndroidGamesService.createShortcutForTab)
        val safeFileName = game.appName
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")  // Keep hyphens, same as AndroidGamesService
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(100)  // Limit filename length, same as AndroidGamesService
        val gamePath = "./$safeFileName.app"

        // Use custom search term if provided, otherwise use app name
        val searchTerm = customSearchTerm ?: game.appName

        Log.d(TAG, "Scraping Android game: '${game.appName}' (package: ${game.packageName})")
        Log.d(TAG, "Generated filename: '$safeFileName', gamePath: '$gamePath', systemName: '$systemName'")

        when (val result = igdbService.searchAndroidGame(searchTerm, game.packageName)) {
            is MetadataResult.Success -> {
                var metadata = result.metadata.copy(path = gamePath)
                Log.d(TAG, "IGDB found match: '${result.metadata.name}', desc length: ${result.metadata.desc?.length ?: 0}")

                // Download cover artwork if we have an image URL
                Log.d(TAG, "Cover image URL: '${metadata.image}'")
                if (!metadata.image.isNullOrBlank()) {
                    val coverPath = igdbService.downloadCoverImage(metadata.image!!, systemName, safeFileName)
                    Log.d(TAG, "Cover download result: $coverPath")
                    if (coverPath != null) {
                        val relativePath = "./media/covers/$safeFileName.jpg"
                        metadata = metadata.copy(image = relativePath)
                    } else {
                        Log.w(TAG, "Failed to download cover image")
                        metadata = metadata.copy(image = null)
                    }
                } else {
                    Log.d(TAG, "No cover image URL available from IGDB")
                }

                // Download screenshots if available
                val screenshotUrls = result.metadata.screenshotUrls
                var hasScreenshot = false
                if (!screenshotUrls.isNullOrEmpty()) {
                    val screenshotPath = igdbService.downloadScreenshots(screenshotUrls, systemName, safeFileName)
                    if (screenshotPath != null) {
                        // Use first screenshot as fanart/titlescreen
                        val relativePath = "./media/screenshots/$safeFileName.jpg"
                        metadata = metadata.copy(titlescreen = relativePath)
                        hasScreenshot = true
                        Log.d(TAG, "Downloaded ${screenshotUrls.size.coerceAtMost(5)} screenshots for ${game.appName}")
                    }
                }

                // Generate miximage if we have cover art
                if (!metadata.image.isNullOrBlank() || hasScreenshot) {
                    val miximagePath = igdbService.generateMiximage(systemName, safeFileName)
                    if (miximagePath != null) {
                        Log.d(TAG, "Generated miximage for ${game.appName}")
                    }
                }

                // Clear temporary URLs before saving (not needed in gamelist.xml)
                // Note: IGDB only provides YouTube video IDs which ES-DE doesn't support directly
                metadata = metadata.copy(screenshotUrls = null, videoUrl = null)

                // Save to gamelist.xml using system name
                Log.d(TAG, "Writing metadata to gamelist.xml: path='${metadata.path}', name='${metadata.name}', desc length=${metadata.desc?.length ?: 0}, video='${metadata.video}'")
                gamelistService.writeGameMetadata(systemName, metadata)

                // Verify it was saved
                val saved = gamelistService.hasMetadata(systemName, gamePath)
                Log.d(TAG, "Metadata saved verification: $saved for path '$gamePath' in system '$systemName'")

                Log.d(TAG, "Saved Android metadata for: ${metadata.name}")
                MetadataResult.Success(metadata)
            }
            is MetadataResult.NotFound -> {
                Log.w(TAG, "No metadata found for Android game: $searchTerm")
                MetadataResult.NotFound(game.appName)
            }
            is MetadataResult.Error -> {
                Log.e(TAG, "Error scraping Android metadata: ${result.message}")
                result
            }
        }
    }

    /**
     * Legacy method for backwards compatibility - returns Boolean
     */
    suspend fun scrapeAndSaveAndroidMetadataBoolean(game: AndroidGame, tab: AndroidTab): Boolean {
        return scrapeAndSaveAndroidMetadata(game, tab) is MetadataResult.Success
    }

    /**
     * Re-scrape metadata and artwork for a single Android game.
     * This will overwrite any existing metadata and artwork.
     * @param game The Android game to re-scrape
     * @param tab The tab the game is in
     * @param customSearchTerm Optional custom search term
     * @return MetadataResult indicating success or failure
     */
    suspend fun reScrapeAndroidGame(
        game: AndroidGame,
        tab: AndroidTab,
        customSearchTerm: String? = null
    ): MetadataResult = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            return@withContext MetadataResult.Error("IGDB credentials not configured")
        }

        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        // Generate filename same way as when creating shortcut
        val safeFileName = game.appName
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(100)

        // Delete existing cover artwork to force re-download
        val coversDir = esdeConfigService.getMediaDirectory(systemName, "covers")
        if (coversDir != null) {
            listOf(".png", ".jpg", ".jpeg", ".webp").forEach { ext ->
                File(coversDir, "$safeFileName$ext").delete()
            }
        }

        // Delete existing screenshots
        val screenshotsDir = esdeConfigService.getMediaDirectory(systemName, "screenshots")
        if (screenshotsDir != null) {
            // Delete main screenshot and numbered variants
            for (i in 0..5) {
                val suffix = if (i == 0) "" else "_$i"
                File(screenshotsDir, "$safeFileName$suffix.jpg").delete()
            }
        }

        // Delete existing miximages
        val miximagesDir = esdeConfigService.getMediaDirectory(systemName, "miximages")
        if (miximagesDir != null) {
            listOf(".png", ".jpg").forEach { ext ->
                File(miximagesDir, "$safeFileName$ext").delete()
            }
        }

        // Delete existing videos
        val videosDir = esdeConfigService.getMediaDirectory(systemName, "videos")
        if (videosDir != null) {
            listOf(".mp4", ".webm", ".avi").forEach { ext ->
                File(videosDir, "$safeFileName$ext").delete()
            }
        }

        // Now scrape fresh
        scrapeAndSaveAndroidMetadata(game, tab, customSearchTerm)
    }

    /**
     * Try to fetch IGDB artwork for a game without saving metadata.
     * Used when adding a game to prioritize IGDB artwork over app icon.
     * @return true if IGDB artwork was found and downloaded
     */
    suspend fun tryFetchIgdbArtwork(game: AndroidGame, tab: AndroidTab): Boolean = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            return@withContext false
        }

        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        val safeFileName = game.appName
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(100)

        when (val result = igdbService.searchAndroidGame(game.appName, game.packageName)) {
            is MetadataResult.Success -> {
                var hasArtwork = false

                // Download cover artwork if available
                if (!result.metadata.image.isNullOrBlank()) {
                    val coverPath = igdbService.downloadCoverImage(result.metadata.image!!, systemName, safeFileName)
                    if (coverPath != null) {
                        hasArtwork = true
                        Log.d(TAG, "Downloaded IGDB cover for ${game.appName}")
                    }
                }

                // Download screenshots if available
                val screenshotUrls = result.metadata.screenshotUrls
                if (!screenshotUrls.isNullOrEmpty()) {
                    val screenshotPath = igdbService.downloadScreenshots(screenshotUrls, systemName, safeFileName)
                    if (screenshotPath != null) {
                        Log.d(TAG, "Downloaded IGDB screenshots for ${game.appName}")
                    }
                }

                // Generate miximage if we have any artwork
                if (hasArtwork) {
                    val miximagePath = igdbService.generateMiximage(systemName, safeFileName)
                    if (miximagePath != null) {
                        Log.d(TAG, "Generated miximage for ${game.appName}")
                    }
                }

                hasArtwork
            }
            else -> false
        }
    }

    // Track state for interactive scraping
    private var pendingGames: MutableList<AndroidGame> = mutableListOf()
    private var currentTab: AndroidTab = AndroidTab.GAMES
    private var successCount = 0
    private var failedCount = 0
    private var skippedCount = 0
    private var currentIndex = 0

    /**
     * Start scraping metadata for all Android games in a tab that don't have it.
     * This initiates the scraping process and will pause when a game is not found,
     * allowing the user to provide a refined search term.
     *
     * @param tab The Android tab to scrape
     * @param onProgress Callback for progress updates
     */
    suspend fun startAndroidMetadataScraping(tab: AndroidTab, onProgress: ((ScrapeProgress) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            Log.w(TAG, "IGDB credentials not configured")
            return@withContext
        }

        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        // Get all installed apps that are in ES-DE for this tab
        val installedApps = androidGamesService.getInstalledApps()
        val appsInTab = installedApps.filter { it.isInEsdeForTab(tab) }

        // Filter to only those without metadata
        val gamesWithoutMetadata = appsInTab.filter { app ->
            val safeFileName = app.appName
                .replace(Regex("[^a-zA-Z0-9\\s-]"), "")  // Keep hyphens, same as AndroidGamesService
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .take(100)
            !gamelistService.hasMetadata(systemName, "./$safeFileName.app")
        }

        // Initialize scraping state
        pendingGames = gamesWithoutMetadata.toMutableList()
        currentTab = tab
        successCount = 0
        failedCount = 0
        skippedCount = 0
        currentIndex = 0

        // Continue scraping
        continueAndroidMetadataScraping(onProgress)
    }

    // Track last result for debugging
    private var lastResultInfo: String = ""

    /**
     * Continue scraping from where we left off.
     * Call this after the user provides a refined search term or skips a game.
     */
    suspend fun continueAndroidMetadataScraping(onProgress: ((ScrapeProgress) -> Unit)? = null) = withContext(Dispatchers.IO) {
        val total = pendingGames.size

        while (currentIndex < total) {
            val game = pendingGames[currentIndex]

            val progress = ScrapeProgress(
                total = total,
                completed = currentIndex,
                successful = successCount,
                failed = failedCount + skippedCount,
                currentGame = game.appName,
                lastResult = lastResultInfo
            )
            _scrapeProgress.value = progress
            onProgress?.invoke(progress)

            val result = scrapeAndSaveAndroidMetadata(game, currentTab)

            when (result) {
                is MetadataResult.Success -> {
                    lastResultInfo = "OK: '${game.appName}' -> '${result.metadata.name}'"
                    successCount++
                    currentIndex++
                }
                is MetadataResult.NotFound -> {
                    lastResultInfo = "NOT FOUND: '${game.appName}'"
                    // Pause and ask user for input
                    val pendingSearch = PendingMetadataSearch(
                        game = game,
                        originalSearchTerm = game.appName,
                        tab = currentTab
                    )
                    val pausedProgress = ScrapeProgress(
                        total = total,
                        completed = currentIndex,
                        successful = successCount,
                        failed = failedCount + skippedCount,
                        currentGame = game.appName,
                        pendingUserInput = pendingSearch,
                        lastResult = lastResultInfo
                    )
                    _scrapeProgress.value = pausedProgress
                    onProgress?.invoke(pausedProgress)
                    Log.d(TAG, "Paused scraping for user input: ${game.appName}")
                    return@withContext // Pause here, waiting for user input
                }
                is MetadataResult.Error -> {
                    lastResultInfo = "ERROR: '${game.appName}' - ${result.message}"
                    failedCount++
                    currentIndex++
                }
            }

            // Delay to avoid rate limiting (IGDB has limits)
            kotlinx.coroutines.delay(300)
        }

        // All done
        val finalProgress = ScrapeProgress(
            total = total,
            completed = total,
            successful = successCount,
            failed = failedCount + skippedCount,
            currentGame = null,
            lastResult = lastResultInfo
        )
        _scrapeProgress.value = finalProgress
        onProgress?.invoke(finalProgress)

        Log.d(TAG, "Scraped Android metadata for ${currentTab.name}: $successCount/$total successful, $skippedCount skipped")
    }

    /**
     * Retry the current game with a refined search term.
     * @param refinedSearchTerm The new search term to use
     * @param onProgress Callback for progress updates
     */
    suspend fun retryWithRefinedSearch(refinedSearchTerm: String, onProgress: ((ScrapeProgress) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (currentIndex >= pendingGames.size) return@withContext

        val game = pendingGames[currentIndex]
        val total = pendingGames.size

        val progress = ScrapeProgress(
            total = total,
            completed = currentIndex,
            successful = successCount,
            failed = failedCount + skippedCount,
            currentGame = "$refinedSearchTerm (retry)"
        )
        _scrapeProgress.value = progress
        onProgress?.invoke(progress)

        val result = scrapeAndSaveAndroidMetadata(game, currentTab, refinedSearchTerm)

        when (result) {
            is MetadataResult.Success -> {
                successCount++
                currentIndex++
                // Continue with the rest
                continueAndroidMetadataScraping(onProgress)
            }
            is MetadataResult.NotFound -> {
                // Still not found, pause again
                val pendingSearch = PendingMetadataSearch(
                    game = game,
                    originalSearchTerm = refinedSearchTerm,
                    tab = currentTab
                )
                val pausedProgress = ScrapeProgress(
                    total = total,
                    completed = currentIndex,
                    successful = successCount,
                    failed = failedCount + skippedCount,
                    currentGame = game.appName,
                    pendingUserInput = pendingSearch
                )
                _scrapeProgress.value = pausedProgress
                onProgress?.invoke(pausedProgress)
            }
            is MetadataResult.Error -> {
                failedCount++
                currentIndex++
                continueAndroidMetadataScraping(onProgress)
            }
        }
    }

    /**
     * Skip the current game and continue with the rest.
     * @param onProgress Callback for progress updates
     */
    suspend fun skipCurrentGame(onProgress: ((ScrapeProgress) -> Unit)? = null) = withContext(Dispatchers.IO) {
        skippedCount++
        currentIndex++
        continueAndroidMetadataScraping(onProgress)
    }

    /**
     * Legacy method - scrape all without user interaction (auto-skip not found)
     * @param tab The Android tab to scrape
     * @param onProgress Callback for progress updates
     * @return Number of games successfully scraped
     */
    suspend fun scrapeAllMissingAndroidMetadata(tab: AndroidTab, onProgress: ((ScrapeProgress) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        if (!igdbService.hasCredentials()) {
            Log.w(TAG, "IGDB credentials not configured")
            return@withContext 0
        }

        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        // Get all installed apps that are in ES-DE for this tab
        val installedApps = androidGamesService.getInstalledApps()
        val appsInTab = installedApps.filter { it.isInEsdeForTab(tab) }

        // Filter to only those without metadata
        val gamesWithoutMetadata = appsInTab.filter { app ->
            val safeFileName = app.appName
                .replace(Regex("[^a-zA-Z0-9\\s-]"), "")  // Keep hyphens, same as AndroidGamesService
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .take(100)
            !gamelistService.hasMetadata(systemName, "./$safeFileName.app")
        }

        var localSuccessCount = 0
        val total = gamesWithoutMetadata.size

        gamesWithoutMetadata.forEachIndexed { index, game ->
            val progress = ScrapeProgress(
                total = total,
                completed = index,
                successful = localSuccessCount,
                failed = index - localSuccessCount,
                currentGame = game.appName
            )
            _scrapeProgress.value = progress
            onProgress?.invoke(progress)

            if (scrapeAndSaveAndroidMetadata(game, tab) is MetadataResult.Success) {
                localSuccessCount++
            }

            // Delay to avoid rate limiting (IGDB has limits)
            kotlinx.coroutines.delay(300)
        }

        val finalProgress = ScrapeProgress(
            total = total,
            completed = total,
            successful = localSuccessCount,
            failed = total - localSuccessCount,
            currentGame = null
        )
        _scrapeProgress.value = finalProgress
        onProgress?.invoke(finalProgress)

        Log.d(TAG, "Scraped Android metadata for ${tab.name}: $localSuccessCount/$total successful")
        localSuccessCount
    }

    /**
     * Reset scrape progress (call when done or cancelled).
     */
    fun resetScrapeProgress() {
        _scrapeProgress.value = null
    }

    // ==================== Utility Methods ====================

    /**
     * Get count of games without metadata for Windows.
     */
    fun getWindowsGamesWithoutMetadataCount(): Int {
        val windowsPath = windowsGamesService.getEsdeWindowsPath() ?: return 0
        val windowsDir = File(windowsPath)
        val allShortcutFiles = windowsDir.listFiles()?.filter { file ->
            file.extension == "steam" || file.extension == "gog" || file.extension == "epic"
        }?.map { it.name } ?: emptyList()

        return gamelistService.getGamesWithoutMetadata("windows", allShortcutFiles).size
    }

    /**
     * Get count of Android games without metadata for a tab.
     */
    fun getAndroidGamesWithoutMetadataCount(tab: AndroidTab): Int {
        val systemName = when (tab) {
            AndroidTab.GAMES -> "androidgames"
            AndroidTab.APPS -> "android"
            AndroidTab.EMULATORS -> "emulators"
        }

        val installedApps = androidGamesService.getInstalledApps()
        val appsInTab = installedApps.filter { it.isInEsdeForTab(tab) }

        Log.d(TAG, "getAndroidGamesWithoutMetadataCount: tab=$tab, systemName=$systemName, appsInTab=${appsInTab.size}")

        val gameFiles = appsInTab.map { app ->
            val safeFileName = app.appName
                .replace(Regex("[^a-zA-Z0-9\\s-]"), "")  // Keep hyphens, same as AndroidGamesService
                .replace(Regex("\\s+"), "_")
                .trim('_')
                .take(100)
            "$safeFileName.app"
        }

        Log.d(TAG, "getAndroidGamesWithoutMetadataCount: gameFiles=$gameFiles")

        val missingMetadata = gamelistService.getGamesWithoutMetadata(systemName, gameFiles)
        Log.d(TAG, "getAndroidGamesWithoutMetadataCount: missingMetadata=$missingMetadata (count=${missingMetadata.size})")

        return missingMetadata.size
    }

    // ==================== IGDB / PS Vita ====================
    // PS Vita scraping uses the same IGDB credentials as Android scraping.
    // hasIgdbCredentials() / setIgdbCredentials() are defined in the IGDB/Android section above.

    /**
     * Search IGDB for a PS Vita game by name.
     */
    suspend fun searchVitaGame(name: String): MetadataResult = igdbService.searchVitaGame(name)

    /**
     * Scrape and save metadata for a single PS Vita game using IGDB.
     * @param game The VitaGame to scrape
     * @param options Scrape options controlling which media types to download
     * @return True if metadata was found and saved; false if not found
     * @throws Exception if the IGDB API returns an error (credentials, network, etc.)
     */
    suspend fun scrapeAndSaveVitaMetadata(
        game: VitaGame,
        options: ScrapeOptions = ScrapeOptions()
    ): Boolean = withContext(Dispatchers.IO) {
        when (val result = igdbService.searchVitaGame(game.displayName)) {
            is MetadataResult.NotFound -> {
                Log.w(TAG, "No IGDB results for Vita game: ${game.displayName}")
                return@withContext false
            }
            is MetadataResult.Error -> {
                throw Exception(result.message)
            }
            is MetadataResult.Success -> {
                val igdb = result.metadata
                val gameFileName = File(game.filePath).nameWithoutExtension
                val gamePath = "./$gameFileName.psvita"

                var metadata = GameMetadata(
                    path = gamePath,
                    name = igdb.name.ifBlank { game.displayName },
                    desc = if (options.scrapeMetadata) igdb.desc else null,
                    rating = if (options.scrapeMetadata) igdb.rating else null,
                    releasedate = if (options.scrapeMetadata) igdb.releasedate else null,
                    developer = if (options.scrapeMetadata) igdb.developer else null,
                    publisher = if (options.scrapeMetadata) igdb.publisher else null,
                    genre = if (options.scrapeMetadata) igdb.genre else null,
                    players = if (options.scrapeMetadata) igdb.players else null
                )

                if (options.scrapeArtwork) {
                    igdb.image?.let { url ->
                        val coverPath = igdbService.downloadCoverImage(url, "psvita", gameFileName)
                        if (coverPath != null) {
                            metadata = metadata.copy(image = "./media/covers/$gameFileName.jpg")
                        }
                    }
                    igdb.screenshotUrls?.let { urls ->
                        val screenshotPath = igdbService.downloadScreenshots(urls, "psvita", gameFileName)
                        if (screenshotPath != null) {
                            metadata = metadata.copy(titlescreen = "./media/screenshots/$gameFileName.jpg")
                        }
                    }
                    igdbService.generateMiximage("psvita", gameFileName)
                }

                gamelistService.writeGameMetadata("psvita", metadata)
                Log.d(TAG, "Saved IGDB metadata for Vita game: ${igdb.name}")
                true
            }
        }
    }

    /**
     * Scrape metadata for all PS Vita games that don't have it.
     * @param onProgress Callback for progress updates
     * @return Number of games successfully scraped
     */
    suspend fun scrapeAllMissingVitaMetadata(
        options: ScrapeOptions = ScrapeOptions(),
        onProgress: ((ScrapeProgress) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val vitaPath = vitaGamesService.getEsdeVitaPath()
        if (vitaPath == null) {
            Log.e(TAG, "Could not get psvita path")
            return@withContext 0
        }

        val vitaDir = File(vitaPath)
        val allFiles = vitaDir.listFiles { f ->
            f.isFile && f.extension == "psvita"
        }?.filter { file ->
            !gamelistService.hasMetadata("psvita", "./${file.name}")
        } ?: return@withContext 0

        var successCount = 0
        val total = allFiles.size

        allFiles.forEachIndexed { index, file ->
            val progress = ScrapeProgress(
                total = total,
                completed = index,
                successful = successCount,
                failed = index - successCount,
                currentGame = file.nameWithoutExtension
            )
            _scrapeProgress.value = progress
            onProgress?.invoke(progress)

            try {
                val titleId = file.readText().trim()
                val displayName = file.nameWithoutExtension
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

                val game = VitaGame(
                    titleId = titleId,
                    displayName = displayName,
                    filePath = file.absolutePath
                )
                val scraped = scrapeAndSaveVitaMetadata(game, options)
                if (scraped) successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error scraping metadata for ${file.name}", e)
            }

            // Small delay to avoid ScreenScraper rate limiting
            kotlinx.coroutines.delay(1000)
        }

        val finalProgress = ScrapeProgress(
            total = total,
            completed = total,
            successful = successCount,
            failed = total - successCount
        )
        _scrapeProgress.value = finalProgress
        onProgress?.invoke(finalProgress)

        successCount
    }

    /**
     * Get count of PS Vita games without metadata.
     */
    fun getVitaGamesWithoutMetadataCount(): Int {
        val vitaPath = vitaGamesService.getEsdeVitaPath() ?: return 0
        val vitaDir = File(vitaPath)
        if (!vitaDir.exists()) return 0

        val allFiles = vitaDir.listFiles { f ->
            f.isFile && f.extension == "psvita"
        }?.map { it.name } ?: return 0

        return gamelistService.getGamesWithoutMetadata("psvita", allFiles).size
    }

    /**
     * Remove gamelist metadata for a PS Vita game by its path (e.g. "./GameName.psvita").
     */
    fun removeVitaGameMetadata(path: String) {
        gamelistService.removeGameMetadata("psvita", path)
    }
}
