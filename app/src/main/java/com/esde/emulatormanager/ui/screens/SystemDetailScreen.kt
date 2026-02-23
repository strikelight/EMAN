package com.esde.emulatormanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.esde.emulatormanager.data.BiosFile
import com.esde.emulatormanager.data.EsdeDefaultConfig
import com.esde.emulatormanager.data.KnownEmulators
import com.esde.emulatormanager.data.SystemBios
import com.esde.emulatormanager.data.model.InstalledEmulator
import com.esde.emulatormanager.data.model.SystemEmulatorConfig
import com.esde.emulatormanager.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDetailScreen(
    config: SystemEmulatorConfig,
    availableEmulators: List<InstalledEmulator>,
    onBack: () -> Unit,
    onEmulatorToggle: (InstalledEmulator, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(config.system.name)
    var showSaveSnackbar by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = config.system.fullName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = config.system.name.uppercase(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = {
            if (showSaveSnackbar) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { showSaveSnackbar = false }) {
                            Text("OK")
                        }
                    }
                ) {
                    Text("Configuration saved! Restart ES-DE to apply changes.")
                }
            }
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // System info card
            item {
                SystemInfoCard(
                    config = config,
                    categoryColor = categoryColor
                )
            }

            // Partition emulators — plain filter is fine here (LazyListScope, not @Composable)
            val defaultEmulators = availableEmulators.filter { EsdeDefaultConfig.isDefaultEmulator(it.packageName) }
            val customEmulators = availableEmulators.filter { !EsdeDefaultConfig.isDefaultEmulator(it.packageName) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Installed Emulators",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${availableEmulators.size} found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (availableEmulators.isEmpty()) {
                item { EmptyEmulatorState() }
            } else {
                // Explanatory note — only shown when there are ES-DE default emulators
                if (defaultEmulators.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "\"ES-DE Default\" emulators are built into ES-DE and always available. " +
                                        "Use toggles below to add extra emulators to your custom configuration.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ES-DE default emulators — badge only, no toggle
                items(defaultEmulators, key = { it.packageName }) { emulator ->
                    DefaultEmulatorItem(emulator = emulator)
                }

                // Custom emulators — toggleable
                if (customEmulators.isNotEmpty()) {
                    if (defaultEmulators.isNotEmpty()) {
                        item {
                            Text(
                                text = "Custom Configuration",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    items(customEmulators, key = { it.packageName }) { emulator ->
                        var isEnabled by remember {
                            mutableStateOf(config.configuredEmulators.contains(emulator.appName))
                        }
                        EmulatorItem(
                            emulator = emulator,
                            isEnabled = isEnabled,
                            onToggle = { enabled ->
                                isEnabled = enabled
                                onEmulatorToggle(emulator, enabled)
                                showSaveSnackbar = true
                            }
                        )
                    }
                }
            }

            // Required BIOS / Firmware files
            item {
                val biosFiles = remember(config.system.name) {
                    SystemBios.getForSystem(config.system.name)
                }
                if (biosFiles.isNotEmpty()) {
                    BiosFilesCard(biosFiles = biosFiles)
                }
            }

            // Supported file extensions
            // For Windows/PC systems, show the extensions from configured emulators if available
            item {
                val effectiveExtensions = remember(config) {
                    getEffectiveExtensions(config)
                }
                SupportedExtensionsCard(
                    extensions = effectiveExtensions
                )
            }

            // Instructions
            item {
                InstructionsCard()
            }
        }
    }
}

@Composable
fun SystemInfoCard(
    config: SystemEmulatorConfig,
    categoryColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = categoryColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(categoryColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getSystemIcon(config.system.name),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.system.fullName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Platform: ${config.system.platform}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ROM Path: ${config.system.path}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyEmulatorState(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Emulators Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Install a compatible emulator from the Play Store to enable this system in ES-DE.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SupportedExtensionsCard(
    extensions: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Supported File Types",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = extensions.replace(" ", ", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun InstructionsCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "How it works",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            InstructionStep(
                number = "1",
                text = "Enable the emulators you want to use for this system"
            )
            InstructionStep(
                number = "2",
                text = "Configuration is saved to ES-DE/custom_systems folder"
            )
            InstructionStep(
                number = "3",
                text = "Restart ES-DE for changes to take effect"
            )
            InstructionStep(
                number = "4",
                text = "Select your preferred emulator per-game in ES-DE"
            )
        }
    }
}

@Composable
fun InstructionStep(
    number: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun BiosFilesCard(
    biosFiles: List<BiosFile>,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var copiedFilename by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Required BIOS / Firmware",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            biosFiles.forEach { biosFile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = biosFile.filename,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (biosFile.notes != null) {
                            Text(
                                text = biosFile.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(biosFile.filename))
                            copiedFilename = biosFile.filename
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (copiedFilename == biosFile.filename)
                                Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                            contentDescription = "Copy filename",
                            modifier = Modifier.size(16.dp),
                            tint = if (copiedFilename == biosFile.filename)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Get the effective file extensions for a system based on configured emulators.
 * For Windows/PC systems, aggregates extensions from all configured/available emulators.
 */
private fun getEffectiveExtensions(config: SystemEmulatorConfig): String {
    val systemId = config.system.name

    // For Windows/PC systems, collect extensions from all relevant emulators
    if (systemId == "windows" || systemId == "pc") {
        val allExtensions = mutableSetOf<String>()

        // Collect from configured emulators (from XML)
        for (emulatorName in config.configuredEmulators) {
            val knownEmulator = KnownEmulators.emulators.find { known ->
                known.displayName.equals(emulatorName, ignoreCase = true)
            }
            knownEmulator?.supportedExtensions?.split(" ")?.forEach { ext ->
                if (ext.isNotBlank()) allExtensions.add(ext.trim())
            }
        }

        // Also collect from installed/available emulators
        for (emulator in config.availableEmulators) {
            val knownEmulator = KnownEmulators.findByPackageNameFuzzy(emulator.packageName, emulator.appName)
            knownEmulator?.supportedExtensions?.split(" ")?.forEach { ext ->
                if (ext.isNotBlank()) allExtensions.add(ext.trim())
            }
        }

        if (allExtensions.isNotEmpty()) {
            return allExtensions.sorted().joinToString(" ")
        }
    }

    // Fall back to system's default extensions
    return config.system.extensions
}
