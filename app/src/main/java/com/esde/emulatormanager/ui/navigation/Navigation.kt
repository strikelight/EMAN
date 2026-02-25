package com.esde.emulatormanager.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.esde.emulatormanager.data.model.AndroidGamesUiState
import com.esde.emulatormanager.data.model.CustomEmulatorMapping
import com.esde.emulatormanager.data.model.InstalledEmulator
import com.esde.emulatormanager.data.model.MainUiState
import com.esde.emulatormanager.data.model.ProfilesUiState
import com.esde.emulatormanager.data.model.VitaGamesUiState
import com.esde.emulatormanager.data.model.WindowsGamesUiState
import com.esde.emulatormanager.ui.screens.*
import com.esde.emulatormanager.ui.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Emulators : Screen("emulators")
    data object Games : Screen("games")
    data object WindowsGames : Screen("windows_games")
    data object AndroidGames : Screen("android_games")
    data object VitaGames : Screen("vita_games")
    data object AddVitaGame : Screen("add_vita_game")
    data object Profiles : Screen("profiles")
    data object AddSteamGame : Screen("add_steam_game")
    data object AddGogGame : Screen("add_gog_game")
    data object ImportEpicGame : Screen("import_epic_game")
    data object AddCustomEmulator : Screen("add_custom_emulator")
    data object ConfigureCustomEmulator : Screen("configure_custom_emulator")
    data object About : Screen("about")
    data object SystemDetail : Screen("system/{systemId}") {
        fun createRoute(systemId: String) = "system/$systemId"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val title: String,
    val shortTitle: String,  // Abbreviated label for narrow screens
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(
        screen = Screen.Home,
        title = "Systems",
        shortTitle = "Systems",
        selectedIcon = Icons.Filled.Gamepad,
        unselectedIcon = Icons.Outlined.Gamepad
    ),
    BottomNavItem(
        screen = Screen.Emulators,
        title = "Emulators",
        shortTitle = "Emus",
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps
    ),
    BottomNavItem(
        screen = Screen.Games,
        title = "Games",
        shortTitle = "Games",
        selectedIcon = Icons.Filled.VideogameAsset,
        unselectedIcon = Icons.Outlined.VideogameAsset
    ),
    BottomNavItem(
        screen = Screen.Profiles,
        title = "Profiles",
        shortTitle = "Profiles",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    ),
    BottomNavItem(
        screen = Screen.About,
        title = "About",
        shortTitle = "About",
        selectedIcon = Icons.Filled.Info,
        unselectedIcon = Icons.Outlined.Info
    )
)

/**
 * Adaptive nav label state based on screen width per tab.
 * Material 3 NavigationBarItem clips label text beyond ~7 chars at 11sp regardless of container
 * width, so we both shrink the font AND use shortened labels on cramped screens.
 *
 * S24 Ultra example: ~412dp wide / 6 tabs = ~68dp/tab → useShortTitles = true, 10sp
 * Typical 360dp phone: ~60dp/tab → useShortTitles = true, 9sp
 * Tablet / large screen: ~80dp/tab → useShortTitles = false, 11sp
 */
data class NavLabelStyle(val fontSize: TextUnit, val useShortTitles: Boolean)

