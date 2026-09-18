package com.taizi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taizi.domain.model.EmulatorConfig
import com.taizi.domain.model.System
import com.taizi.ui.components.focusHighlight

/**
 * Lets the user override which player (emulator) a system launches with, so a
 * non-default RetroArch build or standalone can be used per console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerPickerDialog(
    system: System,
    players: List<EmulatorConfig>,
    onSelect: (EmulatorConfig) -> Unit,
    onUseDefault: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMode by remember { mutableStateOf(false) }
    var customPackage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Player")
                Text(
                    text = system.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Current: " + (system.emulatorType.ifBlank { "not set" }) +
                            (system.emulatorPackage?.let { "\n$it" } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (customMode) {
                    OutlinedTextField(
                        value = customPackage,
                        onValueChange = { customPackage = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Package name") },
                        placeholder = { Text("com.example.retroarch") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { customMode = false }) { Text("Cancel") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val pkg = customPackage.trim()
                                if (pkg.isNotEmpty()) {
                                    onSelect(
                                        EmulatorConfig(
                                            type = if (pkg.contains("retroarch", ignoreCase = true))
                                                "RetroArch" else "Custom",
                                            packageName = pkg,
                                            core = system.core
                                        )
                                    )
                                }
                            }
                        ) { Text("Set") }
                    }
                } else {
                    if (players.isEmpty()) {
                        Text(
                            text = "No supported players found. Use a custom package below.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    players.forEach { player ->
                        PlayerRow(
                            label = player.type,
                            packageName = player.packageName.orEmpty(),
                            selected = player.packageName == system.emulatorPackage,
                            onClick = { onSelect(player.copy(core = system.core)) }
                        )
                    }
                    PlayerRow(
                        label = "Custom package…",
                        packageName = "Use another app's package name",
                        selected = false,
                        onClick = { customMode = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onUseDefault) { Text("Use default player") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun PlayerRow(
    label: String,
    packageName: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusHighlight(shape = RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (packageName.isNotEmpty()) {
                Text(
                    text = packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
