package com.esde.emulatormanager.data.service

import android.util.Log
import com.esde.emulatormanager.data.model.GameMetadata
import com.esde.emulatormanager.data.model.GogGame
import com.esde.emulatormanager.data.model.MetadataResult
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
 * Service for fetching game metadata from GOG.com API.
 * Uses the public GOG products API to get game information by product ID.
 */
@Singleton
class GogApiService @Inject constructor() {
    companion object {
        private const val TAG = "GogApiService"

        // GOG API endpoints
        private const val GOG_PRODUCTS_URL = "https://api.gog.com/products"
        private const val GOG_CATALOG_URL = "https://catalog.gog.com/v1/catalog"
        private const val MAX_SEARCH_RESULTS = 50
    }

    // Track last error for UI feedback
    var lastError: String? = null
        private set

    /**
     * Search for GOG games by name.
     * Returns null if there was an error.
     */
    suspend fun searchGames(query: String): List<GogGame>? = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        lastError = null
        Log.d(TAG, "Searching GOG for: $query")

        var connection: HttpURLConnection? = null
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "$GOG_CATALOG_URL?query=$encodedQuery&limit=$MAX_SEARCH_RESULTS"
            Log.d(TAG, "Connecting to: $searchUrl")

            val url = URL(searchUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
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
            Log.e(TAG, "Error searching GOG", e)
            lastError = errorMsg
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse the JSON response from GOG catalog API.
     * The catalog API returns products with "id" as a string and "title" for the name.
     */
    private fun parseSearchResults(json: String): List<GogGame> {
        val games = mutableListOf<GogGame>()
        try {
            val jsonObject = JSONObject(json)
            val products = jsonObject.optJSONArray("products") ?: return emptyList()

            for (i in 0 until products.length()) {
                val product = products.getJSONObject(i)
                // GOG catalog API returns id as string, title for the name
                val idStr = product.optString("id", "")
                val title = product.optString("title", "")

                // Only include games (not DLC, extras, etc.)
                val productType = product.optString("productType", "")

                val id = idStr.toLongOrNull() ?: -1
                if (id > 0 && title.isNotBlank() && (productType == "game" || productType == "pack")) {
                    games.add(GogGame(productId = id, name = title))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing search results", e)
        }
        return games
    }

    /**
     * Get metadata for a GOG game by product ID.
     *
     * @param productId GOG product ID
     * @param gamePath The relative path to the game file (e.g., "./Game Namezst")
     * @return MetadataResult with the game metadata or error
     */
    suspend fun getGameMetadata(productId: Long, gamePath: String): MetadataResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val detailsUrl = "$GOG_PRODUCTS_URL/$productId?expand=description,screenshots,videos"
            Log.d(TAG, "Fetching GOG metadata for productId: $productId")

            val url = URL(detailsUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                return@withContext parseGogMetadata(jsonObject, gamePath, productId)
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext MetadataResult.NotFound("GOG Product ID: $productId")
            } else {
                return@withContext MetadataResult.Error("HTTP error: $responseCode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting GOG metadata for $productId", e)
            return@withContext MetadataResult.Error("Failed to fetch GOG metadata: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse metadata from GOG API response.
     */
    private fun parseGogMetadata(data: JSONObject, gamePath: String, productId: Long): MetadataResult {
        try {
            val title = data.optString("title", "")
            if (title.isBlank()) {
                return MetadataResult.Error("Game title not found in GOG data")
            }

            // Parse description
            val descObj = data.optJSONObject("description")
            val leadDesc = descObj?.optString("lead", "") ?: ""
            val fullDesc = descObj?.optString("full", "") ?: ""
            // Use lead description if available, otherwise use truncated full description
            val description = if (leadDesc.isNotBlank()) {
                // Strip HTML tags
                leadDesc.replace(Regex("<[^>]*>"), "").trim()
            } else {
                fullDesc.replace(Regex("<[^>]*>"), "").trim().take(500)
            }

            // Parse release date (format: "2020-09-03T19:50:00+0300")
            val releaseDateStr = data.optString("release_date", "")
            val releaseDate = parseReleaseDateToEsdeFormat(releaseDateStr)

            // Parse developer and publisher from links
            val linksObj = data.optJSONObject("links")
            val developer = data.optJSONObject("_embedded")
                ?.optJSONObject("developer")
                ?.optString("name")?.takeIf { it.isNotEmpty() }
            val publisher = data.optJSONObject("_embedded")
                ?.optJSONObject("publisher")
                ?.optString("name")?.takeIf { it.isNotEmpty() }

            // Get genres from content_system_compatibility or tags
            val genres: String? = null // GOG API doesn't provide genres in this endpoint easily

            // Get cover image URL
            val imagesObj = data.optJSONObject("images")
            val coverUrl = imagesObj?.optString("logo2x")?.takeIf { it.isNotEmpty() }
                ?: imagesObj?.optString("logo")?.takeIf { it.isNotEmpty() }

            // Parse screenshot URLs
            val screenshotsArray = data.optJSONArray("screenshots")
            val screenshotUrls = screenshotsArray?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val screenshot = arr.optJSONObject(i)
                    // Get the largest format available
                    val formatterUrls = screenshot?.optJSONArray("formatted_images")
                    formatterUrls?.let { formats ->
                        // Find the largest format (usually last one)
                        if (formats.length() > 0) {
                            formats.optJSONObject(formats.length() - 1)?.optString("image_url")?.takeIf { it.isNotEmpty() }
                        } else null
                    }
                }
            }

            // Parse video URLs (GOG provides YouTube embed URLs)
            val videosArray = data.optJSONArray("videos")
            val videoUrl = videosArray?.let { arr ->
                if (arr.length() > 0) {
                    val video = arr.getJSONObject(0)
                    video.optString("video_url").takeIf { it.isNotEmpty() }
                } else null
            }

            val metadata = GameMetadata(
                path = gamePath,
                name = title,
                desc = description.ifBlank { null },
                rating = null, // GOG doesn't provide ratings in the API
                releasedate = releaseDate,
                developer = developer,
                publisher = publisher,
                genre = genres,
                players = null,
                image = coverUrl, // Store URL temporarily for download
                screenshotUrls = screenshotUrls,
                videoUrl = videoUrl // Note: GOG videos are YouTube URLs
            )

            Log.d(TAG, "Parsed GOG metadata for: $title (${screenshotUrls?.size ?: 0} screenshots)")
            return MetadataResult.Success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing GOG metadata", e)
            return MetadataResult.Error("Failed to parse GOG metadata: ${e.message}")
        }
    }

    /**
     * Parse GOG release date string to ES-DE format (YYYYMMDDTHHMMSS).
     * GOG dates are in ISO format like "2020-09-03T19:50:00+0300"
     */
    private fun parseReleaseDateToEsdeFormat(dateStr: String): String? {
        if (dateStr.isBlank()) return null

        try {
            // Extract date portion before timezone
            val dateOnly = dateStr.substringBefore("+").substringBefore("-", dateStr.take(10))

            // Try to parse ISO format
            val formats = listOf(
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US),
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            )

            for (format in formats) {
                try {
                    val parseStr = if (dateStr.contains("T")) dateStr.substringBefore("+").substringBeforeLast("-") else dateStr.take(10)
                    val date = format.parse(parseStr)
                    if (date != null) {
                        return java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.US).format(date)
                    }
                } catch (e: Exception) {
                    // Try next format
                }
            }

            // If parsing fails, try to extract just the year
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
     * Download cover artwork for a GOG game.
     *
     * @param imageUrl The GOG image URL
     * @param mediaDir The ES-DE downloaded_media/windows/covers directory
     * @param gameFileName The filename of the game (without extension)
     * @return The path to the downloaded image, or null if download failed
     */
    suspend fun downloadCoverArtwork(imageUrl: String, mediaDir: File, gameFileName: String): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Downloading GOG cover artwork from: $imageUrl")

        var connection: HttpURLConnection? = null
        try {
            // Ensure output directory exists
            if (!mediaDir.exists()) {
                mediaDir.mkdirs()
            }

            // GOG URLs might need https prefix
            val fullUrl = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl

            val url = URL(fullUrl)
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
                    imageUrl.contains(".png") -> ".png"
                    else -> ".jpg"
                }

                val outputFile = File(mediaDir, "$gameFileName$extension")
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d(TAG, "Downloaded GOG artwork to: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            } else {
                Log.e(TAG, "Failed to download GOG artwork: HTTP $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading GOG artwork", e)
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Download screenshots for a GOG game.
     *
     * @param screenshotUrls List of screenshot URLs from GOG API
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

        Log.d(TAG, "Downloading GOG screenshots for: $gameFileName (${screenshotUrls.size} available)")

        // Ensure directory exists
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }

        var firstScreenshotPath: String? = null
        val maxScreenshots = minOf(5, screenshotUrls.size)

        for (i in 0 until maxScreenshots) {
            val imageUrl = screenshotUrls[i]
            try {
                // GOG URLs might need https prefix
                val fullUrl = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl

                // First screenshot has no suffix, others have _1, _2, etc.
                val suffix = if (i == 0) "" else "_$i"
                val outputFile = File(mediaDir, "$gameFileName$suffix.jpg")

                val downloaded = downloadImageToFile(fullUrl, outputFile)
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
     * Try to find a video for a GOG game by searching the Steam store for a matching app.
     * Since GOG videos are YouTube embeds (not downloadable), we use Steam CDN as a fallback.
     *
     * @param gameName The name of the game to search for
     * @param mediaDir The ES-DE downloaded_media/windows/videos directory
     * @param gameFileName The filename of the game (without extension)
     * @return The path to the downloaded video, or null if not found/downloadable
     */
    suspend fun downloadVideoViaSteam(
        gameName: String,
        mediaDir: File,
        gameFileName: String
    ): String? = withContext(Dispatchers.IO) {
        Log.d(TAG, "Trying to find Steam video for game: $gameName")

        // Search Steam store for a matching app by name
        val steamAppId = findSteamAppId(gameName) ?: return@withContext null

        // Try to get the movie ID from the Steam store API
        val movieId = getSteamMovieId(steamAppId) ?: return@withContext null

        // Download from Steam CDN
        val videoUrl = "https://cdn.cloudflare.steamstatic.com/steam/apps/$movieId/movie480.mp4"
        Log.d(TAG, "Downloading GOG game video from Steam CDN: $videoUrl")

        if (!mediaDir.exists()) mediaDir.mkdirs()
        val outputFile = File(mediaDir, "$gameFileName.mp4")

        var connection: HttpURLConnection? = null
        try {
            val url = URL(videoUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 120000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Downloaded Steam video for game to: ${outputFile.absolutePath}")
                return@withContext outputFile.absolutePath
            }
            Log.w(TAG, "Steam video HTTP ${connection.responseCode} for: $videoUrl")
            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Steam video for GOG game", e)
            outputFile.delete()
            return@withContext null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Search the Steam store for an app ID matching the given game name.
     * Uses the Steam search API.
     */
    private fun findSteamAppId(gameName: String): Int? {
        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(gameName, "UTF-8")
            val url = URL("https://store.steampowered.com/api/storesearch/?term=$encoded&l=english&cc=US")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            // Steam storesearch returns { "total": N, "items": [...] }
            val items = json.optJSONArray("items") ?: return null

            // Find the best match by name (case-insensitive exact match preferred)
            val normalizedQuery = gameName.lowercase().trim()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val name = item.optString("name", "").lowercase().trim()
                val appId = item.optInt("id", -1)
                if (appId > 0 && name == normalizedQuery) {
                    Log.d(TAG, "Found exact Steam match for '$gameName': appId=$appId")
                    return appId
                }
            }
            // Fall back to first result
            if (items.length() > 0) {
                val appId = items.optJSONObject(0)?.optInt("id", -1) ?: -1
                if (appId > 0) {
                    Log.d(TAG, "Using first Steam result for '$gameName': appId=$appId")
                    return appId
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Steam store for: $gameName", e)
            return null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Get the first movie/trailer ID for a Steam app.
     */
    private fun getSteamMovieId(appId: Int): Long? {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://store.steampowered.com/api/appdetails/?appids=$appId&filters=movies")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "EMAN/1.0")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)
            val appData = json.optJSONObject(appId.toString()) ?: return null
            if (!appData.optBoolean("success", false)) return null

            val data = appData.optJSONObject("data") ?: return null
            val movies = data.optJSONArray("movies") ?: return null
            if (movies.length() == 0) return null

            val movieId = movies.getJSONObject(0).optLong("id", -1)
            return if (movieId > 0) movieId else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Steam movie ID for app $appId", e)
            return null
        } finally {
            connection?.disconnect()
        }
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
}
