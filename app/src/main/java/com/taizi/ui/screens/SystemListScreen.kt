package com.taizi.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.taizi.domain.model.LibraryLayout
import com.taizi.domain.model.System
import com.taizi.ui.components.layoutIcon
import com.taizi.ui.components.FullMotionScale
import com.taizi.ui.components.focusHighlight
import com.taizi.ui.theme.SystemAccent
import com.taizi.ui.theme.accentFor
import com.taizi.ui.theme.imageFor
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val NO_PENDING_PAGE = -1

/** Matches the pager's own snap-settle feel, so d-pad and swipe look alike. */
private val PageSlideSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

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

    val layout by viewModel.systemsLayout.collectAsState()
    var focusedIndex by remember {
        mutableIntStateOf(viewModel.getSystemPagerPage().coerceIn(0, systems.lastIndex))
    }
    // A rescan can shrink the list under a remembered selection.
    val selected = focusedIndex.coerceIn(0, systems.lastIndex)
    val focused = systems[selected]
    // Every layout shares one selection, so switching keeps the same system.
    val onFocusChange: (Int) -> Unit = {
        focusedIndex = it
        viewModel.setSystemPagerPage(it)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AccentGlow(accent = accentFor(focused.id))

        // Size the carousel against the viewport rather than a fixed height, so
        // short screens (a 1080p handheld at native density) keep the metadata
        // row below it on-screen instead of clipping it off the bottom.
        val carouselHeight = (maxHeight - 190.dp).coerceIn(140.dp, 300.dp)

        Column(modifier = Modifier.fillMaxSize()) {
            LibraryHeader(
                totalSystems = systems.size,
                totalGames = systems.sumOf { it.romCount },
                layout = layout,
                onLayoutClick = { viewModel.setSystemsLayout(nextSystemsLayout(layout)) },
                onScanClick = onScanClick,
                onSearchClick = onSearchClick,
                onAppsClick = onAppsClick,
                onSettingsClick = onSettingsClick
            )

            when (layout) {
                LibraryLayout.CAROUSEL -> SystemCarousel(
                    systems = systems,
                    initialPage = selected,
                    carouselHeight = carouselHeight,
                    onFocusChange = onFocusChange,
                    onSystemClick = onSystemClick
                )
                LibraryLayout.GRID, LibraryLayout.LIST -> SystemGrid(
                    systems = systems,
                    selectedIndex = selected,
                    columnCount = if (layout == LibraryLayout.GRID) 4 else 1,
                    onFocusChange = onFocusChange,
                    onSystemClick = onSystemClick
                )
            }
        }
    }
}

private fun nextSystemsLayout(current: LibraryLayout): LibraryLayout = when (current) {
    LibraryLayout.CAROUSEL -> LibraryLayout.GRID
    LibraryLayout.GRID -> LibraryLayout.LIST
    LibraryLayout.LIST -> LibraryLayout.CAROUSEL
}

