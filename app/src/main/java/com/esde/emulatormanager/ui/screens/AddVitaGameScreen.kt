package com.esde.emulatormanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.esde.emulatormanager.data.model.VitaSearchResult

/**
 * Screen for adding a PS Vita game to ES-DE.
 * Offers two modes:
 *  1. IGDB search — look up by game name; user must always supply the Title ID manually
 *  2. Manual entry — display name + Title ID
 *
 * Title IDs are not returned by IGDB; the user gets them from the Vita3K game list
 * (e.g., PCSG00123 for a JP retail game, PCSB00xxx for EU, PCSE00xxx for US).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVitaGameScreen(
    searchQuery: String,
    isSearching: Boolean,
    searchResults: List<VitaSearchResult>,
    errorMessage: String?,
    successMessage: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onAddGame: (titleId: String, displayName: String) -> Unit,
    onDismissError: () -> Unit,
    onDismissSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Expand state: which search result (by ssId) has its add form expanded
    var expandedResultId by remember { mutableStateOf<String?>(null) }

    // Manual entry state
    var manualDisplayName by remember { mutableStateOf("") }
    var manualTitleId by remember { mutableStateOf("") }

    // Per-result title ID entry state
    val resultTitleIds = remember { mutableStateMapOf<String, String>() }

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
            // Reset manual form after successful add
            manualDisplayName = ""
            manualTitleId = ""
            expandedResultId = null
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Add PS Vita Game",
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
                }
            )
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
            // Info card
            item {
                VitaInfoCard()
            }

            // IGDB search section
            item {
                Text(
                    text = "Search IGDB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Game name") },
                    placeholder = { Text("e.g. Persona 4 Golden") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    onSearch(searchQuery)
                                },
                                enabled = searchQuery.length >= 2
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            if (searchQuery.length >= 2) onSearch(searchQuery)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search results
            if (searchResults.isNotEmpty()) {
                items(searchResults, key = { it.ssId }) { result ->
                    val isExpanded = expandedResultId == result.ssId
                    val titleId = resultTitleIds[result.ssId] ?: ""

                    VitaSearchResultCard(
                        result = result,
                        isExpanded = isExpanded,
                        titleId = titleId,
                        onExpand = {
                            expandedResultId = if (isExpanded) null else result.ssId
                        },
                        onTitleIdChange = { resultTitleIds[result.ssId] = it },
                        onAdd = {
                            if (titleId.isNotBlank()) {
                                onAddGame(titleId.trim().uppercase(), result.name)
                            }
                        }
                    )
                }
            } else if (!isSearching && searchQuery.isNotBlank()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No results found on IGDB. Try manual entry below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Divider
            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = "Manual Entry",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            // Manual entry form
            item {
                VitaManualEntryCard(
                    displayName = manualDisplayName,
                    titleId = manualTitleId,
                    onDisplayNameChange = { manualDisplayName = it },
                    onTitleIdChange = { manualTitleId = it },
                    onAdd = {
                        if (manualDisplayName.isNotBlank() && manualTitleId.isNotBlank()) {
                            onAddGame(manualTitleId.trim().uppercase(), manualDisplayName.trim())
                        }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

// ---- Sub-composables ----

@Composable
private fun VitaInfoCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Games are added as .psvita shortcut files in your ES-DE ROM folder. " +
                    "The Title ID (e.g. PCSE00120) is written inside the file and used by Vita3K to launch the game.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Find your Title IDs in Vita3K's game list. Format: PCSE (US), PCSB (EU), PCSG/PCSA (JP).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VitaSearchResultCard(
    result: VitaSearchResult,
    isExpanded: Boolean,
    titleId: String,
    onExpand: () -> Unit,
    onTitleIdChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    result.year?.let { year ->
                        Text(
                            text = year,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onExpand) {
                    Text(if (isExpanded) "Collapse" else "Select")
                }
            }

            if (isExpanded) {
                HorizontalDivider()

                Text(
                    text = "Enter the Title ID from Vita3K for this game:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = titleId,
                    onValueChange = onTitleIdChange,
                    label = { Text("Title ID") },
                    placeholder = { Text("e.g. PCSE00120") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onAdd,
                    enabled = titleId.length >= 9,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to ES-DE")
                }
            }
        }
    }
}

@Composable
private fun VitaManualEntryCard(
    displayName: String,
    titleId: String,
    onDisplayNameChange: (String) -> Unit,
    onTitleIdChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Enter game details manually",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Display name") },
                placeholder = { Text("e.g. Persona 4 Golden") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = titleId,
                onValueChange = onTitleIdChange,
                label = { Text("Title ID") },
                placeholder = { Text("e.g. PCSE00120") },
                supportingText = {
                    Text("9-character ID from Vita3K game list (PCSE/PCSB/PCSG/PCSA + 5 digits)")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onAdd,
                enabled = displayName.isNotBlank() && titleId.length >= 9,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to ES-DE")
            }
        }
    }
}
