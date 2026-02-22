package com.esde.emulatormanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esde.emulatormanager.data.model.Profile
import com.esde.emulatormanager.data.model.ProfilesUiState
import com.esde.emulatormanager.ui.components.HelpIconButton
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(
    uiState: ProfilesUiState,
    onCreateProfile: (String, Boolean) -> Unit,
    onLoadProfile: (String) -> Unit,
    onSaveToProfile: (String) -> Unit,
    onRenameProfile: (String, String) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onToggleDeviceAssociation: (String, Boolean) -> Unit,
    onShowCreateDialog: (Boolean) -> Unit,
    onShowRenameDialog: (Profile?) -> Unit,
    onShowDeleteDialog: (Profile?) -> Unit,
    onShowLoadDialog: (Profile?) -> Unit,
    onDismissDevicePrompt: () -> Unit,
    onDismissSuccess: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Show success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissSuccess()
        }
    }

    // Show error message
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profiles",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { onShowCreateDialog(true) },
                        enabled = uiState.esdeConfigured
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Profile"
                        )
                    }
                    HelpIconButton(
                        title = "Profiles",
                        description = "Save and restore your complete ES-DE configuration across devices or as backups.",
                        features = listOf(
                            "A profile captures your current Android shortcuts, Windows game shortcuts, custom emulator mappings, and folder paths",
                            "Loading a profile replaces all existing shortcuts in your ROM folders with the profile's saved shortcuts",
                            "Your current configuration is automatically backed up before any profile is loaded",
                            "Associate a profile with a specific device so it is suggested automatically when that device is detected",
                            "Profiles without a device association work on any device",
                            "Profiles are stored in ES-DE/profiles/ on your SD card — accessible from any device sharing that card",
                            "Use ⋮ menu on a profile card to Load, Save Current, Rename, Link/Unlink device, or Delete"
                        ),
                        iconLegends = listOf(
                            Icons.Default.Add to "Add — create a new profile from current configuration",
                            Icons.Outlined.Info to "Help — show this info dialog"
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                !uiState.esdeConfigured -> {
                    EmptyStateMessage(
                        icon = Icons.Outlined.Warning,
                        title = "ES-DE Not Configured",
                        message = "Please set up ES-DE before using profiles. Profiles are stored in the ES-DE folder on your SD card."
                    )
                }

                uiState.profiles.isEmpty() -> {
                    EmptyStateMessage(
                        icon = Icons.Outlined.Person,
                        title = "No Profiles",
                        message = "Create a profile to save your current configuration. Profiles store your shortcuts and settings so you can switch between devices."
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current device info card
                        item {
                            CurrentDeviceCard(
                                deviceName = uiState.currentDeviceName,
                                activeProfile = uiState.activeProfile
                            )
                        }

                        // Profile cards
                        items(uiState.profiles, key = { it.id }) { profile ->
                            ProfileCard(
                                profile = profile,
                                isActive = uiState.activeProfile?.id == profile.id,
                                currentDeviceName = uiState.currentDeviceName,
                                onLoad = { onShowLoadDialog(profile) },
                                onSave = { onSaveToProfile(profile.id) },
                                onRename = { onShowRenameDialog(profile) },
                                onDelete = { onShowDeleteDialog(profile) },
                                onToggleDeviceAssociation = { associate ->
                                    onToggleDeviceAssociation(profile.id, associate)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Profile Dialog
    if (uiState.showCreateDialog) {
        CreateProfileDialog(
            onDismiss = { onShowCreateDialog(false) },
            onCreate = onCreateProfile
        )
    }

    // Rename Dialog
    if (uiState.showRenameDialog && uiState.profileToEdit != null) {
        RenameProfileDialog(
            currentName = uiState.profileToEdit.name,
            onDismiss = { onShowRenameDialog(null) },
            onRename = { newName -> onRenameProfile(uiState.profileToEdit.id, newName) }
        )
    }

    // Delete Confirmation Dialog
    if (uiState.showDeleteConfirmDialog && uiState.profileToEdit != null) {
        DeleteProfileDialog(
            profileName = uiState.profileToEdit.name,
            onDismiss = { onShowDeleteDialog(null) },
            onConfirm = { onDeleteProfile(uiState.profileToEdit.id) }
        )
    }

    // Load Confirmation Dialog
    if (uiState.showLoadConfirmDialog && uiState.profileToEdit != null) {
        LoadProfileDialog(
            profile = uiState.profileToEdit,
            onDismiss = { onShowLoadDialog(null) },
            onConfirm = { onLoadProfile(uiState.profileToEdit.id) }
        )
    }

    // Device Switch Prompt
    if (uiState.showDeviceSwitchPrompt) {
        DeviceSwitchDialog(
            matchingProfiles = uiState.matchingProfiles,
            allProfiles = uiState.profiles,
            onSelectProfile = { profileId ->
                onDismissDevicePrompt()
                onLoadProfile(profileId)
            },
            onDismiss = onDismissDevicePrompt,
            onCreateNew = {
                onDismissDevicePrompt()
                onShowCreateDialog(true)
            }
        )
    }
}

@Composable
private fun EmptyStateMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CurrentDeviceCard(
    deviceName: String,
    activeProfile: Profile?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Device",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (activeProfile != null) {
                    Text(
                        text = "Active profile: ${activeProfile.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    isActive: Boolean,
    currentDeviceName: String,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleDeviceAssociation: (Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                // Profile icon with active indicator
                Box {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Outlined.Person,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Profile info
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Device association
                    val deviceInfo = profile.deviceFingerprint?.let {
                        "${it.manufacturer} ${it.model}"
                    } ?: "Any device"

                    Text(
                        text = deviceInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Last modified
                    Text(
                        text = "Modified: ${dateFormat.format(Date(profile.modifiedAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Menu button
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
                            text = { Text("Load") },
                            onClick = {
                                showMenu = false
                                onLoad()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Current") },
                            onClick = {
                                showMenu = false
                                onSave()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Save, contentDescription = null)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        if (profile.deviceFingerprint != null) {
                            DropdownMenuItem(
                                text = { Text("Unlink from Device") },
                                onClick = {
                                    showMenu = false
                                    onToggleDeviceAssociation(false)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.LinkOff, contentDescription = null)
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Link to This Device") },
                                onClick = {
                                    showMenu = false
                                    onToggleDeviceAssociation(true)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null)
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            // Stats row
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = profile.getSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CreateProfileDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var associateWithDevice by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = associateWithDevice,
                        onCheckedChange = { associateWithDevice = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Associate with this device",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    text = "When associated, this profile will be automatically suggested when you use this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, associateWithDevice) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
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
private fun RenameProfileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onRename(name) },
                enabled = name.isNotBlank() && name != currentName
            ) {
                Text("Rename")
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
private fun DeleteProfileDialog(
    profileName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile") },
        text = {
            Text("Are you sure you want to delete \"$profileName\"? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
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
private fun LoadProfileDialog(
    profile: Profile,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load Profile") },
        text = {
            Column {
                Text("Load \"${profile.name}\"?")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This will replace your current shortcuts with those from this profile. Your current configuration will be backed up automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = profile.getSummary(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Load")
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
private fun DeviceSwitchDialog(
    matchingProfiles: List<Profile>,
    allProfiles: List<Profile>,
    onSelectProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (matchingProfiles.isNotEmpty()) "Welcome Back!"
                else "Different Device Detected"
            )
        },
        text = {
            Column {
                if (matchingProfiles.isNotEmpty()) {
                    Text("Profiles found for this device:")
                    Spacer(modifier = Modifier.height(12.dp))
                    matchingProfiles.forEach { profile ->
                        TextButton(
                            onClick = { onSelectProfile(profile.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(profile.name)
                        }
                    }
                } else {
                    Text("No profiles are associated with this device. Would you like to select an existing profile or create a new one?")
                    Spacer(modifier = Modifier.height(12.dp))
                    if (allProfiles.isNotEmpty()) {
                        Text(
                            text = "Available profiles:",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        allProfiles.take(5).forEach { profile ->
                            TextButton(
                                onClick = { onSelectProfile(profile.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.Person, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(profile.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Text("Create New")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        }
    )
}
