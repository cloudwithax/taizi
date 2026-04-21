package com.taizi.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private fun treeUriToFilePath(uri: Uri): String? {
    val docId = uri.lastPathSegment ?: return null
    val split = docId.split(":")
    if (split.size < 2) return null
    val volume = split[0]
    val relativePath = split[1]
    val root = if (volume == "primary") {
        Environment.getExternalStorageDirectory().absolutePath
    } else {
        "/storage/$volume"
    }
    return "$root/$relativePath"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val library = viewModel.uiState.collectAsState().value
    val currentLibrary = (library as? MainUiState.LibraryLoaded)?.library

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUriToFilePath(it) }?.let { path ->
            viewModel.triggerFullScan(path)
            onNavigateUp()
        }
    }

    var showClearCacheDialog by remember { mutableStateOf(false) }
    val scrapeStatus by viewModel.scrapeStatus.collectAsState()

    val (initIndex, initOffset) = viewModel.getSettingsScroll()
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initIndex,
        initialFirstVisibleItemScrollOffset = initOffset
    )
    LaunchedEffect(Unit) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (i, off) -> viewModel.setSettingsScroll(i, off) }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?") },
            text = { Text("This will remove cached library data. Your ROMs and box art database are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    viewModel.clearCache()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

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
            state = listState,
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
                        onClick = { folderPickerLauncher.launch(null) }
                    )
                    SettingsItem(
                        title = "Rescan Library",
                        subtitle = "Force full rescan of ROM folders",
                        icon = MaterialIcons.Filled.Refresh,
                        onClick = {
                            viewModel.triggerFullScan()
                            onNavigateUp()
                        }
                    )
                    SettingsItem(
                        title = "Clear Cache",
                        subtitle = "Remove cached library data",
                        icon = MaterialIcons.Filled.Delete,
                        onClick = { showClearCacheDialog = true }
                    )
                }
            }

            item {
                SettingsSection(title = "Scraping") {
                    if (scrapeStatus.isRunning) {
                        SettingsItem(
                            title = "Scraping… ${scrapeStatus.current}/${scrapeStatus.total}",
                            subtitle = "${scrapeStatus.gameName} · ${scrapeStatus.systemName}",
                            icon = MaterialIcons.Filled.HourglassBottom,
                            onClick = { viewModel.cancelScrape() }
                        )
                    } else {
                        SettingsItem(
                            title = "Scrape All Box Art",
                            subtitle = "Download art from IGDB (runs in background)",
                            icon = MaterialIcons.Filled.Image,
                            onClick = { viewModel.scrapeAll() }
                        )
                    }
                }
            }

            item {
                SettingsSection(title = "System") {
                    SettingsItem(
                        title = "Set as Default Launcher",
                        subtitle = "Replace home screen with Taizi",
                        icon = MaterialIcons.Filled.Home,
                        onClick = {
                            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )
                    SettingsItem(
                        title = "Battery Optimization",
                        subtitle = "Disable battery optimization for Taizi",
                        icon = MaterialIcons.Filled.BatteryChargingFull,
                        onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:${context.packageName}")
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item {
                SettingsSection(title = "About") {
                    SettingsItem(
                        title = "Version",
                        subtitle = "1.0.4",
                        icon = MaterialIcons.Filled.Info,
                        onClick = {}
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                fontWeight = FontWeight.Bold,
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
