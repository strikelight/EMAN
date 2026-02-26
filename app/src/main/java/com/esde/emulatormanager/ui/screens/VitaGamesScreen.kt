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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.esde.emulatormanager.data.model.ScrapeOptions
import com.esde.emulatormanager.data.model.ScrapeProgress
import com.esde.emulatormanager.data.model.VitaGame
import com.esde.emulatormanager.data.model.VitaGamesUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VitaGamesScreen(
    uiState: VitaGamesUiState,
    onScanGames: () -> Unit,
    onRemoveGame: (VitaGame) -> Unit,
    onAddGame: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
    onScrapeMetadata: () -> Unit = {},
    onShowScrapeSettings: () -> Unit = {},
    onDismissScrapeSettings: () -> Unit = {},
    onUpdateScrapeOptions: (ScrapeOptions) -> Unit = {},
    onSetPendingReScrapeGame: (VitaGame?) -> Unit = {},
    onReScrapeGame: (VitaGame, String?) -> Unit = { _, _ -> },
    onClearPendingReScrape: () -> Unit = {},
    onSavePath: (String) -> Unit = {},
    onSetIgdbCredentials: (String, String) -> Unit = { _, _ -> },
    currentIgdbClientId: String? = null,
    getArtworkPath: (String) -> String? = { null },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPathDialog by remember { mutableStateOf(false) }

    // Folder picker launcher for ROM path editing
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val path = getVitaPathFromUri(context, it)
            if (path != null) {
                onSavePath(path)
            }
        }
        showPathDialog = false
    }

    // Launch folder picker when triggered
    LaunchedEffect(showPathDialog) {
        if (showPathDialog) {
            val initialUri = try {
                val basePath = uiState.esdeVitaPath
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

    // Scrape settings dialog
    if (uiState.showScrapeOptionsDialog) {
        VitaScrapeSettingsDialog(
            options = uiState.scrapeOptions,
            onOptionsChanged = onUpdateScrapeOptions,
            onDismiss = onDismissScrapeSettings
        )
    }

    // Re-scrape confirmation dialog
    uiState.pendingReScrapeGame?.let { game ->
        VitaReScrapeDialog(
            game = game,
            onConfirm = { searchTerm -> onReScrapeGame(game, searchTerm) },
            onDismiss = onClearPendingReScrape
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "PS Vita Games",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.games.isNotEmpty()) {
                            Text(
                                text = "${uiState.games.size} game${if (uiState.games.size != 1) "s" else ""} in ES-DE",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onScanGames,
                        enabled = !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh"
                            )
                        }
                    }
                    IconButton(onClick = onShowScrapeSettings) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Scrape settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGame) {
                Icon(Icons.Default.Add, contentDescription = "Add PS Vita game")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ROM path card (always show — uses default path if not yet configured)
            item {
                VitaPathCard(
                    path = uiState.esdeVitaPath ?: "Not configured",
                    onEditClick = { showPathDialog = true }
                )
            }

            // IGDB credentials card
            item {
                IgdbCredentialsCard(
                    currentClientId = currentIgdbClientId,
                    hasIgdbCredentials = uiState.hasIgdbCredentials,
                    gamesWithoutMetadataCount = uiState.gamesWithoutMetadataCount,
                    onSetCredentials = onSetIgdbCredentials
                )
            }

            // Metadata scrape card
            if (uiState.gamesWithoutMetadataCount > 0 || uiState.isScraping) {
                item {
                    VitaMetadataScrapeCard(
                        gamesWithoutMetadataCount = uiState.gamesWithoutMetadataCount,
                        isScraping = uiState.isScraping,
                        scrapeProgress = uiState.scrapeProgress,
                        hasCredentials = uiState.hasIgdbCredentials,
                        onScrapeClick = onScrapeMetadata
                    )
                }
            }

            // Games list
            if (uiState.games.isEmpty() && !uiState.isLoading) {
                item {
                    VitaEmptyState()
                }
            } else {
                items(uiState.games, key = { it.filePath }) { game ->
                    val gameId = java.io.File(game.filePath).nameWithoutExtension
                    VitaGameItem(
                        game = game,
                        artworkPath = getArtworkPath(gameId),
                        onRemove = { onRemoveGame(game) },
                        onReScrape = { onSetPendingReScrapeGame(game) }
                    )
                }
            }

            // Bottom spacer for FAB
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

// ---- Sub-composables ----

@Composable
private fun VitaPathCard(
    path: String,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                    text = "PS Vita ROMs Path",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = path,
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
    }
}

@Composable
private fun IgdbCredentialsCard(
    currentClientId: String?,
    hasIgdbCredentials: Boolean,
    gamesWithoutMetadataCount: Int,
    onSetCredentials: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(currentClientId ?: "") }
    var clientSecret by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
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
                        Text(text = "1. Go to dev.twitch.tv/console", style = MaterialTheme.typography.bodySmall)
                        Text(text = "2. Log in with your Twitch account (create one if needed)", style = MaterialTheme.typography.bodySmall)
                        Text(text = "3. Click \"+ Register Your Application\"", style = MaterialTheme.typography.bodySmall)
                        Text(text = "4. Fill in the form:", style = MaterialTheme.typography.bodySmall)
                        Text(text = "   • Name: Any name (e.g., \"ESDE Manager\")", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "   • OAuth Redirect URLs: http://localhost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "   • Category: Application Integration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "   • Client Type: Confidential", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "5. Click \"Create\"", style = MaterialTheme.typography.bodySmall)
                        Text(text = "6. Click \"Manage\" on your new app", style = MaterialTheme.typography.bodySmall)
                        Text(text = "7. Copy the Client ID shown", style = MaterialTheme.typography.bodySmall)
                        Text(text = "8. Click \"New Secret\" to generate a Client Secret", style = MaterialTheme.typography.bodySmall)
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
                    onClick = {
                        onSetCredentials(clientId.trim(), clientSecret.trim())
                        showDialog = false
                    },
                    enabled = clientId.isNotBlank() && clientSecret.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    } else if (gamesWithoutMetadataCount > 0) {
                        "$gamesWithoutMetadataCount games missing metadata"
                    } else {
                        "All games have metadata"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!hasIgdbCredentials) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!hasIgdbCredentials) {
                FilledTonalButton(
                    onClick = {
                        clientId = currentClientId ?: ""
                        clientSecret = ""
                        showDialog = true
                    },
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
            } else {
                IconButton(onClick = {
                    clientId = currentClientId ?: ""
                    clientSecret = ""
                    showDialog = true
                }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure IGDB"
                    )
                }
            }
        }
    }
}

