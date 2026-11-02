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

    private fun accessibilityEnabled(): Boolean {
        val expected = "$packageName/${LockdownAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @Composable private fun LockdownScreen() {
        val pm = packageManager
        val apps = remember {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName && it.packageName != "com.android.settings" }
                .sortedBy { it.label.lowercase() }
        }
        var selected by remember { mutableStateOf(LockdownController.selected(this).toSet()) }
        var startTime by remember { mutableStateOf("21:00") }
        var hours by remember { mutableStateOf("8") }
        var minutes by remember { mutableStateOf("0") }
        var message by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var active by remember { mutableStateOf(LockdownController.isActive(this)) }
        var pinDialog by remember { mutableStateOf(false) }
        val accessibility = accessibilityEnabled()

        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium)
                    Text("Strict app access control · No factory reset required", style = MaterialTheme.typography.bodyMedium)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (active) "Blocking window active" else "No active block", style = MaterialTheme.typography.titleMedium)
                            Text(if (active) "Selected apps will be redirected while this session is active." else "Choose apps and schedule a future block.")
                            if (LockdownController.endAt(this@MainActivity) > System.currentTimeMillis()) {
                                Text("Ends: ${DateFormat.getDateTimeInstance().format(Date(LockdownController.endAt(this@MainActivity)))}")
                            }
                        }
                    }
                    if (!accessibility) {
                        Text("Accessibility permission is required for foreground app detection. Lockdown does not read screen content or type on your behalf.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Enable Lockdown Accessibility") }
                    } else Text("Accessibility service: enabled", color = MaterialTheme.colorScheme.primary)

                    Text("Choose apps to block", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selected = apps.map { it.packageName }.toSet() }) { Text("Select all") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                    }
                    Card(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        LazyColumn(Modifier.padding(8.dp)) {
                            items(apps, key = { it.packageName }) { app ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = app.packageName in selected, onCheckedChange = { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName }, enabled = !active)
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label)
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it.take(5) }, label = { Text("Start time (24-hour HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !active)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(2) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !active)
                        OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Extra minutes") }, modifier = Modifier.weight(1f), singleLine = true, enabled = !active)
                    }
                    Button(onClick = {
                        try {
                            require(accessibility) { "Enable Accessibility first." }
                            val parts = startTime.split(":"); require(parts.size == 2)
                            val h = parts[0].toInt(); val m = parts[1].toInt()
                            require(h in 0..23 && m in 0..59)
                            val duration = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                            require(duration in 1..10080); require(selected.isNotEmpty())
                            LockdownController.saveSelection(this@MainActivity, selected)
                            val at = LockdownController.schedule(this@MainActivity, h, m, duration)
                            message = "Scheduled for ${DateFormat.getDateTimeInstance().format(Date(at))}. Duration: $duration minutes."
                        } catch (e: Exception) { message = e.message ?: "Check the time, duration, and app selection." }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !active) { Text("Schedule strict block") }

                    Divider()
                    Text("PIN & settings", style = MaterialTheme.typography.titleLarge)
                    Text("A random six-digit PIN was generated on first launch and is not shown. It can unlock a session early if you already know it. You can choose a replacement PIN here when no session is active.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { newPin = ""; pinDialog = true }, enabled = !active, modifier = Modifier.fillMaxWidth()) { Text("Change PIN") }
                    if (active) Text("PIN changes are locked until the scheduled session ends.", color = MaterialTheme.colorScheme.error)
                    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                    Text("Limitations: without Device Owner, Android cannot prevent you from force-stopping/uninstalling Lockdown or disabling its Accessibility permission in system settings. Samsung battery optimization may also affect reliability. Keep essential apps and emergency functions available.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (pinDialog) AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("Change PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter a new six-digit PIN. PIN changes are available only when no session is active.")
                    OutlinedTextField(value = newPin, onValueChange = { newPin = it.filter(Char::isDigit).take(6) }, label = { Text("New six-digit PIN") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = {
                if (newPin.length == 6) { LockdownController.setPin(this@MainActivity, newPin); pinDialog = false; message = "PIN updated." }
                else message = "Enter exactly six digits."
            }) { Text("Save PIN") } },
            dismissButton = { TextButton(onClick = { pinDialog = false }) { Text("Cancel") } }
        )
    }
}
