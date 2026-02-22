package com.esde.emulatormanager.ui.screens

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.esde.emulatormanager.data.model.ScrapeOptions
import com.esde.emulatormanager.data.model.ScrapeProgress
import com.esde.emulatormanager.data.model.WindowsGameLauncher
import com.esde.emulatormanager.data.model.WindowsGameShortcut
import com.esde.emulatormanager.data.model.WindowsGamesUiState
import com.esde.emulatormanager.ui.components.EmptyState
import com.esde.emulatormanager.ui.components.HelpIconButton
import com.esde.emulatormanager.ui.components.LoadingIndicator
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WindowsGamesScreen(
    uiState: WindowsGamesUiState,
    onScanGames: () -> Unit,
    onAddGame: (WindowsGameShortcut) -> Unit,
    onRemoveGame: (WindowsGameShortcut) -> Unit,
    onAddAllFromLauncher: (WindowsGameLauncher) -> Unit,
    onAddSteamGame: () -> Unit,
    onAddGogGame: () -> Unit = {},
    onImportEpicGame: () -> Unit = {},
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
    onShowPathSelectionDialog: (Boolean) -> Unit,
    onSavePath: (String) -> Unit,
    onConfigureGameHubLite: () -> Unit,
    onScrapeMetadata: () -> Unit = {},
    onShowScrapeSettings: () -> Unit = {},
    onDismissScrapeSettings: () -> Unit = {},
    onUpdateScrapeOptions: (ScrapeOptions) -> Unit = {},
    onSetPendingReScrapeGame: (WindowsGameShortcut) -> Unit = {},
    onReScrapeGame: (WindowsGameShortcut) -> Unit = {},
    onClearPendingReScrape: () -> Unit = {},
    getArtworkPath: (String) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddGameMenu by remember { mutableStateOf(false) }

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Convert the URI to an actual file path
            val path = getPathFromUri(context, it)
            if (path != null) {
                onSavePath(path)
            }
        }
        onShowPathSelectionDialog(false)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Windows Games",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val totalGames = uiState.launchers.sumOf { it.games.size }
                        val addedGames = uiState.launchers.sumOf { launcher ->
                            launcher.games.count { it.isInEsde }
                        }
                        if (totalGames > 0) {
                            Text(
                                text = "$addedGames / $totalGames in ES-DE",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
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
                                contentDescription = "Scan for games"
                            )
                        }
                    }
                    IconButton(onClick = onShowScrapeSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Scrape settings"
                        )
                    }
                    HelpIconButton(
                        title = "Windows Games",
                        description = "Add Windows/PC games to ES-DE using GameHub Lite or GameNative.",
                        features = listOf(
                            "Games are grouped by platform: Steam, GOG, and Epic Games",
                            "Add Steam games by searching the Steam database (+ button → Add Steam Game)",
                            "Add GOG games by searching the GOG database (+ button → Add GOG Game)",
                            "Import Epic games from .epicgame files exported by GameNative (+ button → Import Epic Game)",
                            "GameHub Lite launches Steam games via .steam shortcut files",
                            "GameNative launches Steam, GOG, and Epic games via .steam/.gog/.epic shortcut files",
                            "ES-DE Launcher Configuration card shows status of GameHub Lite & GameNative — use Fix/Update to apply",
                            "Scrape settings (tune icon) controls what is downloaded: metadata, artwork, and/or videos",
                            "Scrape settings apply to all operations: batch scrape, re-scrape, and adding new games",
                            "Metadata card scrapes missing games — tap Scrape to start using current settings",
                            "Tap ⋮ on any added game to re-scrape its metadata individually",
                            "Set custom Windows ROMs folder path via the edit icon on the path card"
                        ),
                        iconLegends = listOf(
                            Icons.Default.Refresh to "Refresh — rescan Windows launchers for games",
                            Icons.Default.Tune to "Scrape Settings — configure what metadata/artwork to download",
                            Icons.Outlined.Info to "Help — show this info dialog"
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { showAddGameMenu = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                    },
                    text = { Text("Add Game") }
                )
                DropdownMenu(
                    expanded = showAddGameMenu,
                    onDismissRequest = { showAddGameMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Steam Game") },
                        onClick = {
                            showAddGameMenu = false
                            onAddSteamGame()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add GOG Game") },
                        onClick = {
                            showAddGameMenu = false
                            onAddGogGame()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Storefront,
                                contentDescription = null
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Import Epic Game") },
                        onClick = {
                            showAddGameMenu = false
                            onImportEpicGame()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                LoadingIndicator(modifier = Modifier.padding(paddingValues))
            }
            uiState.launchers.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.DesktopWindows,
                    title = "No Windows Launchers Found",
                    description = "Install GameHub, Winlator, or other Windows game launchers to manage games",
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
                        // Extra bottom padding to account for FAB (56dp) + margin (16dp)
                        bottom = paddingValues.calculateBottomPadding() + 80.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ES-DE path info
                    item {
                        EsdePathsCard(
                            windowsPath = uiState.esdeWindowsPath,
                            mediaPath = uiState.esdeMediaPath,
                            settingsFilePath = uiState.esdeSettingsFilePath,
                            onEditClick = { onShowPathSelectionDialog(true) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Launcher configuration card (GameHub Lite + GameNative)
                    item {
                        LauncherConfigCard(
                            isGameHubLiteConfigured = uiState.isGameHubLiteConfigured,
                            isGameNativeConfigured = uiState.isGameNativeConfigured,
                            onConfigureClick = onConfigureGameHubLite,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Metadata scraping card
                    item {
                        MetadataScrapeCard(
                            gamesWithoutMetadataCount = uiState.gamesWithoutMetadataCount,
                            isScraping = uiState.isScraping,
                            scrapeProgress = uiState.scrapeProgress,
                            onScrapeClick = onScrapeMetadata,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Windows Games - unified section showing games by platform
                    item {
                        WindowsGamesSection(
                            launchers = uiState.launchers,
                            onAddGame = onAddGame,
                            onRemoveGame = onRemoveGame,
                            onAddAllFromLauncher = onAddAllFromLauncher,
                            onReScrapeGame = onSetPendingReScrapeGame,
                            getArtworkPath = getArtworkPath
                        )
                    }
                }
            }
        }
    }

    // Scrape settings dialog (opened from toolbar tune icon)
    if (uiState.showScrapeOptionsDialog) {
        ScrapeSettingsDialog(
            options = uiState.scrapeOptions,
            onOptionsChanged = onUpdateScrapeOptions,
            onDismiss = onDismissScrapeSettings
        )
    }

    // Re-scrape confirmation dialog
    if (uiState.pendingReScrapeGame != null) {
        ReScrapeConfirmDialog(
            game = uiState.pendingReScrapeGame!!,
            onConfirm = {
                onReScrapeGame(uiState.pendingReScrapeGame!!)
            },
            onDismiss = onClearPendingReScrape
        )
    }

    // Launch folder picker when dialog flag is set
    LaunchedEffect(uiState.showPathSelectionDialog) {
        if (uiState.showPathSelectionDialog) {
            // Try to start at the current path or a default location
            val initialUri = try {
                val basePath = uiState.esdeWindowsPath
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
}

/**
 * Convert a content URI from the folder picker to an actual file path.
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        // For document tree URIs, we need to extract the path
        val docId = DocumentsContract.getTreeDocumentId(uri)

        // Handle primary storage (most common case)
        if (docId.startsWith("primary:")) {
            val relativePath = docId.removePrefix("primary:")
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        } else if (docId.contains(":")) {
            // Handle other storage volumes (SD card, etc.)
            val split = docId.split(":")
            val storageId = split[0]
            val relativePath = if (split.size > 1) split[1] else ""

            // Try to find the storage path
            val storagePaths = context.getExternalFilesDirs(null)
            for (storagePath in storagePaths) {
                if (storagePath != null) {
                    val rootPath = storagePath.absolutePath.substringBefore("/Android")
                    if (rootPath.contains(storageId) || storageId == "primary") {
                        return "$rootPath/$relativePath"
                    }
                }
            }
            // Fallback: just use the relative path with primary storage
            "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
        } else {
            // Simple path without prefix
            "${Environment.getExternalStorageDirectory().absolutePath}/$docId"
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun EsdePathsCard(
    windowsPath: String?,
    mediaPath: String?,
    settingsFilePath: String?,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Windows ROMs path row
            Row(
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
                        text = "Windows ROMs Path",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = windowsPath ?: "Not configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
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

            // Media/Artwork path row
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Artwork Download Path",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = mediaPath ?: "Using ES-DE default",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (settingsFilePath != null) {
                            "from: $settingsFilePath"
                        } else {
                            "(es_settings.xml not found)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (settingsFilePath != null) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LauncherConfigCard(
    isGameHubLiteConfigured: Boolean,
    isGameNativeConfigured: Boolean,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allConfigured = isGameHubLiteConfigured && isGameNativeConfigured

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (allConfigured) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            }
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
                    imageVector = if (allConfigured) Icons.Default.CheckCircle else Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = if (allConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ES-DE Launcher Configuration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (allConfigured) {
                            "All Windows launchers are configured"
                        } else {
                            "Configure ES-DE to auto-launch Windows games"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = onConfigureClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (allConfigured) Icons.Default.Refresh else Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (allConfigured) "Update" else "Fix")
                }
            }

            // Show individual launcher status
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            // GameHub Lite status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGameHubLiteConfigured) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isGameHubLiteConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GameHub Lite",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ".steam files",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // GameNative status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGameNativeConfigured) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (isGameNativeConfigured) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GameNative",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = ".gog, .epic files",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScrapeSettingsDialog(
    options: ScrapeOptions,
    onOptionsChanged: (ScrapeOptions) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scrape Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Choose what to download when scraping games. These settings apply to batch scraping, re-scraping, and adding new games.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = options.scrapeMetadata,
                        onCheckedChange = { onOptionsChanged(options.copy(scrapeMetadata = it)) }
                    )
                    Column {
                        Text("Metadata", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Name, description, genre, rating, etc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = options.scrapeArtwork,
                        onCheckedChange = { onOptionsChanged(options.copy(scrapeArtwork = it)) }
                    )
                    Column {
                        Text("Artwork", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Cover art, screenshots, and miximage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = options.scrapeVideos,
                        onCheckedChange = { onOptionsChanged(options.copy(scrapeVideos = it)) }
                    )
                    Column {
                        Text("Videos", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Video previews (may take longer)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun MetadataScrapeCard(
    gamesWithoutMetadataCount: Int,
    isScraping: Boolean,
    scrapeProgress: ScrapeProgress?,
    onScrapeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                        text = "Game Metadata",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = if (isScraping && scrapeProgress != null) {
                            "Scraping: ${scrapeProgress.currentGame ?: "..."}"
                        } else if (gamesWithoutMetadataCount > 0) {
                            "$gamesWithoutMetadataCount games missing metadata"
                        } else {
                            "All games have metadata"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (gamesWithoutMetadataCount > 0 && !isScraping) {
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
                } else if (isScraping) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
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
}

@Composable
private fun WindowsGamesSection(
    launchers: List<WindowsGameLauncher>,
    onAddGame: (WindowsGameShortcut) -> Unit,
    onRemoveGame: (WindowsGameShortcut) -> Unit,
    onAddAllFromLauncher: (WindowsGameLauncher) -> Unit,
    onReScrapeGame: (WindowsGameShortcut) -> Unit = {},
    getArtworkPath: (String) -> String? = { null },
    modifier: Modifier = Modifier
) {
    // Collect all games from all launchers and group by platform (launcherName)
    val allGames = launchers.flatMap { it.games }

    // Group games by their launcher name (Steam, GOG, Epic)
    val steamGames = allGames.filter { it.launcherName.contains("Steam", ignoreCase = true) }
    val gogGames = allGames.filter { it.launcherName.contains("GOG", ignoreCase = true) }
    val epicGames = allGames.filter { it.launcherName.contains("Epic", ignoreCase = true) }
    val otherGames = allGames.filter { game ->
        !game.launcherName.contains("Steam", ignoreCase = true) &&
        !game.launcherName.contains("GOG", ignoreCase = true) &&
        !game.launcherName.contains("Epic", ignoreCase = true)
    }

    val totalGames = allGames.size
    val addedGames = allGames.count { it.isInEsde }

    if (totalGames == 0) {
        // Empty state
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Windows Games",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Add games using the + button",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Windows Games",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$addedGames / $totalGames in ES-DE",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Steam Games Section
            if (steamGames.isNotEmpty()) {
                PlatformSubsection(
                    platformName = "Steam",
                    platformDescription = "GameHub Lite / GameNative (.steam)",
                    games = steamGames,
                    onAddGame = onAddGame,
                    onRemoveGame = onRemoveGame,
                    onReScrapeGame = onReScrapeGame,
                    getArtworkPath = getArtworkPath
                )
            }

            // GOG Games Section
            if (gogGames.isNotEmpty()) {
                if (steamGames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                PlatformSubsection(
                    platformName = "GOG",
                    platformDescription = "GameNative (.gog)",
                    games = gogGames,
                    onAddGame = onAddGame,
                    onRemoveGame = onRemoveGame,
                    onReScrapeGame = onReScrapeGame,
                    getArtworkPath = getArtworkPath
                )
            }

            // Epic Games Section
            if (epicGames.isNotEmpty()) {
                if (steamGames.isNotEmpty() || gogGames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                PlatformSubsection(
                    platformName = "Epic Games",
                    platformDescription = "GameNative (.epic)",
                    games = epicGames,
                    onAddGame = onAddGame,
                    onRemoveGame = onRemoveGame,
                    onReScrapeGame = onReScrapeGame,
                    getArtworkPath = getArtworkPath
                )
            }

            // Other games (if any)
            if (otherGames.isNotEmpty()) {
                if (steamGames.isNotEmpty() || gogGames.isNotEmpty() || epicGames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                }
                PlatformSubsection(
                    platformName = "Other",
                    platformDescription = "Other launchers",
                    games = otherGames,
                    onAddGame = onAddGame,
                    onRemoveGame = onRemoveGame,
                    onReScrapeGame = onReScrapeGame,
                    getArtworkPath = getArtworkPath
                )
            }
        }
    }
}

@Composable
private fun PlatformSubsection(
    platformName: String,
    platformDescription: String,
    games: List<WindowsGameShortcut>,
    onAddGame: (WindowsGameShortcut) -> Unit,
    onRemoveGame: (WindowsGameShortcut) -> Unit,
    onReScrapeGame: (WindowsGameShortcut) -> Unit,
    getArtworkPath: (String) -> String?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Platform header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = platformName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = platformDescription,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${games.count { it.isInEsde }}/${games.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Games list
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (game in games) {
                GameItem(
                    game = game,
                    artworkPath = getArtworkPath(game.id),
                    onAdd = { onAddGame(game) },
                    onRemove = { onRemoveGame(game) },
                    onReScrape = { onReScrapeGame(game) }
                )
            }
        }
    }
}

@Composable
private fun LauncherSection(
    launcher: WindowsGameLauncher,
    onAddGame: (WindowsGameShortcut) -> Unit,
    onRemoveGame: (WindowsGameShortcut) -> Unit,
    onAddAll: () -> Unit,
    onReScrapeGame: (WindowsGameShortcut) -> Unit = {},
    getArtworkPath: (String) -> String? = { null },
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Launcher header
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Launcher icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (launcher.icon != null) {
                        val bitmap = remember(launcher.icon) {
                            launcher.icon.toBitmap().asImageBitmap()
                        }
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = launcher.displayName,
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DesktopWindows,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = launcher.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${launcher.games.size} games found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Add all button
                val notAddedCount = launcher.games.count { !it.isInEsde }
                if (notAddedCount > 0) {
                    FilledTonalButton(
                        onClick = onAddAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add All ($notAddedCount)")
                    }
                }
            }

            if (launcher.games.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Games list
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (game in launcher.games) {
                        GameItem(
                            game = game,
                            artworkPath = getArtworkPath(game.id),
                            onAdd = { onAddGame(game) },
                            onRemove = { onRemoveGame(game) },
                            onReScrape = { onReScrapeGame(game) }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (launcher.dataPath != null) {
                        "No games found in ${launcher.dataPath}"
                    } else {
                        "Could not locate games directory"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GameItem(
    game: WindowsGameShortcut,
    artworkPath: String? = null,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onReScrape: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    // Load artwork bitmap if available
    val artworkBitmap = remember(artworkPath) {
        artworkPath?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (game.isInEsde) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Game artwork or fallback icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (artworkBitmap != null) {
                Image(
                    bitmap = artworkBitmap,
                    contentDescription = game.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (game.isInEsde) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "IN ES-DE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = game.executablePath.substringAfterLast("/"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add/Remove button
        if (game.isInEsde) {
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

        // Options menu (only show for games in ES-DE)
        if (game.isInEsde) {
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
                    DropdownMenuItem(
                        text = { Text("Re-scrape metadata") },
                        onClick = {
                            showMenu = false
                            onReScrape()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReScrapeConfirmDialog(
    game: WindowsGameShortcut,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-scrape Metadata") },
        text = {
            Text(
                "Re-scrape metadata and artwork for \"${game.name}\"?\n\nThis will download fresh cover art, screenshots, and generate a new miximage from Steam.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text("Re-scrape")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
