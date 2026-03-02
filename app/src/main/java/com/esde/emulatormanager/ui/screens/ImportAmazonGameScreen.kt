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
import com.esde.emulatormanager.data.model.AmazonGame

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAmazonGameScreen(
    importPath: String,
    foundGames: List<AmazonGame>,
    existingGames: Set<String>,
    isScanning: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onPathChange: (String) -> Unit,
    onScanPath: () -> Unit,
    onImportGame: (AmazonGame) -> Unit,
    onImportAll: () -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    // File picker launcher for .amazongame files
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            Log.d("ImportAmazonGameScreen", "Selected URI: $it")

            val displayName = getAmazonDisplayNameFromUri(context, it)
            Log.d("ImportAmazonGameScreen", "Display name: $displayName")
            selectedFileName = displayName

            val path = getAmazonFilePathFromUri(context, it)
            Log.d("ImportAmazonGameScreen", "Converted path: $path")

            if (path != null) {
                onPathChange(path)
            } else if (displayName != null && displayName.endsWith(".amazon")) {
                val possiblePath = findAmazonFileInCommonLocations(displayName)
                Log.d("ImportAmazonGameScreen", "Found in common location: $possiblePath")
                if (possiblePath != null) {
                    onPathChange(possiblePath)
                } else {
                    val defaultPath = "${Environment.getExternalStorageDirectory().absolutePath}/Download/$displayName"
                    Log.d("ImportAmazonGameScreen", "Trying default path: $defaultPath")
                    onPathChange(defaultPath)
                }
            }
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            onDismissError()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
            onDismissSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Import Amazon Game",
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
                                text = "How to import Amazon games",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "1. Open GameNative and go to your Amazon library\n" +
                                        "2. Long-press on a game and select \"Export for Frontend\"\n" +
                                        "3. Note the export location (usually Downloads folder)\n" +
                                        "4. Use the Browse button below to select the .amazon file\n" +
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
                    placeholder = { Text("Select a .amazon file") },
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
                    enabled = importPath.isNotEmpty() && importPath.endsWith(".amazongame") && !isScanning
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
            if (importPath.isNotEmpty() && !importPath.endsWith(".amazongame")) {
                item {
                    Text(
                        text = "Please select a .amazongame file exported from GameNative",
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
                    AmazonGameItem(
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
                                text = "No Amazon games imported yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Use the Browse button above to select\na .amazon file from GameNative",
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
private fun AmazonGameItem(
    game: AmazonGame,
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
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingCart,
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
                    text = "Amazon Games",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getAmazonDisplayNameFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    } catch (e: Exception) {
        Log.e("ImportAmazonGameScreen", "Error getting display name", e)
        null
    }
}

private fun findAmazonFileInCommonLocations(fileName: String): String? {
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

private fun getAmazonFilePathFromUri(context: android.content.Context, uri: Uri): String? {
    return try {
        if (uri.scheme == "file") {
            return uri.path
        }

        if (!DocumentsContract.isDocumentUri(context, uri)) {
            return uri.path
        }

        val docId = DocumentsContract.getDocumentId(uri)

        when {
            uri.authority == "com.android.providers.downloads.documents" -> {
                if (docId.startsWith("raw:")) {
                    return docId.removePrefix("raw:")
                }
                null
            }
            docId.startsWith("primary:") -> {
                val relativePath = docId.removePrefix("primary:")
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            }
            docId.contains(":") -> {
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
            }
            else -> {
                "${Environment.getExternalStorageDirectory().absolutePath}/$docId"
            }
        }
    } catch (e: Exception) {
        Log.e("ImportAmazonGameScreen", "Error converting URI to path", e)
        null
    }
}
