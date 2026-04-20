package com.taizi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons as MaterialIcons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.taizi.domain.model.Game
import com.taizi.ui.theme.DarkColorPalette
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    systemId: String,
    viewModel: MainViewModel = hiltViewModel(),
    onGameClick: (Game) -> Unit,
    onBack: () -> Unit
) {
    val system = viewModel.getSystemById(systemId)
    val games = viewModel.getGamesForSystem(systemId)
    var searchQuery by remember { mutableStateOf("") }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val filteredGames = remember(games, searchQuery, showFavoritesOnly) {
        games.filter { game ->
            val matchesSearch = game.name.contains(searchQuery, ignoreCase = true)
            val matchesFavorite = if (showFavoritesOnly) game.favorite else true
            matchesSearch && matchesFavorite
        }
    }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = system?.name ?: "Games",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MaterialIcons.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Search
                IconButton(onClick = {
                    // TODO: Implement search dialog
                }) {
                    Icon(MaterialIcons.Filled.Search, contentDescription = "Search")
                }

                // Filter favorites
                IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                    Icon(
                        imageVector = if (showFavoritesOnly) MaterialIcons.Filled.Favorite else MaterialIcons.Outlined.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = if (showFavoritesOnly) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }

                // More options
                IconButton(onClick = {
                    // TODO: Show bottom sheet with sort options
                }) {
                    Icon(MaterialIcons.Filled.MoreVert, contentDescription = "More")
                }
            }
        )

        // Search Bar (if enabled)
        if (searchQuery.isNotEmpty()) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { /* Handle search */ },
                active = true,
                onActiveChange = { active ->
                    if (!active) searchQuery = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Suggestions
            }
        }

        // Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${filteredGames.size} games",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialIcons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = system?.path ?: "",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Games Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(filteredGames) { game ->
                GameCard(
                    game = game,
                    onClick = { onGameClick(game) },
                    onFavoriteClick = { viewModel.toggleFavorite(game.path, !game.favorite) }
                )
            }
        }
    }
}

@Composable
fun GameCard(
    game: Game,
    onClick: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var imageLoading by remember { mutableStateOf(true) }
    var imageError by remember { mutableStateOf(false) }

    // Try to load box art
    val boxArtPath = game.boxArtPath
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(boxArtPath ?: game.path) // Fallback to trying to load ROM as image
            .crossfade(true)
            .build()
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = DarkColorPalette.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Image area (2/3 height)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .clip(MaterialTheme.shapes.small)
                    .background(Color(0xFF2A2A2A)),
                contentAlignment = Alignment.Center
            ) {
                if (painter.intrinsicSize.width > 0 && painter.intrinsicSize.height > 0) {
                    androidx.compose.foundation.Image(
                        painter = painter,
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (imageLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = MaterialIcons.Filled.SportsEsports,
                        contentDescription = game.name,
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF6A6A6A)
                    )
                }

                // Multi-disc indicator
                if (game.isMultiDisc) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${game.discs.size}",
                            fontSize = 8.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Favorite indicator
                if (game.favorite) {
                    Icon(
                        imageVector =                         MaterialIcons.Filled.Favorite,
                        contentDescription = "Favorite",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp)
                            .size(16.dp),
                        tint = Color.Red
                    )
                }
            }

            // Title area (1/3 height)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = game.displayName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                // Play count & last played
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Played: ${game.playCount}",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Favorite button
                    Icon(
                        imageVector = if (game.favorite) MaterialIcons.Filled.Favorite else MaterialIcons.Outlined.FavoriteBorder,
                        contentDescription = "Toggle favorite",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onFavoriteClick(!game.favorite) },
                        tint = if (game.favorite) Color.Red else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    // Simplified search bar - could be expanded
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search games…") },
        leadingIcon = { Icon(MaterialIcons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                        Icon(MaterialIcons.Filled.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}
