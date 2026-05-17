package com.taizi.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taizi.ui.components.focusHighlight
import com.taizi.ui.theme.BrandAccent
import java.io.File

@Immutable
private data class StorageVolume(
    val path: String,
    val label: String,
    val isRemovable: Boolean,
    val icon: ImageVector
)

@Immutable
private data class DirEntry(
    val file: File,
    val name: String,
    val childCount: Int
)

private fun detectStorageVolumes(): List<StorageVolume> {
    val volumes = mutableListOf<StorageVolume>()
    val internal = Environment.getExternalStorageDirectory()
    if (internal.exists() && internal.canRead()) {
        volumes.add(
            StorageVolume(
                path = internal.absolutePath,
                label = "Internal Storage",
                isRemovable = false,
                icon = Icons.Filled.Smartphone
            )
        )
    }
    val sdCardDirs = listOf("/storage/sdcard1", "/storage/extSdCard", "/storage/SD_CARD")
    for (sdPath in sdCardDirs) {
        val sdDir = File(sdPath)
        if (sdDir.exists() && sdDir.canRead()) {
            volumes.add(
                StorageVolume(
                    path = sdPath,
                    label = "SD Card",
                    isRemovable = true,
                    icon = Icons.Filled.SdCard
                )
            )
        }
    }
    val storageDir = File("/storage")
    if (storageDir.exists() && storageDir.canRead()) {
        storageDir.listFiles()?.filter { it.isDirectory && it.canRead() }?.forEach { dir ->
            if (dir.name != "emulated" && dir.name != "self" && dir.absolutePath !in volumes.map { it.path }) {
                val isSd = dir.name.equals("sdcard1", ignoreCase = true) ||
                        dir.name.equals("extsdcard", ignoreCase = true) ||
                        dir.name.contains("sd", ignoreCase = true)
                volumes.add(
                    StorageVolume(
                        path = dir.absolutePath,
                        label = if (isSd) "SD Card (${dir.name})" else dir.name,
                        isRemovable = isSd,
                        icon = if (isSd) Icons.Filled.SdCard else Icons.Filled.Storage
                    )
                )
            }
        }
    }
    return volumes.distinctBy { it.path }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerDialog(
    initialPath: String = "/storage/emulated/0",
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPath by rememberSaveable { mutableStateOf(initialPath) }
    var showingVolumes by remember { mutableStateOf(true) }
    val storageVolumes = remember { detectStorageVolumes() }
    val volumePaths = remember { storageVolumes.map { it.path }.toSet() }

    // Show volume selection when at root level; show directory contents otherwise
    val isAtRoot = showingVolumes || currentPath == "/" || currentPath == "/storage"

    val directories = remember(currentPath) {
        val dir = File(currentPath)
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.map { file ->
                    val childDirs = file.listFiles()?.count {
                        it.isDirectory && !it.name.startsWith(".")
                    } ?: 0
                    DirEntry(file, file.name, childDirs)
                }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()
        } else {
            emptyList()
        }
    }

    val parentFile = remember(currentPath) {
        val parent = File(currentPath).parentFile
        if (parent != null && parent.canRead() && parent.path != "/") parent else null
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Select Folder",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (!showingVolumes) {
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showingVolumes) {
                        // At root — no back button needed
                    } else {
                        IconButton(onClick = {
                            val parent = File(currentPath).parentFile
                            if (parent != null && parent.canRead() && parent.path != "/") {
                                // Go up one directory
                                currentPath = parent.absolutePath
                            } else {
                                // At volume root — go back to volume selection
                                showingVolumes = true
                                currentPath = initialPath
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .focusHighlight(shape = RoundedCornerShape(8.dp), accent = Color.White)
                            .background(BrandAccent)
                            .clickable { onSelect(currentPath) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "USE THIS FOLDER",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                scrollBehavior = scrollBehavior
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isAtRoot) {
                    item {
                        SectionLabel("STORAGE DEVICES")
                    }

                    items(storageVolumes, key = { it.path }) { volume ->
                        VolumeItem(
                            volume = volume,
                            onClick = {
                                currentPath = volume.path
                                showingVolumes = false
                            }
                        )
                    }

                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }

                    item {
                        SectionLabel("BROWSE")
                    }

                    item {
                        VolumeItem(
                            volume = StorageVolume(
                                path = "/storage",
                                label = "File System Root",
                                isRemovable = false,
                                icon = Icons.Filled.Storage
                            ),
                            onClick = {
                                currentPath = "/storage"
                                showingVolumes = false
                            }
                        )
                    }
                } else {
                    if (parentFile != null) {
                        item {
                            GoUpItem(
                                parentName = parentFile.name.ifEmpty { parentFile.absolutePath },
                                onClick = {
                                    val parent = File(currentPath).parentFile
                                    if (parent != null && parent.canRead() && parent.path != "/") {
                                        currentPath = parent.absolutePath
                                    } else {
                                        showingVolumes = true
                                        currentPath = initialPath
                                    }
                                }
                            )
                        }
                        item {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    item {
                        Text(
                            text = File(currentPath).name.ifEmpty { currentPath }.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    if (directories.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No folders found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    items(directories, key = { it.file.absolutePath }) { entry ->
                        DirectoryRow(
                            entry = entry,
                            onClick = { currentPath = entry.file.absolutePath }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun GoUpItem(
    parentName: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go up",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Go to parent folder",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = parentName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun VolumeItem(
    volume: StorageVolume,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (volume.isRemovable) Color(0xFFFFC857).copy(alpha = 0.15f)
                    else BrandAccent.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = volume.icon,
                contentDescription = null,
                tint = if (volume.isRemovable) Color(0xFFFFC857) else BrandAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volume.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                text = volume.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Open",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun DirectoryRow(
    entry: DirEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder",
                tint = BrandAccent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.childCount > 0) {
                Text(
                    text = "${entry.childCount} subfolder${if (entry.childCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = "Open",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}