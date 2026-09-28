package com.taizi.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.ui.graphics.vector.ImageVector
import com.taizi.domain.model.LibraryLayout

/** The header toggle shows the layout currently in use. */
fun layoutIcon(layout: LibraryLayout): ImageVector = when (layout) {
    LibraryLayout.CAROUSEL -> Icons.Filled.ViewCarousel
    LibraryLayout.GRID -> Icons.Filled.GridView
    LibraryLayout.LIST -> Icons.Filled.ViewList
}
