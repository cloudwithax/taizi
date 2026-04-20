package com.taizi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.taizi.ui.theme.accentFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    systemId: String,
    viewModel: MainViewModel = hiltViewModel(),
    onGameClick: (Game) -> Unit,
    onBack: () -> Unit
) {
    val system = viewModel.getSystemById(systemId)
    val games = viewModel.getGamesForSystem(systemId)
    val accent = accentFor(systemId)

    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val filteredGames = remember(games, searchQuery, showFavoritesOnly) {
        games.filter { game ->
            val matchesSearch = searchQuery.isBlank() ||
                    game.name.contains(searchQuery, ignoreCase = true)
            val matchesFavorite = if (showFavoritesOnly) game.favorite else true
            matchesSearch && matchesFavorite
        }
    }

    val gridState = rememberLazyGridState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SystemBanner(
            systemName = system?.name ?: "Games",
            systemPath = system?.path ?: "",
            visibleCount = filteredGames.size,
            totalCount = games.size,
            accentPrimary = accent.primary,
            accentSecondary = accent.secondary,
            showFavoritesOnly = showFavoritesOnly,
            searchOpen = searchOpen,
            onBack = onBack,
            onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) searchQuery = "" },
            onToggleFavorites = { showFavoritesOnly = !showFavoritesOnly }
        )

        if (searchOpen) {
            Spacer(modifier = Modifier.height(12.dp))
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                accent = accent.primary
            )
        }

        if (filteredGames.isEmpty()) {
            EmptyGames(hasQuery = searchQuery.isNotBlank() || showFavoritesOnly)
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 128.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            items(filteredGames, key = { it.path }) { game ->
                GameCard(
                    game = game,
                    accent = accent.primary,
                    onClick = { onGameClick(game) },
                    onFavoriteClick = { viewModel.toggleFavorite(game.path, !game.favorite) }
                )
            }
        }
    }
}

@Composable
private fun SystemBanner(
    systemName: String,
    systemPath: String,
    visibleCount: Int,
    totalCount: Int,
    accentPrimary: Color,
    accentSecondary: Color,
    showFavoritesOnly: Boolean,
    searchOpen: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
    onToggleFavorites: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(accentSecondary, MaterialTheme.colorScheme.background)
                )
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = systemName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (visibleCount == totalCount) "$totalCount games"
                        else "$visibleCount of $totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RoundIconButton(
                    onClick = onToggleFavorites,
                    highlightColor = if (showFavoritesOnly) accentPrimary else null
                ) {
                    Icon(
                        imageVector = if (showFavoritesOnly) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = if (showFavoritesOnly) Color.White
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                RoundIconButton(
                    onClick = onToggleSearch,
                    highlightColor = if (searchOpen) accentPrimary else null
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = if (searchOpen) Color.White
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (systemPath.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = systemPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    onClick: () -> Unit,
    highlightColor: Color? = null,
    content: @Composable () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = highlightColor ?: MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(40.dp)
    ) {
        IconButton(onClick = onClick) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    accent: Color
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text("Search games") },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = accent
        )
    )
}

@Composable
private fun GameCard(
    game: Game,
    accent: Color,
    onClick: () -> Unit,
    onFavoriteClick: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(game.boxArtPath)
            .crossfade(true)
            .build()
    )
    val hasArt = game.boxArtPath != null &&
            painter.intrinsicSize.width > 0 &&
            painter.intrinsicSize.height > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (hasArt) {
                androidx.compose.foundation.Image(
                    painter = painter,
                    contentDescription = game.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.75f)
                                ),
                                startY = 200f
                            )
                        )
                )
            } else {
                PlaceholderArt(title = game.displayName, accent = accent)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (game.isMultiDisc) {
                    Chip(text = "${game.discs.size} DISCS", background = accent)
                } else {
                    Spacer(modifier = Modifier.size(0.dp))
                }
                IconButton(
                    onClick = { onFavoriteClick(!game.favorite) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (game.favorite) Icons.Filled.Favorite
                        else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (game.favorite) accent else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = game.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (game.playCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Played ${game.playCount}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, background: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = background
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.White
        )
    }
}

@Composable
private fun PlaceholderArt(title: String, accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(accent.copy(alpha = 0.55f), Color(0xFF0B0B10))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.take(1).uppercase(),
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun EmptyGames(hasQuery: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasQuery) "No matches" else "No games yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (hasQuery) "Try a different filter."
            else "Drop ROMs into this system's folder and rescan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
