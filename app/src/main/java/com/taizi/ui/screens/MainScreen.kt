package com.taizi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taizi.R
import com.taizi.ui.components.StatusBar
import com.taizi.ui.theme.BrandAccent
import com.taizi.ui.theme.TaiziTheme

@Composable
fun MainScreen(viewModel: MainViewModel, onSelectFolder: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()

    BackHandler {
        if (currentScreen !is Screen.SystemList) viewModel.navigateBack()
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
                            MainUiState.Loading -> Unit
                            MainUiState.Initial -> InitialSetupContent(
                                onSelectFolder = onSelectFolder
                            )
                            MainUiState.Scanning -> ScanningContent(scanProgress)
                            is MainUiState.LibraryLoaded -> SystemListScreen(
                                systems = viewModel.systemsForDisplay(state.library),
                                onSystemClick = { viewModel.navigateToSystem(it.id) },
                                onScanClick = { viewModel.triggerFullScan() },
                                onSelectFolder = onSelectFolder,
                                onSettingsClick = { viewModel.setScreen(Screen.Settings) },
                                onSearchClick = { viewModel.setScreen(Screen.Search) },
                                onAppsClick = { viewModel.setScreen(Screen.AppDrawer) },
                                viewModel = viewModel
                            )
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
                        onNavigateUp = viewModel::navigateBack,
                        onSelectFolder = onSelectFolder
                    )
                    is Screen.Search -> SearchScreen(
                        viewModel = viewModel,
                        onGameClick = { game ->
                            viewModel.launchGame(game)
                        },
                        onBack = viewModel::navigateBack
                    )
                    is Screen.AppDrawer -> AppDrawerScreen(
                        onBack = viewModel::navigateBack,
                        viewModel = viewModel
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
                textAlign = TextAlign.Center
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
        Image(
            painter = painterResource(id = R.drawable.icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
        )
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
    val angle = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var startNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (startNanos == 0L) startNanos = now
                val elapsedMs = (now - startNanos) / 1_000_000f
                angle.floatValue = (elapsedMs / 1400f * 360f) % 360f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Canvas(
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = angle.floatValue }
            ) {
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2f
                drawArc(
                    color = BrandAccent,
                    startAngle = 0f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke.width, size.height - stroke.width),
                    style = stroke
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Scanning library",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when {
                    progress.total > 0 -> "${progress.count} / ${progress.total} ROMs"
                    progress.count > 0 -> "${progress.count} ROMs"
                    else -> "Indexing ROMs · this may take a moment"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (progress.gameName.isNotBlank()) {
                CurrentRomLine(
                    systemName = progress.systemName,
                    gameName = progress.gameName
                )
            }
        }
    }
}

@Composable
private fun CurrentRomLine(systemName: String, gameName: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            if (systemName.isNotBlank()) {
                Text(
                    text = systemName.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            Text(
                text = gameName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
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
