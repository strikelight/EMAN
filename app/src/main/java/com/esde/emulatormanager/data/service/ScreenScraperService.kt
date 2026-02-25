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
        private const val BASE_URL = "https://www.screenscraper.fr/api2/jeuInfos.php"
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

    fun setCredentials(username: String, password: String) {
        prefs.edit()
            .putString(PREF_USERNAME, username.trim())
            .putString(PREF_PASSWORD, password.trim())
            .apply()
    }

    fun hasCredentials(): Boolean {
        return !prefs.getString(PREF_USERNAME, null).isNullOrBlank()
    }

    fun getUsername(): String? {
        return prefs.getString(PREF_USERNAME, null)?.takeIf { it.isNotBlank() }
    }

    fun clearCredentials() {
        prefs.edit().remove(PREF_USERNAME).remove(PREF_PASSWORD).apply()
    }

    /**
     * Search for a PS Vita game by name.
     * ScreenScraper jeuInfos returns the single best match, not a list.
     * Returns a list of 0 or 1 result.
     */
    suspend fun searchVitaGame(name: String): List<VitaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val responseJson = callApi(name) ?: return@withContext emptyList()
            val jeu = responseJson.optJSONObject("jeu") ?: return@withContext emptyList()

            val ssId = jeu.optInt("id", -1).toString()
            if (ssId == "-1") return@withContext emptyList()

            val gameName = extractPreferredText(jeu.optJSONArray("noms"), "region", "wor", "us")
                ?: jeu.optString("nom", "").takeIf { it.isNotBlank() }
                ?: return@withContext emptyList()

            val year = extractPreferredText(jeu.optJSONArray("dates"), "region", "wor", "us")
                ?.take(4)

            val coverUrl = extractMediaUrl(jeu.optJSONArray("medias"), "box-2D")

            listOf(VitaSearchResult(ssId = ssId, name = gameName, year = year, coverUrl = coverUrl))
        } catch (e: Exception) {
            Log.e(TAG, "Error searching ScreenScraper: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Get full game details by ScreenScraper game ID.
     */
    suspend fun getVitaGameDetails(ssId: String): VitaGameDetails? = withContext(Dispatchers.IO) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error getting ScreenScraper game details: ${e.message}", e)
            null
        }
    }

    /**
     * Search by name and return full details in one call.
     * Used when scraping metadata without prior search.
     */
    suspend fun getVitaGameDetailsByName(name: String): VitaGameDetails? = withContext(Dispatchers.IO) {
        try {
            val responseJson = callApi(name) ?: return@withContext null
            val jeu = responseJson.optJSONObject("jeu") ?: return@withContext null

            val ssId = jeu.optInt("id", -1).toString()
            val gameName = extractPreferredText(jeu.optJSONArray("noms"), "region", "wor", "us")
                ?: name

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
                name = gameName,
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

    private fun buildApiUrl(gameName: String? = null, gameId: String? = null): String {
        val sb = StringBuilder(BASE_URL)
        sb.append("?devid=").append(DEV_ID)
        sb.append("&devpassword=").append(DEV_PASS)
        sb.append("&softname=").append(SOFT_NAME)
        sb.append("&output=json")
        sb.append("&systemeid=").append(VITA_SYSTEM_ID)

        if (gameName != null) {
            sb.append("&romnom=").append(URLEncoder.encode(gameName, "UTF-8"))
        }
        if (gameId != null) {
            sb.append("&crc=&md5=&sha1=&romtaille=&gameid=").append(gameId)
        }

        val username = prefs.getString(PREF_USERNAME, null)
        val password = prefs.getString(PREF_PASSWORD, null)
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            sb.append("&ssid=").append(URLEncoder.encode(username, "UTF-8"))
            sb.append("&sspassword=").append(URLEncoder.encode(password, "UTF-8"))
        }

        return sb.toString()
    }

    private fun callApi(gameName: String): JSONObject? {
        val url = buildApiUrl(gameName = gameName)
        return makeRequest(url)
    }

    private fun callApiById(gameId: String): JSONObject? {
        val url = buildApiUrl(gameId = gameId)
        return makeRequest(url)
    }

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
                val json = JSONObject(responseText)
                json.optJSONObject("response")
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: ""
                Log.w(TAG, "ScreenScraper error $responseCode: $errorText")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ScreenScraper request failed: ${e.message}", e)
            null
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
