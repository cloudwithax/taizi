package com.taizi.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taizi.domain.model.BiosStatus
import com.taizi.domain.model.System
import com.taizi.ui.theme.SystemAccent
import com.taizi.ui.theme.accentFor
import com.taizi.ui.theme.imageFor

@Composable
fun SystemListScreen(
    systems: List<System>,
    onSystemClick: (System) -> Unit,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (systems.isEmpty()) {
        EmptySystems(modifier = modifier)
        return
    }

    val pagerState = rememberPagerState(pageCount = { systems.size })
    val focused = systems.getOrNull(pagerState.currentPage) ?: systems.first()
    val focusedAccent = accentFor(focused.id)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AccentGlow(accent = focusedAccent)

        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(
                totalSystems = systems.size,
                totalGames = systems.sumOf { it.romCount },
                onScanClick = onScanClick,
                onSettingsClick = onSettingsClick
            )

            Spacer(modifier = Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                pageSpacing = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 20.dp)
            ) { page ->
                SystemTile(
                    system = systems[page],
                    accent = accentFor(systems[page].id),
                    onClick = { onSystemClick(systems[page]) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            PagerDots(
                count = systems.size,
                current = pagerState.currentPage,
                activeColor = focusedAccent.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            FocusedSystemMeta(system = focused)

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun LibraryHeader(
    totalSystems: Int,
    totalGames: Int,
    onScanClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "LIBRARY",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 4.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$totalSystems systems · $totalGames games",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        CircularIconButton(onClick = onScanClick) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Rescan library",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        CircularIconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CircularIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@Composable
private fun AccentGlow(accent: SystemAccent) {
    val animated by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "glow"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.55f * animated }
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.secondary, Color.Transparent),
                    radius = 1100f
                )
            )
    )
}

@Composable
private fun SystemTile(
    system: System,
    accent: SystemAccent,
    onClick: () -> Unit
) {
    val imageRes = imageFor(system.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.primary,
                        accent.secondary,
                        Color(0xFF0B0B10)
                    )
                )
            )
            .clickable { onClick() }
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(id = imageRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 20.dp, bottom = 20.dp)
                    .graphicsLayer { alpha = 0.95f }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xEE0B0B10), Color.Transparent),
                        endX = 700f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xAA0B0B10)),
                        startY = 220f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = accent.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White
                    )
                }
                if (system.biosStatus == BiosStatus.MISSING) {
                    BiosChip()
                }
            }

            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                Text(
                    text = system.name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${system.romCount} ${if (system.romCount == 1) "game" else "games"}",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun BiosChip() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFFFC857),
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "BIOS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color(0xFFFFC857)
            )
        }
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, activeColor: Color) {
    val displayCount = count.coerceAtMost(9)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(displayCount) { i ->
            val isActive = i == (current.coerceAtMost(displayCount - 1))
            val width by animateFloatAsState(
                targetValue = if (isActive) 18f else 6f,
                animationSpec = tween(220),
                label = "dot"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(width.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) activeColor
                        else MaterialTheme.colorScheme.outline
                    )
            )
        }
        if (count > displayCount) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${current + 1}/$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusedSystemMeta(system: System) {
    val accent = accentFor(system.id)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetaPill(
            label = "Emulator",
            value = system.emulatorType.ifBlank { "—" },
            accent = accent.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        MetaPill(
            label = "Games",
            value = system.romCount.toString(),
            accent = accent.primary
        )
        if (system.biosStatus == BiosStatus.MISSING) {
            Spacer(modifier = Modifier.width(12.dp))
            MetaPill(
                label = "Status",
                value = "BIOS needed",
                accent = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun MetaPill(label: String, value: String, accent: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = accent
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptySystems(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No systems found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add ROMs to your library and rescan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
