package com.lockdown.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

private data class AppEntry(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockdownController.initialize(this)
        setContent { LockdownScreen() }
    }

    override fun onResume() {
        super.onResume()
        // The screen also refreshes its status when it is brought back to the foreground.
    }

    private fun accessibilityEnabled(): Boolean {
        val expected = "$packageName/${LockdownAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @Composable
    private fun LockdownScreen() {
        val pm = packageManager
        val apps = remember {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map {
                    AppEntry(
                        it.loadLabel(pm).toString(),
                        it.activityInfo.packageName
                    )
                }
                .distinctBy { it.packageName }
                .filter {
                    it.packageName != packageName &&
                        it.packageName != "com.android.settings"
                }
                .sortedBy { it.label.lowercase() }
        }

        val appByPackage = remember(apps) { apps.associateBy { it.packageName } }
        var selected by remember {
            mutableStateOf(LockdownController.selected(this).toSet())
        }
        var startTime by remember { mutableStateOf("21:00") }
        var hours by remember { mutableStateOf("8") }
        var minutes by remember { mutableStateOf("0") }
        var message by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var active by remember { mutableStateOf(LockdownController.isActive(this)) }
        var pinDialog by remember { mutableStateOf(false) }
        var showAddApps by remember { mutableStateOf(false) }
        val accessibility = accessibilityEnabled()
        val scheduledStart = LockdownController.startAt(this)
        val scheduledEnd = LockdownController.endAt(this)
        val now = System.currentTimeMillis()
        val hasFutureSchedule = scheduledStart > now && scheduledEnd > now
        val isExpired = scheduledEnd > 0L && scheduledEnd <= now

        fun updateSelection(updated: Set<String>) {
            selected = updated
            LockdownController.saveSelection(this@MainActivity, updated)
        }

        MaterialTheme {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Strict app access control · No factory reset required",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                when {
                                    active -> "Blocking window active"
                                    hasFutureSchedule -> "Block scheduled"
                                    isExpired -> "Last block expired"
                                    else -> "No active block"
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                when {
                                    active -> "Your selected apps are protected until the session ends."
                                    hasFutureSchedule -> "Your selected apps will be blocked when the scheduled start time arrives."
                                    else -> "You can manage your blocked-app list freely while no session is active."
                                }
                            )
                            if (hasFutureSchedule || active) {
                                Text(
                                    "Starts: ${DateFormat.getDateTimeInstance().format(Date(scheduledStart))}"
                                )
                                Text(
                                    "Ends: ${DateFormat.getDateTimeInstance().format(Date(scheduledEnd))}"
                                )
                            }
                            Text("Selected apps: ${selected.size}")
                        }
                    }

                    if (!accessibility) {
                        Text(
                            "Accessibility permission is required for foreground app detection. Lockdown does not read screen content or type on your behalf.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Lockdown Accessibility")
                        }
                    } else {
                        Text(
                            "Accessibility service: enabled",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Always-visible list of apps currently selected for blocking.
                    Text("Blocked Apps", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (active) {
                            "App selection is locked until this session ends."
                        } else {
                            "These apps will be blocked during your next scheduled session. Add or remove apps without entering your PIN."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (selected.isEmpty()) {
                                Text("No apps selected yet.")
                            } else {
                                selected.sortedBy {
                                    appByPackage[it]?.label ?: it
                                }.forEach { packageName ->
                                    val app = appByPackage[packageName]
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(app?.label ?: packageName)
                                            if (app != null) {
                                                Text(
                                                    packageName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                Text(
                                                    "App is no longer installed",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        TextButton(
                                            enabled = !active,
                                            onClick = {
                                                updateSelection(selected - packageName)
                                            }
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                    HorizontalDivider()
                                }
                            }
                            OutlinedButton(
                                onClick = { showAddApps = !showAddApps },
                                enabled = !active,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (showAddApps) "Hide app list" else "Add apps")
                            }
                        }
                    }

                    if (showAddApps) {
                        Text("Choose apps to add", style = MaterialTheme.typography.titleMedium)
                        Card(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                        ) {
                            LazyColumn(Modifier.padding(8.dp)) {
                                items(apps.filter { it.packageName !in selected }, key = { it.packageName }) { app ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(app.label)
                                            Text(
                                                app.packageName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(
                                            enabled = !active,
                                            onClick = {
                                                updateSelection(selected + app.packageName)
                                            }
                                        ) {
                                            Text("Add")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (!active) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it.take(5) },
                            label = { Text("Start time (24-hour HH:mm)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = hours,
                                onValueChange = { hours = it.filter(Char::isDigit).take(2) },
                                label = { Text("Hours") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = minutes,
                                onValueChange = { minutes = it.filter(Char::isDigit).take(2) },
                                label = { Text("Extra minutes") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Button(
                            onClick = {
                                try {
                                    require(accessibility) { "Enable Accessibility first." }
                                    val parts = startTime.split(":")
                                    require(parts.size == 2)
                                    val h = parts[0].toInt()
                                    val m = parts[1].toInt()
                                    require(h in 0..23 && m in 0..59)
                                    val duration =
                                        (hours.toIntOrNull() ?: 0) * 60 +
                                            (minutes.toIntOrNull() ?: 0)
                                    require(duration in 1..10080)
                                    require(selected.isNotEmpty()) {
                                        "Add at least one app to Blocked Apps."
                                    }
                                    LockdownController.saveSelection(this@MainActivity, selected)
                                    val at = LockdownController.schedule(
                                        this@MainActivity,
                                        h,
                                        m,
                                        duration
                                    )
                                    active = LockdownController.isActive(this@MainActivity)
                                    message =
                                        "Scheduled for ${DateFormat.getDateTimeInstance().format(Date(at))}. Duration: $duration minutes."
                                } catch (e: Exception) {
                                    message = e.message
                                        ?: "Check the time, duration, and app selection."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Schedule strict block")
                        }
                    } else {
                        Text(
                            "Your app selection and schedule settings are locked while this session is active.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    HorizontalDivider()
                    Text("PIN & settings", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "The PIN is only for ending an active session early or accessing a blocked app if the blocker requests it. You do not need a PIN to add or remove apps when no session is active.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = {
                            newPin = ""
                            pinDialog = true
                        },
                        enabled = !active,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Change PIN")
                    }
                    if (active) {
                        Text(
                            "PIN changes are locked until the scheduled session ends.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "Limitations: without Device Owner, Android cannot prevent you from force-stopping/uninstalling Lockdown or disabling its Accessibility permission in system settings. Samsung battery optimization may also affect reliability. Keep essential apps and emergency functions available.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (pinDialog) {
            AlertDialog(
                onDismissRequest = { pinDialog = false },
                title = { Text("Change PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Enter a new six-digit PIN. PIN changes are available only when no session is active."
                        )
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = {
                                newPin = it.filter(Char::isDigit).take(6)
                            },
                            label = { Text("New six-digit PIN") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPin.length == 6) {
                                LockdownController.setPin(this@MainActivity, newPin)
                                pinDialog = false
                                message = "PIN updated."
                            } else {
                                message = "Enter exactly six digits."
                            }
                        }
                    ) {
                        Text("Save PIN")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
