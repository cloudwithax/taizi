package com.taizi.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.taizi.ui.components.StatusBar
import com.taizi.ui.theme.BrandAccent
import com.taizi.ui.theme.TaiziTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

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

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val context = LocalContext.current

    var showNetworkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val blocked = withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 3000)
                }
                false
            } catch (_: Exception) {
                true
            }
        }
        if (blocked) showNetworkDialog = true
    }

    if (showNetworkDialog) {
        AlertDialog(
            onDismissRequest = { showNetworkDialog = false },
            icon = {
                Icon(
                    Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Network Access Blocked") },
            text = {
                Text(
                    "Taizi needs network access to download box art. " +
                    "Open App Info and enable \"Allow network access\" or " +
                    "\"Unrestricted data usage\" under Data Usage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNetworkDialog = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                }) { Text("Open App Info") }
            },
            dismissButton = {
                TextButton(onClick = { showNetworkDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUriToFilePath(it) }?.let(viewModel::triggerFullScan)
    }

    val isHome = currentScreen is Screen.SystemList
    androidx.activity.compose.BackHandler(enabled = !isHome) {
        viewModel.navigateBack()
    }

    TaiziTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Box(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                    StatusBar()
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentScreen) {
                    is Screen.SystemList -> {
                        when (val state = uiState) {
                            MainUiState.Loading -> Box(Modifier.fillMaxSize())
                            MainUiState.Initial -> InitialSetupContent(
                                onSelectFolder = { folderPickerLauncher.launch(null) }
                            )
                            MainUiState.Scanning -> {
                                val progress by viewModel.scanProgress.collectAsState()
                                ScanningContent(progress = progress)
                            }
                            is MainUiState.LibraryLoaded -> {
                                SystemListScreen(
                                    systems = state.library.systems.filter { it.romCount > 0 },
                                    onSystemClick = { viewModel.navigateToSystem(it.id) },
                                    onScanClick = { viewModel.triggerFullScan() }
                                )
                            }
                            is MainUiState.Error -> ErrorContent(
                                message = state.message,
                                onRetry = { viewModel.triggerFullScan() }
                            )
                        }
                    }
                    is Screen.GameList -> {
                        val systemId = (currentScreen as Screen.GameList).systemId
                        GameListScreen(
                            systemId = systemId,
                            viewModel = viewModel,
                            onGameClick = viewModel::launchGame,
                            onBack = viewModel::navigateBack
                        )
                    }
                    is Screen.Settings -> SettingsScreen(
                        viewModel = viewModel,
                        onNavigateUp = viewModel::navigateBack
                    )
                    is Screen.AppDrawer -> AppDrawerScreen(
                        onBack = viewModel::navigateBack
                    )
                    is Screen.Search -> SearchScreen(
                        viewModel = viewModel,
                        onGameClick = viewModel::launchGame,
                        onBack = viewModel::navigateBack
                    )
                    else -> Unit
                }
            }
        }
    }
}

@Composable
fun InitialSetupContent(onSelectFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B0A17),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Wordmark()

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "a launcher for your retro handheld",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onSelectFolder,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Choose ROM folder",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            HintCard(
                lines = listOf(
                    "Organize ROMs by system in subfolders:",
                    "roms/snes/  roms/gba/  roms/psx/"
                )
            )
        }
    }
}

@Composable
private fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.size(56.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(BrandAccent, Color(0xFF7A1236))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(34.dp)
                        .padding(start = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "taizi",
            fontSize = 44.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun HintCard(lines: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            lines.forEachIndexed { index, line ->
                Text(
                    text = line,
                    style = if (index == 0) MaterialTheme.typography.labelMedium
                    else MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = if (index == 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                if (index < lines.size - 1) Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun ScanningContent(progress: ScanProgress = ScanProgress()) {
    val angleState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            angleState.floatValue = (angleState.floatValue + 6f) % 360f
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer { rotationZ = angleState.floatValue }
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            colors = listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary,
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Scanning library",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Indexing ROMs · this may take a moment",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (progress.gameName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Indexed ${progress.gameName} · ${progress.systemName} · ${progress.count}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            modifier = Modifier.size(72.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) { Text("Retry") }
    }
}
