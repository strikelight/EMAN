package com.esde.emulatormanager.data.model

/**
 * Represents metadata for a game in ES-DE.
 * This maps to the fields in gamelist.xml
 */
data class GameMetadata(
    val path: String,                    // Required: relative path to the game file
    val name: String,                    // Display name
    val desc: String? = null,            // Description
    val rating: Float? = null,           // Rating 0.0-1.0
    val releasedate: String? = null,     // Format: YYYYMMDDTHHMMSS or YYYYMMDD
    val developer: String? = null,       // Developer name
    val publisher: String? = null,       // Publisher name
    val genre: String? = null,           // Genre(s)
    val players: String? = null,         // Number of players (e.g., "1", "1-4")
    val image: String? = null,           // Path to cover image
    val marquee: String? = null,         // Path to marquee/logo image
    val video: String? = null,           // Path to video preview
    val thumbnail: String? = null,       // Path to thumbnail
    val fanart: String? = null,          // Path to fanart/background
    val titlescreen: String? = null,     // Path to title screen
    val manual: String? = null,          // Path to manual PDF
    val favorite: Boolean = false,       // Marked as favorite
    val hidden: Boolean = false,         // Hidden from view
    val kidgame: Boolean = false,        // Kid-friendly game
    val playcount: Int = 0,              // Play count
    val lastplayed: String? = null,      // Last played date
    val sortname: String? = null,        // Sort name (for alphabetical sorting)
    val collectionsortname: String? = null, // Collection sort name
    val altemulator: String? = null,     // Alternative emulator to use
    // Temporary fields for IGDB/Steam URLs (not saved to gamelist.xml)
    val screenshotUrls: List<String>? = null,  // Screenshot URLs for downloading
    val videoUrl: String? = null               // Video URL for downloading (Steam MP4 or YouTube ID for IGDB)
)

/**
 * Result from metadata scraping
 */
sealed class MetadataResult {
    data class Success(val metadata: GameMetadata) : MetadataResult()
    data class NotFound(val gameName: String) : MetadataResult()
    data class Error(val message: String) : MetadataResult()
}

/**
 * Options controlling which media types are downloaded during a scrape operation.
 */
data class ScrapeOptions(
    val scrapeArtwork: Boolean = true,       // Cover art / screenshots
    val scrapeMetadata: Boolean = true,      // Text metadata (name, desc, genre, etc.)
    val scrapeVideos: Boolean = true         // Video previews
)

/**
 * Scraping progress for batch operations
 */
data class ScrapeProgress(
    val total: Int,
    val completed: Int,
    val successful: Int,
    val failed: Int,
    val currentGame: String? = null,
    val pendingUserInput: PendingMetadataSearch? = null,
    val lastResult: String? = null  // Debug info about last scrape attempt
) {
    val isComplete: Boolean get() = completed >= total && pendingUserInput == null
    val progressPercent: Float get() = if (total > 0) completed.toFloat() / total else 0f
}

/**
 * Represents a game that needs user input to refine the search.
 * References AndroidGame and AndroidTab from Models.kt
 */
data class PendingMetadataSearch(
    val game: AndroidGame,
    val originalSearchTerm: String,
    val tab: AndroidTab
)
