package com.esde.emulatormanager.data.service

import android.content.Context
import com.esde.emulatormanager.data.model.CustomEmulatorConfig
import com.esde.emulatormanager.data.model.CustomEmulatorMapping
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for managing user-defined custom emulator mappings
 * These are emulators not in the known database that users want to add
 */
@Singleton
class CustomEmulatorService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val configFile: File
        get() = File(context.filesDir, "custom_emulators.json")

    private var cachedConfig: CustomEmulatorConfig? = null

    /**
     * Load custom emulator configuration
     */
    fun loadConfig(): CustomEmulatorConfig {
        cachedConfig?.let { return it }

        if (!configFile.exists()) {
            return CustomEmulatorConfig().also { cachedConfig = it }
        }

        return try {
            val json = configFile.readText()
            parseConfig(json).also { cachedConfig = it }
        } catch (e: Exception) {
            CustomEmulatorConfig().also { cachedConfig = it }
        }
    }

    /**
     * Save custom emulator configuration
     */
    fun saveConfig(config: CustomEmulatorConfig) {
        try {
            val json = serializeConfig(config)
            configFile.writeText(json)
            cachedConfig = config
        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Add a new custom emulator mapping
     */
    fun addCustomEmulator(mapping: CustomEmulatorMapping) {
        val config = loadConfig()
        val existingIndex = config.mappings.indexOfFirst { it.packageName == mapping.packageName }

        val updatedMappings = if (existingIndex >= 0) {
            // Update existing mapping
            config.mappings.toMutableList().apply {
                set(existingIndex, mapping)
            }
        } else {
            // Add new mapping
            config.mappings + mapping
        }

        saveConfig(config.copy(mappings = updatedMappings))
    }

    /**
     * Remove a custom emulator mapping
     */
    fun removeCustomEmulator(packageName: String) {
        val config = loadConfig()
        val updatedMappings = config.mappings.filter { it.packageName != packageName }
        saveConfig(config.copy(mappings = updatedMappings))
    }

    /**
     * Update systems for a custom emulator
     */
    fun updateEmulatorSystems(packageName: String, systems: List<String>) {
        val config = loadConfig()
        val updatedMappings = config.mappings.map { mapping ->
            if (mapping.packageName == packageName) {
                mapping.copy(supportedSystems = systems)
            } else {
                mapping
            }
        }
        saveConfig(config.copy(mappings = updatedMappings))
    }

    /**
     * Get custom emulators that support a specific system
     */
    fun getCustomEmulatorsForSystem(systemId: String): List<CustomEmulatorMapping> {
        return loadConfig().mappings.filter { it.supportedSystems.contains(systemId) }
    }

    /**
     * Check if a package is a custom emulator
     */
    fun isCustomEmulator(packageName: String): Boolean {
        return loadConfig().mappings.any { it.packageName == packageName }
    }

    /**
     * Get a custom emulator mapping by package name
     */
    fun getCustomEmulator(packageName: String): CustomEmulatorMapping? {
        return loadConfig().mappings.find { it.packageName == packageName }
    }

    /**
     * Get all custom emulator mappings
     */
    fun getAllCustomEmulators(): List<CustomEmulatorMapping> {
        return loadConfig().mappings
    }

    private fun parseConfig(json: String): CustomEmulatorConfig {
        val jsonObject = JSONObject(json)
        val version = jsonObject.optInt("version", 1)
        val mappingsArray = jsonObject.optJSONArray("mappings") ?: JSONArray()

        val mappings = mutableListOf<CustomEmulatorMapping>()
        for (i in 0 until mappingsArray.length()) {
            val mappingJson = mappingsArray.getJSONObject(i)
            val systemsArray = mappingJson.optJSONArray("supportedSystems") ?: JSONArray()
            val systems = mutableListOf<String>()
            for (j in 0 until systemsArray.length()) {
                systems.add(systemsArray.getString(j))
            }

            mappings.add(
                CustomEmulatorMapping(
                    packageName = mappingJson.getString("packageName"),
                    appName = mappingJson.getString("appName"),
                    activityName = mappingJson.optString("activityName", null),
                    supportedSystems = systems,
                    displayLabel = mappingJson.optString("displayLabel", null)
                )
            )
        }

        return CustomEmulatorConfig(version = version, mappings = mappings)
    }

    private fun serializeConfig(config: CustomEmulatorConfig): String {
        val jsonObject = JSONObject()
        jsonObject.put("version", config.version)

        val mappingsArray = JSONArray()
        for (mapping in config.mappings) {
            val mappingJson = JSONObject()
            mappingJson.put("packageName", mapping.packageName)
            mappingJson.put("appName", mapping.appName)
            mapping.activityName?.let { mappingJson.put("activityName", it) }
            mapping.displayLabel?.let { mappingJson.put("displayLabel", it) }

            val systemsArray = JSONArray()
            for (system in mapping.supportedSystems) {
                systemsArray.put(system)
            }
            mappingJson.put("supportedSystems", systemsArray)

            mappingsArray.put(mappingJson)
        }
        jsonObject.put("mappings", mappingsArray)

        return jsonObject.toString(2)
    }
}
