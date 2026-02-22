package com.esde.emulatormanager.data.parser

import android.util.Xml
import com.esde.emulatormanager.data.model.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.io.StringReader

/**
 * Parser for ES-DE configuration files (es_systems.xml and es_find_rules.xml)
 */
class EsdeConfigParser {

    /**
     * Parse es_systems.xml content
     */
    fun parseSystemsXml(inputStream: InputStream): ConfigResult<List<GameSystem>> {
        return try {
            val systems = mutableListOf<GameSystem>()
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentSystem: GameSystemBuilder? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "system" -> currentSystem = GameSystemBuilder()
                            "name" -> currentSystem?.name = parser.nextText()
                            "fullname" -> currentSystem?.fullName = parser.nextText()
                            "path" -> currentSystem?.path = parser.nextText()
                            "extension" -> currentSystem?.extensions = parser.nextText()
                            "command" -> {
                                val label = parser.getAttributeValue(null, "label")
                                val command = parser.nextText()
                                currentSystem?.commands?.add(EmulatorCommand(label, command))
                            }
                            "platform" -> currentSystem?.platform = parser.nextText()
                            "theme" -> currentSystem?.theme = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "system" && currentSystem != null) {
                            currentSystem.build()?.let { systems.add(it) }
                            currentSystem = null
                        }
                    }
                }
                eventType = parser.next()
            }

            ConfigResult.Success(systems)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to parse es_systems.xml: ${e.message}", e)
        }
    }

    /**
     * Parse es_find_rules.xml content
     */
    fun parseFindRulesXml(inputStream: InputStream): ConfigResult<List<EmulatorRule>> {
        return try {
            val rules = mutableListOf<EmulatorRule>()
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var currentEmulatorName: String? = null
            var currentEntries = mutableListOf<String>()
            var inAndroidPackageRule = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "emulator" -> {
                                currentEmulatorName = parser.getAttributeValue(null, "name")
                                currentEntries = mutableListOf()
                            }
                            "rule" -> {
                                val type = parser.getAttributeValue(null, "type")
                                inAndroidPackageRule = type == "androidpackage"
                            }
                            "entry" -> {
                                if (inAndroidPackageRule) {
                                    val entry = parser.nextText()
                                    if (entry.isNotBlank()) {
                                        currentEntries.add(entry)
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "rule" -> inAndroidPackageRule = false
                            "emulator" -> {
                                if (currentEmulatorName != null && currentEntries.isNotEmpty()) {
                                    rules.add(EmulatorRule(currentEmulatorName, currentEntries.toList()))
                                }
                                currentEmulatorName = null
                                currentEntries = mutableListOf()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            ConfigResult.Success(rules)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to parse es_find_rules.xml: ${e.message}", e)
        }
    }

    /**
     * Parse systems XML from string content
     */
    fun parseSystemsXmlString(content: String): ConfigResult<List<GameSystem>> {
        return try {
            val systems = mutableListOf<GameSystem>()
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            var currentSystem: GameSystemBuilder? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "system" -> currentSystem = GameSystemBuilder()
                            "name" -> currentSystem?.name = parser.nextText()
                            "fullname" -> currentSystem?.fullName = parser.nextText()
                            "path" -> currentSystem?.path = parser.nextText()
                            "extension" -> currentSystem?.extensions = parser.nextText()
                            "command" -> {
                                val label = parser.getAttributeValue(null, "label")
                                val command = parser.nextText()
                                currentSystem?.commands?.add(EmulatorCommand(label, command))
                            }
                            "platform" -> currentSystem?.platform = parser.nextText()
                            "theme" -> currentSystem?.theme = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "system" && currentSystem != null) {
                            currentSystem.build()?.let { systems.add(it) }
                            currentSystem = null
                        }
                    }
                }
                eventType = parser.next()
            }

            ConfigResult.Success(systems)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to parse es_systems.xml: ${e.message}", e)
        }
    }

    /**
     * Parse find rules XML from string content
     */
    fun parseFindRulesXmlString(content: String): ConfigResult<List<EmulatorRule>> {
        return try {
            val rules = mutableListOf<EmulatorRule>()
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(content))

            var eventType = parser.eventType
            var currentEmulatorName: String? = null
            var currentEntries = mutableListOf<String>()
            var inAndroidPackageRule = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "emulator" -> {
                                currentEmulatorName = parser.getAttributeValue(null, "name")
                                currentEntries = mutableListOf()
                            }
                            "rule" -> {
                                val type = parser.getAttributeValue(null, "type")
                                inAndroidPackageRule = type == "androidpackage"
                            }
                            "entry" -> {
                                if (inAndroidPackageRule) {
                                    val entry = parser.nextText()
                                    if (entry.isNotBlank()) {
                                        currentEntries.add(entry)
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "rule" -> inAndroidPackageRule = false
                            "emulator" -> {
                                if (currentEmulatorName != null && currentEntries.isNotEmpty()) {
                                    rules.add(EmulatorRule(currentEmulatorName, currentEntries.toList()))
                                }
                                currentEmulatorName = null
                                currentEntries = mutableListOf()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            ConfigResult.Success(rules)
        } catch (e: Exception) {
            ConfigResult.Error("Failed to parse es_find_rules.xml: ${e.message}", e)
        }
    }

    private class GameSystemBuilder {
        var name: String? = null
        var fullName: String? = null
        var path: String? = null
        var extensions: String? = null
        var commands: MutableList<EmulatorCommand> = mutableListOf()
        var platform: String? = null
        var theme: String? = null

        fun build(): GameSystem? {
            return if (name != null && fullName != null) {
                GameSystem(
                    name = name!!,
                    fullName = fullName!!,
                    path = path ?: "",
                    extensions = extensions ?: "",
                    commands = commands.toList(),
                    platform = platform ?: name!!,
                    theme = theme ?: name!!
                )
            } else null
        }
    }
}
