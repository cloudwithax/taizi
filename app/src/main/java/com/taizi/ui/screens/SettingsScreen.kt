package com.taizi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taizi.domain.repository.LibraryRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateUp: () -> Unit
) {
    val library = viewModel.uiState.collectAsState().value
    val currentLibrary = (library as? MainUiState.LibraryLoaded)?.library

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(MaterialIcons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SettingsSection(title = "Library") {
                    SettingsItem(
                        title = "ROM Root",
                        subtitle = currentLibrary?.romRoot ?: "Not set",
                        icon = MaterialIcons.Filled.Folder,
                        onClick = { /* TODO: Pick folder */ }
                    )
                    SettingsItem(
                        title = "Rescan Library",
                        subtitle = "Force full rescan of ROM folders",
                        icon = MaterialIcons.Filled.Refresh,
                        onClick = { viewModel.triggerFullScan() }
                    )
                }
            }

            item {
                SettingsSection(title = "Emulators") {
                    SettingsItem(
                        title = "Configure Emulators",
                        subtitle = "Set up emulator for each system",
                        icon = MaterialIcons.Filled.Settings,
                        onClick = { /* TODO: Navigate to emulator manager */ }
                    )
                }
            }

            item {
                SettingsSection(title = "Appearance") {
                    val colors = MaterialTheme.colorScheme
                    SettingsItem(
                        title = "Theme",
                        subtitle = "Dark (forced)",
                        icon = MaterialIcons.Filled.Palette,
                        onClick = { /* Theme picker */ }
                    )
                }
            }

            item {
                SettingsSection(title = "Scraping") {
                    SettingsItem(
                        title = "Enable Scraping",
                        subtitle = "Download box art from Screenscraper.fr",
                        icon = MaterialIcons.Filled.Image,
                        onClick = { /* Toggle scraper */ }
                    )
                    SettingsItem(
                        title = "Screenscraper Account",
                        subtitle = "Configure account credentials",
                        icon = MaterialIcons.Filled.AccountCircle,
                        onClick = { /* Configure account */ }
                    )
                }
            }

            item {
                SettingsSection(title = "System") {
                    SettingsItem(
                        title = "Battery Optimization",
                        subtitle = "Disable battery optimization for Taizi",
                        icon = MaterialIcons.Filled.BatteryChargingFull,
                        onClick = { /* Request ignore battery optimization */ }
                    )
                    SettingsItem(
                        title = "Set as Default Launcher",
                        subtitle = "Replace home screen with Taizi",
                        icon = MaterialIcons.Filled.Home,
                        onClick = { /* Open launcher picker */ }
                    )
                }
            }

            item {
                SettingsSection(title = "About") {
                    SettingsItem(
                        title = "Version",
                        subtitle = "1.0.0",
                        icon = MaterialIcons.Filled.Info,
                        onClick = {}
                    )
                    SettingsItem(
                        title = "Check for Updates",
                        subtitle = "Check GitHub for new releases",
                        icon = MaterialIcons.Filled.SystemUpdate,
                        onClick = { /* Check updates */ }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

            Icon(
                imageVector = MaterialIcons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}
