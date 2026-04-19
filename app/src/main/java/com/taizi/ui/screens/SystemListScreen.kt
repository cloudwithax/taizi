package com.taizi.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.taizi.domain.model.BiosStatus
import com.taizi.domain.model.System
import com.taizi.ui.theme.DarkColorPalette
import kotlinx.coroutines.delay
@Composable
fun SystemListScreen(
    systems: List<System>,
    onSystemClick: (System) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val columns = when {
        systems.size <= 4 -> 2
        systems.size <= 8 -> 3
        else -> 4
    }

    Column(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count = systems.size) { index ->
                SystemCard(
                    system = systems[index],
                    onClick = { onSystemClick(systems[index]) }
                )
            }
        }

        // Floating Scan Button
        FloatingActionButton(
            onClick = onScanClick,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.End)
        ) {
            Icon(MaterialIcons.Filled.Refresh, contentDescription = "Scan Library")
        }
    }
}

@Composable
fun SystemCard(
    system: System,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = DarkColorPalette.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // System Icon (placeholder - would use actual box art in future)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFF3A3A3A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MaterialIcons.Filled.SportsEsports,
                    contentDescription = system.name,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF6A6A6A)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = system.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${system.romCount} games",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // BIOS status indicator
            if (system.biosStatus == com.taizi.domain.model.BiosStatus.MISSING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector =                         MaterialIcons.Filled.Warning,
                    contentDescription = "BIOS Missing",
                    tint = Color.Red,
                    modifier = Modifier.size(12.dp)
                )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "BIOS",
                        fontSize = 8.sp,
                        color = Color.Red
                    )
                }
            }
        }
    }
}
