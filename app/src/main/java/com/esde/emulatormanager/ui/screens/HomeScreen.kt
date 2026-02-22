package com.esde.emulatormanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.esde.emulatormanager.data.model.SystemCategory
import com.esde.emulatormanager.data.model.SystemEmulatorConfig
import com.esde.emulatormanager.ui.components.*
import com.esde.emulatormanager.ui.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isLoading: Boolean,
    systems: List<SystemEmulatorConfig>,
    searchQuery: String,
    selectedCategory: SystemCategory?,
    installedEmulatorsCount: Int,
    esdeConfigPath: String?,
    successMessage: String?,
    errorMessage: String?,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (SystemCategory?) -> Unit,
    onSystemClick: (SystemEmulatorConfig) -> Unit,
    onRefresh: () -> Unit,
    onBackup: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearch by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message in snackbar
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = SnackbarDuration.Short
            )
            onDismissSuccess()
        }
    }

    // Show error message in snackbar
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
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
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Search systems...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                    } else {
                        Column {
                            Text(
                                text = "EMAN",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (installedEmulatorsCount > 0) {
                                Text(
                                    text = "$installedEmulatorsCount emulators detected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) onSearchQueryChange("")
                    }) {
                        Icon(
                            imageVector = if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearch) "Close search" else "Search"
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = onBackup) {
                        Icon(
                            imageVector = Icons.Outlined.Backup,
                            contentDescription = "Backup configuration"
                        )
                    }
                    HelpIconButton(
                        title = "Systems",
                        description = "Browse and configure emulators for each gaming system supported by ES-DE.",
                        features = listOf(
                            "View all gaming systems detected by ES-DE",
                            "See which emulators are installed and available for each system",
                            "Tap a system to configure which emulator to use and set alternatives",
                            "Filter systems by category (Nintendo, Sony, Arcade, etc.)",
                            "Search for specific systems by name",
                            "Backup button exports your ES-DE emulator configuration",
                            "ES-DE status card shows whether ES-DE is detected and configured"
                        ),
                        iconLegends = listOf(
                            Icons.Default.Search to "Search — find systems by name",
                            Icons.Default.Refresh to "Refresh — reload systems and emulator detection",
                            Icons.Outlined.Backup to "Backup — export your ES-DE emulator configuration",
                            Icons.Outlined.Info to "Help — show this info dialog"
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (isLoading) {
            LoadingIndicator(modifier = Modifier.padding(paddingValues))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // ES-DE status card
                EsdeStatusCard(
                    configPath = esdeConfigPath,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Category filter chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("All") },
                            leadingIcon = if (selectedCategory == null) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                    items(SystemCategory.entries.toTypedArray()) { category ->
                        CategoryChip(
                            category = category,
                            isSelected = selectedCategory == category,
                            onClick = { onCategorySelected(if (selectedCategory == category) null else category) }
                        )
                    }
                }

                // Systems list
                if (systems.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.SportsEsports,
                        title = "No systems found",
                        description = if (searchQuery.isNotBlank()) {
                            "No systems match your search"
                        } else {
                            "Install some emulators to get started"
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(
                            items = systems,
                            key = { it.system.name }
                        ) { config ->
                            SystemCard(
                                config = config,
                                onClick = { onSystemClick(config) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EsdeStatusCard(
    configPath: String?,
    modifier: Modifier = Modifier
) {
    val isConfigured = configPath != null

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isConfigured) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isConfigured) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                contentDescription = null,
                tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConfigured) "ES-DE Configured" else "ES-DE Not Found",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = configPath ?: "Please install and run ES-DE first",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
