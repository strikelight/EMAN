package com.esde.emulatormanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esde.emulatormanager.data.model.*
import com.esde.emulatormanager.data.repository.EmulatorRepository
import com.esde.emulatormanager.data.service.AndroidGamesService
import com.esde.emulatormanager.data.service.DeviceIdentificationService
import com.esde.emulatormanager.data.service.GogApiService
import com.esde.emulatormanager.data.service.MetadataService
import com.esde.emulatormanager.data.service.ProfileService
import com.esde.emulatormanager.data.service.SteamApiService
import com.esde.emulatormanager.data.service.VitaGamesService
import com.esde.emulatormanager.data.service.VitaTitleDatabase
import com.esde.emulatormanager.data.service.WindowsGamesService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: EmulatorRepository,
    private val windowsGamesService: WindowsGamesService,
    private val steamApiService: SteamApiService,
    private val gogApiService: GogApiService,
    private val androidGamesService: AndroidGamesService,
    private val profileService: ProfileService,
    private val deviceIdentificationService: DeviceIdentificationService,
    private val metadataService: MetadataService,
    private val vitaGamesService: VitaGamesService,
    private val vitaTitleDatabase: VitaTitleDatabase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _installedEmulators = MutableStateFlow<List<InstalledEmulator>>(emptyList())
    val installedEmulators: StateFlow<List<InstalledEmulator>> = _installedEmulators.asStateFlow()

    private val _allInstalledApps = MutableStateFlow<List<InstalledEmulator>>(emptyList())
    val allInstalledApps: StateFlow<List<InstalledEmulator>> = _allInstalledApps.asStateFlow()

    private val _customEmulators = MutableStateFlow<List<CustomEmulatorMapping>>(emptyList())
    val customEmulators: StateFlow<List<CustomEmulatorMapping>> = _customEmulators.asStateFlow()

    private val _windowsGamesState = MutableStateFlow(WindowsGamesUiState())
    val windowsGamesState: StateFlow<WindowsGamesUiState> = _windowsGamesState.asStateFlow()

    private val _androidGamesState = MutableStateFlow(AndroidGamesUiState())
    val androidGamesState: StateFlow<AndroidGamesUiState> = _androidGamesState.asStateFlow()

    private val _profilesState = MutableStateFlow(ProfilesUiState())
    val profilesState: StateFlow<ProfilesUiState> = _profilesState.asStateFlow()

    private val _vitaGamesState = MutableStateFlow(VitaGamesUiState())
    val vitaGamesState: StateFlow<VitaGamesUiState> = _vitaGamesState.asStateFlow()

    init {
        checkEsdeConfiguration()
    }

    fun checkEsdeConfiguration() {
        _uiState.update { it.copy(
            esdeConfigPath = repository.getEsdeConfigPath()
        )}
    }

    fun setStoragePermissionGranted(granted: Boolean) {
        _uiState.update { it.copy(hasStoragePermission = granted) }
        if (granted) {
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load installed emulators
            val emulators = repository.getInstalledEmulators()
            _installedEmulators.value = emulators

            // Load custom emulator mappings
            _customEmulators.value = repository.getCustomEmulators()

            // Load all installed apps (for custom emulator icons)
            _allInstalledApps.value = repository.getAllInstalledApps()

            // Load system configurations
            when (val result = repository.getSystemConfigurations()) {
                is ConfigResult.Success -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        systems = result.data,
                        error = null
                    )}
                }
                is ConfigResult.Error -> {
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = result.message
                    )}
                }
            }
        }
    }

    fun loadAllApps() {
        viewModelScope.launch {
            _allInstalledApps.value = repository.getAllInstalledApps()
        }
    }

    fun refreshData() {
        loadData()
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: SystemCategory?) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun getFilteredSystems(): List<SystemEmulatorConfig> {
        val state = _uiState.value
        var systems = state.systems

        // Filter by category
        if (state.selectedCategory != null) {
            systems = systems.filter { config ->
                state.selectedCategory.platforms.contains(config.system.name)
            }
        }

        // Filter by search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase()
            systems = systems.filter { config ->
                config.system.name.lowercase().contains(query) ||
                config.system.fullName.lowercase().contains(query)
            }
        }

        return systems
    }

    fun addEmulatorToSystem(systemId: String, emulator: InstalledEmulator) {
        viewModelScope.launch {
            when (val result = repository.addEmulatorToSystem(systemId, emulator)) {
                is ConfigResult.Success -> {
                    loadData() // Refresh data after adding
                }
                is ConfigResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun removeEmulatorFromSystem(systemId: String, emulatorPackage: String) {
        viewModelScope.launch {
            when (val result = repository.removeEmulatorFromSystem(systemId, emulatorPackage)) {
                is ConfigResult.Success -> {
                    loadData() // Refresh data after removing
                }
                is ConfigResult.Error -> {
                    _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun getInstalledEmulatorsForSystem(systemId: String): List<InstalledEmulator> {
        // Get known emulators (using fuzzy matching to handle variant package names)
        val knownEmulators = _installedEmulators.value.filter { emulator ->
            val knownEmulator = com.esde.emulatormanager.data.KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName)
            knownEmulator?.supportedSystems?.contains(systemId) == true
        }

        // Get custom emulators for this system
        val customEmulatorPackages = _customEmulators.value
            .filter { it.supportedSystems.contains(systemId) }
            .map { it.packageName }
            .toSet()

        val customEmulators = _allInstalledApps.value.filter { app ->
            customEmulatorPackages.contains(app.packageName)
        }

        // Combine and deduplicate
        return (knownEmulators + customEmulators).distinctBy { it.packageName }
    }

    fun addCustomEmulator(emulator: InstalledEmulator, supportedSystems: List<String>) {
        viewModelScope.launch {
            repository.addCustomEmulator(emulator, supportedSystems)
            loadData() // Refresh to show the new emulator
        }
    }

    fun removeCustomEmulator(packageName: String) {
        viewModelScope.launch {
            repository.removeCustomEmulator(packageName)
            loadData() // Refresh
        }
    }

    fun isCustomEmulator(packageName: String): Boolean {
        return repository.isCustomEmulator(packageName)
    }

    /**
     * Check if an emulator is configured for a specific system
     * Reads from ES-DE XML files
     */
    fun isEmulatorConfiguredForSystem(packageName: String, systemId: String): Boolean {
        return repository.isEmulatorConfiguredForSystem(packageName, systemId)
    }

    fun getAllSystems(): List<GameSystem> {
        return repository.getAllSystems()
    }

    fun getCustomEmulatorSystems(packageName: String): List<String> {
        // Get systems where this emulator is configured from XML files
        val allSystems = repository.getAllSystems()
        return allSystems
            .filter { system -> repository.isEmulatorConfiguredForSystem(packageName, system.name) }
            .map { it.name }
    }

    fun backupConfiguration() {
        viewModelScope.launch {
            when (val result = repository.backupConfiguration()) {
                is ConfigResult.Success -> {
                    _uiState.update { it.copy(
                        error = null,
                        successMessage = "Backup created successfully"
                    )}
                }
                is ConfigResult.Error -> {
                    _uiState.update { it.copy(error = "Backup failed: ${result.message}") }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    // ========== Windows Games Functions ==========

    fun loadWindowsGames() {
        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                // NOTE: Do NOT sync esdeWindowsPath back to the service here.
                // The service (WindowsGamesService.customWindowsPath) is persisted in
                // SharedPreferences and is the source of truth. Pushing the stale UI state
                // back would overwrite a path just restored by profile load.

                val launchers = windowsGamesService.getInstalledLaunchers()
                val esdeWindowsPath = windowsGamesService.getEsdeWindowsPath()
                val esdeMediaPath = repository.getMediaBasePath()
                val esdeSettingsFilePath = repository.getSettingsFilePath()

                _windowsGamesState.update {
                    it.copy(
                        isLoading = false,
                        launchers = launchers,
                        esdeWindowsPath = esdeWindowsPath,
                        esdeMediaPath = esdeMediaPath,
                        esdeSettingsFilePath = esdeSettingsFilePath
                    )
                }
            }
        }
    }

    fun scanWindowsGames() {
        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isScanning = true) }

            withContext(Dispatchers.IO) {
                val launchers = windowsGamesService.getInstalledLaunchers()

                _windowsGamesState.update {
                    it.copy(
                        isScanning = false,
                        launchers = launchers,
                        successMessage = "Found ${launchers.sumOf { l -> l.games.size }} games"
                    )
                }
            }
        }
    }

    fun addWindowsGameToEsde(game: WindowsGameShortcut) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = windowsGamesService.createShortcut(game)) {
                    is ConfigResult.Success -> {
                        // Refresh the launchers list
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(
                                launchers = launchers,
                                successMessage = "Added ${game.name} to ES-DE"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun removeWindowsGameFromEsde(game: WindowsGameShortcut) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = windowsGamesService.removeShortcut(game.id)) {
                    is ConfigResult.Success -> {
                        // Refresh the launchers list
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(
                                launchers = launchers,
                                successMessage = "Removed ${game.name} from ES-DE"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun addAllWindowsGamesFromLauncher(launcher: WindowsGameLauncher) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = windowsGamesService.addAllGamesFromLauncher(launcher)) {
                    is ConfigResult.Success -> {
                        val addedCount = result.data
                        // Refresh the launchers list
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(
                                launchers = launchers,
                                successMessage = "Added $addedCount games from ${launcher.displayName}"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun clearWindowsGamesSuccess() {
        _windowsGamesState.update { it.copy(successMessage = null) }
    }

    fun clearWindowsGamesError() {
        _windowsGamesState.update { it.copy(error = null) }
    }

    fun setShowPathSelectionDialog(show: Boolean) {
        _windowsGamesState.update { it.copy(showPathSelectionDialog = show) }
    }

    fun saveWindowsRomsPath(path: String) {
        // Update the service with the custom path
        windowsGamesService.setCustomPath(path)

        _windowsGamesState.update {
            it.copy(
                esdeWindowsPath = path,
                successMessage = "Windows ROMs path updated"
            )
        }
    }

    // ========== Steam Game Search Functions ==========

    fun updateSteamSearchQuery(query: String) {
        _windowsGamesState.update { it.copy(searchQuery = query) }
    }

    fun searchSteamGames() {
        val query = _windowsGamesState.value.searchQuery
        if (query.length < 2) return

        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isSearching = true, error = null) }

            val results = steamApiService.searchGames(query)

            _windowsGamesState.update {
                if (results == null) {
                    // Search failed
                    it.copy(
                        isSearching = false,
                        searchResults = emptyList(),
                        error = steamApiService.lastError ?: "Failed to search Steam database. Check your internet connection."
                    )
                } else {
                    it.copy(
                        isSearching = false,
                        searchResults = results
                    )
                }
            }
        }
    }

    fun addSteamGameToEsde(game: SteamGame, launcherPackage: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = windowsGamesService.createSteamShortcut(game, launcherPackage)) {
                    is ConfigResult.Success -> {
                        val shortcutPath = result.data
                        val gameFileName = java.io.File(shortcutPath).nameWithoutExtension

                        // Scrape full metadata using current scrape settings
                        val scraped = metadataService.scrapeAndSaveSteamMetadata(game.appId, gameFileName, _windowsGamesState.value.scrapeOptions)
                        android.util.Log.d("MainViewModel", "Steam metadata scrape for ${game.name}: $scraped")

                        val message = if (scraped) {
                            "Added ${game.name} to ES-DE with metadata & artwork"
                        } else {
                            "Added ${game.name} to ES-DE (metadata scrape failed)"
                        }

                        _windowsGamesState.update {
                            it.copy(
                                successMessage = message,
                                gamesWithoutMetadataCount = metadataService.getWindowsGamesWithoutMetadataCount()
                            )
                        }

                        // Refresh the games list after adding
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(launchers = launchers)
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun getExistingSteamAppIds(): Set<Int> {
        return windowsGamesService.getExistingSteamGames().map { it.second }.toSet()
    }

    fun getSteamCompatibleLaunchers(): List<Pair<String, String>> {
        return windowsGamesService.getSteamCompatibleLaunchers()
    }

    /**
     * Get the artwork path for a Windows game.
     * @param gameId The game's shortcut ID (filename without extension)
     * @return The path to the artwork file, or null if not found
     */
    fun getWindowsGameArtworkPath(gameId: String): String? {
        return windowsGamesService.getArtworkPath(gameId)
    }

    fun showAddGameDialog(show: Boolean) {
        _windowsGamesState.update { it.copy(showAddGameDialog = show) }
    }

    fun clearSteamSearchResults() {
        _windowsGamesState.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList()
            )
        }
    }

    // ========== GOG Game Search Functions ==========

    fun updateGogSearchQuery(query: String) {
        _windowsGamesState.update { it.copy(gogSearchQuery = query) }
    }

    fun searchGogGames() {
        val query = _windowsGamesState.value.gogSearchQuery
        if (query.length < 2) return

        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isSearchingGog = true, error = null) }

            val results = gogApiService.searchGames(query)

            _windowsGamesState.update {
                if (results == null) {
                    it.copy(
                        isSearchingGog = false,
                        gogSearchResults = emptyList(),
                        error = gogApiService.lastError ?: "Failed to search GOG database. Check your internet connection."
                    )
                } else {
                    it.copy(
                        isSearchingGog = false,
                        gogSearchResults = results
                    )
                }
            }
        }
    }

    fun addGogGameToEsde(game: GogGame, launcherPackage: String) {
        viewModelScope.launch {
            // Show loading state immediately
            _windowsGamesState.update {
                it.copy(isSearchingGog = true)
            }

            withContext(Dispatchers.IO) {
                when (val result = windowsGamesService.createGogShortcut(game, launcherPackage)) {
                    is ConfigResult.Success -> {
                        val shortcutPath = result.data
                        val gameFileName = windowsGamesService.getGameFileName(java.io.File(shortcutPath))

                        // Download artwork from GOG
                        var artworkDownloaded = false

                        // Use the metadata service to scrape and save metadata using current scrape settings
                        try {
                            artworkDownloaded = metadataService.scrapeAndSaveGogMetadata(game.productId, gameFileName, _windowsGamesState.value.scrapeOptions)
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "GOG metadata scrape exception: ${e.message}", e)
                        }

                        val message = if (artworkDownloaded) {
                            "Added ${game.name} to ES-DE with artwork"
                        } else {
                            "Added ${game.name} to ES-DE"
                        }

                        // Refresh the games list
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(
                                isSearchingGog = false,
                                successMessage = message,
                                launchers = launchers
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(
                                isSearchingGog = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun getExistingGogProductIds(): Set<Long> {
        return windowsGamesService.getExistingGogGames().map { it.second }.toSet()
    }

    fun getGogCompatibleLaunchers(): List<Pair<String, String>> {
        return windowsGamesService.getGogCompatibleLaunchers()
    }

    fun clearGogSearchResults() {
        _windowsGamesState.update {
            it.copy(
                gogSearchQuery = "",
                gogSearchResults = emptyList()
            )
        }
    }

    // ========== Epic Game Import Functions ==========

    fun updateEpicImportPath(path: String) {
        _windowsGamesState.update { it.copy(epicImportPath = path) }
    }

    fun scanForEpicGames() {
        val path = _windowsGamesState.value.epicImportPath
        if (path.isBlank()) return

        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isScanningEpic = true, error = null) }

            withContext(Dispatchers.IO) {
                val file = java.io.File(path)

                // Now we expect a single .epicgame file, not a folder
                if (file.exists() && file.isFile && file.name.endsWith(".epicgame")) {
                    try {
                        val internalId = file.readText().trim()
                        val gameName = file.nameWithoutExtension.replace("_", " ")

                        val epicGame = EpicGame(
                            internalId = internalId,
                            name = gameName,
                            sourcePath = file.absolutePath
                        )

                        // Import the game directly
                        when (val result = windowsGamesService.importEpicShortcut(file)) {
                            is ConfigResult.Success -> {
                                val importedGame = result.data
                                val gameFileName = windowsGamesService.getGameFileName(java.io.File(importedGame.sourcePath))

                                // Try to scrape metadata using IGDB
                                var metadataScraped = false
                                if (metadataService.hasIgdbCredentials()) {
                                    try {
                                        metadataScraped = metadataService.scrapeAndSaveEpicMetadata(importedGame.name, gameFileName, _windowsGamesState.value.scrapeOptions)
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainViewModel", "Epic metadata scrape exception: ${e.message}", e)
                                    }
                                }

                                val message = if (metadataScraped) {
                                    "Imported ${importedGame.name} with metadata"
                                } else {
                                    "Imported ${importedGame.name}"
                                }

                                // Refresh existing games list
                                val existingGames = windowsGamesService.getExistingEpicGames()

                                _windowsGamesState.update {
                                    it.copy(
                                        isScanningEpic = false,
                                        epicImportPath = "",
                                        epicFoundGames = existingGames,
                                        successMessage = message
                                    )
                                }

                                // Refresh the launchers list
                                val launchers = windowsGamesService.getInstalledLaunchers()
                                _windowsGamesState.update {
                                    it.copy(launchers = launchers)
                                }
                            }
                            is ConfigResult.Error -> {
                                _windowsGamesState.update {
                                    it.copy(
                                        isScanningEpic = false,
                                        error = result.message
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Error importing Epic file: ${file.name}", e)
                        _windowsGamesState.update {
                            it.copy(
                                isScanningEpic = false,
                                error = "Error importing file: ${e.message}"
                            )
                        }
                    }
                } else {
                    _windowsGamesState.update {
                        it.copy(
                            isScanningEpic = false,
                            error = if (!file.exists()) "File not found: $path" else "Please select a .epicgame file"
                        )
                    }
                }
            }
        }
    }

    fun importEpicGame(game: EpicGame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sourceFile = java.io.File(game.sourcePath)
                when (val result = windowsGamesService.importEpicShortcut(sourceFile)) {
                    is ConfigResult.Success -> {
                        val importedGame = result.data
                        val gameFileName = windowsGamesService.getGameFileName(java.io.File(importedGame.sourcePath))

                        // Try to scrape metadata using IGDB
                        var metadataScraped = false
                        if (metadataService.hasIgdbCredentials()) {
                            try {
                                metadataScraped = metadataService.scrapeAndSaveEpicMetadata(importedGame.name, gameFileName, _windowsGamesState.value.scrapeOptions)
                            } catch (e: Exception) {
                                android.util.Log.e("MainViewModel", "Epic metadata scrape exception: ${e.message}", e)
                            }
                        }

                        val message = if (metadataScraped) {
                            "Imported ${importedGame.name} with metadata"
                        } else {
                            "Imported ${importedGame.name}"
                        }

                        _windowsGamesState.update {
                            it.copy(successMessage = message)
                        }

                        // Refresh found games list to mark as imported
                        scanForEpicGames()

                        // Refresh the games list
                        val launchers = windowsGamesService.getInstalledLaunchers()
                        _windowsGamesState.update {
                            it.copy(launchers = launchers)
                        }
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun importAllEpicGames() {
        val games = _windowsGamesState.value.epicFoundGames
        val existingNames = getExistingEpicGameNames()

        viewModelScope.launch {
            var importedCount = 0
            games.filterNot { existingNames.contains(it.name.lowercase()) }.forEach { game ->
                withContext(Dispatchers.IO) {
                    val sourceFile = java.io.File(game.sourcePath)
                    when (windowsGamesService.importEpicShortcut(sourceFile)) {
                        is ConfigResult.Success -> importedCount++
                        is ConfigResult.Error -> { /* Skip failed imports */ }
                    }
                }
            }

            _windowsGamesState.update {
                it.copy(successMessage = "Imported $importedCount Epic games")
            }

            // Refresh
            scanForEpicGames()
            val launchers = windowsGamesService.getInstalledLaunchers()
            _windowsGamesState.update {
                it.copy(launchers = launchers)
            }
        }
    }

    fun getExistingEpicGameNames(): Set<String> {
        return windowsGamesService.getExistingEpicGames().map { it.name.lowercase() }.toSet()
    }

    fun getEpicCompatibleLaunchers(): List<Pair<String, String>> {
        return windowsGamesService.getEpicCompatibleLaunchers()
    }

    fun clearEpicImportState() {
        // Load existing Epic games to show in the list
        val existingGames = windowsGamesService.getExistingEpicGames()
        _windowsGamesState.update {
            it.copy(
                epicImportPath = "",
                epicFoundGames = existingGames
            )
        }
    }

    // ========== Amazon Game Import Functions ==========

    fun updateAmazonImportPath(path: String) {
        _windowsGamesState.update { it.copy(amazonImportPath = path) }
    }

    fun scanForAmazonGames() {
        val path = _windowsGamesState.value.amazonImportPath
        if (path.isBlank()) return

        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isScanningAmazon = true, error = null) }

            withContext(Dispatchers.IO) {
                val file = java.io.File(path)

                if (file.exists() && file.isFile && file.name.endsWith(".amazon")) {
                    try {
                        when (val result = windowsGamesService.importAmazonShortcut(file)) {
                            is ConfigResult.Success -> {
                                val importedGame = result.data
                                val gameFileName = java.io.File(importedGame.sourcePath).nameWithoutExtension

                                var metadataScraped = false
                                try {
                                    metadataScraped = metadataService.scrapeAndSaveEpicMetadata(
                                        importedGame.name, gameFileName, _windowsGamesState.value.scrapeOptions
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("MainViewModel", "Amazon metadata scrape exception: ${e.message}", e)
                                }

                                val existingGames = windowsGamesService.getExistingAmazonGames()
                                _windowsGamesState.update {
                                    it.copy(
                                        isScanningAmazon = false,
                                        amazonImportPath = "",
                                        amazonFoundGames = existingGames,
                                        successMessage = if (metadataScraped) {
                                            "Imported ${importedGame.name} with metadata"
                                        } else {
                                            "Imported ${importedGame.name}"
                                        },
                                        gamesWithoutMetadataCount = metadataService.getWindowsGamesWithoutMetadataCount()
                                    )
                                }
                            }
                            is ConfigResult.Error -> {
                                _windowsGamesState.update {
                                    it.copy(
                                        isScanningAmazon = false,
                                        error = result.message
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainViewModel", "Error importing Amazon file: ${file.name}", e)
                        _windowsGamesState.update {
                            it.copy(
                                isScanningAmazon = false,
                                error = "Error importing Amazon game: ${e.message}"
                            )
                        }
                    }
                } else {
                    _windowsGamesState.update {
                        it.copy(
                            isScanningAmazon = false,
                            error = if (!file.exists()) "File not found: $path" else "Please select a .amazon file"
                        )
                    }
                }
            }
        }
    }

    fun importAmazonGame(game: AmazonGame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val sourceFile = java.io.File(game.sourcePath)
                when (val result = windowsGamesService.importAmazonShortcut(sourceFile)) {
                    is ConfigResult.Success -> {
                        val importedGame = result.data
                        val gameFileName = java.io.File(importedGame.sourcePath).nameWithoutExtension
                        try {
                            metadataService.scrapeAndSaveEpicMetadata(
                                importedGame.name, gameFileName, _windowsGamesState.value.scrapeOptions
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("MainViewModel", "Amazon metadata scrape exception: ${e.message}", e)
                        }
                        _windowsGamesState.update {
                            it.copy(successMessage = "Imported ${importedGame.name}")
                        }
                        scanForAmazonGames()
                    }
                    is ConfigResult.Error -> {
                        _windowsGamesState.update { it.copy(error = result.message) }
                    }
                }
            }
        }
    }

    fun importAllAmazonGames() {
        val games = _windowsGamesState.value.amazonFoundGames
        val existingNames = getExistingAmazonGameNames()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var importedCount = 0
                games.filter { it.name.lowercase() !in existingNames }.forEach { game ->
                    val sourceFile = java.io.File(game.sourcePath)
                    when (windowsGamesService.importAmazonShortcut(sourceFile)) {
                        is ConfigResult.Success -> importedCount++
                        else -> { /* skip failures */ }
                    }
                }
                _windowsGamesState.update {
                    it.copy(successMessage = "Imported $importedCount Amazon games")
                }
                scanForAmazonGames()
            }
        }
    }

    fun getExistingAmazonGameNames(): Set<String> {
        return windowsGamesService.getExistingAmazonGames().map { it.name.lowercase() }.toSet()
    }

    fun getAmazonCompatibleLaunchers(): List<Pair<String, String>> {
        return windowsGamesService.getAmazonCompatibleLaunchers()
    }

    fun clearAmazonImportState() {
        val existingGames = windowsGamesService.getExistingAmazonGames()
        _windowsGamesState.update {
            it.copy(
                amazonImportPath = "",
                amazonFoundGames = existingGames
            )
        }
    }

    // ========== ES-DE Configuration Functions ==========

    /**
     * Configure ES-DE to use GameHub Lite and GameNative with proper auto-launch for Windows games
     */
    fun configureGameHubLiteForWindows() {
        val windowsPath = _windowsGamesState.value.esdeWindowsPath
        if (windowsPath.isNullOrBlank()) {
            _windowsGamesState.update {
                it.copy(error = "Please set the Windows ROMs path first")
            }
            return
        }

        viewModelScope.launch {
            _windowsGamesState.update { it.copy(isLoading = true) }

            when (val result = repository.configureGameHubLiteForWindows(windowsPath)) {
                is ConfigResult.Success -> {
                    _windowsGamesState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "ES-DE configured for Windows launchers. Restart ES-DE to apply changes.",
                            isGameHubLiteConfigured = true,
                            isGameNativeConfigured = true
                        )
                    }
                }
                is ConfigResult.Error -> {
                    _windowsGamesState.update {
                        it.copy(
                            isLoading = false,
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    /**
     * Check if GameHub Lite is configured for Windows
     */
    fun checkGameHubLiteConfiguration() {
        viewModelScope.launch {
            val isConfigured = repository.isGameHubLiteConfiguredForWindows()
            _windowsGamesState.update {
                it.copy(isGameHubLiteConfigured = isConfigured)
            }
        }
    }

    /**
     * Check if GameNative is configured for Windows
     */
    fun checkGameNativeConfiguration() {
        viewModelScope.launch {
            val isConfigured = repository.isGameNativeConfiguredForWindows()
            _windowsGamesState.update {
                it.copy(isGameNativeConfigured = isConfigured)
            }
        }
    }

    /**
     * Check both GameHub Lite and GameNative configuration
     */
    fun checkAllLauncherConfigurations() {
        viewModelScope.launch {
            val isGameHubLiteConfigured = repository.isGameHubLiteConfiguredForWindows()
            val isGameNativeConfigured = repository.isGameNativeConfiguredForWindows()
            _windowsGamesState.update {
                it.copy(
                    isGameHubLiteConfigured = isGameHubLiteConfigured,
                    isGameNativeConfigured = isGameNativeConfigured
                )
            }
        }
    }

    // ========== Android Games Functions ==========

    fun loadAndroidGames() {
        viewModelScope.launch {
            _androidGamesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                val allApps = androidGamesService.getInstalledApps()
                val currentTab = _androidGamesState.value.selectedTab
                val staleEntries = androidGamesService.getStaleEntriesForTab(currentTab)

                // Get paths for each tab
                val gamesPath = androidGamesService.getPathForTab(AndroidTab.GAMES)
                val appsPath = androidGamesService.getPathForTab(AndroidTab.APPS)
                val emulatorsPath = androidGamesService.getPathForTab(AndroidTab.EMULATORS)

                _androidGamesState.update {
                    it.copy(
                        isLoading = false,
                        allApps = allApps,
                        staleEntries = staleEntries,
                        esdeGamesPath = gamesPath,
                        esdeAppsPath = appsPath,
                        esdeEmulatorsPath = emulatorsPath
                    )
                }
            }
        }
    }

    fun scanAndroidGames() {
        viewModelScope.launch {
            _androidGamesState.update { it.copy(isScanning = true) }

            withContext(Dispatchers.IO) {
                val allApps = androidGamesService.getInstalledApps()
                val staleEntries = androidGamesService.getStaleEntries()
                val showAllApps = _androidGamesState.value.showAllApps
                val filteredGames = if (showAllApps) allApps else allApps.filter { it.isGame }

                val staleMsg = if (staleEntries.isNotEmpty()) ", ${staleEntries.size} stale" else ""
                _androidGamesState.update {
                    it.copy(
                        isScanning = false,
                        allApps = allApps,
                        games = filteredGames,
                        staleEntries = staleEntries,
                        successMessage = "Found ${allApps.filter { it.isGame }.size} games, ${allApps.size} total apps$staleMsg"
                    )
                }
            }
        }
    }

    fun addAndroidGameToEsde(game: AndroidGame) {
        viewModelScope.launch {
            val currentTab = _androidGamesState.value.selectedTab
            withContext(Dispatchers.IO) {
                // Only try to fetch IGDB artwork for Games tab, not Apps or Emulators
                val hasIgdbArtwork = if (currentTab == AndroidTab.GAMES) {
                    metadataService.tryFetchIgdbArtwork(game, currentTab)
                } else {
                    false
                }

                when (val result = androidGamesService.createShortcutForTab(game, currentTab)) {
                    is ConfigResult.Success -> {
                        // Refresh the apps list and metadata count
                        val allApps = androidGamesService.getInstalledApps()
                        val metadataCount = metadataService.getAndroidGamesWithoutMetadataCount(currentTab)

                        val artworkMessage = when {
                            currentTab != AndroidTab.GAMES -> "" // Don't mention IGDB for non-games
                            hasIgdbArtwork -> " (with IGDB artwork)"
                            else -> " (no IGDB match)"
                        }
                        _androidGamesState.update {
                            it.copy(
                                allApps = allApps,
                                gamesWithoutMetadataCount = metadataCount,
                                successMessage = "Added ${game.appName} to ES-DE$artworkMessage"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun removeAndroidGameFromEsde(game: AndroidGame) {
        viewModelScope.launch {
            val currentTab = _androidGamesState.value.selectedTab
            withContext(Dispatchers.IO) {
                when (val result = androidGamesService.removeShortcutForTab(game, currentTab)) {
                    is ConfigResult.Success -> {
                        // Refresh the apps list
                        val allApps = androidGamesService.getInstalledApps()

                        _androidGamesState.update {
                            it.copy(
                                allApps = allApps,
                                successMessage = "Removed ${game.appName} from ES-DE"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun addAllAndroidGamesToEsde() {
        viewModelScope.launch {
            val currentTab = _androidGamesState.value.selectedTab
            withContext(Dispatchers.IO) {
                // Get filtered games for current tab that aren't already in ES-DE for this tab
                val gamesToAdd = getFilteredAndroidGames().filter { !it.isInEsdeForTab(currentTab) }

                when (val result = androidGamesService.addAllGamesForTab(gamesToAdd, currentTab)) {
                    is ConfigResult.Success -> {
                        val addedCount = result.data
                        // Refresh the apps list
                        val allApps = androidGamesService.getInstalledApps()

                        _androidGamesState.update {
                            it.copy(
                                allApps = allApps,
                                successMessage = "Added $addedCount apps to ES-DE"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun toggleShowAllApps() {
        _androidGamesState.update {
            it.copy(showAllApps = !it.showAllApps)
        }
    }

    fun updateAndroidSearchQuery(query: String) {
        _androidGamesState.update { it.copy(searchQuery = query) }
    }

    fun getFilteredAndroidGames(): List<AndroidGame> {
        val state = _androidGamesState.value
        val query = state.searchQuery.lowercase()

        // If showAllApps is true, show all apps regardless of category (to find miscategorized apps)
        // Otherwise, filter by current tab category
        val tabFiltered = if (state.showAllApps) {
            state.allApps
        } else {
            when (state.selectedTab) {
                AndroidTab.GAMES -> state.allApps.filter { it.isGame && !it.isEmulator }
                AndroidTab.APPS -> state.allApps.filter { !it.isGame && !it.isEmulator }
                AndroidTab.EMULATORS -> state.allApps.filter { it.isEmulator }
            }
        }

        // Then filter by search query
        return if (query.isBlank()) {
            tabFiltered
        } else {
            tabFiltered.filter {
                it.appName.lowercase().contains(query) ||
                it.packageName.lowercase().contains(query)
            }
        }
    }

    fun clearAndroidGamesSuccess() {
        _androidGamesState.update { it.copy(successMessage = null) }
    }

    fun clearAndroidGamesError() {
        _androidGamesState.update { it.copy(error = null) }
    }

    fun selectAndroidTab(tab: AndroidTab) {
        _androidGamesState.update { it.copy(selectedTab = tab, searchQuery = "", showAllApps = false) }
        // Refresh stale entries for the new tab
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val staleEntries = androidGamesService.getStaleEntriesForTab(tab)
                _androidGamesState.update { it.copy(staleEntries = staleEntries) }
            }
        }
    }

    fun getCurrentTabPath(): String? {
        val state = _androidGamesState.value
        return when (state.selectedTab) {
            AndroidTab.GAMES -> state.esdeGamesPath
            AndroidTab.APPS -> state.esdeAppsPath
            AndroidTab.EMULATORS -> state.esdeEmulatorsPath
        }
    }

    fun saveAndroidRomsPath(path: String) {
        val currentTab = _androidGamesState.value.selectedTab
        androidGamesService.setCustomPath(currentTab, path)

        val updatedState = when (currentTab) {
            AndroidTab.GAMES -> _androidGamesState.value.copy(esdeGamesPath = path)
            AndroidTab.APPS -> _androidGamesState.value.copy(esdeAppsPath = path)
            AndroidTab.EMULATORS -> _androidGamesState.value.copy(esdeEmulatorsPath = path)
        }

        _androidGamesState.update {
            updatedState.copy(successMessage = "${currentTab.name.lowercase().replaceFirstChar { it.uppercase() }} ROMs path updated")
        }
    }

    fun removeStaleAndroidEntry(entry: StaleAndroidEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = androidGamesService.removeStaleEntry(entry)) {
                    is ConfigResult.Success -> {
                        // Refresh stale entries list
                        val staleEntries = androidGamesService.getStaleEntries()

                        _androidGamesState.update {
                            it.copy(
                                staleEntries = staleEntries,
                                successMessage = "Removed ${entry.displayName}"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    fun removeAllStaleAndroidEntries() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val staleEntries = _androidGamesState.value.staleEntries

                when (val result = androidGamesService.removeAllStaleEntries(staleEntries)) {
                    is ConfigResult.Success -> {
                        val removedCount = result.data
                        // Refresh stale entries list
                        val newStaleEntries = androidGamesService.getStaleEntries()

                        _androidGamesState.update {
                            it.copy(
                                staleEntries = newStaleEntries,
                                successMessage = "Removed $removedCount stale entries"
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate missing artwork for existing Android shortcuts in the current tab.
     * Uses app icons as cover artwork for items that don't have artwork yet.
     */
    fun generateMissingAndroidArtwork() {
        val currentTab = _androidGamesState.value.selectedTab
        val categoryName = when (currentTab) {
            AndroidTab.GAMES -> "games"
            AndroidTab.APPS -> "apps"
            AndroidTab.EMULATORS -> "emulators"
        }

        viewModelScope.launch {
            _androidGamesState.update { it.copy(isScanning = true) }

            withContext(Dispatchers.IO) {
                when (val result = androidGamesService.generateMissingArtworkForTab(currentTab)) {
                    is ConfigResult.Success -> {
                        val generatedCount = result.data
                        _androidGamesState.update {
                            it.copy(
                                isScanning = false,
                                successMessage = if (generatedCount > 0) {
                                    "Generated $generatedCount missing artwork image(s)"
                                } else {
                                    "All $categoryName already have artwork"
                                }
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _androidGamesState.update {
                            it.copy(
                                isScanning = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    // ========== Metadata Scraping Functions ==========

    /**
     * Scrape metadata for all Windows/Steam games that don't have it.
     */
    /** Show the scrape options dialog before starting a batch scrape. */
    fun showScrapeOptionsDialog() {
        _windowsGamesState.update { it.copy(showScrapeOptionsDialog = true) }
    }

    /** Dismiss the scrape options dialog without scraping. */
    fun dismissScrapeOptionsDialog() {
        _windowsGamesState.update { it.copy(showScrapeOptionsDialog = false) }
    }

    /** Update the scrape options held in state (called as the user toggles checkboxes). */
    fun updateScrapeOptions(options: ScrapeOptions) {
        _windowsGamesState.update { it.copy(scrapeOptions = options) }
    }

    fun scrapeWindowsMetadata() {
        viewModelScope.launch {
            val options = _windowsGamesState.value.scrapeOptions
            _windowsGamesState.update { it.copy(isScraping = true, scrapeProgress = null) }

            withContext(Dispatchers.IO) {
                val successCount = metadataService.scrapeAllMissingSteamMetadata(options) { progress ->
                    _windowsGamesState.update { it.copy(scrapeProgress = progress) }
                }

                metadataService.resetScrapeProgress()

                _windowsGamesState.update {
                    it.copy(
                        isScraping = false,
                        scrapeProgress = null,
                        gamesWithoutMetadataCount = metadataService.getWindowsGamesWithoutMetadataCount(),
                        successMessage = if (successCount > 0) {
                            "Scraped metadata for $successCount games"
                        } else {
                            "No games needed metadata"
                        }
                    )
                }
            }
        }
    }

    /**
     * Re-scrape metadata and artwork for a single Windows game.
     * This will delete existing artwork and download fresh from Steam.
     */
    fun reScrapeWindowsGame(game: WindowsGameShortcut) {
        viewModelScope.launch {
            val options = _windowsGamesState.value.scrapeOptions
            _windowsGamesState.update { it.copy(isScraping = true, pendingReScrapeGame = null) }

            withContext(Dispatchers.IO) {
                val windowsPath = windowsGamesService.getEsdeWindowsPath()

                val success = when {
                    // Steam game: read appId from .steam file
                    windowsPath != null && java.io.File(windowsPath, "${game.id}.steam").exists() -> {
                        val appId = java.io.File(windowsPath, "${game.id}.steam").readText().trim().toIntOrNull()
                        if (appId != null) metadataService.reScrapeWindowsGame(appId, game.id, options) else false
                    }
                    // GOG game: read productId from .gog file
                    windowsPath != null && java.io.File(windowsPath, "${game.id}.gog").exists() -> {
                        val productId = java.io.File(windowsPath, "${game.id}.gog").readText().trim().toLongOrNull()
                        if (productId != null) metadataService.reScrapeGogGame(productId, game.id, options) else false
                    }
                    // Epic game: use game name for IGDB search
                    windowsPath != null && java.io.File(windowsPath, "${game.id}.epic").exists() -> {
                        metadataService.reScrapeEpicGame(game.name, game.id, options)
                    }
                    // Amazon game: use game name for IGDB search
                    windowsPath != null && java.io.File(windowsPath, "${game.id}.amazon").exists() -> {
                        metadataService.reScrapeEpicGame(game.name, game.id, options)
                    }
                    else -> false
                }

                _windowsGamesState.update {
                    it.copy(
                        isScraping = false,
                        gamesWithoutMetadataCount = metadataService.getWindowsGamesWithoutMetadataCount(),
                        successMessage = if (success) {
                            "Re-scraped metadata for ${game.name}"
                        } else {
                            "Failed to re-scrape metadata for ${game.name}"
                        }
                    )
                }
            }
        }
    }

    /**
     * Set the pending re-scrape game (shows confirmation dialog).
     */
    fun setPendingReScrapeWindowsGame(game: WindowsGameShortcut?) {
        _windowsGamesState.update { it.copy(pendingReScrapeGame = game) }
    }

    /**
     * Clear the pending re-scrape game.
     */
    fun clearPendingReScrapeWindowsGame() {
        _windowsGamesState.update { it.copy(pendingReScrapeGame = null) }
    }

    /**
     * Scrape metadata for Android games in the current tab that don't have it.
     * Uses interactive scraping that pauses when a game is not found.
     */
    fun scrapeAndroidMetadata() {
        val currentTab = _androidGamesState.value.selectedTab

        if (!metadataService.hasIgdbCredentials()) {
            _androidGamesState.update {
                it.copy(error = "IGDB credentials not configured. Go to Settings to set up IGDB API access.")
            }
            return
        }

        viewModelScope.launch {
            _androidGamesState.update { it.copy(isScraping = true, scrapeProgress = null) }

            withContext(Dispatchers.IO) {
                metadataService.startAndroidMetadataScraping(currentTab) { progress ->
                    _androidGamesState.update { it.copy(scrapeProgress = progress) }

                    // Check if scraping is complete (no pending user input and all done)
                    if (progress.isComplete && progress.pendingUserInput == null) {
                        val stillMissing = metadataService.getAndroidGamesWithoutMetadataCount(currentTab)
                        val debugInfo = progress.lastResult ?: ""
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                gamesWithoutMetadataCount = stillMissing,
                                successMessage = if (progress.successful > 0) {
                                    "Scraped: ${progress.successful} OK, ${progress.failed} failed. Still missing: $stillMissing. Last: $debugInfo"
                                } else if (progress.failed > 0) {
                                    "Scrape failed for ${progress.failed} apps. Still missing: $stillMissing. Last: $debugInfo"
                                } else {
                                    "No apps needed metadata"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Retry scraping for the current game with a refined search term.
     */
    fun retryMetadataWithRefinedSearch(refinedSearchTerm: String) {
        val currentTab = _androidGamesState.value.selectedTab

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                metadataService.retryWithRefinedSearch(refinedSearchTerm) { progress ->
                    _androidGamesState.update { it.copy(scrapeProgress = progress) }

                    // Check if scraping is complete
                    if (progress.isComplete && progress.pendingUserInput == null) {
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                gamesWithoutMetadataCount = metadataService.getAndroidGamesWithoutMetadataCount(currentTab),
                                successMessage = if (progress.successful > 0) {
                                    "Scraped metadata for ${progress.successful} apps"
                                } else {
                                    "No apps needed metadata"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Skip the current game that wasn't found and continue scraping.
     */
    fun skipCurrentMetadataScrape() {
        val currentTab = _androidGamesState.value.selectedTab

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                metadataService.skipCurrentGame { progress ->
                    _androidGamesState.update { it.copy(scrapeProgress = progress) }

                    // Check if scraping is complete
                    if (progress.isComplete && progress.pendingUserInput == null) {
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                gamesWithoutMetadataCount = metadataService.getAndroidGamesWithoutMetadataCount(currentTab),
                                successMessage = if (progress.successful > 0) {
                                    "Scraped metadata for ${progress.successful} apps"
                                } else {
                                    "No apps needed metadata"
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Re-scrape metadata and artwork for a single Android game.
     * @param game The game to re-scrape
     * @param customSearchTerm Optional custom search term to use instead of app name
     */
    fun reScrapeAndroidGame(game: AndroidGame, customSearchTerm: String? = null) {
        val currentTab = _androidGamesState.value.selectedTab

        if (!metadataService.hasIgdbCredentials()) {
            _androidGamesState.update {
                it.copy(error = "IGDB credentials not configured. Go to Settings to set up IGDB API access.")
            }
            return
        }

        viewModelScope.launch {
            _androidGamesState.update { it.copy(isScraping = true, pendingReScrapeGame = null) }

            withContext(Dispatchers.IO) {
                when (val result = metadataService.reScrapeAndroidGame(game, currentTab, customSearchTerm)) {
                    is MetadataResult.Success -> {
                        val count = metadataService.getAndroidGamesWithoutMetadataCount(currentTab)
                        val videoInfo = if (!result.metadata.video.isNullOrBlank()) {
                            ", video: ${result.metadata.video.take(50)}..."
                        } else {
                            ", video: none"
                        }
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                gamesWithoutMetadataCount = count,
                                pendingReScrapeGame = null,
                                successMessage = "Re-scraped ${game.appName} (${result.metadata.name}$videoInfo)"
                            )
                        }
                    }
                    is MetadataResult.NotFound -> {
                        // Show dialog to let user refine the search
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                pendingReScrapeGame = game
                            )
                        }
                    }
                    is MetadataResult.Error -> {
                        _androidGamesState.update {
                            it.copy(
                                isScraping = false,
                                pendingReScrapeGame = null,
                                error = "Error scraping ${game.appName}: ${result.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Clear the pending re-scrape game (dismiss dialog without action).
     */
    fun clearPendingReScrapeGame() {
        _androidGamesState.update { it.copy(pendingReScrapeGame = null) }
    }

    /**
     * Update the count of games without metadata for Windows.
     */
    fun updateWindowsMetadataCount() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val count = metadataService.getWindowsGamesWithoutMetadataCount()
                _windowsGamesState.update { it.copy(gamesWithoutMetadataCount = count) }
            }
        }
    }

    /**
     * Update the count of games without metadata for the current Android tab.
     */
    fun updateAndroidMetadataCount() {
        val currentTab = _androidGamesState.value.selectedTab
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val count = metadataService.getAndroidGamesWithoutMetadataCount(currentTab)
                val hasCredentials = metadataService.hasIgdbCredentials()
                _androidGamesState.update {
                    it.copy(
                        gamesWithoutMetadataCount = count,
                        hasIgdbCredentials = hasCredentials
                    )
                }
            }
        }
    }

    /**
     * Check if IGDB credentials are configured.
     */
    fun hasIgdbCredentials(): Boolean = metadataService.hasIgdbCredentials()

    /**
     * Set IGDB credentials.
     */
    fun setIgdbCredentials(clientId: String, clientSecret: String) {
        metadataService.setIgdbCredentials(clientId, clientSecret)
        _androidGamesState.update { it.copy(hasIgdbCredentials = true) }
        _vitaGamesState.update { it.copy(hasIgdbCredentials = true) }
    }

    /**
     * Get current IGDB client ID (for display).
     */
    fun getIgdbClientId(): String? = metadataService.getIgdbClientId()

    /**
     * Clear IGDB credentials.
     */
    fun clearIgdbCredentials() {
        metadataService.clearIgdbCredentials()
        _androidGamesState.update { it.copy(hasIgdbCredentials = false) }
        _vitaGamesState.update { it.copy(hasIgdbCredentials = false) }
    }

    // ========== PS Vita Games Functions ==========

    fun loadVitaGames() {
        viewModelScope.launch {
            _vitaGamesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                val vitaPath = vitaGamesService.getEsdeVitaPath()
                val games = vitaGamesService.scanVitaGames()
                val count = metadataService.getVitaGamesWithoutMetadataCount()
                val hasCredentials = metadataService.hasIgdbCredentials()

                _vitaGamesState.update {
                    it.copy(
                        isLoading = false,
                        games = games,
                        esdeVitaPath = vitaPath,
                        gamesWithoutMetadataCount = count,
                        hasIgdbCredentials = hasCredentials
                    )
                }
            }
        }
    }

    fun scanVitaGames() {
        viewModelScope.launch {
            _vitaGamesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                val games = vitaGamesService.scanVitaGames()

                _vitaGamesState.update {
                    it.copy(
                        isLoading = false,
                        games = games,
                        successMessage = "Found ${games.size} PS Vita game${if (games.size != 1) "s" else ""}"
                    )
                }
            }
        }
    }

    fun saveVitaRomsPath(path: String) {
        vitaGamesService.setCustomPath(path)
        // Update path immediately, then rescan games from the new location
        viewModelScope.launch {
            _vitaGamesState.update { it.copy(isLoading = true, esdeVitaPath = path) }
            withContext(Dispatchers.IO) {
                val games = vitaGamesService.scanVitaGames()
                val count = metadataService.getVitaGamesWithoutMetadataCount()
                _vitaGamesState.update {
                    it.copy(
                        isLoading = false,
                        games = games,
                        gamesWithoutMetadataCount = count,
                        successMessage = "PS Vita ROM path updated"
                    )
                }
            }
        }
    }

    fun addVitaGameToEsde(titleId: String, displayName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val vitaPath = vitaGamesService.getEsdeVitaPath()
                    ?: run {
                        _vitaGamesState.update { it.copy(error = "Could not determine psvita ROM path") }
                        return@withContext
                    }

                when (val result = vitaGamesService.createVitaShortcut(titleId, displayName, vitaPath)) {
                    is ConfigResult.Success -> {
                        val game = result.data

                        // Auto-scrape if we have ScreenScraper credentials
                        var metadataScraped = false
                        if (metadataService.hasIgdbCredentials()) {
                            try {
                                metadataScraped = metadataService.scrapeAndSaveVitaMetadata(
                                    game,
                                    _vitaGamesState.value.scrapeOptions
                                )
                            } catch (e: Exception) {
                                android.util.Log.e("MainViewModel", "Vita metadata scrape exception: ${e.message}", e)
                            }
                        }

                        val message = if (metadataScraped) {
                            "Added ${game.displayName} with metadata"
                        } else {
                            "Added ${game.displayName}"
                        }

                        val games = vitaGamesService.scanVitaGames()
                        _vitaGamesState.update {
                            it.copy(
                                games = games,
                                successMessage = message,
                                gamesWithoutMetadataCount = metadataService.getVitaGamesWithoutMetadataCount()
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _vitaGamesState.update { it.copy(error = result.message) }
                    }
                }
            }
        }
    }

    fun removeVitaGameFromEsde(game: VitaGame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = vitaGamesService.removeVitaGame(game)) {
                    is ConfigResult.Success -> {
                        // Remove gamelist entry
                        val gameFileName = java.io.File(game.filePath).nameWithoutExtension
                        metadataService.removeVitaGameMetadata("./$gameFileName.psvita")

                        val games = vitaGamesService.scanVitaGames()
                        _vitaGamesState.update {
                            it.copy(
                                games = games,
                                successMessage = "Removed ${game.displayName}",
                                gamesWithoutMetadataCount = metadataService.getVitaGamesWithoutMetadataCount()
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _vitaGamesState.update { it.copy(error = result.message) }
                    }
                }
            }
        }
    }

    fun updateVitaSearchQuery(query: String) {
        _vitaGamesState.update { it.copy(searchQuery = query) }
    }

    fun searchVitaGames(query: String) {
        if (query.length < 2) return
        viewModelScope.launch {
            _vitaGamesState.update { it.copy(isSearching = true, error = null) }

            withContext(Dispatchers.IO) {
                val results = try {
                    when (val result = metadataService.searchVitaGame(query)) {
                        is MetadataResult.Success -> listOf(
                            VitaSearchResult(
                                ssId = "igdb",
                                name = result.metadata.name,
                                year = result.metadata.releasedate?.take(4)
                            )
                        )
                        is MetadataResult.NotFound -> emptyList()
                        is MetadataResult.Error -> {
                            _vitaGamesState.update {
                                it.copy(isSearching = false, error = "IGDB search failed: ${result.message}")
                            }
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "IGDB search error: ${e.message}", e)
                    _vitaGamesState.update {
                        it.copy(isSearching = false, error = "Search failed: ${e.message}")
                    }
                    return@withContext
                }

                _vitaGamesState.update {
                    it.copy(isSearching = false, searchResults = results)
                }
            }
        }
    }

    fun clearVitaSearchResults() {
        _vitaGamesState.update {
            it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false)
        }
    }

    fun updateVitaMetadataCount() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val count = metadataService.getVitaGamesWithoutMetadataCount()
                val hasCredentials = metadataService.hasIgdbCredentials()
                _vitaGamesState.update {
                    it.copy(
                        gamesWithoutMetadataCount = count,
                        hasIgdbCredentials = hasCredentials
                    )
                }
            }
        }
    }

    fun scrapeVitaMetadata() {
        viewModelScope.launch {
            val options = _vitaGamesState.value.scrapeOptions
            _vitaGamesState.update { it.copy(isScraping = true, scrapeProgress = null) }

            withContext(Dispatchers.IO) {
                val successCount = metadataService.scrapeAllMissingVitaMetadata(options) { progress ->
                    _vitaGamesState.update { it.copy(scrapeProgress = progress) }
                }

                metadataService.resetScrapeProgress()

                val games = vitaGamesService.scanVitaGames()
                _vitaGamesState.update {
                    it.copy(
                        isScraping = false,
                        scrapeProgress = null,
                        games = games,
                        gamesWithoutMetadataCount = metadataService.getVitaGamesWithoutMetadataCount(),
                        successMessage = if (successCount > 0) {
                            "Scraped metadata for $successCount game${if (successCount != 1) "s" else ""}"
                        } else {
                            "No games needed metadata"
                        }
                    )
                }
            }
        }
    }

    fun reScrapeVitaGame(game: VitaGame, searchTerm: String? = null) {
        viewModelScope.launch {
            val options = _vitaGamesState.value.scrapeOptions
            _vitaGamesState.update { it.copy(isScraping = true, pendingReScrapeGame = null) }

            withContext(Dispatchers.IO) {
                // Use search term override if provided, otherwise use display name
                val gameToScrape = if (searchTerm != null) {
                    game.copy(displayName = searchTerm)
                } else {
                    game
                }

                var apiError: String? = null
                val success = try {
                    metadataService.scrapeAndSaveVitaMetadata(gameToScrape, options)
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "Vita re-scrape error: ${e.message}", e)
                    apiError = e.message
                    false
                }

                val games = vitaGamesService.scanVitaGames()
                _vitaGamesState.update {
                    it.copy(
                        isScraping = false,
                        games = games,
                        gamesWithoutMetadataCount = metadataService.getVitaGamesWithoutMetadataCount(),
                        successMessage = if (success) "Re-scraped metadata for ${game.displayName}" else null,
                        error = if (!success) apiError ?: "No metadata found for \"${game.displayName}\" on IGDB" else null
                    )
                }
            }
        }
    }

    fun setPendingReScrapeVitaGame(game: VitaGame?) {
        _vitaGamesState.update { it.copy(pendingReScrapeGame = game) }
    }

    fun clearPendingReScrapeVitaGame() {
        _vitaGamesState.update { it.copy(pendingReScrapeGame = null) }
    }

    fun clearVitaSuccess() {
        _vitaGamesState.update { it.copy(successMessage = null) }
    }

    fun clearVitaError() {
        _vitaGamesState.update { it.copy(error = null) }
    }

    fun showVitaScrapeOptionsDialog() {
        _vitaGamesState.update { it.copy(showScrapeOptionsDialog = true) }
    }

    fun dismissVitaScrapeOptionsDialog() {
        _vitaGamesState.update { it.copy(showScrapeOptionsDialog = false) }
    }

    fun updateVitaScrapeOptions(options: ScrapeOptions) {
        _vitaGamesState.update { it.copy(scrapeOptions = options, showScrapeOptionsDialog = false) }
    }

    /** Look up the best-matching PS Vita Title ID for a given game name from the bundled database. */
    fun lookupVitaTitleId(gameName: String): String? = vitaTitleDatabase.findBestMatch(gameName)

    // ========== Games Hub Counts ==========

    fun getWindowsGamesInEsdeCount(): Int {
        return _windowsGamesState.value.launchers.sumOf { launcher ->
            launcher.games.count { it.isInEsde }
        }
    }

    fun getAndroidGamesInEsdeCount(): Int {
        return _androidGamesState.value.allApps.count { it.isInEsde }
    }

    fun getVitaGamesInEsdeCount(): Int {
        return _vitaGamesState.value.games.size
    }

    /**
     * Get the artwork path for a PS Vita game (cover image from ES-DE media directory).
     */
    fun getVitaGameArtworkPath(gameId: String): String? {
        return vitaGamesService.getArtworkPath(gameId)
    }

    /**
     * Reclassify an app to a different category (Game, App, or Emulator)
     */
    fun reclassifyApp(game: AndroidGame, newCategory: AppCategory) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Set the classification override
                androidGamesService.setClassificationOverride(game.packageName, newCategory)

                // Reload the app list to reflect the change
                val apps = androidGamesService.getInstalledApps()
                val staleEntries = androidGamesService.getStaleEntriesForTab(_androidGamesState.value.selectedTab)

                _androidGamesState.update {
                    it.copy(
                        allApps = apps,
                        staleEntries = staleEntries,
                        successMessage = "Reclassified '${game.appName}' as ${newCategory.name.lowercase().replaceFirstChar { c -> c.uppercase() }}"
                    )
                }
            }
        }
    }

    /**
     * Remove a classification override and revert to auto-detection
     */
    fun resetAppClassification(game: AndroidGame) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                androidGamesService.removeClassificationOverride(game.packageName)

                // Reload the app list to reflect the change
                val apps = androidGamesService.getInstalledApps()
                val staleEntries = androidGamesService.getStaleEntriesForTab(_androidGamesState.value.selectedTab)

                _androidGamesState.update {
                    it.copy(
                        allApps = apps,
                        staleEntries = staleEntries,
                        successMessage = "Reset classification for '${game.appName}'"
                    )
                }
            }
        }
    }

    // ========== Profile Management Functions ==========

    /**
     * Load all profiles and update state.
     */
    fun loadProfiles() {
        viewModelScope.launch {
            _profilesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                val container = profileService.loadProfiles()
                val activeProfile = profileService.getActiveProfile()
                val currentFingerprint = deviceIdentificationService.getDeviceFingerprint()
                val deviceName = deviceIdentificationService.getDeviceDisplayName()
                val matchingProfiles = profileService.getProfilesForCurrentDevice()
                val esdeConfigured = profileService.isEsdeConfigured()

                _profilesState.update {
                    it.copy(
                        isLoading = false,
                        profiles = container.profiles,
                        activeProfile = activeProfile,
                        currentDeviceFingerprint = currentFingerprint,
                        currentDeviceName = deviceName,
                        matchingProfiles = matchingProfiles,
                        esdeConfigured = esdeConfigured
                    )
                }
            }
        }
    }

    /**
     * Create a new profile from current configuration.
     */
    fun createProfile(name: String, associateWithDevice: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.createProfile(name, associateWithDevice)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(
                                successMessage = "Profile '$name' created",
                                showCreateDialog = false
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Load/restore a profile (replaces current configuration).
     */
    fun loadProfile(profileId: String) {
        viewModelScope.launch {
            _profilesState.update { it.copy(isLoading = true) }

            withContext(Dispatchers.IO) {
                when (val result = profileService.loadProfile(profileId)) {
                    is ConfigResult.Success -> {
                        val loadResult = result.data

                        // Reload profiles state
                        loadProfiles()

                        // Reload other data to reflect profile changes
                        loadData()
                        loadAndroidGames()
                        loadWindowsGames()
                        loadVitaGames()

                        // Refresh IGDB credentials state in case the profile restored them
                        val hasCredentials = metadataService.hasIgdbCredentials()
                        _androidGamesState.update { it.copy(hasIgdbCredentials = hasCredentials) }
                        _vitaGamesState.update { it.copy(hasIgdbCredentials = hasCredentials) }

                        // Build detailed success message
                        val message = loadResult.getSummary()

                        _profilesState.update {
                            it.copy(
                                isLoading = false,
                                successMessage = message,
                                showLoadConfirmDialog = false,
                                profileToEdit = null
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Save current configuration to an existing profile.
     */
    fun saveCurrentToProfile(profileId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.saveToProfile(profileId)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(successMessage = "Profile saved")
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Rename a profile.
     */
    fun renameProfile(profileId: String, newName: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.renameProfile(profileId, newName)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(
                                successMessage = "Profile renamed",
                                showRenameDialog = false,
                                profileToEdit = null
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Delete a profile.
     */
    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.deleteProfile(profileId)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(
                                successMessage = "Profile deleted",
                                showDeleteConfirmDialog = false,
                                profileToEdit = null
                            )
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Associate/disassociate a profile with the current device.
     */
    fun setProfileDeviceAssociation(profileId: String, associate: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.setProfileDeviceAssociation(profileId, associate)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(successMessage = if (associate) "Profile linked to this device" else "Profile unlinked from device")
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    /**
     * Toggle auto-save for a profile.
     */
    fun toggleProfileAutoSave(profileId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (val result = profileService.setProfileAutoSave(profileId, enabled)) {
                    is ConfigResult.Success -> {
                        loadProfiles()
                        _profilesState.update {
                            it.copy(successMessage = if (enabled) "Auto-save enabled" else "Auto-save disabled")
                        }
                    }
                    is ConfigResult.Error -> {
                        _profilesState.update {
                            it.copy(error = result.message)
                        }
                    }
                }
            }
        }
    }

    // Profile dialog state management
    fun showCreateProfileDialog(show: Boolean) {
        _profilesState.update { it.copy(showCreateDialog = show) }
    }

    fun showRenameDialog(profile: Profile?) {
        _profilesState.update {
            it.copy(showRenameDialog = profile != null, profileToEdit = profile)
        }
    }

    fun showDeleteConfirmDialog(profile: Profile?) {
        _profilesState.update {
            it.copy(showDeleteConfirmDialog = profile != null, profileToEdit = profile)
        }
    }

    fun showLoadConfirmDialog(profile: Profile?) {
        _profilesState.update {
            it.copy(showLoadConfirmDialog = profile != null, profileToEdit = profile)
        }
    }

    fun dismissDeviceSwitchPrompt() {
        _profilesState.update { it.copy(showDeviceSwitchPrompt = false) }
    }

    fun clearProfilesError() {
        _profilesState.update { it.copy(error = null) }
    }

    fun clearProfilesSuccess() {
        _profilesState.update { it.copy(successMessage = null) }
    }

    /**
     * Check for device switch on app launch.
     * Shows prompt if profiles exist but none match current device.
     */
    fun checkForDeviceSwitch() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val container = profileService.loadProfiles()
                if (container.profiles.isEmpty()) return@withContext

                val matchingProfiles = profileService.getProfilesForCurrentDevice()
                val activeProfile = profileService.getActiveProfile()

                // If there are profiles but none for this device
                if (matchingProfiles.isEmpty() && container.profiles.isNotEmpty()) {
                    _profilesState.update {
                        it.copy(
                            showDeviceSwitchPrompt = true,
                            matchingProfiles = emptyList()
                        )
                    }
                }
                // If there's a profile for this device but it's not active
                else if (matchingProfiles.isNotEmpty() && activeProfile == null) {
                    if (matchingProfiles.size == 1) {
                        // Auto-load the single matching profile
                        loadProfile(matchingProfiles.first().id)
                    } else {
                        // Multiple profiles - let user choose
                        _profilesState.update {
                            it.copy(
                                showDeviceSwitchPrompt = true,
                                matchingProfiles = matchingProfiles
                            )
                        }
                    }
                }
            }
        }
    }

}
