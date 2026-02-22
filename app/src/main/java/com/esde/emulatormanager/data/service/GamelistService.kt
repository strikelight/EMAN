package com.esde.emulatormanager.data.service

import android.util.Log
import android.util.Xml
import com.esde.emulatormanager.data.model.GameMetadata
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for reading and writing ES-DE gamelist.xml files.
 * ES-DE stores gamelist.xml files in ES-DE/gamelists/<system>/gamelist.xml
 */
@Singleton
class GamelistService @Inject constructor(
    private val esdeConfigService: EsdeConfigService
) {
    companion object {
        private const val TAG = "GamelistService"
        private const val GAMELIST_FILE = "gamelist.xml"
    }

    /**
     * Get the gamelist.xml file path for a system.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @return The gamelist.xml File, or null if ES-DE is not configured
     */
    private fun getGamelistFile(systemName: String): File? {
        val gamelistDir = esdeConfigService.getGamelistDirectory(systemName) ?: return null
        return File(gamelistDir, GAMELIST_FILE)
    }

    /**
     * Read all game entries from a gamelist.xml file.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @return Map of game path to metadata
     */
    fun readGamelist(systemName: String): Map<String, GameMetadata> {
        val gamelistFile = getGamelistFile(systemName)
        if (gamelistFile == null || !gamelistFile.exists()) {
            Log.d(TAG, "No gamelist.xml found for system: $systemName")
            return emptyMap()
        }

        val games = mutableMapOf<String, GameMetadata>()

        try {
            FileInputStream(gamelistFile).use { inputStream ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(inputStream, "UTF-8")

                var eventType = parser.eventType
                var currentGame: MutableMap<String, String>? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "game" -> currentGame = mutableMapOf()
                                else -> {
                                    if (currentGame != null && parser.name != "gameList") {
                                        val text = parser.nextText()
                                        if (text.isNotBlank()) {
                                            currentGame[parser.name] = text
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "game" && currentGame != null) {
                                val path = currentGame["path"]
                                if (path != null) {
                                    games[path] = parseGameMetadata(currentGame)
                                }
                                currentGame = null
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }
            Log.d(TAG, "Read ${games.size} games from gamelist for system: $systemName")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading gamelist.xml: ${e.message}", e)
        }

        return games
    }

    /**
     * Get metadata for a specific game.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param gamePath The relative path to the game file (e.g., "./Game Name.steam")
     * @return The game metadata, or null if not found
     */
    fun getGameMetadata(systemName: String, gamePath: String): GameMetadata? {
        val games = readGamelist(systemName)
        return games[gamePath] ?: games["./$gamePath"] ?: games[gamePath.removePrefix("./")]
    }

    /**
     * Check if a game has metadata in the gamelist.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param gamePath The relative path to the game file
     * @return True if the game has any useful metadata (description, rating, developer, publisher, genre, etc.)
     */
    fun hasMetadata(systemName: String, gamePath: String): Boolean {
        val metadata = getGameMetadata(systemName, gamePath) ?: return false
        // Consider it has metadata if any of these fields are populated
        return !metadata.desc.isNullOrBlank() ||
               metadata.rating != null ||
               !metadata.developer.isNullOrBlank() ||
               !metadata.publisher.isNullOrBlank() ||
               !metadata.genre.isNullOrBlank() ||
               !metadata.releasedate.isNullOrBlank() ||
               !metadata.image.isNullOrBlank()
    }

    /**
     * Write or update metadata for a single game.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param metadata The game metadata to write
     */
    fun writeGameMetadata(systemName: String, metadata: GameMetadata) {
        val games = readGamelist(systemName).toMutableMap()
        games[metadata.path] = metadata
        writeGamelist(systemName, games.values.toList())
    }

    /**
     * Write or update metadata for multiple games.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param metadataList List of game metadata to write
     */
    fun writeMultipleGameMetadata(systemName: String, metadataList: List<GameMetadata>) {
        val games = readGamelist(systemName).toMutableMap()
        metadataList.forEach { metadata ->
            games[metadata.path] = metadata
        }
        writeGamelist(systemName, games.values.toList())
    }

    /**
     * Write the complete gamelist.xml file.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param games List of all games to write
     */
    private fun writeGamelist(systemName: String, games: List<GameMetadata>) {
        val gamelistFile = getGamelistFile(systemName)
            ?: throw IllegalStateException("ES-DE gamelists directory not found")

        try {
            // Create backup of existing file
            if (gamelistFile.exists()) {
                val backupFile = File(gamelistFile.parent, "gamelist.xml.bak")
                gamelistFile.copyTo(backupFile, overwrite = true)
            }

            FileOutputStream(gamelistFile).use { outputStream ->
                val serializer = Xml.newSerializer()
                serializer.setOutput(outputStream, "UTF-8")
                serializer.startDocument("UTF-8", true)
                serializer.text("\n")
                serializer.startTag(null, "gameList")
                serializer.text("\n")

                for (game in games) {
                    writeGameEntry(serializer, game)
                }

                serializer.endTag(null, "gameList")
                serializer.endDocument()
            }

            Log.d(TAG, "Wrote ${games.size} games to gamelist for system: $systemName")
        } catch (e: Exception) {
            Log.e(TAG, "Error writing gamelist.xml: ${e.message}", e)
            throw e
        }
    }

    /**
     * Write a single game entry to the XML serializer.
     */
    private fun writeGameEntry(serializer: XmlSerializer, game: GameMetadata) {
        serializer.text("\t")
        serializer.startTag(null, "game")
        serializer.text("\n")

        // Required field
        writeElement(serializer, "path", game.path)
        writeElement(serializer, "name", game.name)

        // Optional fields - only write if not null/blank
        game.desc?.let { writeElement(serializer, "desc", it) }
        game.rating?.let { writeElement(serializer, "rating", it.toString()) }
        game.releasedate?.let { writeElement(serializer, "releasedate", it) }
        game.developer?.let { writeElement(serializer, "developer", it) }
        game.publisher?.let { writeElement(serializer, "publisher", it) }
        game.genre?.let { writeElement(serializer, "genre", it) }
        game.players?.let { writeElement(serializer, "players", it) }
        game.image?.let { writeElement(serializer, "image", it) }
        game.marquee?.let { writeElement(serializer, "marquee", it) }
        game.video?.let { writeElement(serializer, "video", it) }
        game.thumbnail?.let { writeElement(serializer, "thumbnail", it) }
        game.fanart?.let { writeElement(serializer, "fanart", it) }
        game.titlescreen?.let { writeElement(serializer, "titlescreen", it) }
        game.manual?.let { writeElement(serializer, "manual", it) }
        game.sortname?.let { writeElement(serializer, "sortname", it) }
        game.collectionsortname?.let { writeElement(serializer, "collectionsortname", it) }
        game.altemulator?.let { writeElement(serializer, "altemulator", it) }
        game.lastplayed?.let { writeElement(serializer, "lastplayed", it) }

        // Boolean fields - only write if true
        if (game.favorite) writeElement(serializer, "favorite", "true")
        if (game.hidden) writeElement(serializer, "hidden", "true")
        if (game.kidgame) writeElement(serializer, "kidgame", "true")
        if (game.playcount > 0) writeElement(serializer, "playcount", game.playcount.toString())

        serializer.text("\t")
        serializer.endTag(null, "game")
        serializer.text("\n")
    }

    /**
     * Write a single XML element.
     */
    private fun writeElement(serializer: XmlSerializer, name: String, value: String) {
        serializer.text("\t\t")
        serializer.startTag(null, name)
        serializer.text(value)
        serializer.endTag(null, name)
        serializer.text("\n")
    }

    /**
     * Parse a map of strings into GameMetadata.
     */
    private fun parseGameMetadata(data: Map<String, String>): GameMetadata {
        return GameMetadata(
            path = data["path"] ?: "",
            name = data["name"] ?: "",
            desc = data["desc"],
            rating = data["rating"]?.toFloatOrNull(),
            releasedate = data["releasedate"],
            developer = data["developer"],
            publisher = data["publisher"],
            genre = data["genre"],
            players = data["players"],
            image = data["image"],
            marquee = data["marquee"],
            video = data["video"],
            thumbnail = data["thumbnail"],
            fanart = data["fanart"],
            titlescreen = data["titlescreen"],
            manual = data["manual"],
            favorite = data["favorite"]?.lowercase() == "true",
            hidden = data["hidden"]?.lowercase() == "true",
            kidgame = data["kidgame"]?.lowercase() == "true",
            playcount = data["playcount"]?.toIntOrNull() ?: 0,
            lastplayed = data["lastplayed"],
            sortname = data["sortname"],
            collectionsortname = data["collectionsortname"],
            altemulator = data["altemulator"]
        )
    }

    /**
     * Check if a GameMetadata object has any useful metadata fields populated.
     */
    private fun hasUsefulMetadata(metadata: GameMetadata?): Boolean {
        if (metadata == null) return false
        return !metadata.desc.isNullOrBlank() ||
               metadata.rating != null ||
               !metadata.developer.isNullOrBlank() ||
               !metadata.publisher.isNullOrBlank() ||
               !metadata.genre.isNullOrBlank() ||
               !metadata.releasedate.isNullOrBlank() ||
               !metadata.image.isNullOrBlank()
    }

    /**
     * Get games that are missing metadata.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param gameFiles List of game file names to check
     * @return List of game file names that are missing metadata
     */
    fun getGamesWithoutMetadata(systemName: String, gameFiles: List<String>): List<String> {
        val existingMetadata = readGamelist(systemName)

        return gameFiles.filter { fileName ->
            val path = "./$fileName"
            val metadata = existingMetadata[path] ?: existingMetadata[fileName]
            !hasUsefulMetadata(metadata)
        }
    }

    /**
     * Remove metadata for a game.
     * @param systemName The system name (e.g., "windows", "androidgames")
     * @param gamePath The relative path to the game file
     */
    fun removeGameMetadata(systemName: String, gamePath: String) {
        val games = readGamelist(systemName).toMutableMap()
        games.remove(gamePath)
        games.remove("./$gamePath")
        games.remove(gamePath.removePrefix("./"))
        writeGamelist(systemName, games.values.toList())
    }
}
