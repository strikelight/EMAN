package com.esde.emulatormanager.data.service

import android.content.Context
import android.util.Log
import com.esde.emulatormanager.data.model.VitaSearchResult
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * Service for the ScreenScraper API v2.
 * ScreenScraper provides game metadata, artwork, and video clips.
 * PS Vita system ID: 62
 *
 * Anonymous access: ~20 req/day
 * With credentials: higher limits (free account at screenscraper.fr)
 */
@Singleton
class ScreenScraperService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScreenScraperService"
        // jeuInfos.php — fetch full details by gameid (or by ROM hash/name)
        private const val GAME_INFO_URL = "https://www.screenscraper.fr/api2/jeuInfos.php"
        // jeuRecherche.php — search by game name, returns up to 30 results ranked by probability
        private const val SEARCH_URL = "https://www.screenscraper.fr/api2/jeuRecherche.php"
        private const val VITA_SYSTEM_ID = 62
        private const val DEV_ID = "EMAN"
        private const val DEV_PASS = ""
        private const val SOFT_NAME = "EMAN"
        private const val CONNECT_TIMEOUT = 15000
        private const val READ_TIMEOUT = 30000
        private const val VIDEO_READ_TIMEOUT = 120000
    }

    private val prefs = context.getSharedPreferences("screenscraper_prefs", Context.MODE_PRIVATE)
    private val PREF_USERNAME = "ss_username"
    private val PREF_PASSWORD = "ss_password"

    // ---- User account credentials (optional, increases rate limits) ----
    // DEV_ID / DEV_PASS are hardcoded — register at screenscraper.fr to get a devpassword.

    fun setCredentials(username: String, password: String) {
        prefs.edit()
            .putString(PREF_USERNAME, username.trim())
            .putString(PREF_PASSWORD, password.trim())
            .apply()
    }

    /** True when a ScreenScraper user account has been entered (higher rate limits). */
    fun hasCredentials(): Boolean =
        !prefs.getString(PREF_USERNAME, null).isNullOrBlank()

    fun getUsername(): String? =
        prefs.getString(PREF_USERNAME, null)?.takeIf { it.isNotBlank() }

    fun clearCredentials() {
        prefs.edit().remove(PREF_USERNAME).remove(PREF_PASSWORD).apply()
    }

    /**
     * Search for a PS Vita game by name using jeuRecherche.php.
     * Returns up to 30 results ranked by match probability.
     * Each result has the game ID needed to fetch full details.
     */
    suspend fun searchVitaGame(name: String): List<VitaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val responseJson = callSearch(name) ?: return@withContext emptyList()
            val jeuxArray = responseJson.optJSONArray("jeux") ?: return@withContext emptyList()

            val results = mutableListOf<VitaSearchResult>()
            for (i in 0 until jeuxArray.length()) {
                val jeu = jeuxArray.optJSONObject(i) ?: continue
                val ssId = jeu.optInt("id", -1)
                if (ssId == -1) continue

                // Names can be a string or array of {region, text} objects
                val gameName = when {
                    jeu.has("noms") -> extractPreferredText(jeu.optJSONArray("noms"), "region", "wor", "us")
                        ?: jeu.optString("nom", "").takeIf { it.isNotBlank() }
                    jeu.has("nom") -> jeu.optString("nom", "").takeIf { it.isNotBlank() }
                    else -> null
                } ?: continue

                val year = when {
                    jeu.has("dates") -> extractPreferredText(jeu.optJSONArray("dates"), "region", "wor", "us")?.take(4)
                    jeu.has("date") -> jeu.optString("date", "").takeIf { it.isNotBlank() }?.take(4)
                    else -> null
                }

                results.add(VitaSearchResult(ssId = ssId.toString(), name = gameName, year = year))
            }
            results
        } catch (e: ScreenScraperApiException) {
            throw e  // Let API errors propagate so callers can surface the message
        } catch (e: Exception) {
            Log.e(TAG, "Error searching ScreenScraper: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get full game details by ScreenScraper game ID.
     */
    suspend fun getVitaGameDetails(ssId: String): VitaGameDetails? = withContext(Dispatchers.IO) {
        // Note: callApiById can throw ScreenScraperApiException — let it propagate
        try {
            val responseJson = callApiById(ssId) ?: return@withContext null
            val jeu = responseJson.optJSONObject("jeu") ?: return@withContext null

            val name = extractPreferredText(jeu.optJSONArray("noms"), "region", "wor", "us")
                ?: jeu.optString("nom", "").takeIf { it.isNotBlank() }
                ?: return@withContext null

            val desc = extractPreferredText(jeu.optJSONArray("synopsis"), "langue", "en", "en")

            val ratingRaw = jeu.optJSONObject("note")?.optString("text")?.toFloatOrNull()
            val rating = ratingRaw?.div(20.0f)?.coerceIn(0.0f, 1.0f)

            val developer = jeu.optJSONObject("developpeur")?.optString("text")?.takeIf { it.isNotBlank() }
            val publisher = jeu.optJSONObject("editeur")?.optString("text")?.takeIf { it.isNotBlank() }

            val genre = try {
                jeu.optJSONArray("genres")
                    ?.optJSONObject(0)
                    ?.optJSONArray("noms")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.takeIf { it.isNotBlank() }
            } catch (e: Exception) { null }

            val dateRaw = extractPreferredText(jeu.optJSONArray("dates"), "region", "wor", "us")
            val releaseDate = dateRaw?.let { convertDate(it) }

            val medias = jeu.optJSONArray("medias")
            val coverUrl = extractMediaUrl(medias, "box-2D")
            val screenshotUrl = extractMediaUrl(medias, "ss")
            val videoUrl = extractMediaUrl(medias, "video-normalized")
                ?: extractMediaUrl(medias, "video")

            VitaGameDetails(
                ssId = ssId,
                name = name,
                desc = desc,
                rating = rating,
                developer = developer,
                publisher = publisher,
                genre = genre,
                releaseDate = releaseDate,
                coverUrl = coverUrl,
                screenshotUrl = screenshotUrl,
                videoUrl = videoUrl
            )
        } catch (e: ScreenScraperApiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ScreenScraper game details: ${e.message}", e)
            null
        }
    }

    /**
     * Search by name and return full details.
     * Uses jeuRecherche.php to find the best match, then jeuInfos.php by gameid for full details.
     * This is the correct approach for name-based lookups without a ROM file hash.
     */
    suspend fun getVitaGameDetailsByName(name: String): VitaGameDetails? = withContext(Dispatchers.IO) {
        try {
            // Step 1: Search by name to get the game ID
            val searchResponse = callSearch(name) ?: run {
                Log.w(TAG, "No search response for: $name")
                return@withContext null
            }
            val jeuxArray = searchResponse.optJSONArray("jeux")
            if (jeuxArray == null || jeuxArray.length() == 0) {
                Log.w(TAG, "No search results for: $name")
                return@withContext null
            }

            // Take the best match (first result, ranked by probability)
            val firstResult = jeuxArray.optJSONObject(0) ?: return@withContext null
            val gameId = firstResult.optInt("id", -1)
            if (gameId == -1) return@withContext null

            Log.d(TAG, "Found game ID $gameId for '$name', fetching full details")

            // Step 2: Fetch full details by game ID
            getVitaGameDetails(gameId.toString())
        } catch (e: ScreenScraperApiException) {
            throw e  // Surface rate-limit / auth errors to the caller
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ScreenScraper details by name: ${e.message}", e)
            null
        }
    }

    /**
     * Download a media file (cover, screenshot, or video) from a URL to a local file.
     */
    suspend fun downloadMedia(url: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            if (!destFile.parentFile?.exists()!!) {
                destFile.parentFile?.mkdirs()
            }

            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = if (url.contains(".mp4") || url.contains("video")) {
                VIDEO_READ_TIMEOUT
            } else {
                READ_TIMEOUT
            }
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val contentLength = connection.contentLength
                // Skip videos over 50MB
                if (contentLength > 50 * 1024 * 1024) {
                    Log.w(TAG, "Skipping download — file too large (${contentLength / 1024 / 1024}MB): $url")
                    return@withContext false
                }

                connection.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }

                Log.d(TAG, "Downloaded media to: ${destFile.absolutePath}")
                true
            } else {
                Log.e(TAG, "Failed to download media: HTTP ${connection.responseCode} — $url")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading media from $url: ${e.message}", e)
            false
        } finally {
            connection?.disconnect()
        }
    }

    // ==================== Private helpers ====================

    /** Append common auth params to a URL builder. */
    private fun appendAuthParams(sb: StringBuilder) {
        sb.append("&devid=").append(URLEncoder.encode(DEV_ID, "UTF-8"))
        sb.append("&devpassword=").append(URLEncoder.encode(DEV_PASS, "UTF-8"))
        sb.append("&softname=").append(SOFT_NAME)
        sb.append("&output=json")
        val username = prefs.getString(PREF_USERNAME, null)
        val password = prefs.getString(PREF_PASSWORD, null)
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            sb.append("&ssid=").append(URLEncoder.encode(username, "UTF-8"))
            sb.append("&sspassword=").append(URLEncoder.encode(password, "UTF-8"))
        }
    }

    /**
     * Build a jeuRecherche.php URL for name-based search.
     * Uses `recherche` parameter — NOT `romnom`.
     */
    private fun buildSearchUrl(gameName: String): String {
        val sb = StringBuilder(SEARCH_URL)
        sb.append("?systemeid=").append(VITA_SYSTEM_ID)
        sb.append("&recherche=").append(URLEncoder.encode(gameName, "UTF-8"))
        appendAuthParams(sb)
        return sb.toString()
    }

    /**
     * Build a jeuInfos.php URL for fetching full details by game ID.
     */
    private fun buildGameInfoUrl(gameId: String): String {
        val sb = StringBuilder(GAME_INFO_URL)
        sb.append("?systemeid=").append(VITA_SYSTEM_ID)
        sb.append("&gameid=").append(gameId)
        appendAuthParams(sb)
        return sb.toString()
    }

    /** Call jeuRecherche.php — returns response JSON containing jeux[] array. */
    private fun callSearch(gameName: String): JSONObject? {
        val url = buildSearchUrl(gameName)
        return makeRequest(url)
    }

    /** Call jeuInfos.php by game ID — returns response JSON containing jeu object. */
    private fun callApiById(gameId: String): JSONObject? {
        val url = buildGameInfoUrl(gameId)
        return makeRequest(url)
    }

    /**
     * Makes an HTTP request and returns the parsed `response` JSON object.
     * Returns null only when the game genuinely isn't found (200 with no data).
     * Throws [ScreenScraperApiException] for HTTP errors (rate limiting, auth, etc.)
     * so callers can surface a meaningful message to the user.
     */
    private fun makeRequest(urlString: String): JSONObject? {
        var connection: HttpURLConnection? = null
        return try {
            Log.d(TAG, "ScreenScraper request: $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("User-Agent", "EMAN/1.0")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            Log.d(TAG, "ScreenScraper response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().readText()

                // ScreenScraper sometimes returns plain-text errors with HTTP 200
                // (e.g. unregistered devid, API key issues). Catch JSON parse failures.
                val json = try {
                    JSONObject(responseText)
                } catch (e: Exception) {
                    val snippet = responseText.take(300)
                    Log.w(TAG, "ScreenScraper returned non-JSON (200): $snippet")
                    throw ScreenScraperApiException("ScreenScraper error: $snippet")
                }

                val response = json.optJSONObject("response")

                // Check for server-side error embedded in a 200 response
                val serverError = response?.optJSONObject("serveur")?.optString("erreur")
                if (!serverError.isNullOrBlank()) {
                    Log.w(TAG, "ScreenScraper server error: $serverError")
                    throw ScreenScraperApiException(serverError)
                }

                response
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText()?.take(300) ?: ""
                Log.w(TAG, "ScreenScraper HTTP $responseCode: $errorBody")
                val message = when (responseCode) {
                    429, 430 -> "ScreenScraper daily scrape limit reached (devid). Add your ScreenScraper account credentials for a higher limit."
                    431 -> "Your ScreenScraper account has reached its daily scrape limit. Try again tomorrow."
                    401 -> "ScreenScraper credentials are invalid. Check your username and password in Settings."
                    400 -> "ScreenScraper rejected the request (400). $errorBody"
                    else -> "ScreenScraper returned HTTP $responseCode. $errorBody"
                }
                throw ScreenScraperApiException(message)
            }
        } catch (e: ScreenScraperApiException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "ScreenScraper request failed: ${e.message}", e)
            throw ScreenScraperApiException("Network error connecting to ScreenScraper: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Extract text from a JSON array preferring a specific region/langue value.
     * Falls back to first item if preferred region not found.
     */
    private fun extractPreferredText(
        array: org.json.JSONArray?,
        keyName: String,
        vararg preferredValues: String
    ): String? {
        if (array == null || array.length() == 0) return null

        for (preferred in preferredValues) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optString(keyName) == preferred) {
                    val text = obj.optString("text", "").takeIf { it.isNotBlank() }
                    if (text != null) return text
                }
            }
        }

        // Fall back to first non-blank entry
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val text = obj.optString("text", "").takeIf { it.isNotBlank() }
            if (text != null) return text
        }

        return null
    }

    /**
     * Extract a media URL from the medias array by type.
     * Prefers "wor" region, then any region.
     */
    private fun extractMediaUrl(
        medias: org.json.JSONArray?,
        type: String
    ): String? {
        if (medias == null || medias.length() == 0) return null

        // First pass: find type + wor region
        for (i in 0 until medias.length()) {
            val media = medias.optJSONObject(i) ?: continue
            if (media.optString("type") == type && media.optString("region") == "wor") {
                val url = media.optString("url", "").takeIf { it.isNotBlank() }
                if (url != null) return url
            }
        }

        // Second pass: find type with any region
        for (i in 0 until medias.length()) {
            val media = medias.optJSONObject(i) ?: continue
            if (media.optString("type") == type) {
                val url = media.optString("url", "").takeIf { it.isNotBlank() }
                if (url != null) return url
            }
        }

        return null
    }

    /**
     * Convert a date string from ScreenScraper format (YYYY-MM-DD or YYYY) to ES-DE format (YYYYMMDDTHHMMSS).
     */
    private fun convertDate(dateStr: String): String {
        return try {
            when {
                dateStr.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                    val parts = dateStr.split("-")
                    "${parts[0]}${parts[1]}${parts[2]}T000000"
                }
                dateStr.matches(Regex("\\d{4}")) -> {
                    "${dateStr}0101T000000"
                }
                else -> dateStr
            }
        } catch (e: Exception) {
            dateStr
        }
    }
}

/**
 * Thrown when ScreenScraper API returns an error (rate limit, auth failure, network error).
 * Used to surface a human-readable message to the user instead of a generic "no metadata found".
 */
class ScreenScraperApiException(message: String) : Exception(message)

/**
 * Full game details returned by ScreenScraper
 */
data class VitaGameDetails(
    val ssId: String,
    val name: String,
    val desc: String? = null,
    val rating: Float? = null,           // normalized 0.0–1.0 (SS returns 0–20)
    val developer: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,     // YYYYMMDDTHHMMSS format
    val coverUrl: String? = null,        // box-2D media URL
    val screenshotUrl: String? = null,   // ss media URL
    val videoUrl: String? = null         // video-normalized or video URL
)
