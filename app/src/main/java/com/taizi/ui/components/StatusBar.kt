package com.taizi.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Top status bar showing WiFi and battery status
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var wifiConnected by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }

    // Initial battery status from sticky intent
    val batteryStatus: Intent? = remember {
        context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }

    LaunchedEffect(batteryStatus) {
        batteryStatus?.let { intent ->
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).coerceAtLeast(0)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
    }

    // Poll WiFi every few seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            wifiConnected = isWifiConnected(context)
            delay(5000)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // WiFi indicator
            Text(
                text = if (wifiConnected) "WiFi" else "No WiFi",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (wifiConnected) Color(0xFF4CAF50) else Color(0xFFF44336)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Battery indicator
            Text(
                text = "$batteryLevel%${if (isCharging) " ⚡" else ""}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun isWifiConnected(context: Context): Boolean {
    return try {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetworkInfo
        network?.type == ConnectivityManager.TYPE_WIFI && network.isConnected
    } catch (e: Exception) {
        false
    }
}

