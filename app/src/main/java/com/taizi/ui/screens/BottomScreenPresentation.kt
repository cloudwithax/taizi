package com.taizi.ui.screens

import android.app.Presentation
import android.content.Context
import android.view.Display
import android.view.WindowManager
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.taizi.ui.theme.BrandAccent
import com.taizi.ui.theme.TaiziTheme
import kotlinx.coroutines.flow.StateFlow

data class BottomUiData(
    val systemsCount: Int = 0,
    val totalGames: Int = 0,
    val favoritesCount: Int = 0,
    val isScanning: Boolean = false,
    val isLibraryLoaded: Boolean = false
)

class LauncherPresentation(
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onSelectFolder: () -> Unit,
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            (lifecycleOwner as? ViewModelStoreOwner)?.let { setViewTreeViewModelStoreOwner(it) }
            (lifecycleOwner as? SavedStateRegistryOwner)?.let { setViewTreeSavedStateRegistryOwner(it) }
            (lifecycleOwner as? OnBackPressedDispatcherOwner)?.let { setViewTreeOnBackPressedDispatcherOwner(it) }

            setContent {
                TaiziTheme {
                    LauncherContent(
                        viewModel = viewModel,
                        onSelectFolder = onSelectFolder
                    )
                }
            }
        }

        setContentView(composeView)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun LauncherContent(
    viewModel: MainViewModel,
    onSelectFolder: () -> Unit
) {
    MainScreen(
        viewModel = viewModel,
        onSelectFolder = onSelectFolder
    )
}

@Composable
fun Dashboard(bottomState: StateFlow<BottomUiData>) {
    val data by bottomState.collectAsState()

    if (!data.isLibraryLoaded) {
        EmptyDashboard()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14141C),
                        Color(0xFF0B0B10)
                    )
                )
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(BrandAccent)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TAIZI",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 4.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val segments = listOf(
                data.systemsCount to BrandAccent,
                data.totalGames to Color(0xFFFFC857),
                data.favoritesCount to Color(0xFF5B4BFF)
            ).filter { it.first > 0 }.ifEmpty {
                listOf(1 to MaterialTheme.colorScheme.surfaceVariant)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            ) {
                val totalWeight = segments.sumOf { it.first.toDouble() }
                segments.forEach { (count, color) ->
                    if (totalWeight > 0) {
                        Box(
                            modifier = Modifier
                                .weight(count.toFloat())
                                .fillMaxSize()
                                .background(color)
                        )
                    }
                }
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatRow(
                icon = Icons.Filled.Gamepad,
                label = "Systems",
                value = data.systemsCount.toString(),
                accent = BrandAccent
            )
            StatRow(
                icon = Icons.Filled.VideogameAsset,
                label = "Games",
                value = data.totalGames.toString(),
                accent = Color(0xFFFFC857)
            )
            StatRow(
                icon = Icons.Filled.Star,
                label = "Favorites",
                value = data.favoritesCount.toString(),
                accent = Color(0xFF5B4BFF)
            )
        }
    }
}

@Composable
private fun StatRow(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = accent
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmptyDashboard() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF14141C),
                        Color(0xFF0B0B10)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(BrandAccent)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "taizi",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "select a ROM folder to begin",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
