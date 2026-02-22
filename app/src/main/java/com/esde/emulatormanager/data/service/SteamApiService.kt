package com.esde.emulatormanager.data.service

import android.util.Log
import com.esde.emulatormanager.data.model.GameMetadata
import com.esde.emulatormanager.data.model.MetadataResult
import com.esde.emulatormanager.data.model.SteamGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for searching Steam games using the Steam Store Search API.
 * Uses the store search endpoint which is publicly accessible and returns relevant results.
 */
@Singleton
class SteamApiService @Inject constructor() {
    companion object {
        private const val TAG = "SteamApiService"
        // Use Steam Store search API - publicly accessible and works without API key
        private const val STEAM_SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
        private const val MAX_SEARCH_RESULTS = 50

        // Steam CDN URLs for artwork
        // Library capsule (600x900) - best for ES-DE covers
        private const val STEAM_LIBRARY_CAPSULE_URL = "https://steamcdn-a.akamaihd.net/steam/apps/%d/library_600x900.jpg"
        // Alternative cover image
        private const val STEAM_CAPSULE_URL = "https://steamcdn-a.akamaihd.net/steam/apps/%d/capsule_616x353.jpg"
        // Header image (460x215)
        private const val STEAM_HEADER_URL = "https://steamcdn-a.akamaihd.net/steam/apps/%d/header.jpg"
        // Logo/hero image
        private const val STEAM_LOGO_URL = "https://steamcdn-a.akamaihd.net/steam/apps/%d/logo.png"

        // Screenshot URL format - screenshots are numbered starting from 0
        // Full size format: ss_{hash}.1920x1080.jpg or 0000000001_ss_{hash}_1920x1080.jpg
        // The API returns "screenshots" array with path_full URLs
    }

    // Track last error for UI feedback
    var lastError: String? = null
        private set

    /**
     * Search for Steam games by name using the Steam Store Search API.
     * Returns null if there was an error.
     */
    suspend fun searchGames(query: String): List<SteamGame>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        lastError = null
        Log.d(TAG, "Searching Steam Store for: $query")

        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$STEAM_SEARCH_URL?term=$encodedQuery&l=english&cc=US"
            Log.d(TAG, "Connecting to: $searchUrl")

