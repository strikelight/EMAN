package com.esde.emulatormanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Games hub screen — entry point for all game management sections.
 * Shows summary cards for Android, Windows, and PS Vita games.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesHubScreen(
    androidGamesCount: Int,
    windowsGamesCount: Int,
    vitaGamesCount: Int,
    onNavigateToAndroid: () -> Unit,
    onNavigateToWindows: () -> Unit,
    onNavigateToVita: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Games") }
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Manage games added to ES-DE",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            GamesHubCard(
                icon = Icons.Filled.Android,
                title = "Android",
                subtitle = if (androidGamesCount > 0) {
                    "$androidGamesCount app${if (androidGamesCount != 1) "s" else ""} in ES-DE"
                } else {
                    "Add Android games, apps & emulators"
                },
                onClick = onNavigateToAndroid
            )

            GamesHubCard(
                icon = Icons.Filled.DesktopWindows,
                title = "Windows Games",
                subtitle = if (windowsGamesCount > 0) {
                    "$windowsGamesCount game${if (windowsGamesCount != 1) "s" else ""} in ES-DE"
                } else {
                    "Add Steam, GOG & Epic games"
                },
                onClick = onNavigateToWindows
            )

            GamesHubCard(
                icon = Icons.Filled.SportsEsports,
                title = "PS Vita Games",
                subtitle = if (vitaGamesCount > 0) {
                    "$vitaGamesCount game${if (vitaGamesCount != 1) "s" else ""} in ES-DE"
                } else {
                    "Add PS Vita games via Vita3K"
                },
                onClick = onNavigateToVita
            )
        }
    }
}

@Composable
private fun GamesHubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Navigate",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
