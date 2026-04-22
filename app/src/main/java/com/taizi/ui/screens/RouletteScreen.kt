package com.taizi.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.taizi.domain.model.Game
import com.taizi.ui.theme.accentFor
import kotlin.math.abs

@Composable
fun RouletteScreen(
    systemId: String,
    winner: Game,
    viewModel: MainViewModel = hiltViewModel(),
    onComplete: (Game) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val games = remember(uiState, systemId) { viewModel.getGamesForSystem(systemId) }
    val accent = accentFor(systemId).primary

    val density = LocalDensity.current
    val itemWidthPx = with(density) { 140.dp.toPx() }
    val containerWidthPx = with(density) { 300.dp.toPx() }

    val (sequence, winnerIndex) = remember(games, winner) {
        val pool = games.toMutableList()
        pool.remove(winner)
        if (pool.isEmpty()) pool.addAll(games.filter { it != winner })
        val seq = buildList {
            repeat(30) {
                add(pool.random().also { pool.remove(it) })
                if (pool.isEmpty()) pool.addAll(games.filter { it != winner })
            }
            add(winner)
            repeat(5) {
                add(pool.random().also { pool.remove(it) })
                if (pool.isEmpty()) pool.addAll(games.filter { it != winner })
            }
        }
        seq to seq.indexOf(winner)
    }
    val targetOffset = containerWidthPx / 2f - (winnerIndex * itemWidthPx + itemWidthPx / 2f)

    var started by remember { mutableStateOf(false) }
    val offsetX by animateFloatAsState(
        targetValue = if (started) targetOffset else 0f,
        animationSpec = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
        label = "roulette"
    )

    LaunchedEffect(Unit) {
        started = true
    }

    var animationDone by remember { mutableStateOf(false) }
    LaunchedEffect(offsetX) {
        if (started && abs(offsetX - targetOffset) < 1f && !animationDone) {
            animationDone = true
            kotlinx.coroutines.delay(800)
            onComplete(winner)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B0B10), Color(0xFF1A1A2E))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (animationDone) "WINNER!" else "SPINNING...",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp
                ),
                color = accent
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .width(300.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF12121A))
                    .drawBehind {
                        drawRoundRect(
                            color = accent.copy(alpha = 0.6f),
                            size = size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(color = accent.copy(alpha = 0.5f))
                        .zIndex(2f)
                )

                Row(
                    modifier = Modifier
                        .graphicsLayer { translationX = offsetX }
                ) {
                    sequence.forEachIndexed { index, game ->
                        val itemCenter = offsetX + index * itemWidthPx + itemWidthPx / 2f
                        val containerCenter = containerWidthPx / 2f
                        val distance = abs(itemCenter - containerCenter)
                        val maxDist = containerWidthPx / 1.5f
                        val normalized = (distance / maxDist).coerceIn(0f, 1f)

                        val scale = 1f - (normalized * 0.3f)
                        val alpha = 1f - (normalized * 0.8f)

                        Box(
                            modifier = Modifier
                                .width(140.dp)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.alpha = alpha
                                }
                                .padding(6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            RouletteGameArt(
                                game = game,
                                accent = accent,
                                isWinner = index == winnerIndex && animationDone
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = game.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (index == winnerIndex && animationDone) accent else Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.TopCenter)) {
                    PointerTriangle(accent = accent)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (animationDone) "Launching..." else "Good luck!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun RouletteGameArt(game: Game, accent: Color, isWinner: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        PlaceholderArt(title = game.displayName, accent = accent)
        if (game.boxArtPath != null) {
            AsyncImage(
                model = game.boxArtPath,
                contentDescription = game.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low
            )
        }
        if (isWinner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(accent.copy(alpha = 0.15f))
            )
        }
    }
}

@Composable
private fun PointerTriangle(accent: Color) {
    val path = remember { Path() }
    Box(
        modifier = Modifier
            .size(width = 24.dp, height = 16.dp)
            .drawBehind {
                path.reset()
                path.moveTo(size.width / 2f, size.height)
                path.lineTo(0f, 0f)
                path.lineTo(size.width, 0f)
                path.close()
                drawPath(path, accent)
            }
    )
}