            val url = URL(searchUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.setRequestProperty("Accept", "application/json")

            Log.d(TAG, "Waiting for response...")
            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "Reading response...")
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Response size: ${response.length} chars")
                val games = parseSearchResults(response)
                Log.d(TAG, "Found ${games.size} games")
                games.take(MAX_SEARCH_RESULTS)
            } else {
                val errorMsg = "HTTP error: $responseCode"
                Log.e(TAG, errorMsg)
                lastError = errorMsg
                null
            }
        } catch (e: java.net.UnknownHostException) {
            val errorMsg = "No internet connection"
            Log.e(TAG, errorMsg, e)
            lastError = errorMsg
            null
        } catch (e: java.net.SocketTimeoutException) {
            val errorMsg = "Connection timed out"
            Log.e(TAG, errorMsg, e)
            lastError = errorMsg
            null
        } catch (e: Exception) {
            val errorMsg = "Error: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, "Error searching Steam", e)
            lastError = errorMsg
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse the JSON response from Steam Store Search API.
     * Response format: {"total":N,"items":[{"type":"app","name":"Game Name","id":12345,...},...]}
     */
    private fun parseSearchResults(json: String): List<SteamGame> {
        val games = mutableListOf<SteamGame>()
        try {
            val jsonObject = JSONObject(json)
            val items = jsonObject.optJSONArray("items") ?: return emptyList()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                // Only include apps (games), not bundles or other types
                val type = item.optString("type", "")
                if (type == "app") {
                    val appId = item.getInt("id")
                    val name = item.optString("name", "")

                    if (name.isNotBlank()) {
                        games.add(SteamGame(appId = appId, name = name))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search results", e)
        }
        return games
    }

    /**
     * Get details for a specific game by App ID using the Steam Store API.
     */
    suspend fun getGameByAppId(appId: Int): SteamGame? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val detailsUrl = "https://store.steampowered.com/api/appdetails?appids=$appId"
            val url = URL(detailsUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val appData = jsonObject.optJSONObject(appId.toString())

                if (appData?.optBoolean("success") == true) {
                    val data = appData.getJSONObject("data")
                    val name = data.optString("name", "")
                    if (name.isNotBlank()) {
                        return@withContext SteamGame(appId = appId, name = name)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting game details for $appId", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Clear any cached data (currently not used as we search on demand).
     */
    fun clearCache() {
        // No cache to clear with the new search-based approach
    }

    /**
     * Get full metadata for a Steam game by App ID.
     * Fetches description, release date, developers, publishers, genres, etc.
     *
     * @param appId Steam App ID
     * @param gamePath The relative path to the game file (e.g., "./Game Name.steam")
     * @return MetadataResult with the game metadata or error
     */
    suspend fun getGameMetadata(appId: Int, gamePath: String): MetadataResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val detailsUrl = "https://store.steampowered.com/api/appdetails?appids=$appId&l=english"
            Log.d(TAG, "Fetching Steam metadata for appId: $appId")

            val url = URL(detailsUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                val appData = jsonObject.optJSONObject(appId.toString())

                if (appData?.optBoolean("success") == true) {
                    val data = appData.getJSONObject("data")
                    return@withContext parseFullSteamMetadata(data, gamePath, appId)
                } else {
                    return@withContext MetadataResult.NotFound("Steam App ID: $appId")
                }
            } else {
                return@withContext MetadataResult.Error("HTTP error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Steam metadata for $appId", e)
            return@withContext MetadataResult.Error("Failed to fetch Steam metadata: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse full metadata from Steam API response.
     */
    private fun parseFullSteamMetadata(data: JSONObject, gamePath: String, appId: Int): MetadataResult {
        try {
            val name = data.optString("name", "")
            if (name.isBlank()) {
                return MetadataResult.Error("Game name not found in Steam data")
            }

            // Parse description - use short_description if available, fall back to about_the_game
            val shortDesc = data.optString("short_description", "")
            val aboutGame = data.optString("about_the_game", "")
            // Strip HTML tags from description
            val description = if (shortDesc.isNotBlank()) {
                shortDesc.replace(Regex("<[^>]*>"), "").trim()
            } else {
                aboutGame.replace(Regex("<[^>]*>"), "").trim().take(500) // Limit long descriptions
            }

            // Parse release date
            val releaseInfo = data.optJSONObject("release_date")
            val releaseDateStr = releaseInfo?.optString("date", "") ?: ""
            val releaseDate = parseReleaseDateToEsdeFormat(releaseDateStr)

            // Parse developers
            val developersArray = data.optJSONArray("developers")
            val developer = developersArray?.let {
                (0 until it.length()).mapNotNull { i -> it.optString(i, null) }.firstOrNull()
            }

            // Parse publishers
            val publishersArray = data.optJSONArray("publishers")
            val publisher = publishersArray?.let {
                (0 until it.length()).mapNotNull { i -> it.optString(i, null) }.firstOrNull()
            }

            // Parse genres
            val genresArray = data.optJSONArray("genres")
            val genres = genresArray?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.optString("description", null)
                }
            }?.joinToString(", ")

            // Parse metacritic score (convert to 0-1 scale)
            val metacritic = data.optJSONObject("metacritic")
            val rating = metacritic?.optInt("score", -1)?.let { score ->
                if (score > 0) score.toFloat() / 100f else null
            }

            // Get cover image path (will be downloaded separately)
            val headerImage = data.optString("header_image", null)

            // Parse screenshot URLs
            val screenshotsArray = data.optJSONArray("screenshots")
            val screenshotUrls = screenshotsArray?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    arr.optJSONObject(i)?.optString("path_full", null)
                }
            }

            // Parse video URL from movies array
            // Steam API now uses streaming formats (HLS/DASH) but the old CDN still works
            // We extract the movie ID and construct the direct download URL
            val moviesArray = data.optJSONArray("movies")
            val videoUrl = moviesArray?.let { arr ->
                if (arr.length() > 0) {
                    val movie = arr.getJSONObject(0)
                    val movieId = movie.optLong("id", -1)
                    if (movieId > 0) {
                        // Construct direct video URL using Steam CDN pattern
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/$movieId/movie480.mp4"
                    } else null
                } else null
            }

            val metadata = GameMetadata(
                path = gamePath,
                name = name,
                desc = description.ifBlank { null },
                rating = rating,
                releasedate = releaseDate,
                developer = developer,
                publisher = publisher,
                genre = genres,
                players = null, // Steam doesn't provide this in a simple format
                image = headerImage, // Store URL temporarily
                screenshotUrls = screenshotUrls, // Store screenshot URLs for downloading
                videoUrl = videoUrl // Store video URL for downloading
            )

            Log.d(TAG, "Parsed Steam metadata for: $name (${screenshotUrls?.size ?: 0} screenshots, video: ${videoUrl != null})")
            return MetadataResult.Success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Steam metadata", e)
            return MetadataResult.Error("Failed to parse Steam metadata: ${e.message}")
        }
    }

    /**
     * Parse Steam release date string to ES-DE format (YYYYMMDDTHHMMSS).
     * Steam dates can be in various formats like "Jan 1, 2020" or "Q1 2020"
     */
    private fun parseReleaseDateToEsdeFormat(dateStr: String): String? {
        if (dateStr.isBlank()) return null

        try {
            // Try common date formats
            val formats = listOf(
                java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("d MMM, yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("dd MMM, yyyy", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy", java.util.Locale.US)
            )

            for (format in formats) {
                try {
                    val date = format.parse(dateStr)
                    if (date != null) {
                        return java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US).format(date)
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }

            // If all formats fail, try to extract just the year
            val yearMatch = Regex("\\d{4}").find(dateStr)
            if (yearMatch != null) {
                return "${yearMatch.value}0101T000000"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse release date: $dateStr")
        }
        return null
    }

    /**
     * Download cover artwork for a Steam game to the ES-DE media folder.
     * Downloads the library capsule (600x900) which is ideal for ES-DE covers.
     *
     * @param appId Steam App ID
     * @param gameFileName The filename of the game (without extension) - used for naming the artwork
     * @param mediaDir The ES-DE downloaded_media/windows/covers directory
     * @return The path to the downloaded image, or null if download failed
     */
    suspend fun downloadCoverArtwork(appId: Int, gameFileName: String, mediaDir: File): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading cover artwork for Steam appId: $appId")

        // Try library capsule first (best for covers), then fall back to header
        val artworkUrls = listOf(
            STEAM_LIBRARY_CAPSULE_URL.format(appId),
            STEAM_CAPSULE_URL.format(appId),
            STEAM_HEADER_URL.format(appId)
        )

        for (artworkUrl in artworkUrls) {
            try {
                val result = downloadImage(artworkUrl, mediaDir, gameFileName)
                if (result != null) {
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed to download from $artworkUrl: ${e.message}")
            }
        }

        Log.e(TAG, "Failed to download any artwork for appId: $appId")
        null
    }

    /**
     * Download marquee/logo artwork for a Steam game.
     *
     * @param appId Steam App ID
     * @param gameFileName The filename of the game (without extension)
     * @param mediaDir The ES-DE downloaded_media/windows/marquees directory
     * @return The path to the downloaded image, or null if download failed
     */
    suspend fun downloadMarqueeArtwork(appId: Int, gameFileName: String, mediaDir: File): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading marquee artwork for Steam appId: $appId")

        val artworkUrl = STEAM_LOGO_URL.format(appId)
        try {
            return@withContext downloadImage(artworkUrl, mediaDir, gameFileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download marquee artwork: ${e.message}")
            null
        }
    }

    /**
     * Download an image from a URL to a file.
     *
     * @param imageUrl The URL to download from
     * @param outputDir The directory to save the image in
     * @param fileName The filename to save as (without extension)
     * @return The path to the downloaded file, or null if download failed
     */
    private fun downloadImage(imageUrl: String, outputDir: File, fileName: String): String? {
        var connection: HttpURLConnection? = null
        try {
            // Ensure output directory exists
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val url = URL(imageUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Determine file extension from content type or URL
                val contentType = connection.contentType ?: ""
                val extension = when {
                    contentType.contains("png") -> ".png"
                    contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
                    imageUrl.endsWith(".png") -> ".png"
                    else -> ".jpg"
                }

                val outputFile = File(outputDir, "$fileName$extension")
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d(TAG, "Downloaded artwork to: ${outputFile.absolutePath}")
                return outputFile.absolutePath
            } else {
                Log.d(TAG, "HTTP $responseCode for $imageUrl")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image from $imageUrl", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Download screenshots for a Steam game.
     * Downloads up to 5 screenshots from the provided URLs.
     *
     * @param screenshotUrls List of screenshot URLs from Steam API
     * @param mediaDir The ES-DE downloaded_media/windows/screenshots directory
     * @param gameFileName The filename of the game (without extension)
     * @return The path to the first downloaded screenshot, or null if none downloaded
     */
    suspend fun downloadScreenshots(
        screenshotUrls: List<String>,
        mediaDir: File,
        gameFileName: String
    ): String? = withContext(Dispatchers.IO) {
        if (screenshotUrls.isEmpty()) return@withContext null

        Log.d(TAG, "Downloading screenshots for: $gameFileName (${screenshotUrls.size} available)")

        // Ensure directory exists
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }

        var firstScreenshotPath: String? = null
        val maxScreenshots = minOf(5, screenshotUrls.size)

        for (i in 0 until maxScreenshots) {
            val url = screenshotUrls[i]
            try {
                // First screenshot has no suffix, others have _1, _2, etc.
                val suffix = if (i == 0) "" else "_$i"
                val outputFile = File(mediaDir, "$gameFileName$suffix.jpg")

                val downloaded = downloadImageToFile(url, outputFile)
                if (downloaded && i == 0) {
                    firstScreenshotPath = outputFile.absolutePath
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading screenshot $i: ${e.message}")
            }
        }

        Log.d(TAG, "Downloaded ${if (firstScreenshotPath != null) maxScreenshots else 0} screenshots")
        firstScreenshotPath
    }

    /**
     * Download an image directly to a specific file.
     */
    private fun downloadImageToFile(imageUrl: String, outputFile: File): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(imageUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading to file: ${e.message}")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Download video for a Steam game.
     * Steam provides direct MP4 video URLs that can be downloaded.
     *
     * @param videoUrl The MP4 video URL from Steam API
     * @param mediaDir The ES-DE downloaded_media/windows/videos directory
     * @param gameFileName The filename of the game (without extension)
     * @return The path to the downloaded video, or null if download failed
     */
    suspend fun downloadVideo(
        videoUrl: String,
        mediaDir: File,
        gameFileName: String
    ): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading video for: $gameFileName")

        var connection: HttpURLConnection? = null
        try {
            // Ensure directory exists
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }

            val outputFile = File(mediaDir, "$gameFileName.mp4")

            val url = URL(videoUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000  // Longer timeout for video
            connection.readTimeout = 120000    // 2 minutes for larger files
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.contentLength
                Log.d(TAG, "Video size: ${contentLength / 1024}KB")

                // Skip if video is too large (over 50MB)
                if (contentLength > 50 * 1024 * 1024) {
                    Log.d(TAG, "Skipping video download - file too large (${contentLength / 1024 / 1024}MB)")
                    return@withContext null
                }

                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d(TAG, "Downloaded video to: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to download video: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading video: ${e.message}", e)
        } finally {
            connection?.disconnect()
        }
        null
    }

    /**
     * Generate a miximage combining cover art and a screenshot.
     * Similar to IGDB miximage but for Steam games.
     *
     * @param coversDir The covers directory containing the cover image
     * @param screenshotsDir The screenshots directory containing screenshots
     * @param miximagesDir The miximages directory where the output will be saved
     * @param gameFileName The filename of the game (without extension)
     * @return The path to the generated miximage, or null if generation failed
     */
    fun generateMiximage(
        coversDir: File,
        screenshotsDir: File,
        miximagesDir: File,
        gameFileName: String
    ): String? {
        try {
            // Find cover image
            val coverExtensions = listOf(".jpg", ".jpeg", ".png", ".webp")
            var coverFile: File? = null
            for (ext in coverExtensions) {
                val file = File(coversDir, "$gameFileName$ext")
                if (file.exists()) {
                    coverFile = file
                    break
                }
            }

            if (coverFile == null) {
                Log.d(TAG, "No cover found for miximage: $gameFileName")
                return null
            }

            // Find screenshot (use first one)
            var screenshotFile: File? = null
            for (ext in coverExtensions) {
                val file = File(screenshotsDir, "$gameFileName$ext")
                if (file.exists()) {
                    screenshotFile = file
                    break
                }
            }

            // Load cover bitmap
            val coverBitmap = android.graphics.BitmapFactory.decodeFile(coverFile.absolutePath)
            if (coverBitmap == null) {
                Log.e(TAG, "Failed to load cover image: ${coverFile.absolutePath}")
                return null
            }

            // Miximage dimensions (ES-DE default is 1280x960)
            val mixWidth = 1280
            val mixHeight = 960

            // Create the miximage bitmap with transparency
            val mixBitmap = android.graphics.Bitmap.createBitmap(mixWidth, mixHeight, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(mixBitmap)

            // Start with transparent background
            canvas.drawColor(android.graphics.Color.TRANSPARENT)

            // Calculate cover dimensions first so we know how much margin to leave
            val coverMaxHeight = (mixHeight * 0.55f).toInt()  // 55% of height
            val coverAspect = coverBitmap.width.toFloat() / coverBitmap.height
            val coverHeight = coverMaxHeight
            val coverWidth = (coverHeight * coverAspect).toInt()

            // Screenshot inset - leave room for the cover to hang off the screenshot but stay on canvas
            val screenshotMarginLeft = (coverWidth * 0.22f).toInt()
            val screenshotMarginBottom = (coverHeight * 0.18f).toInt()
            val screenshotMarginTop = 30
            val screenshotMarginRight = 30

            if (screenshotFile != null) {
                // Load screenshot
                val screenshotBitmap = android.graphics.BitmapFactory.decodeFile(screenshotFile.absolutePath)
                if (screenshotBitmap != null) {
                    // Draw screenshot inset from the edges
                    val screenshotRect = android.graphics.RectF(
                        screenshotMarginLeft.toFloat(),
                        screenshotMarginTop.toFloat(),
                        (mixWidth - screenshotMarginRight).toFloat(),
                        (mixHeight - screenshotMarginBottom).toFloat()
                    )

                    // Calculate scaled dimensions maintaining aspect ratio
                    val screenshotAspect = screenshotBitmap.width.toFloat() / screenshotBitmap.height
                    val targetAspect = screenshotRect.width() / screenshotRect.height()

                    val srcRect = if (screenshotAspect > targetAspect) {
                        val cropWidth = (screenshotBitmap.height * targetAspect).toInt()
                        val cropX = (screenshotBitmap.width - cropWidth) / 2
                        android.graphics.Rect(cropX, 0, cropX + cropWidth, screenshotBitmap.height)
                    } else {
                        val cropHeight = (screenshotBitmap.width / targetAspect).toInt()
                        val cropY = (screenshotBitmap.height - cropHeight) / 2
                        android.graphics.Rect(0, cropY, screenshotBitmap.width, cropY + cropHeight)
                    }

                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(screenshotBitmap, srcRect, screenshotRect, paint)

                    // Add subtle vignette effect at bottom-left
                    val vignettePaint = android.graphics.Paint()
                    vignettePaint.shader = android.graphics.RadialGradient(
                        screenshotMarginLeft.toFloat(), (mixHeight - screenshotMarginBottom).toFloat(),
                        mixWidth * 0.5f,
                        android.graphics.Color.parseColor("#60000000"),
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(screenshotRect, vignettePaint)

                    screenshotBitmap.recycle()
                }
            }

            // Position cover: overlapping the screenshot's bottom-left corner
            val coverLeft = (screenshotMarginLeft * 0.3f).toInt()
            val coverTop = mixHeight - coverHeight - (screenshotMarginBottom * 0.3f).toInt()

            // Draw shadow
            val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            shadowPaint.color = android.graphics.Color.parseColor("#60000000")
            canvas.drawRect(
                (coverLeft + 12).toFloat(),
                (coverTop + 12).toFloat(),
                (coverLeft + coverWidth + 12).toFloat(),
                (coverTop + coverHeight + 12).toFloat(),
                shadowPaint
            )

            // Draw cover
            val coverDestRect = android.graphics.Rect(coverLeft, coverTop, coverLeft + coverWidth, coverTop + coverHeight)
            val coverPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(coverBitmap, null, coverDestRect, coverPaint)

            // Add border around cover
            val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            borderPaint.style = android.graphics.Paint.Style.STROKE
            borderPaint.strokeWidth = 3f
            borderPaint.color = android.graphics.Color.parseColor("#50ffffff")
            canvas.drawRect(
                coverLeft.toFloat(),
                coverTop.toFloat(),
                (coverLeft + coverWidth).toFloat(),
                (coverTop + coverHeight).toFloat(),
                borderPaint
            )

            coverBitmap.recycle()

            // Ensure output directory exists
            if (!miximagesDir.exists()) {
                miximagesDir.mkdirs()
            }

            // Save miximage
            val outputFile = File(miximagesDir, "$gameFileName.png")
            java.io.FileOutputStream(outputFile).use { out ->
                mixBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
            }
            mixBitmap.recycle()

            Log.d(TAG, "Generated miximage: ${outputFile.absolutePath}")
            return outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating miximage: ${e.message}", e)
            return null
        }
    }
}
