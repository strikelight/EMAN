package com.esde.emulatormanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.esde.emulatormanager.data.model.InstalledEmulator
import com.esde.emulatormanager.data.model.SystemCategory
import com.esde.emulatormanager.data.model.SystemEmulatorConfig
import com.esde.emulatormanager.ui.theme.*

/**
 * System card showing a gaming system with its available emulators
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemCard(
    config: SystemEmulatorConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = getCategoryColor(config.system.name)
    val hasEmulators = config.availableEmulators.isNotEmpty()

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // System icon with category color
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(categoryColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getSystemIcon(config.system.name),
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // System info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.system.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (hasEmulators) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = if (hasEmulators) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (hasEmulators) {
                            "${config.availableEmulators.size} emulator${if (config.availableEmulators.size > 1) "s" else ""} available"
                        } else {
                            "No emulators installed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasEmulators) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Arrow indicator
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Configure",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Emulator item for the configuration screen
 */
@Composable
fun EmulatorItem(
    emulator: InstalledEmulator,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var checked by remember { mutableStateOf(isEnabled) }
    val scale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.95f,
        label = "scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                checked = !checked
                onToggle(checked)
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
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
            // App icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (emulator.icon != null) {
                    val bitmap = remember(emulator.icon) {
                        emulator.icon.toBitmap().asImageBitmap()
                    }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = emulator.appName,
                        modifier = Modifier.size(40.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            // Emulator info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = emulator.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
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

            // Toggle switch
            Switch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    onToggle(it)
                }
            )
        }
    }
}

/**
 * Emulator item for emulators already in ES-DE's default config.
 * Shows an "ES-DE Default" badge instead of a toggle, since these cannot
 * be suppressed via custom_systems — they are always available in ES-DE.
 */
@Composable
fun DefaultEmulatorItem(
    emulator: InstalledEmulator,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (emulator.icon != null) {
                    val bitmap = remember(emulator.icon) {
                        emulator.icon.toBitmap().asImageBitmap()
                    }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = emulator.appName,
                        modifier = Modifier.size(40.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            // Emulator info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = emulator.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
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

            Spacer(modifier = Modifier.width(8.dp))

            // "ES-DE Default" badge — no toggle
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "ES-DE Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Category filter chip
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChip(
    category: SystemCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = when (category) {
        SystemCategory.NINTENDO -> NintendoColor
        SystemCategory.SONY -> SonyColor
        SystemCategory.SEGA -> SegaColor
        SystemCategory.MICROSOFT -> MicrosoftColor
        SystemCategory.ATARI -> AtariColor
        SystemCategory.ARCADE -> ArcadeColor
        SystemCategory.COMPUTERS -> ComputersColor
        SystemCategory.HANDHELDS -> HandheldsColor
        SystemCategory.OTHER -> OtherColor
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = categoryColor.copy(alpha = 0.2f),
            selectedLabelColor = categoryColor
        ),
        modifier = modifier
    )
}

/**
 * Empty state component
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Loading indicator
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Get category color based on system ID
 */
fun getCategoryColor(systemId: String): Color {
    return when (systemId) {
        in SystemCategory.NINTENDO.platforms -> NintendoColor
        in SystemCategory.SONY.platforms -> SonyColor
        in SystemCategory.SEGA.platforms -> SegaColor
        in SystemCategory.MICROSOFT.platforms -> MicrosoftColor
        in SystemCategory.ATARI.platforms -> AtariColor
        in SystemCategory.ARCADE.platforms -> ArcadeColor
        in SystemCategory.COMPUTERS.platforms -> ComputersColor
        in SystemCategory.HANDHELDS.platforms -> HandheldsColor
        else -> OtherColor
    }
}

/**
 * Get icon for a system
 */
fun getSystemIcon(systemId: String): ImageVector {
    return when (systemId) {
        in listOf("gb", "gbc", "gba", "nds", "3ds", "psp", "psvita", "virtualboy",
            "atarilynx", "gamegear", "ngp", "ngpc", "wonderswan", "wonderswancolor") -> Icons.Default.PhoneAndroid
        in listOf("arcade", "mame", "fbneo", "neogeo", "cps1", "cps2", "cps3") -> Icons.Default.Casino
        in listOf("dos", "amiga", "c64", "msx", "msx2", "pc88", "pc98", "x68000",
            "zxspectrum", "amstradcpc", "atarist", "scummvm") -> Icons.Default.Computer
        else -> Icons.Default.Gamepad
    }
}

/**
 * Help dialog showing information about a screen's purpose, features, and toolbar icon legend
 */
@Composable
fun HelpDialog(
    title: String,
    description: String,
    features: List<String>,
    iconLegends: List<Pair<ImageVector, String>> = emptyList(),
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (features.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Features:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    features.forEach { feature ->
                        Row(
                            modifier = Modifier.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (iconLegends.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Toolbar Icons:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    iconLegends.forEach { (icon, label) ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

/**
 * Help icon button that shows a dialog when clicked
 */
@Composable
fun HelpIconButton(
    title: String,
    description: String,
    features: List<String> = emptyList(),
    iconLegends: List<Pair<ImageVector, String>> = emptyList()
) {
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "Help"
        )
    }

    if (showDialog) {
        HelpDialog(
            title = title,
            description = description,
            features = features,
            iconLegends = iconLegends,
            onDismiss = { showDialog = false }
        )
    }
}