@Composable
private fun adaptiveNavLabelStyle(): NavLabelStyle {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val widthPerTab = screenWidthDp / bottomNavItems.size
    return when {
        widthPerTab >= 80 -> NavLabelStyle(11.sp, false)  // Tablets / large phones — full labels fit
        widthPerTab >= 70 -> NavLabelStyle(10.sp, true)   // S24 Ultra, large phones (~412dp)
        widthPerTab >= 58 -> NavLabelStyle(9.sp,  true)   // Mid-range phones (~360–400dp)
        else              -> NavLabelStyle(8.sp,  true)   // Compact / small devices
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()
    val installedEmulators by viewModel.installedEmulators.collectAsState()
    val allApps by viewModel.allInstalledApps.collectAsState()
    val customEmulators by viewModel.customEmulators.collectAsState()
    val windowsGamesState by viewModel.windowsGamesState.collectAsState()
    val androidGamesState by viewModel.androidGamesState.collectAsState()
    val profilesState by viewModel.profilesState.collectAsState()
    val vitaGamesState by viewModel.vitaGamesState.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // State for selected emulator when configuring
    var selectedEmulatorForConfig by remember { mutableStateOf<InstalledEmulator?>(null) }

    // Show navigation on main screens and all Games sub-screens
    val gamesSubRoutes = listOf(
        Screen.Games.route,
        Screen.WindowsGames.route,
        Screen.AndroidGames.route,
        Screen.VitaGames.route,
        Screen.AddVitaGame.route
    )
    val showNavigation = currentDestination?.route in listOf(
        Screen.Home.route,
        Screen.Emulators.route,
        Screen.Profiles.route,
        Screen.About.route
    ) + gamesSubRoutes

    // Detect landscape mode using configuration
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // Navigation click handler
    // Special case: if tapping "Games" while already in a Games sub-section,
    // navigate back to the Games hub instead of doing a full root pop.
    val onNavItemClick: (Screen) -> Unit = { screen ->
        val currentRoute = currentDestination?.route
        val isInGamesSubSection = currentRoute in gamesSubRoutes && currentRoute != Screen.Games.route

        if (screen == Screen.Games && isInGamesSubSection) {
            // Pop back to the Games hub
            navController.navigate(Screen.Games.route) {
                popUpTo(Screen.Games.route) { inclusive = false }
                launchSingleTop = true
            }
        } else {
            navController.navigate(screen.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    if (isLandscape && showNavigation) {
        // Landscape: Use NavigationRail on the left side
        Row(modifier = modifier.fillMaxSize()) {
            NavigationRail {
                // Wrap in a scrollable column so all items are reachable on short screens
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = when (item.screen) {
                            Screen.Games -> currentDestination?.route in gamesSubRoutes
                            else -> currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                        }

                        NavigationRailItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            selected = selected,
                            onClick = { onNavItemClick(item.screen) }
                        )
                    }
                }
            }

            // Main content
            Box(modifier = Modifier.weight(1f)) {
                NavHostContent(
                    navController = navController,
                    viewModel = viewModel,
                    uiState = uiState,
                    installedEmulators = installedEmulators,
                    allApps = allApps,
                    customEmulators = customEmulators,
                    windowsGamesState = windowsGamesState,
                    androidGamesState = androidGamesState,
                    profilesState = profilesState,
                    vitaGamesState = vitaGamesState,
                    selectedEmulatorForConfig = selectedEmulatorForConfig,
                    onSelectedEmulatorChange = { selectedEmulatorForConfig = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // Portrait or non-main screens: Use bottom navigation bar
        Scaffold(
            bottomBar = {
                if (showNavigation) {
                    NavigationBar {
                        val labelStyle = adaptiveNavLabelStyle()
                        bottomNavItems.forEach { item ->
                            val selected = when (item.screen) {
                                Screen.Games -> currentDestination?.route in gamesSubRoutes
                                else -> currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                            }
                            val label = if (labelStyle.useShortTitles) item.shortTitle else item.title

                            NavigationBarItem(
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.title
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        fontSize = labelStyle.fontSize,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                selected = selected,
                                onClick = { onNavItemClick(item.screen) }
                            )
                        }
                    }
                }
            },
            modifier = modifier
        ) { paddingValues ->
            NavHostContent(
                navController = navController,
                viewModel = viewModel,
                uiState = uiState,
                installedEmulators = installedEmulators,
                allApps = allApps,
                customEmulators = customEmulators,
                windowsGamesState = windowsGamesState,
                androidGamesState = androidGamesState,
                profilesState = profilesState,
                vitaGamesState = vitaGamesState,
                selectedEmulatorForConfig = selectedEmulatorForConfig,
                onSelectedEmulatorChange = { selectedEmulatorForConfig = it },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun NavHostContent(
    navController: NavHostController,
    viewModel: MainViewModel,
    uiState: MainUiState,
    installedEmulators: List<InstalledEmulator>,
    allApps: List<InstalledEmulator>,
    customEmulators: List<CustomEmulatorMapping>,
    windowsGamesState: WindowsGamesUiState,
    androidGamesState: AndroidGamesUiState,
    profilesState: ProfilesUiState,
    vitaGamesState: VitaGamesUiState,
    selectedEmulatorForConfig: InstalledEmulator?,
    onSelectedEmulatorChange: (InstalledEmulator?) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                isLoading = uiState.isLoading,
                systems = viewModel.getFilteredSystems(),
                searchQuery = uiState.searchQuery,
                selectedCategory = uiState.selectedCategory,
                installedEmulatorsCount = installedEmulators.size + customEmulators.size,
                esdeConfigPath = uiState.esdeConfigPath,
                successMessage = uiState.successMessage,
                errorMessage = uiState.error,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onCategorySelected = viewModel::selectCategory,
                onSystemClick = { config ->
                    navController.navigate(Screen.SystemDetail.createRoute(config.system.name))
                },
                onRefresh = viewModel::refreshData,
                onBackup = viewModel::backupConfiguration,
                onDismissSuccess = viewModel::clearSuccessMessage,
                onDismissError = viewModel::clearError
            )
        }

        composable(Screen.Emulators.route) {
            InstalledEmulatorsScreenWithCustom(
                knownEmulators = installedEmulators,
                customEmulators = customEmulators,
                allApps = allApps,
                onAddCustomEmulator = {
                    viewModel.loadAllApps()
                    navController.navigate(Screen.AddCustomEmulator.route)
                },
                onRemoveCustomEmulator = { packageName ->
                    viewModel.removeCustomEmulator(packageName)
                },
                onEditCustomEmulator = { mapping ->
                    // Find the app info for this mapping
                    val appInfo = allApps.find { it.packageName == mapping.packageName }
                    if (appInfo != null) {
                        onSelectedEmulatorChange(appInfo)
                        navController.navigate(Screen.ConfigureCustomEmulator.route)
                    }
                }
            )
        }

        composable(Screen.WindowsGames.route) {
            // Load Windows games and check launcher configurations when navigating to this screen
            LaunchedEffect(Unit) {
                viewModel.loadWindowsGames()
                viewModel.checkAllLauncherConfigurations()
                viewModel.updateWindowsMetadataCount()
            }

            WindowsGamesScreen(
                uiState = windowsGamesState,
                onScanGames = viewModel::scanWindowsGames,
                onAddGame = viewModel::addWindowsGameToEsde,
                onRemoveGame = viewModel::removeWindowsGameFromEsde,
                onAddAllFromLauncher = viewModel::addAllWindowsGamesFromLauncher,
                onAddSteamGame = {
                    viewModel.clearSteamSearchResults()
                    navController.navigate(Screen.AddSteamGame.route)
                },
                onAddGogGame = {
                    viewModel.clearGogSearchResults()
                    navController.navigate(Screen.AddGogGame.route)
                },
                onImportEpicGame = {
                    viewModel.clearEpicImportState()
                    navController.navigate(Screen.ImportEpicGame.route)
                },
                onDismissSuccess = viewModel::clearWindowsGamesSuccess,
                onDismissError = viewModel::clearWindowsGamesError,
                onShowPathSelectionDialog = viewModel::setShowPathSelectionDialog,
                onSavePath = viewModel::saveWindowsRomsPath,
                onConfigureGameHubLite = viewModel::configureGameHubLiteForWindows,
                onScrapeMetadata = viewModel::scrapeWindowsMetadata,
                onShowScrapeSettings = viewModel::showScrapeOptionsDialog,
                onDismissScrapeSettings = viewModel::dismissScrapeOptionsDialog,
                onUpdateScrapeOptions = viewModel::updateScrapeOptions,
                onSetPendingReScrapeGame = viewModel::setPendingReScrapeWindowsGame,
                onReScrapeGame = viewModel::reScrapeWindowsGame,
                onClearPendingReScrape = viewModel::clearPendingReScrapeWindowsGame,
                getArtworkPath = viewModel::getWindowsGameArtworkPath
            )
        }

        composable(Screen.AndroidGames.route) {
            // Load Android games when navigating to this screen
            LaunchedEffect(Unit) {
                viewModel.loadAndroidGames()
                viewModel.updateAndroidMetadataCount()
            }

            AndroidGamesScreen(
                uiState = androidGamesState,
                filteredGames = viewModel.getFilteredAndroidGames(),
                currentTabPath = viewModel.getCurrentTabPath(),
                onScanGames = viewModel::scanAndroidGames,
                onAddGame = viewModel::addAndroidGameToEsde,
                onRemoveGame = viewModel::removeAndroidGameFromEsde,
                onAddAllGames = viewModel::addAllAndroidGamesToEsde,
                onTabSelected = { tab ->
                    viewModel.selectAndroidTab(tab)
                    viewModel.updateAndroidMetadataCount()
                },
                onSearchQueryChange = viewModel::updateAndroidSearchQuery,
                onToggleShowAllApps = viewModel::toggleShowAllApps,
                onReclassifyApp = viewModel::reclassifyApp,
                onResetClassification = viewModel::resetAppClassification,
                onDismissSuccess = viewModel::clearAndroidGamesSuccess,
                onDismissError = viewModel::clearAndroidGamesError,
                onSavePath = viewModel::saveAndroidRomsPath,
                onRemoveStaleEntry = viewModel::removeStaleAndroidEntry,
                onRemoveAllStaleEntries = viewModel::removeAllStaleAndroidEntries,
                onGenerateMissingArtwork = viewModel::generateMissingAndroidArtwork,
                onScrapeMetadata = viewModel::scrapeAndroidMetadata,
                onRetryMetadataSearch = viewModel::retryMetadataWithRefinedSearch,
                onSkipMetadataScrape = viewModel::skipCurrentMetadataScrape,
                onSetIgdbCredentials = viewModel::setIgdbCredentials,
                onReScrapeGame = { game -> viewModel.reScrapeAndroidGame(game) },
                onReScrapeGameWithTerm = { game, term -> viewModel.reScrapeAndroidGame(game, term) },
                onClearPendingReScrape = viewModel::clearPendingReScrapeGame,
                currentIgdbClientId = viewModel.getIgdbClientId()
            )
        }

        composable(Screen.Profiles.route) {
            LaunchedEffect(Unit) {
                viewModel.loadProfiles()
            }

            ProfilesScreen(
                uiState = profilesState,
                onCreateProfile = viewModel::createProfile,
                onLoadProfile = viewModel::loadProfile,
                onSaveToProfile = viewModel::saveCurrentToProfile,
                onRenameProfile = viewModel::renameProfile,
                onDeleteProfile = viewModel::deleteProfile,
                onToggleDeviceAssociation = viewModel::setProfileDeviceAssociation,
                onShowCreateDialog = viewModel::showCreateProfileDialog,
                onShowRenameDialog = viewModel::showRenameDialog,
                onShowDeleteDialog = viewModel::showDeleteConfirmDialog,
                onShowLoadDialog = viewModel::showLoadConfirmDialog,
                onDismissDevicePrompt = viewModel::dismissDeviceSwitchPrompt,
                onDismissSuccess = viewModel::clearProfilesSuccess,
                onDismissError = viewModel::clearProfilesError
            )
        }

        composable(Screen.About.route) {
            AboutScreen()
        }

        composable(Screen.Games.route) {
            GamesHubScreen(
                androidGamesCount = viewModel.getAndroidGamesInEsdeCount(),
                windowsGamesCount = viewModel.getWindowsGamesInEsdeCount(),
                vitaGamesCount = viewModel.getVitaGamesInEsdeCount(),
                onNavigateToAndroid = {
                    viewModel.loadAndroidGames()
                    viewModel.updateAndroidMetadataCount()
                    navController.navigate(Screen.AndroidGames.route)
                },
                onNavigateToWindows = {
                    viewModel.loadWindowsGames()
                    viewModel.checkAllLauncherConfigurations()
                    viewModel.updateWindowsMetadataCount()
                    navController.navigate(Screen.WindowsGames.route)
                },
                onNavigateToVita = {
                    navController.navigate(Screen.VitaGames.route)
                }
            )
        }

        composable(Screen.VitaGames.route) {
            LaunchedEffect(Unit) {
                viewModel.loadVitaGames()
                viewModel.updateVitaMetadataCount()
            }

            VitaGamesScreen(
                uiState = vitaGamesState,
                onScanGames = viewModel::scanVitaGames,
                onRemoveGame = viewModel::removeVitaGameFromEsde,
                onAddGame = {
                    viewModel.clearVitaSearchResults()
                    navController.navigate(Screen.AddVitaGame.route)
                },
                onDismissSuccess = viewModel::clearVitaSuccess,
                onDismissError = viewModel::clearVitaError,
                onScrapeMetadata = viewModel::scrapeVitaMetadata,
                onShowScrapeSettings = viewModel::showVitaScrapeOptionsDialog,
                onDismissScrapeSettings = viewModel::dismissVitaScrapeOptionsDialog,
                onUpdateScrapeOptions = viewModel::updateVitaScrapeOptions,
                onSetPendingReScrapeGame = viewModel::setPendingReScrapeVitaGame,
                onReScrapeGame = viewModel::reScrapeVitaGame,
                onClearPendingReScrape = viewModel::clearPendingReScrapeVitaGame,
                onSavePath = viewModel::saveVitaRomsPath,
                onSetScreenScraperDevCredentials = viewModel::setScreenScraperDevCredentials,
                currentScreenScraperDevId = viewModel.getScreenScraperDevId(),
                onSetScreenScraperCredentials = viewModel::setScreenScraperCredentials,
                currentScreenScraperUsername = viewModel.getScreenScraperUsername(),
                getArtworkPath = viewModel::getVitaGameArtworkPath
            )
        }

        composable(Screen.AddVitaGame.route) {
            AddVitaGameScreen(
                searchQuery = vitaGamesState.searchQuery,
                isSearching = vitaGamesState.isSearching,
                searchResults = vitaGamesState.searchResults,
                errorMessage = vitaGamesState.error,
                successMessage = vitaGamesState.successMessage,
                onSearchQueryChange = viewModel::updateVitaSearchQuery,
                onSearch = viewModel::searchVitaGames,
                onAddGame = { titleId, displayName ->
                    viewModel.addVitaGameToEsde(titleId, displayName)
                },
                onDismissError = viewModel::clearVitaError,
                onDismissSuccess = viewModel::clearVitaSuccess,
                onBack = {
                    viewModel.clearVitaSearchResults()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddSteamGame.route) {
            val existingAppIds = remember { viewModel.getExistingSteamAppIds() }
            val availableLaunchers = remember { viewModel.getSteamCompatibleLaunchers() }

            AddSteamGameScreen(
                searchQuery = windowsGamesState.searchQuery,
                isSearching = windowsGamesState.isSearching,
                searchResults = windowsGamesState.searchResults,
                existingAppIds = existingAppIds,
                availableLaunchers = availableLaunchers,
                errorMessage = windowsGamesState.error,
                successMessage = windowsGamesState.successMessage,
                onSearchQueryChange = viewModel::updateSteamSearchQuery,
                onSearch = viewModel::searchSteamGames,
                onAddGame = { game, launcherPackage ->
                    viewModel.addSteamGameToEsde(game, launcherPackage)
                },
                onDismissError = viewModel::clearWindowsGamesError,
                onDismissSuccess = viewModel::clearWindowsGamesSuccess,
                onBack = {
                    viewModel.clearSteamSearchResults()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddGogGame.route) {
            val existingProductIds = remember { viewModel.getExistingGogProductIds() }
            val availableLaunchers = remember { viewModel.getGogCompatibleLaunchers() }

            AddGogGameScreen(
                searchQuery = windowsGamesState.gogSearchQuery,
                isSearching = windowsGamesState.isSearchingGog && windowsGamesState.gogSearchResults.isEmpty(),
                isAdding = windowsGamesState.isSearchingGog && windowsGamesState.gogSearchResults.isNotEmpty(),
                searchResults = windowsGamesState.gogSearchResults,
                existingProductIds = existingProductIds,
                availableLaunchers = availableLaunchers,
                errorMessage = windowsGamesState.error,
                successMessage = windowsGamesState.successMessage,
                onSearchQueryChange = viewModel::updateGogSearchQuery,
                onSearch = viewModel::searchGogGames,
                onAddGame = { game, launcherPackage ->
                    viewModel.addGogGameToEsde(game, launcherPackage)
                },
                onDismissError = viewModel::clearWindowsGamesError,
                onDismissSuccess = viewModel::clearWindowsGamesSuccess,
                onBack = {
                    viewModel.clearGogSearchResults()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ImportEpicGame.route) {
            val existingGames = remember { viewModel.getExistingEpicGameNames() }

            ImportEpicGameScreen(
                importPath = windowsGamesState.epicImportPath,
                foundGames = windowsGamesState.epicFoundGames,
                existingGames = existingGames,
                isScanning = windowsGamesState.isScanningEpic,
                errorMessage = windowsGamesState.error,
                successMessage = windowsGamesState.successMessage,
                onPathChange = viewModel::updateEpicImportPath,
                onScanPath = viewModel::scanForEpicGames,
                onImportGame = viewModel::importEpicGame,
                onImportAll = viewModel::importAllEpicGames,
                onDismissError = viewModel::clearWindowsGamesError,
                onDismissSuccess = viewModel::clearWindowsGamesSuccess,
                onBack = {
                    viewModel.clearEpicImportState()
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.AddCustomEmulator.route) {
            AddCustomEmulatorScreen(
                allApps = allApps,
                onAppSelected = { emulator ->
                    onSelectedEmulatorChange(emulator)
                    navController.navigate(Screen.ConfigureCustomEmulator.route)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.ConfigureCustomEmulator.route) {
            val emulator = selectedEmulatorForConfig
            if (emulator != null) {
                val initialSystems = viewModel.getCustomEmulatorSystems(emulator.packageName)
                ConfigureCustomEmulatorScreen(
                    emulator = emulator,
                    allSystems = viewModel.getAllSystems(),
                    initialSelectedSystems = initialSystems,
                    onSave = { systems ->
                        viewModel.addCustomEmulator(emulator, systems)
                        onSelectedEmulatorChange(null)
                        // Go back to emulators screen
                        navController.popBackStack(Screen.Emulators.route, inclusive = false)
                    },
                    onBack = {
                        onSelectedEmulatorChange(null)
                        navController.popBackStack()
                    }
                )
            }
        }

        composable(
            route = Screen.SystemDetail.route,
            arguments = listOf(
                navArgument("systemId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val systemId = backStackEntry.arguments?.getString("systemId") ?: return@composable

            // Find the system config
            val systemConfig = uiState.systems.find { it.system.name == systemId }

            if (systemConfig != null) {
                val availableEmulators = remember(systemId, customEmulators) {
                    viewModel.getInstalledEmulatorsForSystem(systemId)
                }

                SystemDetailScreen(
                    config = systemConfig,
                    availableEmulators = availableEmulators,
                    onBack = { navController.popBackStack() },
                    onEmulatorToggle = { emulator, enabled ->
                        if (enabled) {
                            viewModel.addEmulatorToSystem(systemId, emulator)
                        } else {
                            viewModel.removeEmulatorFromSystem(systemId, emulator.packageName)
                        }
                    }
                )
            }
        }
    }
}