@Composable
private fun VitaMetadataScrapeCard(
    gamesWithoutMetadataCount: Int,
    isScraping: Boolean,
    scrapeProgress: ScrapeProgress?,
    hasCredentials: Boolean,
    onScrapeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                        text = when {
                            isScraping && scrapeProgress != null ->
                                "Scraping: ${scrapeProgress.currentGame ?: "..."}"
                            gamesWithoutMetadataCount > 0 ->
                                "$gamesWithoutMetadataCount game${if (gamesWithoutMetadataCount != 1) "s" else ""} missing metadata"
                            else ->
                                "All games have metadata"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (gamesWithoutMetadataCount > 0 && !isScraping) {
                    FilledTonalButton(
                        onClick = onScrapeClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        enabled = hasCredentials || true // Anonymous access always allowed
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
                    modifier = Modifier.fillMaxWidth()
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
private fun VitaEmptyState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
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
                text = "No PS Vita Games",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Tap + to add a game via IGDB search or manual Title ID entry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun VitaGameItem(
    game: VitaGame,
    artworkPath: String? = null,
    onRemove: () -> Unit,
    onReScrape: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove Game") },
            text = { Text("Remove \"${game.displayName}\" from ES-DE? This will delete the .psvita shortcut file.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirm = false
                        onRemove()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Load artwork bitmap if available
    val artworkBitmap = remember(artworkPath) {
        artworkPath?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
            } catch (e: Exception) { null }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork or fallback icon
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
                    contentDescription = game.displayName,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (game.hasMetadata) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "SCRAPED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            // Title ID as subtitle
            Text(
                text = game.titleId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Remove button
        IconButton(
            onClick = { showRemoveConfirm = true },
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.RemoveCircle,
                contentDescription = "Remove from ES-DE"
            )
        }

        // Options menu
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

@Composable
private fun VitaScrapeSettingsDialog(
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
                    text = "Choose what to download when scraping PS Vita games from IGDB.",
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
            Button(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun VitaReScrapeDialog(
    game: VitaGame,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var searchTerm by remember { mutableStateOf(game.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-scrape Metadata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Re-scrape metadata for \"${game.displayName}\" from IGDB. You can adjust the search term if the game wasn't found.",
                    style = MaterialTheme.typography.bodyMedium,
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
            Button(
                onClick = {
                    val term = searchTerm.trim().takeIf { it != game.displayName && it.isNotBlank() }
                    onConfirm(term)
                }
            ) {
                Text("Re-scrape")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Convert a content URI from the folder picker to an actual file path.
 */
private fun getVitaPathFromUri(context: android.content.Context, uri: Uri): String? {
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
