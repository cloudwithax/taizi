package com.taizi.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.taizi.ui.components.focusHighlight
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LaunchableApp(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable
)

@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context.packageManager) }
    }

    val (initIndex, initOffset) = viewModel.getAppDrawerScroll()
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = initIndex,
        initialFirstVisibleItemScrollOffset = initOffset
    )
    LaunchedEffect(Unit) {
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }.collect { (i, off) -> viewModel.setAppDrawerScroll(i, off) }
    }

    var menuApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var activitiesApp by remember { mutableStateOf<LaunchableApp?>(null) }

    if (menuApp != null) {
        val app = menuApp!!
        AlertDialog(
            onDismissRequest = { menuApp = null },
            modifier = Modifier.fillMaxWidth(0.95f),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            title = { Text(app.label) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DELETE)
                                .setData(Uri.fromParts("package", app.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launchOrToast(context, intent, "Couldn't open uninstaller")
                            menuApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Uninstall", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            activitiesApp = app
                            menuApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Activities", modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                .setData(Uri.fromParts("package", app.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            launchOrToast(context, intent, "Couldn't open app info")
                            menuApp = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Application Info", modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuApp = null }) { Text("Cancel") }
            }
        )
    }

    val actApp = activitiesApp
    if (actApp != null) {
        ActivitiesDialog(
            packageName = actApp.packageName,
            packageManager = context.packageManager,
            onDismiss = { activitiesApp = null },
            onLaunch = { activityName ->
                val intent = Intent()
                    .setClassName(actApp.packageName, activityName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchOrToast(context, intent, "Couldn't launch ${activityName.substringAfterLast('.')}")
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.focusHighlight(shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Column {
                Text(
                    text = "APPS",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = apps?.let { "${it.size} installed" } ?: "Loading…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val list = apps
        when {
            list == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            list.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No launchable apps found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 88.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(list, key = { "${it.packageName}/${it.activityName}" }) { app ->
                    AppTile(
                        app = app,
                        onClick = {
                            val intent = Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LAUNCHER)
                                .setClassName(app.packageName, app.activityName)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        },
                        onLongClick = { menuApp = app }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: LaunchableApp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(LocalContext.current)
            .data(app.icon)
            .crossfade(false)
            .build()
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .focusHighlight(shape = RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.25f))
        ) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ActivitiesDialog(
    packageName: String,
    packageManager: PackageManager,
    onDismiss: () -> Unit,
    onLaunch: (String) -> Unit
) {
    var activities by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(packageName) {
        activities = withContext(Dispatchers.IO) {
            try {
                val pkgInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                pkgInfo.activities
                    ?.filter { it.exported }
                    ?.map { info ->
                        val label = info.loadLabel(packageManager)?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.name.substringAfterLast('.')
                        label to info.name
                    }
                    ?.sortedBy { it.first.lowercase() }
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Activities") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (activities.isEmpty()) {
                    Text("No activities found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    activities.forEach { (label, name) ->
                        TextButton(
                            onClick = {
                                onLaunch(name)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private fun launchOrToast(context: Context, intent: Intent, failureMessage: String) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

private fun loadLaunchableApps(pm: PackageManager): List<LaunchableApp> {
    val seen = HashSet<String>()
    val combined = mutableListOf<android.content.pm.ResolveInfo>()
    for (category in listOf(Intent.CATEGORY_LAUNCHER, Intent.CATEGORY_LEANBACK_LAUNCHER)) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        val results = pm.queryIntentActivities(intent, 0)
        for (info in results) {
            val key = "${info.activityInfo.packageName}/${info.activityInfo.name}"
            if (seen.add(key)) combined += info
        }
    }
    return combined
        .map {
            LaunchableApp(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                activityName = it.activityInfo.name,
                icon = it.loadIcon(pm)
            )
        }
        .sortedBy { it.label.lowercase() }
}
