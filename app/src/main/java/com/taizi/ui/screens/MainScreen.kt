package com.taizi.ui.screens

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.taizi.domain.model.Game
import com.taizi.domain.model.Library
import com.taizi.domain.model.System
import com.taizi.ui.components.StatusBar
import com.taizi.ui.theme.TaiziTheme

private fun treeUriToFilePath(uri: Uri): String? {
    val docId = uri.lastPathSegment ?: return null
    // Document IDs look like "primary:roms" or "XXXX-XXXX:roms"
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
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()

    var showScanningDialog by remember { mutableStateOf(false) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = treeUriToFilePath(uri)
            if (path != null) {
                viewModel.triggerFullScan(path)
            }
        }
    }

    LaunchedEffect(uiState) {
        showScanningDialog = uiState is MainUiState.Scanning
    }

    if (showScanningDialog) {
        ScanningDialog()
    }

    TaiziTheme {
        Scaffold(
            topBar = {
                Column {
                    StatusBar()
                    when (currentScreen) {
                        is com.taizi.ui.screens.Screen.SystemList -> {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Taizi",
                                        fontSize = 18.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                },
                                actions = {
                                    IconButton(onClick = { viewModel.setScreen(com.taizi.ui.screens.Screen.Settings) }) {
                                         Icon(MaterialIcons.Filled.Settings, contentDescription = "Settings")
                                    }
                                }
                            )
                        }
                        is com.taizi.ui.screens.Screen.GameList -> {
                            val systemId = (currentScreen as com.taizi.ui.screens.Screen.GameList).systemId
                            val system = viewModel.getSystemById(systemId)
                            TopAppBar(
                                title = {
                                    Text(
                                        text = system?.name ?: "Games",
                                        fontSize = 18.sp
                                    )
                                },
                                navigationIcon = {
                                IconButton(onClick = { viewModel.navigateBack() }) {
                                     Icon(MaterialIcons.Filled.ArrowBack, contentDescription = "Back")
                                }
                                }
                            )
                        }
                        else -> {}
                    }
                }
            },
            floatingActionButton = {
                when (currentScreen) {
                    is com.taizi.ui.screens.Screen.SystemList -> {
                        FloatingActionButton(
                            onClick = { viewModel.triggerFullScan() }
                        ) {
                             Icon(MaterialIcons.Filled.Refresh, contentDescription = "Scan Library")
                        }
                    }
                    else -> {}
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    is com.taizi.ui.screens.Screen.SystemList -> {
                        when (uiState) {
                            is MainUiState.Initial,
                            is MainUiState.Scanning -> {
                                // Show initial/scanning state
                                when (uiState) {
                                    is MainUiState.Scanning -> ScanningContent()
                                    else -> InitialSetupContent(
                                        onSelectFolder = { folderPickerLauncher.launch(null) }
                                    )
                                }
                            }
                            is MainUiState.LibraryLoaded -> {
                                val library = (uiState as MainUiState.LibraryLoaded).library
                                SystemListScreen(
                                    systems = library.systems,
                                    onSystemClick = { system ->
                                        viewModel.navigateToSystem(system.id)
                                    },
                                    onScanClick = { viewModel.triggerFullScan() }
                                )
                            }
                            is MainUiState.Error -> {
                                ErrorContent(
                                    message = (uiState as MainUiState.Error).message,
                                    onRetry = { viewModel.triggerFullScan() }
                                )
                            }
                        }
                    }
                    is com.taizi.ui.screens.Screen.GameList -> {
                        val systemId = (currentScreen as com.taizi.ui.screens.Screen.GameList).systemId
                        GameListScreen(
                            systemId = systemId,
                            viewModel = viewModel,
                            onGameClick = { game ->
                                viewModel.launchGame(game)
                            },
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                    is com.taizi.ui.screens.Screen.Settings -> {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateUp = { viewModel.navigateBack() }
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun ScanningDialog() {
    AlertDialog(
        onDismissRequest = { /* Cannot dismiss */ },
        title = { Text("Scanning Library") },
        text = {
            Column {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Please wait while we scan your ROM folders…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
fun InitialSetupContent(
    onSelectFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = MaterialIcons.Filled.SportsEsports,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to Taizi",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A lightweight launcher for RG DS",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectFolder,
            modifier = Modifier.fillMaxWidth()
        ) {
                Icon(
                    imageVector = MaterialIcons.Filled.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select ROM Folder")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Make sure your ROMs are organized in folders like:\n/storage/roms/gb/\n/storage/roms/gba/",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = MaterialIcons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Error",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun ScanningContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(64.dp),
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Scanning…",
            fontSize = 20.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This may take a few minutes depending on library size.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
