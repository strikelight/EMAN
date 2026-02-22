package com.esde.emulatormanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.esde.emulatormanager.data.KnownEmulators
import com.esde.emulatormanager.data.RecommendedEmulator
import com.esde.emulatormanager.data.RecommendedEmulators
import com.esde.emulatormanager.data.model.CustomEmulatorMapping
import com.esde.emulatormanager.data.model.InstalledEmulator
import com.esde.emulatormanager.ui.components.EmptyState
import com.esde.emulatormanager.ui.components.HelpIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledEmulatorsScreen(
    emulators: List<InstalledEmulator>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Installed Emulators",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${emulators.size} detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
        if (emulators.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SportsEsports,
                title = "No Emulators Found",
                description = "Install emulators from the Play Store to use with ES-DE",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = emulators,
                    key = { it.packageName }
                ) { emulator ->
                    InstalledEmulatorCard(emulator = emulator)
                }
            }
        }
    }
}

@Composable
fun InstalledEmulatorCard(
    emulator: InstalledEmulator,
    modifier: Modifier = Modifier
) {
    // Collect info from ALL KnownEmulator entries that share this package name,
    // since one physical app (e.g. GameNative) may have multiple entries for different platforms.
    // Fall back to fuzzy matching for apps detected via the second-pass scan (e.g. RPCSX variants).
    val knownEmulator = remember(emulator.packageName) {
        KnownEmulators.findByPackageName(emulator.packageName)
            ?: KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName)
    }
    val allExtensions = remember(emulator.packageName) {
        // Try exact package match first; fall back to the fuzzy-matched entry
        val exactMatches = KnownEmulators.emulators
            .filter { it.packageNames.contains(emulator.packageName) }
        val entries = exactMatches.ifEmpty {
            listOfNotNull(KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName))
        }
        entries.mapNotNull { it.supportedExtensions }
            .flatMap { it.split(" ") }
            .filter { it.isNotBlank() }
            .toSortedSet()
            .joinToString(" ")
            .ifEmpty { null }
    }
    val allSystems = remember(emulator.packageName) {
        val exactMatches = KnownEmulators.emulators
            .filter { it.packageNames.contains(emulator.packageName) }
        val entries = exactMatches.ifEmpty {
            listOfNotNull(KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName))
        }
        entries.flatMap { it.supportedSystems }.toSortedSet()
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
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (emulator.icon != null) {
                        val bitmap = remember(emulator.icon) {
                            emulator.icon.toBitmap().asImageBitmap()
                        }
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = emulator.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Gamepad,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = emulator.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = emulator.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Supported systems and extensions
            if (knownEmulator != null && allSystems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Gamepad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Supports:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = allSystems.joinToString(", ") { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )

                // Show all supported file extensions aggregated across all entries for this package
                if (allExtensions != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "File types:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = allExtensions,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

/**
 * Enhanced emulators screen that shows both known and custom emulators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledEmulatorsScreenWithCustom(
    knownEmulators: List<InstalledEmulator>,
    customEmulators: List<CustomEmulatorMapping>,
    allApps: List<InstalledEmulator>,
    onAddCustomEmulator: () -> Unit,
    onRemoveCustomEmulator: (String) -> Unit,
    onEditCustomEmulator: (CustomEmulatorMapping) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalCount = knownEmulators.size + customEmulators.size
    var showRecommendedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Emulators",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalCount configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Download recommended emulators button
                    IconButton(onClick = { showRecommendedDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Get Emulators"
                        )
                    }
                    HelpIconButton(
                        title = "Emulators",
                        description = "View and manage emulators installed on your device for use with ES-DE.",
                        features = listOf(
                            "Lists all detected emulators — known apps are matched by package name, others by name fuzzy match",
                            "Each card shows the supported systems and file extensions for that emulator",
                            "GameNative appears once but supports .steam, .gog, and .epic file extensions",
                            "Tap download icon to browse recommended emulators and open them in the Play Store",
                            "Use + Add Custom button to register an emulator not in the known list",
                            "Custom emulators can specify package name, launch command, supported systems, and file extensions",
                            "Tap ⋮ on a custom emulator card to edit or delete it"
                        ),
                        iconLegends = listOf(
                            Icons.Outlined.Download to "Get Emulators — browse recommended emulators to install",
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
            ExtendedFloatingActionButton(
                onClick = onAddCustomEmulator,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Custom") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        modifier = modifier
    ) { paddingValues ->
        if (knownEmulators.isEmpty() && customEmulators.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.SportsEsports,
                title = "No Emulators Found",
                description = "Install emulators from the Play Store or add custom ones",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 80.dp // Extra space for FAB
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Custom emulators section
                if (customEmulators.isNotEmpty()) {
                    item {
                        Text(
                            text = "Custom Emulators",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(
                        items = customEmulators,
                        key = { it.packageName }
                    ) { mapping ->
                        // Look up the app icon from allApps
                        val appIcon = remember(mapping.packageName, allApps) {
                            allApps.find { it.packageName == mapping.packageName }?.icon
                        }
                        CustomEmulatorCard(
                            mapping = mapping,
                            appIcon = appIcon,
                            onEdit = { onEditCustomEmulator(mapping) },
                            onRemove = { onRemoveCustomEmulator(mapping.packageName) }
                        )
                    }
                }

                // Known emulators section
                if (knownEmulators.isNotEmpty()) {
                    item {
                        Text(
                            text = "Detected Emulators",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(
                        items = knownEmulators,
                        key = { it.packageName }
                    ) { emulator ->
                        InstalledEmulatorCard(emulator = emulator)
                    }
                }
            }
        }
    }

    // Recommended emulators dialog
    if (showRecommendedDialog) {
        RecommendedEmulatorsDialog(
            installedPackages = knownEmulators.map { it.packageName }.toSet(),
            onDismiss = { showRecommendedDialog = false }
        )
    }
}

/**
 * Dialog showing recommended emulators grouped by system category
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendedEmulatorsDialog(
    installedPackages: Set<String>,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val groupedEmulators = remember { RecommendedEmulators.getGroupedByCategory() }
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Recommended Emulators",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tap an emulator to open its download page",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable list of categories
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedEmulators.forEach { (category, emulators) ->
                        item(key = category) {
                            CategorySection(
                                category = category,
                                emulators = emulators,
                                installedPackages = installedPackages,
                                isExpanded = expandedCategory == category,
                                onToggle = {
                                    expandedCategory = if (expandedCategory == category) null else category
                                },
                                onEmulatorClick = { emulator ->
                                    uriHandler.openUri(emulator.downloadUrl)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySection(
    category: String,
    emulators: List<RecommendedEmulator>,
    installedPackages: Set<String>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onEmulatorClick: (RecommendedEmulator) -> Unit
) {
    Column {
        // Category header (clickable to expand/collapse)
        Surface(
            onClick = onToggle,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${emulators.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Expanded content
        if (isExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                emulators.forEach { emulator ->
                    RecommendedEmulatorItem(
                        emulator = emulator,
                        isInstalled = installedPackages.any { pkg ->
                            val knownId = KnownEmulators.findByPackageName(pkg)?.id
                            knownId == emulator.id || knownId?.startsWith(emulator.id + "_") == true
                        },
                        onClick = { onEmulatorClick(emulator) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendedEmulatorItem(
    emulator: RecommendedEmulator,
    isInstalled: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = emulator.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isInstalled) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "INSTALLED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (emulator.isPaid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiary
                        ) {
                            Text(
                                text = "PAID",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (emulator.isOpenSource) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Code,
                            contentDescription = "Open Source",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = emulator.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (emulator.notes != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = emulator.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                contentDescription = "Open download link",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun CustomEmulatorCard(
    mapping: CustomEmulatorMapping,
    appIcon: android.graphics.drawable.Drawable?,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        val bitmap = remember(appIcon) {
                            appIcon.toBitmap().asImageBitmap()
                        }
                        androidx.compose.foundation.Image(
                            bitmap = bitmap,
                            contentDescription = mapping.appName,
                            modifier = Modifier.size(48.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = mapping.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondary
                        ) {
                            Text(
                                text = "CUSTOM",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = mapping.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action buttons
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Supported systems
            if (mapping.supportedSystems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Gamepad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configured for:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = mapping.supportedSystems.joinToString(", ") { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Custom Emulator?") },
            text = { Text("This will remove ${mapping.appName} from your custom emulator list. The app itself will not be uninstalled.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onRemove()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
