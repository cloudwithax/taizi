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
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import com.taizi.ui.components.BackHoldGate
import com.taizi.ui.screens.FolderPickerDialog
import com.taizi.ui.screens.MainScreen
import com.taizi.ui.screens.MainViewModel
import com.taizi.ui.theme.TaiziTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val storagePermissionGranted = mutableStateOf(false)
    private val showFolderPicker = mutableStateOf(false)

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        storagePermissionGranted.value = hasStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storagePermissionGranted.value = hasStoragePermission()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            TaiziTheme {
                val granted by storagePermissionGranted
                val pickingFolder by showFolderPicker

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
                        if (granted) {
                            MainScreen(
                                viewModel = viewModel,
                                onSelectFolder = { showFolderPicker.value = true }
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
    }

    /**
     * While the Now Playing screen is up, Back is consumed here and only its
     * down/up edges are forwarded — a stray tap does nothing, and the screen
     * closes solely on a sustained hold.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)

        if (BackHoldGate.isArmed) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> BackHoldGate.press()
                KeyEvent.ACTION_UP -> BackHoldGate.release()
            }
            return true
        }

        // Swallow what's left of the press that just closed the guard: its own
        // auto-repeats and its release. A fresh press (repeatCount 0) always
        // gets through, so a missed release can't wedge the Back button.
        if (BackHoldGate.isDraining) {
            if (event.action == KeyEvent.ACTION_UP) {
                BackHoldGate.endDrain()
                return true
            }
            if (event.repeatCount > 0) return true
            BackHoldGate.endDrain()
        }
        return super.dispatchKeyEvent(event)
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
