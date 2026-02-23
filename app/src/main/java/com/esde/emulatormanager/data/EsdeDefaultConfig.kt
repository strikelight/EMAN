package com.esde.emulatormanager.data

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/**
 * Reads the bundled ES-DE default es_find_rules.xml asset and exposes the set of
 * package names that ES-DE already knows about out of the box.
 *
 * Emulators whose package name is in this set are shown with an "ES-DE Default"
 * badge in the UI (no toggle), since the user cannot suppress them via custom_systems.
 */
object EsdeDefaultConfig {

    private var defaultPackages: Set<String>? = null

    /**
     * Parses the bundled es_find_rules.xml asset and caches all package names.
     * Safe to call multiple times — only parses once.
     */
    fun load(context: Context) {
        if (defaultPackages != null) return
        val packages = mutableSetOf<String>()
        try {
            context.assets.open("es_find_rules.xml").use { stream ->
                val pullParser = android.util.Xml.newPullParser()
                pullParser.setInput(stream, "UTF-8")
                var event = pullParser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && pullParser.name == "entry") {
                        val entry = pullParser.nextText().trim()
                        // Entry format: "com.package.name/com.package.Activity"
                        val pkg = entry.substringBefore("/").trim()
                        if (pkg.isNotEmpty()) packages.add(pkg)
                    }
                    event = pullParser.next()
                }
            }
        } catch (e: Exception) {
            // If the asset can't be read, default to empty set — all emulators will show toggles
        }
        defaultPackages = packages
    }

    /**
     * Returns true if the given package name appears in ES-DE's bundled default find rules,
     * meaning ES-DE already supports this emulator out of the box.
     */
    fun isDefaultEmulator(packageName: String): Boolean =
        defaultPackages?.contains(packageName) ?: false
}
