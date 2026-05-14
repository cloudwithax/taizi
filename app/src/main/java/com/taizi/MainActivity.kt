package com.taizi

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.taizi.ui.screens.FolderPickerDialog
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
    private val showFolderPicker = mutableStateOf(false)
    // When true, the launcher is hosted by LauncherPresentation on the
    // secondary display and this activity renders the dashboard. When false
    // (single-screen device, or the secondary display was taken over by
    // another activity), this activity renders the launcher itself so it
    // stays reachable.
    private val launcherInPresentation = mutableStateOf(false)
    private var launcherPresentation: LauncherPresentation? = null

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermissionGranted.value = hasStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storagePermissionGranted.value = hasStoragePermission()
        isDualScreen.value = DeviceDetection.findSecondaryDisplay(this) != null

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TaiziTheme {
                val dual by isDualScreen
                val granted by storagePermissionGranted
                val pickingFolder by showFolderPicker
                val inPresentation by launcherInPresentation

                if (pickingFolder) {
                    FolderPickerDialog(
                        onSelect = { path ->
                            showFolderPicker.value = false
                            viewModel.triggerFullScan(path)
                        },
                        onDismiss = { showFolderPicker.value = false }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when {
                            !granted -> StoragePermissionScreen(onRequestPermission = { requestStoragePermission() })
                            dual && inPresentation -> {
                                BackHandler { }
                                Dashboard(bottomState = viewModel.bottomUiData)
                            }
                            else -> MainScreen(
                                viewModel = viewModel,
                                onSelectFolder = { showFolderPicker.value = true }
                            )
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // After regaining focus, the secondary display may have just been
        // freed (the activity that was on it finished). Retry hosting the
        // launcher on the secondary display so it migrates back from the
        // fallback location.
        if (hasFocus && isDualScreen.value && !launcherInPresentation.value) {
            showLauncherPresentationIfNeeded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissLauncherPresentation()
    }

    private fun showLauncherPresentationIfNeeded() {
        if (!isDualScreen.value) return

        val display = DeviceDetection.findSecondaryDisplay(this) ?: return

        if (launcherPresentation?.display?.displayId == display.displayId &&
            launcherPresentation?.isShowing == true) {
            launcherInPresentation.value = true
            return
        }

        dismissLauncherPresentation()

        val presentation = LauncherPresentation(
            viewModel = viewModel,
            lifecycleOwner = this,
            onSelectFolder = { showFolderPicker.value = true },
            outerContext = this,
            display = display
        )
        // The system dismisses a Presentation when its display gets taken
        // over by another activity. When that happens, fall back to hosting
        // the launcher on this activity so it remains reachable.
        presentation.setOnDismissListener {
            if (launcherPresentation === presentation) {
                launcherPresentation = null
            }
            launcherInPresentation.value = false
        }
        try {
            presentation.show()
        } catch (_: WindowManager.InvalidDisplayException) {
            launcherInPresentation.value = false
            return
        }
        if (presentation.isShowing) {
            launcherPresentation = presentation
            launcherInPresentation.value = true
        } else {
            launcherInPresentation.value = false
        }
    }

    private fun dismissLauncherPresentation() {
        launcherPresentation?.let {
            it.setOnDismissListener(null)
            if (it.isShowing) it.dismiss()
        }
        launcherPresentation = null
        launcherInPresentation.value = false
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
