package com.taizi.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Battery0Bar
import androidx.compose.material.icons.outlined.Battery2Bar
import androidx.compose.material.icons.outlined.Battery4Bar
import androidx.compose.material.icons.outlined.Battery6Bar
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.DateFormat
import java.util.Date

@Composable
fun StatusBar(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var wifiConnected by remember { mutableStateOf(isWifiConnected(context)) }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(currentClock()) }

    LaunchedEffect(Unit) {
        while (isActive) {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                batteryLevel = ((level.coerceAtLeast(0).toFloat() / scale) * 100f).toInt().coerceIn(0, 100)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
            wifiConnected = isWifiConnected(context)
            clock = currentClock()
            delay(15_000)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = clock,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = if (wifiConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                contentDescription = if (wifiConnected) "Online" else "Offline",
                modifier = Modifier.size(14.dp),
                tint = if (wifiConnected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(10.dp))

            if (isCharging) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Charging",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(2.dp))
            }

            Icon(
                imageVector = batteryIcon(batteryLevel),
                contentDescription = "Battery $batteryLevel%",
                modifier = Modifier.size(16.dp),
                tint = batteryTint(batteryLevel)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$batteryLevel%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun currentClock(): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())

private fun batteryIcon(level: Int): ImageVector = when {
    level >= 85 -> Icons.Outlined.BatteryFull
    level >= 60 -> Icons.Outlined.Battery6Bar
    level >= 35 -> Icons.Outlined.Battery4Bar
    level >= 15 -> Icons.Outlined.Battery2Bar
    else -> Icons.Outlined.Battery0Bar
}

@Composable
private fun batteryTint(level: Int): Color = when {
    level <= 15 -> MaterialTheme.colorScheme.primary
    level <= 30 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurface
}

private fun isWifiConnected(context: Context): Boolean {
    return try {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = if (network != null) cm.getNetworkCapabilities(network) else null
        if (caps != null) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        cm.allNetworks.any { net ->
            val c = cm.getNetworkCapabilities(net) ?: return@any false
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
    } catch (_: Exception) {
        false
    }
}
