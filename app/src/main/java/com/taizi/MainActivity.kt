package com.taizi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.taizi.ui.screens.Dashboard
import com.taizi.ui.screens.LauncherPresentation
import com.taizi.ui.screens.MainScreen
import com.taizi.ui.screens.MainViewModel
import com.taizi.ui.theme.TaiziTheme
import com.taizi.util.DeviceDetection
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val storagePermissionGranted = mutableStateOf(false)
    private val isDualScreen = mutableStateOf(false)
    private var launcherPresentation: LauncherPresentation? = null

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermissionGranted.value = hasStoragePermission()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { treeUriToFilePath(it) }?.let(viewModel::triggerFullScan)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storagePermissionGranted.value = hasStoragePermission()
        isDualScreen.value = DeviceDetection.findSecondaryDisplay(this) != null

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TaiziTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val dual by isDualScreen
                    val granted by storagePermissionGranted

                    if (dual) {
                        Dashboard(bottomState = viewModel.bottomUiData)
                    } else {
                        if (granted) {
                            MainScreen(
                                viewModel = viewModel,
                                onSelectFolder = { folderPickerLauncher.launch(null) }
                            )
                        } else {
                            StoragePermissionScreen(onRequestPermission = { requestStoragePermission() })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        storagePermissionGranted.value = hasStoragePermission()
        showLauncherPresentationIfNeeded()
    }

    override fun onPause() {
        super.onPause()
        dismissLauncherPresentation()
    }

    private fun showLauncherPresentationIfNeeded() {
        if (!isDualScreen.value) return

        val display = DeviceDetection.findSecondaryDisplay(this) ?: return

        if (launcherPresentation?.display?.displayId == display.displayId &&
            launcherPresentation?.isShowing == true) {
            return
        }

        dismissLauncherPresentation()

        launcherPresentation = LauncherPresentation(
            viewModel = viewModel,
            lifecycleOwner = this,
            onSelectFolder = { folderPickerLauncher.launch(null) },
            outerContext = this,
            display = display
        ).also {
            it.show()
        }
    }

    private fun dismissLauncherPresentation() {
        launcherPresentation?.let {
            if (it.isShowing) it.dismiss()
        }
        launcherPresentation = null
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        }
    }

    companion object {
        private fun treeUriToFilePath(uri: Uri): String? {
            val docId = uri.lastPathSegment ?: return null
            val split = docId.split(":")
            if (split.size < 2) return null
            val volume = split[0]
            val relativePath = split[1]
            val root = if (volume == "primary") {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "/storage/$volume"
            }
            return "$root/$relativePath"
        }
    }
}

@Composable
private fun StoragePermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "File Access Required",
            fontSize = 24.sp,
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Taizi needs access to your storage to scan ROM folders and launch games.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Storage Access")
        }
    }
}
