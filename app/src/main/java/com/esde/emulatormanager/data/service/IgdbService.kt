package com.esde.emulatormanager.data.service

import android.content.Context
import android.util.Log
import com.esde.emulatormanager.data.model.GameMetadata
import com.esde.emulatormanager.data.model.MetadataResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for fetching game metadata from IGDB (Internet Game Database).
 * IGDB requires Twitch OAuth authentication.
 *
 * To use this service, you need to:
 * 1. Create a Twitch Developer application at https://dev.twitch.tv/console
 * 2. Get your Client ID and Client Secret
 * 3. Set them in the app settings or as build config
 */
@Singleton
class IgdbService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esdeConfigService: EsdeConfigService
) {
    companion object {
        private const val TAG = "IgdbService"
        private const val TWITCH_AUTH_URL = "https://id.twitch.tv/oauth2/token"
        private const val IGDB_API_URL = "https://api.igdb.com/v4"

        // Platform IDs in IGDB
        private const val PLATFORM_ANDROID = 34
        private const val PLATFORM_IOS = 39
        private const val PLATFORM_PC = 6
        private const val PLATFORM_VITA = 46

        // Cache duration for access token (slightly less than actual expiry)
        private const val TOKEN_CACHE_DURATION_MS = 50 * 24 * 60 * 60 * 1000L // 50 days
    }

    // These should be configured by the user or stored securely
    private var clientId: String? = null
    private var clientSecret: String? = null
    private var accessToken: String? = null
    private var tokenExpiry: Long = 0

    /**
     * Set the Twitch API credentials.
     * Users need to create their own Twitch Developer application to use IGDB.
     */
    fun setCredentials(clientId: String, clientSecret: String) {
        this.clientId = clientId
        this.clientSecret = clientSecret
        this.accessToken = null // Reset token when credentials change
        this.tokenExpiry = 0
        saveCredentials()
    }

    /**
     * Check if credentials are configured.
     */
    fun hasCredentials(): Boolean {
        loadCredentials()
        return !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }

    /**
     * Get the current client ID (for display in settings).
     */
    fun getClientId(): String? {
        loadCredentials()
        return clientId
    }

    /**
     * Search for an Android game by name and return metadata.
     * @param gameName The name of the game to search for
     * @param packageName Optional package name for better matching
     * @return MetadataResult with the game metadata or error
     */
    suspend fun searchAndroidGame(gameName: String, packageName: String? = null): MetadataResult = withContext(Dispatchers.IO) {
        if (!hasCredentials()) {
            return@withContext MetadataResult.Error("IGDB credentials not configured. Please set up Twitch API credentials in Settings.")
        }

        try {
            // Get or refresh access token
            val token = getAccessToken() ?: return@withContext MetadataResult.Error("Failed to authenticate with IGDB")

            // Clean up game name for better search results
            val searchName = cleanGameName(gameName)
            Log.d(TAG, "Searching IGDB for: $searchName")

            // Search for the game on Android platform
            val query = """
                search "$searchName";
                fields name, summary, rating, first_release_date,
                       genres.name, involved_companies.company.name, involved_companies.developer, involved_companies.publisher,
                       cover.url, screenshots.url, videos.video_id,
                       game_modes.name, multiplayer_modes.onlinemax;
                where platforms = ($PLATFORM_ANDROID, $PLATFORM_IOS);
                limit 5;
            """.trimIndent()

            val response = makeIgdbRequest("games", query, token)
            if (response == null || response.length() == 0) {
                // Try broader search without platform filter
                val broaderQuery = """
                    search "$searchName";
                    fields name, summary, rating, first_release_date,
                           genres.name, involved_companies.company.name, involved_companies.developer, involved_companies.publisher,
                           cover.url, screenshots.url, videos.video_id,
                           game_modes.name, multiplayer_modes.onlinemax;
                    limit 5;
                """.trimIndent()

                val broaderResponse = makeIgdbRequest("games", broaderQuery, token)
                if (broaderResponse == null || broaderResponse.length() == 0) {
                    return@withContext MetadataResult.NotFound(gameName)
                }
                return@withContext parseIgdbResponse(broaderResponse, gameName)
            }

            parseIgdbResponse(response, gameName)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching IGDB: ${e.message}", e)
            MetadataResult.Error("Failed to search IGDB: ${e.message}")
        }
    }

    /**
     * Search for a PS Vita game by name and return metadata.
     * Uses IGDB platform ID 46 (PlayStation Vita).
     * Falls back to a platform-agnostic search if no Vita-specific results are found.
     */
    suspend fun searchVitaGame(gameName: String): MetadataResult = withContext(Dispatchers.IO) {
        if (!hasCredentials()) {
            return@withContext MetadataResult.Error("IGDB credentials not configured. Please set up Twitch API credentials.")
        }

        try {
            val token = getAccessToken()
                ?: return@withContext MetadataResult.Error("Failed to authenticate with IGDB. Check your Client ID and Secret.")

            val searchName = cleanGameName(gameName)
            Log.d(TAG, "Searching IGDB for PS Vita game: $searchName")

            val fields = """
                name, summary, rating, first_release_date,
                genres.name, involved_companies.company.name, involved_companies.developer, involved_companies.publisher,
                cover.url, screenshots.url, videos.video_id, game_modes.name
            """.trimIndent()

            // Primary: search scoped to PS Vita platform
            val vitaQuery = """
                search "$searchName";
                fields $fields;
                where platforms = ($PLATFORM_VITA);
                limit 5;
            """.trimIndent()

            val vitaResponse = makeIgdbRequest("games", vitaQuery, token)
            if (vitaResponse != null && vitaResponse.length() > 0) {
                val result = parseIgdbResponse(vitaResponse, gameName)
                if (result is MetadataResult.Success) return@withContext result
            }

            // Fallback: search without platform filter (covers cases where Vita isn't tagged)
            Log.d(TAG, "No Vita-specific results, trying broader search for: $searchName")
            val broaderQuery = """
                search "$searchName";
                fields $fields;
                limit 5;
            """.trimIndent()

            val broaderResponse = makeIgdbRequest("games", broaderQuery, token)
            if (broaderResponse == null || broaderResponse.length() == 0) {
                return@withContext MetadataResult.NotFound(gameName)
            }
            parseIgdbResponse(broaderResponse, gameName)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching IGDB for Vita game: ${e.message}", e)
            MetadataResult.Error("Failed to search IGDB: ${e.message}")
        }
    }

    /**
     * Search for a Windows/PC game by name and return metadata.
     * Uses IGDB platform ID 6 (PC Windows).
     * Falls back to a platform-agnostic search if no PC-specific results are found.
     * Used for Epic, GOG, and Amazon games.
     */
    suspend fun searchWindowsGame(gameName: String): MetadataResult = withContext(Dispatchers.IO) {
        if (!hasCredentials()) {
            return@withContext MetadataResult.Error("IGDB credentials not configured. Please set up Twitch API credentials.")
        }

        try {
            val token = getAccessToken()
                ?: return@withContext MetadataResult.Error("Failed to authenticate with IGDB. Check your Client ID and Secret.")

            val searchName = cleanGameName(gameName)
            Log.d(TAG, "Searching IGDB for Windows game: $searchName")

            val fields = """
                name, summary, rating, first_release_date,
                genres.name, involved_companies.company.name, involved_companies.developer, involved_companies.publisher,
                cover.url, screenshots.url, videos.video_id, game_modes.name
            """.trimIndent()

            // Primary: search scoped to PC platform
            val pcQuery = """
                search "$searchName";
                fields $fields;
                where platforms = ($PLATFORM_PC);
                limit 5;
            """.trimIndent()

            val pcResponse = makeIgdbRequest("games", pcQuery, token)
            if (pcResponse != null && pcResponse.length() > 0) {
                val result = parseIgdbResponse(pcResponse, gameName)
                if (result is MetadataResult.Success) return@withContext result
            }

            // Fallback: search without platform filter
            Log.d(TAG, "No PC-specific results, trying broader search for: $searchName")
            val broaderQuery = """
                search "$searchName";
                fields $fields;
                limit 5;
            """.trimIndent()

            val broaderResponse = makeIgdbRequest("games", broaderQuery, token)
            if (broaderResponse == null || broaderResponse.length() == 0) {
                return@withContext MetadataResult.NotFound(gameName)
            }
            parseIgdbResponse(broaderResponse, gameName)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching IGDB for Windows game: ${e.message}", e)
            MetadataResult.Error("Failed to search IGDB: ${e.message}")
        }
    }

    /**
     * Download cover image for a game.
     * @param imageUrl The IGDB image URL (partial)
     * @param systemName The ES-DE system name (e.g., "android", "androidgames")
     * @param gameFileName The game file name (without extension) for naming the image
     * @return Path to downloaded image, or null on failure
     */
    suspend fun downloadCoverImage(imageUrl: String, systemName: String, gameFileName: String): String? = withContext(Dispatchers.IO) {
        try {
            // IGDB returns URLs like //images.igdb.com/igdb/image/upload/t_thumb/co1234.jpg
            // We need to add https: and change size
            val fullUrl = if (imageUrl.startsWith("//")) {
                "https:${imageUrl.replace("t_thumb", "t_cover_big")}"
            } else {
                imageUrl.replace("t_thumb", "t_cover_big")
            }

            val mediaDir = esdeConfigService.getMediaDirectory(systemName, "covers")
            if (mediaDir == null) {
                Log.e(TAG, "Could not get media directory for $systemName")
                return@withContext null
            }

            val outputFile = File(mediaDir, "$gameFileName.jpg")

            val url = URL(fullUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    outputFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Downloaded cover to: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to download image: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading cover image: ${e.message}", e)
        }
        null
    }

    /**
     * Download screenshot images for a game.
     * @param screenshotUrls List of IGDB screenshot URLs
     * @param systemName The ES-DE system name (e.g., "android", "androidgames")
     * @param gameFileName The game file name (without extension) for naming the images
     * @return Path to first downloaded screenshot (for use as fanart), or null on failure
     */
    suspend fun downloadScreenshots(
        screenshotUrls: List<String>,
        systemName: String,
        gameFileName: String
    ): String? = withContext(Dispatchers.IO) {
        if (screenshotUrls.isEmpty()) return@withContext null

        try {
            // Get the screenshots directory for this system
            val screenshotsDir = esdeConfigService.getMediaDirectory(systemName, "screenshots")
            if (screenshotsDir == null) {
                Log.e(TAG, "Could not get screenshots directory for $systemName")
                return@withContext null
            }

            var firstScreenshotPath: String? = null

            // Download up to 5 screenshots
            screenshotUrls.take(5).forEachIndexed { index, imageUrl ->
                try {
                    // IGDB returns URLs like //images.igdb.com/igdb/image/upload/t_thumb/sc1234.jpg
                    // Change to screenshot_big size for better quality
                    val fullUrl = if (imageUrl.startsWith("//")) {
                        "https:${imageUrl.replace("t_thumb", "t_screenshot_big")}"
                    } else {
                        imageUrl.replace("t_thumb", "t_screenshot_big")
                    }

                    val suffix = if (index == 0) "" else "_$index"
                    val outputFile = File(screenshotsDir, "$gameFileName$suffix.jpg")

                    val url = URL(fullUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000

                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d(TAG, "Downloaded screenshot to: ${outputFile.absolutePath}")

                        if (index == 0) {
                            firstScreenshotPath = outputFile.absolutePath
                        }
                    } else {
                        Log.e(TAG, "Failed to download screenshot: HTTP ${connection.responseCode}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading screenshot $index: ${e.message}", e)
                }
            }

            firstScreenshotPath
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading screenshots: ${e.message}", e)
            null
        }
    }

    /**
     * Get the YouTube video URL for ES-DE to use as a video preview.
     * ES-DE can use YouTube URLs directly for video previews.
     *
     * @param youtubeVideoId The YouTube video ID from IGDB
     * @return The full YouTube URL that ES-DE can use
     */
    fun getYouTubeVideoUrl(youtubeVideoId: String): String {
        return "https://www.youtube.com/watch?v=$youtubeVideoId"
    }

    /**
     * Generate a miximage combining cover art and a screenshot.
     * The miximage places the cover on the left and screenshot on the right with a gradient blend.
     * @param systemName The ES-DE system name
     * @param gameFileName The game file name (without extension)
     * @return Path to generated miximage, or null on failure
     */
    suspend fun generateMiximage(
        systemName: String,
        gameFileName: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val coversDir = esdeConfigService.getMediaDirectory(systemName, "covers")
            val screenshotsDir = esdeConfigService.getMediaDirectory(systemName, "screenshots")
            val miximagesDir = esdeConfigService.getMediaDirectory(systemName, "miximages")

            if (coversDir == null || miximagesDir == null) {
                Log.e(TAG, "Could not get media directories for miximage generation")
                return@withContext null
            }

            // Find cover image
            val coverFile = listOf(".jpg", ".png", ".jpeg", ".webp")
                .map { File(coversDir, "$gameFileName$it") }
                .firstOrNull { it.exists() }

            if (coverFile == null) {
                Log.d(TAG, "No cover found for miximage: $gameFileName")
                return@withContext null
            }

            // Find screenshot (optional)
            val screenshotFile = if (screenshotsDir != null) {
                listOf(".jpg", ".png", ".jpeg", ".webp")
                    .map { File(screenshotsDir, "$gameFileName$it") }
                    .firstOrNull { it.exists() }
            } else null

            // Load cover bitmap
            val coverBitmap = android.graphics.BitmapFactory.decodeFile(coverFile.absolutePath)
            if (coverBitmap == null) {
                Log.e(TAG, "Failed to decode cover image: ${coverFile.absolutePath}")
                return@withContext null
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
            // The cover will overlap the screenshot edge, so we need margin on left and bottom
            val screenshotMarginLeft = (coverWidth * 0.22f).toInt()  // 22% of cover width as left margin
            val screenshotMarginBottom = (coverHeight * 0.18f).toInt()  // 18% of cover height as bottom margin
            val screenshotMarginTop = 30  // Top margin for balance
            val screenshotMarginRight = 30  // Right margin for balance

            if (screenshotFile != null) {
                // Load screenshot
                val screenshotBitmap = android.graphics.BitmapFactory.decodeFile(screenshotFile.absolutePath)
                if (screenshotBitmap != null) {
                    // Draw screenshot inset from the edges to create a frame
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
                        // Screenshot is wider - crop sides
                        val cropWidth = (screenshotBitmap.height * targetAspect).toInt()
                        val cropX = (screenshotBitmap.width - cropWidth) / 2
                        android.graphics.Rect(cropX, 0, cropX + cropWidth, screenshotBitmap.height)
                    } else {
                        // Screenshot is taller - crop top/bottom
                        val cropHeight = (screenshotBitmap.width / targetAspect).toInt()
                        val cropY = (screenshotBitmap.height - cropHeight) / 2
                        android.graphics.Rect(0, cropY, screenshotBitmap.width, cropY + cropHeight)
                    }

                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(screenshotBitmap, srcRect, screenshotRect, paint)

                    // Add subtle vignette effect at bottom-left where cover will go
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
            // The cover hangs off the screenshot but stays fully visible on the canvas
            val coverLeft = (screenshotMarginLeft * 0.3f).toInt()  // Positioned so it overlaps screenshot edge
            val coverTop = mixHeight - coverHeight - (screenshotMarginBottom * 0.3f).toInt()  // Overlaps bottom edge

            // Draw shadow (offset and blurred effect)
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

            // Add a subtle border around cover
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

            // Save miximage
            val outputFile = File(miximagesDir, "$gameFileName.png")
            java.io.FileOutputStream(outputFile).use { out ->
                mixBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
            }
            mixBitmap.recycle()

            Log.d(TAG, "Generated miximage: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error generating miximage: ${e.message}", e)
            null
        }
    }

    /**
     * Get or refresh the OAuth access token.
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        // Check if we have a valid cached token
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return@withContext accessToken
        }

        loadCredentials()
        val id = clientId ?: return@withContext null
        val secret = clientSecret ?: return@withContext null

        try {
            val url = URL("$TWITCH_AUTH_URL?client_id=$id&client_secret=$secret&grant_type=client_credentials")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                accessToken = json.getString("access_token")
                val expiresIn = json.getLong("expires_in") * 1000 // Convert to milliseconds
                tokenExpiry = System.currentTimeMillis() + expiresIn - 60000 // Refresh 1 minute early

                Log.d(TAG, "Got new IGDB access token, expires in ${expiresIn / 1000 / 60} minutes")
                return@withContext accessToken
            } else {
                Log.e(TAG, "Failed to get access token: HTTP ${connection.responseCode}")
                val error = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "Error response: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token: ${e.message}", e)
        }
        null
    }

    /**
     * Make a request to the IGDB API.
     */
    private fun makeIgdbRequest(endpoint: String, body: String, token: String): JSONArray? {
        try {
            val url = URL("$IGDB_API_URL/$endpoint")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Client-ID", clientId)
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "text/plain")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            connection.outputStream.use { output ->
                output.write(body.toByteArray())
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                return JSONArray(response)
            } else {
                Log.e(TAG, "IGDB API error: HTTP ${connection.responseCode}")
                val error = connection.errorStream?.bufferedReader()?.readText()
                Log.e(TAG, "Error response: $error")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error making IGDB request: ${e.message}", e)
        }
        return null
    }

    /**
     * Parse IGDB response into GameMetadata.
     */
    private fun parseIgdbResponse(response: JSONArray, originalName: String): MetadataResult {
        if (response.length() == 0) {
            return MetadataResult.NotFound(originalName)
        }

        // Find best match - must be a good match, don't accept arbitrary results
        var bestMatch: JSONObject? = null
        val cleanOriginal = cleanGameName(originalName).lowercase()

        // Extract core words from original name for matching (words with 3+ chars)
        val originalWords = cleanOriginal.split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()

        for (i in 0 until response.length()) {
            val game = response.getJSONObject(i)
            val gameName = game.optString("name", "").lowercase()
            val cleanGameName = cleanGameName(gameName).lowercase()

            // Exact match or one contains the other
            if (cleanGameName == cleanOriginal ||
                cleanGameName.contains(cleanOriginal) ||
                cleanOriginal.contains(cleanGameName)) {
                bestMatch = game
                Log.d(TAG, "Found exact/substring match: '$gameName' for '$originalName'")
                break
            }

            // Check for prefix match (e.g., "Subway Surf" matches "Subway Surfers")
            // This handles cases where one name is a truncated version of the other
            val cleanOriginalNoSpaces = cleanOriginal.replace(" ", "")
            val cleanGameNoSpaces = cleanGameName.replace(" ", "")
            if (cleanOriginalNoSpaces.length >= 5 && cleanGameNoSpaces.length >= 5) {
                if (cleanGameNoSpaces.startsWith(cleanOriginalNoSpaces) ||
                    cleanOriginalNoSpaces.startsWith(cleanGameNoSpaces)) {
                    bestMatch = game
                    Log.d(TAG, "Found prefix match: '$gameName' for '$originalName'")
                    break
                }
            }

            // Check if significant words match (at least half of the words from shorter name)
            val gameWords = cleanGameName.split(Regex("\\s+"))
                .filter { it.length >= 3 }
                .toSet()

            val commonWords = originalWords.intersect(gameWords)
            val minWordCount = minOf(originalWords.size, gameWords.size).coerceAtLeast(1)

            if (commonWords.size >= (minWordCount + 1) / 2 && commonWords.isNotEmpty()) {
                bestMatch = game
                Log.d(TAG, "Found word-based match: '$gameName' for '$originalName' (common words: $commonWords)")
                break
            }

            // Check for word prefix matches (e.g., "surf" matches "surfers")
            val hasWordPrefixMatch = originalWords.any { origWord ->
                gameWords.any { gameWord ->
                    (origWord.length >= 4 && gameWord.startsWith(origWord)) ||
                    (gameWord.length >= 4 && origWord.startsWith(gameWord))
                }
            }
            if (hasWordPrefixMatch && commonWords.isNotEmpty()) {
                bestMatch = game
                Log.d(TAG, "Found word-prefix match: '$gameName' for '$originalName'")
                break
            }
        }

        // If no good match found, return NotFound instead of accepting an unrelated game
        if (bestMatch == null) {
            Log.d(TAG, "No good match found for '$originalName'. IGDB returned ${response.length()} results but none matched.")
            return MetadataResult.NotFound(originalName)
        }

        return try {
            val name = bestMatch.optString("name", originalName)
            val summary = bestMatch.optString("summary", "").takeIf { it.isNotBlank() }

            Log.d(TAG, "Found metadata for '$originalName': name='$name', summary length=${summary?.length ?: 0}")
            val rating = bestMatch.optDouble("rating", -1.0).let {
                if (it >= 0) (it / 100).toFloat() else null // IGDB uses 0-100, ES-DE uses 0-1
            }

            // Parse release date (Unix timestamp)
            val releaseTimestamp = bestMatch.optLong("first_release_date", 0)
            val releaseDate = if (releaseTimestamp > 0) {
                val date = java.util.Date(releaseTimestamp * 1000)
                java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US).format(date)
            } else null

            // Parse genres
            val genres = bestMatch.optJSONArray("genres")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name", null) }
            }?.joinToString(", ")

            // Parse developers and publishers
            var developer: String? = null
            var publisher: String? = null
            bestMatch.optJSONArray("involved_companies")?.let { companies ->
                for (i in 0 until companies.length()) {
                    val company = companies.getJSONObject(i)
                    val companyName = company.optJSONObject("company")?.optString("name")
                    if (company.optBoolean("developer", false) && developer == null) {
                        developer = companyName
                    }
                    if (company.optBoolean("publisher", false) && publisher == null) {
                        publisher = companyName
                    }
                }
            }

            // Parse cover URL
            val coverUrl = bestMatch.optJSONObject("cover")?.optString("url")

            // Parse screenshot URLs
            val screenshotUrls = bestMatch.optJSONArray("screenshots")?.let { arr ->
                (0 until arr.length()).mapNotNull {
                    arr.getJSONObject(it).optString("url", null)
                }
            }

            // Parse players from game modes
            val gameModes = bestMatch.optJSONArray("game_modes")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.getJSONObject(it).optString("name", null) }
            }
            val players = when {
                gameModes?.contains("Multiplayer") == true -> "1-4"
                gameModes?.contains("Single player") == true -> "1"
                else -> null
            }

            // Parse video ID (IGDB uses YouTube video IDs)
            val videosArray = bestMatch.optJSONArray("videos")
            Log.d(TAG, "Videos array for '$name': ${videosArray?.toString() ?: "null"} (length: ${videosArray?.length() ?: 0})")
            val videoId = videosArray?.let { arr ->
                if (arr.length() > 0) {
                    val firstVideo = arr.getJSONObject(0)
                    val id = firstVideo.optString("video_id", null)
                    Log.d(TAG, "First video object: $firstVideo, video_id: $id")
                    id
                } else null
            }
            Log.d(TAG, "Parsed video ID for '$name': $videoId")

            val metadata = GameMetadata(
                path = "", // Will be set by caller
                name = name,
                desc = summary,
                rating = rating,
                releasedate = releaseDate,
                developer = developer,
                publisher = publisher,
                genre = genres,
                players = players,
                image = coverUrl, // Store URL temporarily, will be converted to local path after download
                screenshotUrls = screenshotUrls, // Store screenshot URLs for downloading
                videoUrl = videoId // Store YouTube video ID for downloading
            )

            MetadataResult.Success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing IGDB response: ${e.message}", e)
            MetadataResult.Error("Failed to parse IGDB response: ${e.message}")
        }
    }

    /**
     * Clean up game name for better search results.
     */
    private fun cleanGameName(name: String): String {
        return name
            .replace(Regex("\\s*\\([^)]*\\)"), "") // Remove parenthetical content
            .replace(Regex("\\s*-\\s*[^-]*Edition.*", RegexOption.IGNORE_CASE), "") // Remove edition info
            .replace(Regex("\\s*:\\s*[^:]*$"), "") // Remove subtitle after colon
            .replace(Regex("[™®©]"), "") // Remove trademark symbols
            .trim()
    }

    /**
     * Save credentials to shared preferences.
     */
    private fun saveCredentials() {
        val prefs = context.getSharedPreferences("igdb_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("client_id", clientId)
            .putString("client_secret", clientSecret)
            .apply()
    }

    /**
     * Load credentials from shared preferences.
     */
    private fun loadCredentials() {
        if (clientId != null && clientSecret != null) return

        val prefs = context.getSharedPreferences("igdb_prefs", Context.MODE_PRIVATE)
        clientId = prefs.getString("client_id", null)
        clientSecret = prefs.getString("client_secret", null)
    }

    /**
     * Clear stored credentials.
     */
    fun clearCredentials() {
        clientId = null
        clientSecret = null
        accessToken = null
        tokenExpiry = 0

        val prefs = context.getSharedPreferences("igdb_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
