package com.esde.emulatormanager.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.esde.emulatormanager.data.model.AndroidGame
import com.esde.emulatormanager.data.model.AndroidGamesUiState
import com.esde.emulatormanager.data.model.AndroidTab
import com.esde.emulatormanager.data.model.AppCategory
import com.esde.emulatormanager.data.model.PendingMetadataSearch
import com.esde.emulatormanager.data.model.ScrapeProgress
import com.esde.emulatormanager.data.model.StaleAndroidEntry
import com.esde.emulatormanager.ui.components.EmptyState
import com.esde.emulatormanager.ui.components.HelpIconButton
import com.esde.emulatormanager.ui.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AndroidGamesScreen(
    uiState: AndroidGamesUiState,
    filteredGames: List<AndroidGame>,
    currentTabPath: String?,
    onScanGames: () -> Unit,
    onAddGame: (AndroidGame) -> Unit,
    onRemoveGame: (AndroidGame) -> Unit,
    onAddAllGames: () -> Unit,
    onTabSelected: (AndroidTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleShowAllApps: () -> Unit,
    onReclassifyApp: (AndroidGame, AppCategory) -> Unit,
    onResetClassification: (AndroidGame) -> Unit,
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
    onSavePath: (String) -> Unit,
    onRemoveStaleEntry: (StaleAndroidEntry) -> Unit,
    onRemoveAllStaleEntries: () -> Unit,
    onGenerateMissingArtwork: () -> Unit,
    onScrapeMetadata: () -> Unit = {},
    onRetryMetadataSearch: (String) -> Unit = {},
    onSkipMetadataScrape: () -> Unit = {},
    onSetIgdbCredentials: (String, String) -> Unit = { _, _ -> },
    onReScrapeGame: (AndroidGame) -> Unit = {},
    onReScrapeGameWithTerm: (AndroidGame, String) -> Unit = { _, _ -> },
    onClearPendingReScrape: () -> Unit = {},
    currentIgdbClientId: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPathDialog by remember { mutableStateOf(false) }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = getPathFromUri(context, it)
            if (path != null) {
                onSavePath(path)
            }
        }
        showPathDialog = false
    }

    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onDismissSuccess()
        }
    }

    // Show error message
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            onDismissError()
        }
    }

    // Calculate counts for each tab
    val gamesCount = uiState.allApps.count { it.isGame && !it.isEmulator }
    val appsCount = uiState.allApps.count { !it.isGame && !it.isEmulator }
    val emulatorsCount = uiState.allApps.count { it.isEmulator }
    val addedInCurrentTab = filteredGames.count { it.isInEsdeForTab(uiState.selectedTab) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Android",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$addedInCurrentTab of ${filteredGames.size} added to ES-DE",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    actions = {
                        // Show All Apps toggle
                        IconButton(onClick = onToggleShowAllApps) {
                            Icon(
                                imageVector = if (uiState.showAllApps) Icons.Default.FilterAlt else Icons.Outlined.FilterAltOff,
                                contentDescription = if (uiState.showAllApps) "Show category only" else "Show all apps",
                                tint = if (uiState.showAllApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Refresh button
                        IconButton(
                            onClick = onScanGames,
                            enabled = !uiState.isScanning
                        ) {
                            if (uiState.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan for apps"
                                )
                            }
                        }
                        HelpIconButton(
                            title = "Android Apps",
                            description = "Add Android apps, games, and emulators to ES-DE as launchable shortcuts.",
                            features = listOf(
                                "Three tabs: Games (Android games), Apps (utilities/launchers), Emulators",
                                "App categories are auto-detected; labels with * indicate a user-set override",
                                "Tap ⋮ on any app to reclassify it as Game, App, or Emulator, or reset to auto-detect",
                                "Filter icon toggles between showing only the current category or all installed apps",
                                "Set a custom ROM folder path for each category using the edit icon on the path card",
                                "Image icon on path card generates missing cover artwork from app icons",
                                "Stale entries card shows shortcuts for apps that have been uninstalled",
                                "Game Metadata card (Games tab) scrapes descriptions, ratings, and artwork from IGDB",
                                "IGDB requires a free Twitch developer account — tap Settings gear to configure credentials",
                                "Tap ⋮ on an added game to re-scrape its metadata individually (requires IGDB credentials)"
                            ),
                            iconLegends = listOf(
                                Icons.Default.FilterAlt to "Filter — toggle between current category only / all apps",
                                Icons.Default.Refresh to "Refresh — rescan installed apps",
                                Icons.Outlined.Info to "Help — show this info dialog"
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Tab row
                TabRow(
                    selectedTabIndex = uiState.selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = uiState.selectedTab == AndroidTab.GAMES,
                        onClick = { onTabSelected(AndroidTab.GAMES) },
                        text = { Text("Games ($gamesCount)") },
                        icon = { Icon(Icons.Outlined.SportsEsports, contentDescription = null) }
                    )
                    Tab(
                        selected = uiState.selectedTab == AndroidTab.APPS,
                        onClick = { onTabSelected(AndroidTab.APPS) },
                        text = { Text("Apps ($appsCount)") },
                        icon = { Icon(Icons.Outlined.Apps, contentDescription = null) }
                    )
                    Tab(
                        selected = uiState.selectedTab == AndroidTab.EMULATORS,
                        onClick = { onTabSelected(AndroidTab.EMULATORS) },
                        text = { Text("Emulators ($emulatorsCount)") },
                        icon = { Icon(Icons.Outlined.Gamepad, contentDescription = null) }
                    )
                }
            }
        },
        floatingActionButton = {
            val notAddedCount = filteredGames.count { !it.isInEsdeForTab(uiState.selectedTab) }
            if (notAddedCount > 0) {
                ExtendedFloatingActionButton(
                    onClick = onAddAllGames,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null
                        )
                    },
                    text = { Text("Add All ($notAddedCount)") }
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(paddingValues))
            }
            uiState.allApps.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.Android,
                    title = "No Apps Found",
                    description = "Could not find any installed apps on this device",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = paddingValues.calculateBottomPadding() + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Path info card
                    item {
                        AndroidPathCard(
                            path = currentTabPath,
                            tabName = when (uiState.selectedTab) {
                                AndroidTab.GAMES -> "Games"
                                AndroidTab.APPS -> "Apps"
                                AndroidTab.EMULATORS -> "Emulators"
                            },
                            onEditClick = { showPathDialog = true },
                            onGenerateMissingArtwork = onGenerateMissingArtwork,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Metadata scraping card (only for Games tab)
                    if (uiState.selectedTab == AndroidTab.GAMES) {
                        item {
                            AndroidMetadataScrapeCard(
                                gamesWithoutMetadataCount = uiState.gamesWithoutMetadataCount,
                                isScraping = uiState.isScraping,
                                scrapeProgress = uiState.scrapeProgress,
                                hasIgdbCredentials = uiState.hasIgdbCredentials,
                                onScrapeClick = onScrapeMetadata,
                                onRetrySearch = onRetryMetadataSearch,
                                onSkip = onSkipMetadataScrape,
                                onSetCredentials = onSetIgdbCredentials,
                                currentClientId = currentIgdbClientId,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Search bar
                    item {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Search apps...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (uiState.searchQuery.isNotBlank()) {
                                    IconButton(onClick = { onSearchQueryChange("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Stale entries section (shortcuts for uninstalled apps)
                    if (uiState.staleEntries.isNotEmpty()) {
                        item {
                            StaleEntriesCard(
                                staleEntries = uiState.staleEntries,
                                onRemoveEntry = onRemoveStaleEntry,
                                onRemoveAll = onRemoveAllStaleEntries,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Apps/Games list
                    items(
                        items = filteredGames,
                        key = { it.packageName }
                    ) { game ->
                        AndroidGameItem(
                            game = game,
                            currentTab = uiState.selectedTab,
                            onAdd = { onAddGame(game) },
                            onRemove = { onRemoveGame(game) },
                            onReclassify = { category -> onReclassifyApp(game, category) },
                            onResetClassification = { onResetClassification(game) },
                            onReScrape = { onReScrapeGame(game) },
                            hasIgdbCredentials = uiState.hasIgdbCredentials
                        )
                    }
                }
            }
        }
    }

    // Launch folder picker when dialog flag is set
    LaunchedEffect(showPathDialog) {
        if (showPathDialog) {
            val initialUri = try {
                val basePath = currentTabPath
                    ?: Environment.getExternalStorageDirectory().absolutePath
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:${basePath.removePrefix(Environment.getExternalStorageDirectory().absolutePath + "/")}"
                )
            } catch (e: Exception) {
                null
            }
            folderPickerLauncher.launch(initialUri)
        }
    }

    // Re-scrape Search Dialog (for single game re-scrape that wasn't found)
    if (uiState.pendingReScrapeGame != null) {
        ReScrapeSearchDialog(
            game = uiState.pendingReScrapeGame!!,
            onConfirm = { searchTerm ->
                onReScrapeGameWithTerm(uiState.pendingReScrapeGame!!, searchTerm)
            },
            onDismiss = {
                onClearPendingReScrape()
            }
        )
    }
}

/**
 * Convert a content URI from the folder picker to an actual file path.
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        val docId = DocumentsContract.getTreeDocumentId(uri)

        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        } else if (docId.contains(":")) {
            val split = docId.split(":")
            val storageId = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            val storagePaths = context.getExternalFilesDirs(null)
            for (storagePath in storagePaths) {
                if (storagePath != null) {
                    val rootPath = storagePath.absolutePath.substringBefore("/Android")
                    if (rootPath.contains(storageId) || storageId == "primary") {
                        return "$rootPath/$relativePath"
                    }
                }
            }
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        } else {
            "${Environment.getExternalStorageDirectory().absolutePath}/$docId"
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun AndroidPathCard(
    path: String?,
    tabName: String,
    onEditClick: () -> Unit,
    onGenerateMissingArtwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$tabName ROMs Path",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = path ?: "Not configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Generate missing artwork button
            IconButton(onClick = onGenerateMissingArtwork) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Generate missing artwork",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Path",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AndroidGameItem(
    game: AndroidGame,
    currentTab: AndroidTab,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onReclassify: (AppCategory) -> Unit,
    onResetClassification: () -> Unit,
    onReScrape: () -> Unit,
    hasIgdbCredentials: Boolean,
    modifier: Modifier = Modifier
) {
    val isInEsdeForCurrentTab = game.isInEsdeForTab(currentTab)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInEsdeForCurrentTab) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (game.icon != null) {
                    val bitmap = remember(game.icon) {
                        game.icon.toBitmap().asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = game.appName,
                        modifier = Modifier.size(44.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.appName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (game.isEmulator) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = if (game.hasUserOverride) "EMU*" else "EMU",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (game.isGame) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (game.hasUserOverride) "GAME*" else "GAME",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else if (game.hasUserOverride) {
                        // Show APP label if user explicitly classified as app
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        ) {
                            Text(
                                text = "APP*",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isInEsdeForCurrentTab) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "IN ES-DE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = game.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Add/Remove button
            if (isInEsdeForCurrentTab) {
                IconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircle,
                        contentDescription = "Remove from ES-DE"
                    )
                }
            } else {
                IconButton(
                    onClick = onAdd,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = "Add to ES-DE"
                    )
                }
            }

            // More options menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    Text(
                        text = "Reclassify as:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("Game") },
                        leadingIcon = {
                            Icon(Icons.Outlined.SportsEsports, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onReclassify(AppCategory.GAME)
                        },
                        enabled = !game.isGame
                    )
                    DropdownMenuItem(
                        text = { Text("App") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Android, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onReclassify(AppCategory.APP)
                        },
                        enabled = game.isGame || game.isEmulator
                    )
                    DropdownMenuItem(
                        text = { Text("Emulator") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Gamepad, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onReclassify(AppCategory.EMULATOR)
                        },
                        enabled = !game.isEmulator
                    )
                    if (game.hasUserOverride) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Reset to auto-detect") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onResetClassification()
                            }
                        )
                    }

                    // Re-scrape option (only show for Games tab, in ES-DE, and IGDB is configured)
                    if (currentTab == AndroidTab.GAMES && isInEsdeForCurrentTab && hasIgdbCredentials) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Re-scrape metadata") },
                            leadingIcon = {
                                Icon(Icons.Outlined.CloudDownload, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onReScrape()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReScrapeSearchDialog(
    game: AndroidGame,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchTerm by remember { mutableStateOf(game.appName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Not Found") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "\"${game.appName}\" was not found on IGDB. Enter the correct game name to search:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { searchTerm = it },
                    label = { Text("Search term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Tip: Try the exact game name as it appears on IGDB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(searchTerm) },
                enabled = searchTerm.isNotBlank()
            ) {
                Text("Search")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StaleEntriesCard(
    staleEntries: List<StaleAndroidEntry>,
    onRemoveEntry: (StaleAndroidEntry) -> Unit,
    onRemoveAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Stale Entries (${staleEntries.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Shortcuts for uninstalled apps",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                Row {
                    // Expand/collapse button
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    // Remove all button
                    IconButton(onClick = onRemoveAll) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Remove all stale entries",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Expandable list of stale entries
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                staleEntries.forEach { entry ->
                    StaleEntryItem(
                        entry = entry,
                        onRemove = { onRemoveEntry(entry) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun StaleEntryItem(
    entry: StaleAndroidEntry,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.size(32.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onRemove,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove stale entry"
            )
        }
    }
}

@Composable
private fun AndroidMetadataScrapeCard(
    gamesWithoutMetadataCount: Int,
    isScraping: Boolean,
    scrapeProgress: ScrapeProgress?,
    hasIgdbCredentials: Boolean,
    onScrapeClick: () -> Unit,
    onRetrySearch: (String) -> Unit,
    onSkip: () -> Unit,
    onSetCredentials: (String, String) -> Unit,
    currentClientId: String?,
    modifier: Modifier = Modifier
) {
    var showCredentialsDialog by remember { mutableStateOf(false) }
    var showRefineSearchDialog by remember { mutableStateOf(false) }
    var pendingSearch by remember { mutableStateOf<PendingMetadataSearch?>(null) }

    // Check if we have a pending user input request
    LaunchedEffect(scrapeProgress?.pendingUserInput) {
        if (scrapeProgress?.pendingUserInput != null) {
            pendingSearch = scrapeProgress.pendingUserInput
            showRefineSearchDialog = true
        }
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Game Metadata (IGDB)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (!hasIgdbCredentials) {
                            "IGDB credentials not configured"
                        } else if (scrapeProgress?.pendingUserInput != null) {
                            "Game not found: ${scrapeProgress.pendingUserInput.game.appName}"
                        } else if (isScraping && scrapeProgress != null) {
                            "Scraping: ${scrapeProgress.currentGame ?: "..."}"
                        } else if (gamesWithoutMetadataCount > 0) {
                            "$gamesWithoutMetadataCount games missing metadata"
                        } else {
                            "All games have metadata"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!hasIgdbCredentials || scrapeProgress?.pendingUserInput != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                if (!hasIgdbCredentials) {
                    FilledTonalButton(
                        onClick = { showCredentialsDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Setup")
                    }
                } else if (scrapeProgress?.pendingUserInput != null) {
                    // Show waiting for input indicator
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Waiting for input",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (hasIgdbCredentials && gamesWithoutMetadataCount > 0 && !isScraping) {
                    Row {
                        // Settings button to update credentials
                        IconButton(onClick = { showCredentialsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure IGDB"
                            )
                        }
                        FilledTonalButton(
                            onClick = onScrapeClick,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scrape")
                        }
                    }
                } else if (isScraping && scrapeProgress?.pendingUserInput == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else if (hasIgdbCredentials) {
                    // All games have metadata - just show settings button
                    IconButton(onClick = { showCredentialsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure IGDB"
                        )
                    }
                }
            }

            // Progress bar when scraping
            if (isScraping && scrapeProgress != null) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { scrapeProgress.progressPercent },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${scrapeProgress.completed}/${scrapeProgress.total} (${scrapeProgress.successful} successful, ${scrapeProgress.failed} failed)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // IGDB Credentials Dialog
    if (showCredentialsDialog) {
        IgdbCredentialsDialog(
            currentClientId = currentClientId,
            onDismiss = { showCredentialsDialog = false },
            onSave = { clientId, clientSecret ->
                onSetCredentials(clientId, clientSecret)
                showCredentialsDialog = false
            }
        )
    }

    // Refine Search Dialog (for batch scraping)
    if (showRefineSearchDialog && pendingSearch != null) {
        RefineSearchDialog(
            gameName = pendingSearch!!.game.appName,
            originalSearchTerm = pendingSearch!!.originalSearchTerm,
            onRetry = { refinedTerm ->
                showRefineSearchDialog = false
                onRetrySearch(refinedTerm)
            },
            onSkip = {
                showRefineSearchDialog = false
                onSkip()
            },
            onDismiss = {
                showRefineSearchDialog = false
                onSkip()
            }
        )
    }
}

@Composable
private fun RefineSearchDialog(
    gameName: String,
    originalSearchTerm: String,
    onRetry: (String) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchTerm by remember { mutableStateOf(originalSearchTerm) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Not Found") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Could not find metadata for:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = gameName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Would you like to refine the search term? Try using a shorter or different name.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { searchTerm = it },
                    label = { Text("Search term") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onRetry(searchTerm) },
                enabled = searchTerm.isNotBlank()
            ) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    )
}

@Composable
private fun IgdbCredentialsDialog(
    currentClientId: String?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var clientId by remember { mutableStateOf(currentClientId ?: "") }
    var clientSecret by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("IGDB API Configuration") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "IGDB uses Twitch authentication. Follow these steps:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Step-by-step instructions
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    Text(
                        text = "1. Go to dev.twitch.tv/console",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "2. Log in with your Twitch account (create one if needed)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "3. Click \"+ Register Your Application\"",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "4. Fill in the form:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "   • Name: Any name (e.g., \"ESDE Manager\")",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "   • OAuth Redirect URLs: http://localhost",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "   • Category: Application Integration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "   • Client Type: Confidential",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "5. Click \"Create\"",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "6. Click \"Manage\" on your new app",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "7. Copy the Client ID shown",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "8. Click \"New Secret\" to generate a Client Secret",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client Secret") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(clientId, clientSecret) },
                enabled = clientId.isNotBlank() && clientSecret.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
