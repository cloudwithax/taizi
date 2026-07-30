package com.taizi.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.taizi.data.scraper.ScrapeStatus
import com.taizi.domain.model.Game
import com.taizi.ui.components.BackHoldGate
import com.taizi.ui.components.FullMotionScale
import com.taizi.ui.theme.accentFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** How long Back must be held before the screen lets go. */
private const val HOLD_TO_EXIT_MS = 500

/**
 * Keys swallowed while the guard is up so d-pad / face buttons can't drive the
 * library underneath. Volume, power and everything else pass through.
 */
private val SWALLOWED_KEYS = setOf(
    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight,
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.Menu,
    Key.ButtonA, Key.ButtonB, Key.ButtonX, Key.ButtonY,
    Key.ButtonL1, Key.ButtonR1, Key.ButtonL2, Key.ButtonR2,
    Key.ButtonStart, Key.ButtonSelect, Key.ButtonThumbLeft, Key.ButtonThumbRight
)

/**
 * Full-screen guard shown after a game is handed to its emulator. On dual-screen
 * handhelds the launcher stays visible on the second panel, where a stray palm
 * or thumb would otherwise fire off a tap; nothing here reacts to a plain touch.
 */
@Composable
fun NowPlayingScreen(
    game: Game,
    systemId: String,
    systemName: String,
    emulatorLabel: String,
    startedAt: Long,
    scrapeStatus: ScrapeStatus,
    onExit: () -> Unit
) {
    val accent = accentFor(systemId)

    // The guard is the second screen's whole job while a game runs, so hold the
    // display awake for as long as it's up.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    DisposableEffect(Unit) {
        BackHoldGate.arm()
        onDispose { BackHoldGate.disarm() }
    }

    val backHeld by BackHoldGate.pressed.collectAsState()
    var touchHeld by remember { mutableStateOf(false) }
    val held = backHeld || touchHeld

    // Run the hold timer at full duration even where the system animator scale
    // is 0 — otherwise it elapses instantly and a tap would leave the screen.
    val holdProgress = remember { Animatable(0f) }
    LaunchedEffect(held) {
        withContext(FullMotionScale) {
            if (held) {
                val remaining = ((1f - holdProgress.value) * HOLD_TO_EXIT_MS).toInt()
                holdProgress.animateTo(1f, tween(remaining.coerceAtLeast(1), easing = LinearEasing))
                if (backHeld) BackHoldGate.drainUntilRelease()
                onExit()
            } else {
                holdProgress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            }
        }
    }

    var elapsedMs by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsedMs = (java.lang.System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            delay(1000)
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { focusRequester.requestFocus() } catch (_: Throwable) { }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event -> event.key in SWALLOWED_KEYS }
            // Absorbs every tap that isn't on the hold target above it.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        if (game.boxArtPath != null) {
            AsyncImage(
                model = game.boxArtPath,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.16f; scaleX = 1.2f; scaleY = 1.2f },
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.secondary.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PulsingLabel(accent = accent.primary)

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, accent.primary.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (game.boxArtPath != null) {
                    AsyncImage(
                        model = game.boxArtPath,
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.Low
                    )
                } else {
                    PlaceholderArt(title = game.displayName, accent = accent.primary)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = game.displayName,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = listOf(systemName, emulatorLabel)
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = formatElapsed(elapsedMs),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = accent.primary
            )

            if (scrapeStatus.isRunning) {
                Spacer(modifier = Modifier.height(24.dp))
                ScrapeProgressCard(status = scrapeStatus, accent = accent.primary)
            }

            Spacer(modifier = Modifier.height(32.dp))

            HoldToExit(
                progress = holdProgress.value,
                held = held,
                accent = accent.primary,
                onHoldChanged = { touchHeld = it }
            )
        }
    }
}

@Composable
private fun PulsingLabel(accent: Color) {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            on = !on
            delay(900)
        }
    }
    val dotAlpha by animateFloatAsState(
        targetValue = if (on) 1f else 0.25f,
        animationSpec = tween(durationMillis = 900, easing = LinearEasing),
        label = "nowPlayingDot"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = dotAlpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            ),
            color = accent
        )
    }
}

@Composable
private fun ScrapeProgressCard(status: ScrapeStatus, accent: Color) {
    val fraction = if (status.total > 0) {
        (status.current.toFloat() / status.total).coerceIn(0f, 1f)
    } else 0f
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "scrapeFraction"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scraping box art",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (status.total > 0) {
                    Text(
                        text = "${status.current} / ${status.total}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        if (status.total > 0) {
                            drawRect(
                                color = accent,
                                size = Size(size.width * animatedFraction, size.height)
                            )
                        }
                    }
            ) {
                if (status.total == 0) IndeterminateSweep(accent = accent)
            }

            if (status.gameName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = listOf(status.systemName, status.gameName)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Travelling bar for the stretch before ScreenScraper reports a total. */
@Composable
private fun IndeterminateSweep(accent: Color) {
    var offset by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            offset = (offset + 0.02f) % 1.3f
            delay(16)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val barWidth = size.width * 0.3f
                drawRect(
                    color = accent,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = offset * size.width - barWidth,
                        y = 0f
                    ),
                    size = Size(barWidth, size.height)
                )
            }
    )
}

@Composable
private fun HoldToExit(
    progress: Float,
    held: Boolean,
    accent: Color,
    onHoldChanged: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(
                width = if (held) 2.dp else 1.dp,
                color = if (held) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(28.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldChanged(true)
                        tryAwaitRelease()
                        onHoldChanged(false)
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (progress > 0f) {
                        drawRect(
                            color = accent.copy(alpha = 0.35f),
                            size = Size(size.width * progress, size.height)
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (held) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (held) "Keep holding…" else "Hold Back to leave",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
