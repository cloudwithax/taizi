package com.taizi.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taizi.domain.model.PlayerIssue

/**
 * Raised when a platform's emulator has gone missing — usually because it was
 * just uninstalled. Names what broke, names what Taizi would switch to, and
 * makes taking the fallback a single button press.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerFallbackDialog(
    issues: List<PlayerIssue>,
    onUseFallbacks: () -> Unit,
    onDismiss: () -> Unit
) {
    if (issues.isEmpty()) return

    val fixable = issues.count { it.replacement != null }
    val wasRemoved = issues.any { it.missingPackage != null }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                if (wasRemoved) "A player went missing"
                else "No player installed"
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = when {
                        fixable == 0 && wasRemoved ->
                            "These platforms can't launch anything until you install " +
                                    "a supported emulator."
                        fixable == 0 ->
                            "No supported emulator was found for these platforms. " +
                                    "Install one and they'll start working."
                        wasRemoved ->
                            "The emulator these platforms used isn't installed any " +
                                    "more. Taizi can switch them over now."
                        else ->
                            "These platforms have no player set. Taizi can pick one " +
                                    "for you now."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                issues.forEach { issue ->
                    IssueRow(issue)
                }
            }
        },
        confirmButton = {
            if (fixable > 0) {
                TextButton(onClick = onUseFallbacks) {
                    Text(if (fixable == 1) "Switch player" else "Switch all")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        },
        dismissButton = {
            if (fixable > 0) {
                TextButton(onClick = onDismiss) { Text("Not now") }
            }
        }
    )
}

@Composable
private fun IssueRow(issue: PlayerIssue) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = issue.systemName,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = when {
                issue.replacement != null && issue.missingPackage != null ->
                    "${issue.missingLabel} → ${issue.replacement.displayLabel}"
                issue.replacement != null ->
                    "Use ${issue.replacement.displayLabel}"
                issue.missingPackage != null ->
                    "${issue.missingLabel} removed · no alternative found"
                else -> "No supported player found"
            },
            fontSize = 12.sp,
            color = if (issue.replacement == null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