@Composable
private fun ColumnScope.SystemCarousel(
    systems: List<System>,
    initialPage: Int,
    carouselHeight: Dp,
    onFocusChange: (Int) -> Unit,
    onSystemClick: (System) -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { systems.size }
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .collect { onFocusChange(it) }
    }
    val focused = systems.getOrNull(pagerState.currentPage) ?: systems.first()
    val focusedAccent = accentFor(focused.id)

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // D-pad paging animates the same way a swipe settles. Repeats chain off the
    // pending target instead of re-issuing the current one — otherwise each
    // repeat cancels the in-flight animation and restarts it, which reads as a
    // snap rather than a slide.
    var pendingPage by remember { mutableIntStateOf(NO_PENDING_PAGE) }
    var pageScrollJob by remember { mutableStateOf<Job?>(null) }
    fun stepPage(delta: Int): Boolean {
        val base = if (pendingPage == NO_PENDING_PAGE) pagerState.currentPage else pendingPage
        val target = (base + delta).coerceIn(0, systems.lastIndex)
        if (target == base) return false
        pendingPage = target
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        pageScrollJob?.cancel()
        pageScrollJob = scope.launch(FullMotionScale) {
            try {
                pagerState.animateScrollToPage(target, animationSpec = PageSlideSpec)
            } finally {
                if (pendingPage == target) pendingPage = NO_PENDING_PAGE
            }
        }
        return true
    }

    Spacer(modifier = Modifier.weight(1f))

    HorizontalPager(
        state = pagerState,
        pageSpacing = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(carouselHeight)
            .padding(horizontal = 20.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event: androidx.compose.ui.input.key.KeyEvent ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft, Key.ButtonL1 -> stepPage(-1)
                    Key.DirectionRight, Key.ButtonR1 -> stepPage(1)
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA -> {
                        // Confirm opens the system that's actually on
                        // screen, never the neighbour the pager is
                        // animating toward.
                        systems.getOrNull(pagerState.currentPage)
                            ?.let { onSystemClick(it) }
                        true
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
            scale = scale,
            isFocused = page == pagerState.currentPage
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    PagerDots(
        count = systems.size,
        current = pagerState.currentPage,
        activeColor = focusedAccent.primary,
        pagerState = pagerState
    )

    Spacer(modifier = Modifier.height(16.dp))

    FocusedSystemMeta(system = focused)

    Spacer(modifier = Modifier.height(16.dp))
}

/** Grid and list share this: a list is just a one-column grid of rows. */
@Composable
private fun SystemGrid(
    systems: List<System>,
    selectedIndex: Int,
    columnCount: Int,
    onFocusChange: (Int) -> Unit,
    onSystemClick: (System) -> Unit
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = selectedIndex)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(columnCount) {
        focusRequester.requestFocus()
    }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val isList = columnCount == 1
    val gap = if (isList) 8.dp else 14.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Grid tiles fill a clean 2-row page; list rows keep a fixed height.
        val itemHeight = if (isList) 72.dp
        else ((maxHeight - 16.dp * 2 - gap) / 2).coerceAtLeast(96.dp)

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    val curr = selectedIndex
                    val target = when (event.key) {
                        Key.DirectionDown -> (curr + columnCount).coerceAtMost(systems.lastIndex)
                        // From the top row, let focus move up to the header.
                        Key.DirectionUp ->
                            if (curr < columnCount) return@onKeyEvent false
                            else curr - columnCount
                        Key.DirectionRight ->
                            if (curr % columnCount == columnCount - 1) curr
                            else (curr + 1).coerceAtMost(systems.lastIndex)
                        Key.DirectionLeft ->
                            if (curr % columnCount == 0) curr else curr - 1
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.ButtonA -> {
                            onSystemClick(systems[curr])
                            return@onKeyEvent true
                        }
                        else -> return@onKeyEvent false
                    }
                    if (target != curr) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onFocusChange(target)
                        scope.launch {
                            val layout = gridState.layoutInfo
                            val item = layout.visibleItemsInfo.firstOrNull { it.index == target }
                            val fullyVisible = item != null &&
                                item.offset.y >= layout.viewportStartOffset &&
                                item.offset.y + item.size.height <= layout.viewportEndOffset
                            if (!fullyVisible) gridState.animateScrollToItem(target)
                        }
                    }
                    true
                },
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(16.dp)
        ) {
            itemsIndexed(systems, key = { _, system -> system.id }) { index, system ->
                val accent = accentFor(system.id)
                val isFocused = index == selectedIndex
                if (isList) {
                    SystemRow(
                        system = system,
                        accent = accent,
                        isFocused = isFocused,
                        height = itemHeight,
                        onClick = { onSystemClick(system) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight)
                            .then(
                                if (isFocused) Modifier.border(2.dp, accent.primary, RoundedCornerShape(20.dp))
                                else Modifier
                            )
                    ) {
                        SystemTile(
                            system = system,
                            accent = accent,
                            onClick = { onSystemClick(system) },
                            isFocused = isFocused,
                            compact = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(
    totalSystems: Int,
    totalGames: Int,
    layout: LibraryLayout,
    onLayoutClick: () -> Unit,
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
        CircularIconButton(onClick = onLayoutClick) {
            Icon(
                imageVector = layoutIcon(layout),
                contentDescription = "Change layout",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
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
    scale: Float = 1f,
    isFocused: Boolean = false,
    compact: Boolean = false
) {
    val imageRes = imageFor(system.id)
    val shape = RoundedCornerShape(if (compact) 20.dp else 28.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.linearGradient(
                    // Near-square compact tiles would put the logo over the primary colour;
                    // pull secondary forward so the logo always sits on secondary -> dark.
                    0f to accent.primary,
                    (if (compact) 0.35f else 0.5f) to accent.secondary,
                    1f to Color(0xFF0B0B10)
                )
            )
            .clickable { onClick() }
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.10f))
            )
        }
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

        if (!compact) {
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
        }
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
                .padding(if (compact) 14.dp else 24.dp),
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

            Column(modifier = Modifier.fillMaxWidth(if (compact) 0.7f else 0.55f)) {
                Text(
                    text = system.name,
                    style = (
                        if (compact) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.headlineMedium
                    ).copy(fontWeight = FontWeight.ExtraBold),
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
private fun SystemRow(
    system: System,
    accent: SystemAccent,
    isFocused: Boolean,
    height: Dp,
    onClick: () -> Unit
) {
    val imageRes = imageFor(system.id)
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                if (isFocused) accent.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surface
            )
            .then(if (isFocused) Modifier.border(2.dp, accent.primary, shape) else Modifier)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(accent.secondary, Color(0xFF0B0B10)))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (system.id == MainViewModel.FAVORITES_SYSTEM_ID) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp)
                )
            } else if (imageRes != null) {
                AsyncImage(
                    model = imageRes,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = system.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = system.emulatorType.ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${system.romCount} ${if (system.romCount == 1) "game" else "games"}",
            style = MaterialTheme.typography.titleSmall,
            color = if (isFocused) accent.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
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
