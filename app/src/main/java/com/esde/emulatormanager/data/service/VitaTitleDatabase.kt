package com.esde.emulatormanager.data.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads vita_titles.json (bundled in assets) and provides fuzzy Title ID lookup by game name.
 *
 * The JSON is an array of {id, name} objects sourced from SerialStation API (3,600+ entries).
 * Used to auto-fill the Title ID field when the user selects an IGDB search result.
 */
@Singleton
class VitaTitleDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VitaTitleDatabase"
        // Region preference: US > EU > Asia > FR > JP
        private val REGION_ORDER = listOf("PCSE", "PCSB", "PCSA", "PCSF", "PCSG")
    }

    data class TitleEntry(val id: String, val name: String)

    private val entries: List<TitleEntry> by lazy { loadEntries() }

    private fun loadEntries(): List<TitleEntry> {
        return try {
            val json = context.assets.open("vita_titles.json").bufferedReader().readText()
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                TitleEntry(obj.getString("id"), obj.getString("name"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load vita_titles.json", e)
            emptyList()
        }
    }

    /**
     * Normalise a game name for comparison: lowercase, strip punctuation, drop common articles.
     */
    private fun normalize(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\b(the|a|an)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Find the best matching Title ID for [gameName].
     * Returns null if no reasonable match is found.
     * Prefers US (PCSE) > EU (PCSB) > Asia (PCSA) > FR (PCSF) > JP (PCSG) when scores tie.
     */
    fun findBestMatch(gameName: String): String? {
        if (entries.isEmpty()) return null

        val normalizedQuery = normalize(gameName)
        val queryWords = normalizedQuery.split(" ").filter { it.length > 1 }
        if (queryWords.isEmpty()) return null

        // 1. Exact normalised match
        val exact = entries.filter { normalize(it.name) == normalizedQuery }
        if (exact.isNotEmpty()) return preferredRegion(exact)

        // 2. Score by Jaccard-style word overlap
        val scored = entries.mapNotNull { entry ->
            val entryWords = normalize(entry.name).split(" ").filter { it.length > 1 }
            val intersection = queryWords.count { it in entryWords }
            val union = (queryWords + entryWords).distinct().size
            val score = if (union == 0) 0.0 else intersection.toDouble() / union
            if (score >= 0.4) entry to score else null
        }.sortedByDescending { it.second }

        if (scored.isEmpty()) return null

        // Group entries within 0.05 of the top score and pick best region
        val best = scored.first().second
        val topGroup = scored.filter { it.second >= best - 0.05 }.map { it.first }
        return preferredRegion(topGroup)
    }

    private fun preferredRegion(entries: List<TitleEntry>): String? {
        for (prefix in REGION_ORDER) {
            entries.firstOrNull { it.id.startsWith(prefix) }?.let { return it.id }
        }
        return entries.firstOrNull()?.id
    }
}
