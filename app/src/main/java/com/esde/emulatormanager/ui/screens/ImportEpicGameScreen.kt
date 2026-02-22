package com.esde.emulatormanager.ui.screens

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esde.emulatormanager.data.model.EpicGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportEpicGameScreen(
    importPath: String,
    foundGames: List<EpicGame>,
    existingGames: Set<String>,
    isScanning: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onPathChange: (String) -> Unit,
    onScanPath: () -> Unit,
    onImportGame: (EpicGame) -> Unit,
    onImportAll: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // File picker launcher for .epicgame files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("ImportEpicGameScreen", "Selected URI: $it")

            // First try to get the display name for the UI
            val displayName = getDisplayNameFromUri(context, it)
            Log.d("ImportEpicGameScreen", "Display name: $displayName")
            selectedFileName = displayName

            // Convert the URI to an actual file path
            val path = getFilePathFromUri(context, it)
            Log.d("ImportEpicGameScreen", "Converted path: $path")

            if (path != null) {
                onPathChange(path)
            } else if (displayName != null && displayName.endsWith(".epicgame")) {
                // If path conversion failed but we have a valid .epicgame filename,
                // try to find it in common locations
                val possiblePath = findFileInCommonLocations(displayName)
                Log.d("ImportEpicGameScreen", "Found in common location: $possiblePath")
                if (possiblePath != null) {
                    onPathChange(possiblePath)
                } else {
                    // Last resort: show the display name and hope the file exists at a standard location
                    val defaultPath = "${Environment.getExternalStorageDirectory().absolutePath}/Download/$displayName"
                    Log.d("ImportEpicGameScreen", "Trying default path: $defaultPath")
                    onPathChange(defaultPath)
                }
            }
        }
    }

    // Show error message
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Long
            )
            onDismissError()
        }
    }

    // Show success message
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onDismissSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Epic Game",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        // File selection display value
        val displayValue = if (selectedFileName != null && importPath.isNotEmpty()) {
            selectedFileName ?: importPath.substringAfterLast("/")
        } else if (importPath.isNotEmpty()) {
            importPath.substringAfterLast("/")
        } else {
            ""
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Instructions card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "How to import Epic games",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Open GameNative and go to your Epic library\n" +
                                        "2. Long-press on a game and select \"Export for Frontend\"\n" +
                                        "3. Note the export location (usually Downloads folder)\n" +
                                        "4. Use the Browse button below to select the .epicgame file\n" +
                                        "5. The game will be imported to ES-DE",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // File picker field
            item {
                OutlinedTextField(
                    value = displayValue,
                    onValueChange = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Selected file") },
                    placeholder = { Text("Select a .epicgame file") },
                    readOnly = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = {
                            filePickerLauncher.launch(arrayOf("*/*"))
                        }) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Browse"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Show full path as helper text
            if (importPath.isNotEmpty()) {
                item {
                    Text(
                        text = importPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 16.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Import button
            item {
                Button(
                    onClick = onScanPath,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = importPath.isNotEmpty() && importPath.endsWith(".epicgame") && !isScanning
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isScanning) "Importing..." else "Import Game")
                }
            }

            // Validation message
            if (importPath.isNotEmpty() && !importPath.endsWith(".epicgame")) {
                item {
                    Text(
                        text = "Please select a .epicgame file exported from GameNative",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Previously imported games section header
            if (foundGames.isNotEmpty()) {
                item {
                    Text(
                        text = "Previously Imported Games",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                }

                items(
                    items = foundGames,
                    key = { it.sourcePath }
                ) { game ->
                    EpicGameItem(
                        game = game,
                        isAlreadyAdded = true,
                        onClick = { },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else {
                // Empty state
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SportsEsports,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No Epic games imported yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Use the Browse button above to select\na .epicgame file from GameNative",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpicGameItem(
    game: EpicGame,
    isAlreadyAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Game icon placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = game.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
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
                Text(
                    text = "Epic Games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Get the display name (filename) from a content URI.
 */
private fun getDisplayNameFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (e: Exception) {
        Log.e("ImportEpicGameScreen", "Error getting display name", e)
        null
    }
}

/**
 * Try to find a file in common download/export locations.
 */
private fun findFileInCommonLocations(fileName: String): String? {
    val locations = listOf(
        "${Environment.getExternalStorageDirectory().absolutePath}/Download",
        "${Environment.getExternalStorageDirectory().absolutePath}/Downloads",
        "${Environment.getExternalStorageDirectory().absolutePath}/GameNative",
        "${Environment.getExternalStorageDirectory().absolutePath}/GameNative/exports",
        "${Environment.getExternalStorageDirectory().absolutePath}/Documents"
    )

    for (location in locations) {
        val file = java.io.File(location, fileName)
        if (file.exists()) {
            return file.absolutePath
        }
    }
    return null
}

/**
 * Convert a content URI from the file picker to an actual file path.
 */
private fun getFilePathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        // First, check if this is a file:// URI
        if (uri.scheme == "file") {
            return uri.path
        }

        // For document URIs, we need to extract the path
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            Log.d("ImportEpicGameScreen", "Not a document URI, trying raw path")
            return uri.path
        }

        val docId = DocumentsContract.getDocumentId(uri)
        Log.d("ImportEpicGameScreen", "Document ID: $docId")

        // Handle different document providers
        when {
            // Downloads provider
            uri.authority == "com.android.providers.downloads.documents" -> {
                // Download document IDs can be raw:path or msf:id format
                if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
                // For msf: format, we'll need to query for the actual path
                // Fall through to display name approach
                null
            }
            // External storage provider (primary: or SD card)
            docId.startsWith("primary:") -> {
                val relativePath = docId.removePrefix("primary:")
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            }
            docId.contains(":") -> {
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
            }
            else -> {
                // Simple path without prefix
                "${Environment.getExternalStorageDirectory().absolutePath}/$docId"
            }
        }
    } catch (e: Exception) {
        Log.e("ImportEpicGameScreen", "Error converting URI to path", e)
        null
    }
}
