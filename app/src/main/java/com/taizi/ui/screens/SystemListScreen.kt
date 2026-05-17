package com.taizi.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.view.HapticFeedbackConstants
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.taizi.domain.model.System
import com.taizi.ui.components.focusHighlight
import com.taizi.ui.theme.SystemAccent
import com.taizi.ui.theme.accentFor
import com.taizi.ui.theme.imageFor
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SystemListScreen(
    systems: List<System>,
    onSystemClick: (System) -> Unit,
    onScanClick: () -> Unit,
    onSelectFolder: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onAppsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainViewModel
) {
    if (systems.isEmpty()) {
        EmptySystems(
            onSelectFolder = onSelectFolder,
            onScanClick = onScanClick,
            modifier = modifier
        )
        return
    }

    val pagerState = rememberPagerState(
        initialPage = viewModel.getSystemPagerPage().coerceIn(0, systems.size - 1),
        pageCount = { systems.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { viewModel.setSystemPagerPage(it) }
    }
    val focused = systems.getOrNull(pagerState.currentPage) ?: systems.first()
    val focusedAccent = accentFor(focused.id)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

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
                onSearchClick = onSearchClick,
                onAppsClick = onAppsClick,
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
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (event.key) {
                            Key.DirectionLeft -> {
                                val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                                if (target != pagerState.currentPage) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                    true
                                } else false
                            }
                            Key.DirectionRight -> {
                                val target = (pagerState.currentPage + 1).coerceAtMost(systems.size - 1)
                                if (target != pagerState.currentPage) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                    true
                                } else false
                            }
                            else -> false
                        }
                    }
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                val scale = 1f - (pageOffset * 0.1f).coerceAtMost(0.1f)
                SystemTile(
                    system = systems[page],
                    accent = accentFor(systems[page].id),
                    onClick = { onSystemClick(systems[page]) },
                    scale = scale
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            PagerDots(
                count = systems.size,
                current = pagerState.currentPage,
                activeColor = focusedAccent.primary,
                pagerState = pagerState
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
    onSearchClick: () -> Unit,
    onAppsClick: () -> Unit,
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
        CircularIconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        CircularIconButton(onClick = onAppsClick) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = "Apps",
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
        modifier = Modifier
            .size(40.dp)
            .focusHighlight(shape = CircleShape)
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
    onClick: () -> Unit,
    scale: Float = 1f
) {
    val imageRes = imageFor(system.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
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
        if (system.id == MainViewModel.FAVORITES_SYSTEM_ID) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = accent.primary,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp, top = 20.dp, bottom = 20.dp)
                    .graphicsLayer { alpha = 0.95f }
            )
        } else if (imageRes != null) {
            AsyncImage(
                model = imageRes,
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
private fun PagerDots(
    count: Int,
    current: Int,
    activeColor: Color,
    pagerState: PagerState
) {
    if (count <= 0) return
    val dotSize = 6.dp
    val activeWidth = 18.dp
    val gap = 4.dp
    val step = dotSize + gap
    val windowSize = minOf(count, 20)
    val anchor = (windowSize - 1) / 2
    val maxStart = (count - windowSize).coerceAtLeast(0)

    var scrubbing by remember { mutableStateOf(false) }
    var scrubFrac by remember { mutableFloatStateOf(0f) }

    var smoothFrac by remember {
        mutableFloatStateOf(pagerState.currentPage + pagerState.currentPageOffsetFraction)
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val target = pagerState.currentPage + pagerState.currentPageOffsetFraction
                smoothFrac += (target - smoothFrac) * 0.25f
            }
        }
    }
    val fracCurrent = if (scrubbing) scrubFrac else smoothFrac
    val fracWindowStart = (fracCurrent - anchor).coerceIn(0f, maxStart.toFloat())
    val targetOffset = -(step * fracWindowStart)
    val offset by animateDpAsState(
        targetValue = targetOffset,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "windowShift"
    )

    val windowWidth = step * windowSize + (activeWidth - dotSize)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val view = LocalView.current
    val scrubPxPerPage = with(density) { 28.dp.toPx() }

    val scrubScale by animateFloatAsState(
        targetValue = if (scrubbing) 1.8f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scrubScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(count) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    val originPage = pagerState.currentPage
                    val startX = longPress.position.x
                    scrubFrac = originPage.toFloat()
                    scrubbing = true
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    var lastPage = originPage

                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            change.consume()

                            val dx = change.position.x - startX
                            val cont = (originPage + dx / scrubPxPerPage)
                                .coerceIn(0f, (count - 1).toFloat())
                            scrubFrac = cont
                            val target = cont.roundToInt().coerceIn(0, count - 1)
                            if (target != lastPage) {
                                lastPage = target
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                scope.launch { pagerState.scrollToPage(target) }
                            }
                        }
                    } finally {
                        scrubbing = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(windowWidth)
                .graphicsLayer {
                    scaleX = scrubScale
                    scaleY = scrubScale
                }
                .clipToBounds()
        ) {
            Row(
                modifier = Modifier
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .offset(x = offset),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(count) { i ->
                    val distFromActive = kotlin.math.abs(i - fracCurrent)
                    val activeness = (1f - distFromActive).coerceIn(0f, 1f)
                    val width = dotSize.value + (activeWidth.value - dotSize.value) * activeness
                    val isActive = activeness > 0f
                    Box(
                        modifier = Modifier
                            .height(dotSize)
                            .width(width.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) lerp(
                                    MaterialTheme.colorScheme.outline,
                                    activeColor,
                                    activeness
                                )
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }
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
private fun EmptySystems(
    onSelectFolder: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            text = "Add ROMs to your library and rescan, or choose a different folder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSelectFolder) {
            Text(text = "Choose ROM folder")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onScanClick) {
            Text(text = "Rescan")
        }
    }
}
