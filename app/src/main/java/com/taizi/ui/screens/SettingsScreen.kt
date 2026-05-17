package com.taizi.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.taizi.ui.components.focusHighlight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.taizi.BuildConfig
import com.taizi.data.update.UpdateDownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateUp: () -> Unit,
    onSelectFolder: () -> Unit = {}
) {
    val context = LocalContext.current
    val library = viewModel.uiState.collectAsState().value
    val currentLibrary = (library as? MainUiState.LibraryLoaded)?.library

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val scrapeStatus by viewModel.scrapeStatus.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()

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
                    onNavigateUp()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showUpdateDialog && updateCheckResult != null) {
        val result = updateCheckResult!!
        AlertDialog(
            onDismissRequest = {
                showUpdateDialog = false
                viewModel.resetUpdateState()
            },
            title = {
                Text(
                    when (updateDownloadState) {
                        is UpdateDownloadState.Downloading -> "Downloading Update"
                        is UpdateDownloadState.Ready -> "Install Update"
                        is UpdateDownloadState.Error -> "Update Error"
                        else -> if (result.isUpdateAvailable) "Update Available" else "No Updates"
                    }
                )
            },
            text = {
                Column {
                    when (updateDownloadState) {
                        is UpdateDownloadState.Downloading -> {
                            val progress = (updateDownloadState as UpdateDownloadState.Downloading).progress
                            Text("Downloading ${result.latestVersion}…")
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("$progress%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is UpdateDownloadState.Ready -> {
                            Text("Update ${result.latestVersion} is ready to install. The app will restart after installation.")
                        }
                        is UpdateDownloadState.Error -> {
                            Text((updateDownloadState as UpdateDownloadState.Error).message)
                        }
                        else -> {
                            if (result.isUpdateAvailable) {
                                Text("A new version is available: ${result.latestVersion}")
                                if (result.releaseNotes.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Release notes:", fontWeight = FontWeight.Medium)
                                    Text(result.releaseNotes, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text("You are on the latest version (${result.currentVersion}).")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                when (updateDownloadState) {
                    is UpdateDownloadState.Ready -> {
                        TextButton(onClick = {
                            val file = (updateDownloadState as UpdateDownloadState.Ready).file
                            if (viewModel.canRequestInstallPackages()) {
                                showUpdateDialog = false
                                viewModel.installUpdate(file)
                            } else {
                                val intent = viewModel.getInstallPermissionIntent()
                                context.startActivity(intent)
                            }
                        }) { Text("Install") }
                    }
                    is UpdateDownloadState.Downloading -> {
                        TextButton(onClick = {
                            viewModel.resetUpdateState()
                            showUpdateDialog = false
                        }) { Text("Cancel") }
                    }
                    is UpdateDownloadState.Error -> {
                        TextButton(onClick = {
                            viewModel.resetUpdateState()
                            showUpdateDialog = false
                        }) { Text("Dismiss") }
                    }
                    else -> {
                        if (result.isUpdateAvailable) {
                            TextButton(onClick = {
                                viewModel.downloadUpdate()
                            }) { Text("Download") }
                        } else {
                            TextButton(onClick = {
                                showUpdateDialog = false
                                viewModel.resetUpdateState()
                            }) { Text("OK") }
                        }
                    }
                }
            },
            dismissButton = {
                if (updateDownloadState !is UpdateDownloadState.Downloading) {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        viewModel.resetUpdateState()
                    }) { Text("Close") }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier.focusHighlight(shape = CircleShape)
                    ) {
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
                        onClick = onSelectFolder
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
                            subtitle = "Download art and normalize names from ScreenScraper (runs in background)",
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
                        subtitle = BuildConfig.VERSION_NAME,
                        icon = MaterialIcons.Filled.Info,
                        onClick = {}
                    )
                    val isChecking = updateDownloadState is UpdateDownloadState.Checking
                    SettingsItem(
                        title = if (isChecking) "Checking…" else "Check for Updates",
                        subtitle = if (updateCheckResult?.isUpdateAvailable == true) "Update available!" else "Tap to check for new versions",
                        icon = if (isChecking) MaterialIcons.Filled.Sync else MaterialIcons.Filled.SystemUpdate,
                        onClick = {
                            if (!isChecking) {
                                viewModel.checkForUpdates()
                                showUpdateDialog = true
                            }
                        }
                    )
                    SettingsItem(
                        title = "Taizi",
                        subtitle = "A launcher for your retro handheld",
                        iconRes = com.taizi.R.drawable.icon,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconRes: Int? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

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
