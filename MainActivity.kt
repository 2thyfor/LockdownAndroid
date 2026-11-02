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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    @Composable
    private fun LockdownScreen() {
        val pm = packageManager
        val apps = remember {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).map {
                AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName)
            }.distinctBy { it.packageName }
                .filter { it.packageName != packageName && it.packageName != "com.android.settings" }
                .sortedBy { it.label.lowercase() }
        }
        val appMap = remember(apps) { apps.associateBy { it.packageName } }
        var schedules by remember { mutableStateOf(LockdownController.schedules(this).sortedBy { it.nextStart }) }
        var showEditor by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<BlockSchedule?>(null) }
        var message by remember { mutableStateOf("") }
        var pinDialog by remember { mutableStateOf(false) }
        var customPin by remember { mutableStateOf("") }
        var active by remember { mutableStateOf(LockdownController.isAnyActive(this)) }
        val accessibility = accessibilityEnabled()

        fun refresh() {
            schedules = LockdownController.schedules(this@MainActivity).sortedBy { it.nextStart }
            active = LockdownController.isAnyActive(this@MainActivity)
        }
        fun appNames(s: BlockSchedule): String = s.packages.map { appMap[it]?.label ?: it }.sorted().joinToString(", ")
        fun time(s: BlockSchedule): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(s.nextStart))
        fun duration(minutes: Int): String {
            val h = minutes / 60; val m = minutes % 60
            return if (m == 0) "$h hours" else if (h == 0) "$m minutes" else "$h hours $m minutes"
        }

        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium)
                    Text("Schedule app blocking individually or group apps together.", style = MaterialTheme.typography.bodyMedium)
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (active) "One or more blocks are active" else if (schedules.isEmpty()) "No schedules configured" else "Schedules configured: ${schedules.size}", style = MaterialTheme.typography.titleMedium)
                            Text(if (active) "Apps included in active schedules are currently blocked." else "You can add, edit, and remove schedules while no block is active.")
                        }
                    }
                    if (!accessibility) {
                        Text("Accessibility permission is required to detect when a selected app is opened.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Enable Lockdown Accessibility") }
                    } else Text("Accessibility service: enabled", color = MaterialTheme.colorScheme.primary)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Schedules", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Button(enabled = !active, onClick = { editing = null; showEditor = true }) { Text("Add schedule") }
                    }
                    if (schedules.isEmpty()) Text("No schedules yet. Add a schedule to choose apps, a start time, and a duration.")
                    schedules.forEach { s ->
                        val currentlyActive = s.isActive()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(appNames(s), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    if (currentlyActive) AssistChip(onClick = {}, label = { Text("Active") })
                                    else AssistChip(onClick = {}, label = { Text(if (s.repeatDaily) "Daily" else "One-time") })
                                }
                                Text("Time: ${time(s)}")
                                Text("Duration: ${duration(s.durationMinutes)}")
                                Text("Apps: ${s.packages.size}", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !active, onClick = { editing = s; showEditor = true }) { Text("Edit") }
                                    TextButton(enabled = !active, onClick = {
                                        LockdownController.deleteSchedule(this@MainActivity, s.id); refresh(); message = "Schedule deleted."
                                    }) { Text("Delete") }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("PIN & settings", style = MaterialTheme.typography.titleLarge)
                    Text("Random PIN generation saves the PIN without displaying it. You can also set a PIN you choose. Keep in mind that a random PIN cannot be recovered if you do not know it.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(enabled = !active, onClick = { customPin = ""; pinDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("PIN options") }
                    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                    Text("Limitations: Accessibility-based blocking can be bypassed by disabling Accessibility, force-stopping or uninstalling Lockdown, or using Safe Mode. Android background restrictions may affect exact timing.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showEditor) {
            ScheduleEditor(
                apps = apps,
                initial = editing,
                onDismiss = { showEditor = false; editing = null },
                onSave = { chosen, hour, minute, durationMinutes, daily ->
                    try {
                        require(accessibility) { "Enable Lockdown Accessibility first." }
                        if (editing == null) LockdownController.addSchedule(this, chosen, hour, minute, durationMinutes, daily)
                        else LockdownController.replaceSchedule(this, editing!!.id, chosen, hour, minute, durationMinutes, daily)
                        refresh(); message = "Schedule saved."; showEditor = false; editing = null
                    } catch (e: Exception) { message = e.message ?: "Could not save schedule." }
                }
            )
        }
        if (pinDialog) {
            AlertDialog(
                onDismissRequest = { pinDialog = false },
                title = { Text("PIN options") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Generate a random six-digit PIN silently, or create your own PIN.")
                        OutlinedTextField(value = customPin, onValueChange = { customPin = it.filter(Char::isDigit).take(6) }, label = { Text("Your own six-digit PIN") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (customPin.length == 6) {
                            LockdownController.setPin(this@MainActivity, customPin); message = "Your PIN has been saved."; pinDialog = false
                        } else message = "Enter exactly six digits to create your own PIN."
                    }) { Text("Save my PIN") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            LockdownController.regeneratePin(this@MainActivity)
                            message = "A random PIN was generated and saved. It will not be displayed. If you do not know it, you will not be able to use PIN unlock."
                            pinDialog = false
                        }) { Text("Generate random PIN") }
                        TextButton(onClick = { pinDialog = false }) { Text("Close") }
                    }
                }
            )
        }
    }
}

@Composable
private fun ScheduleEditor(
    apps: List<AppEntry>,
    initial: BlockSchedule?,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Int, Int, Int, Boolean) -> Unit
) {
    var chosen by remember(initial) { mutableStateOf(initial?.packages ?: emptySet()) }
    var time by remember(initial) { mutableStateOf(initial?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "08:00") }
    var hours by remember(initial) { mutableStateOf(((initial?.durationMinutes ?: 60) / 60).toString()) }
    var minutes by remember(initial) { mutableStateOf(((initial?.durationMinutes ?: 60) % 60).toString()) }
    var daily by remember(initial) { mutableStateOf(initial?.repeatDaily ?: false) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New block schedule" else "Edit block schedule") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select one app or group multiple apps")
                apps.forEach { app ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = app.packageName in chosen, onCheckedChange = { checked -> chosen = if (checked) chosen + app.packageName else chosen - app.packageName })
                        Text(app.label)
                    }
                }
                OutlinedTextField(value = time, onValueChange = { time = it.take(5) }, label = { Text("Start time (24-hour HH:mm)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = hours, onValueChange = { hours = it.filter(Char::isDigit).take(3) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = daily, onCheckedChange = { daily = it })
                    Text("Repeat daily")
                }
                Text(if (daily) "This schedule repeats every day." else "This schedule runs once at the next occurrence of this time.", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    val parts = time.split(":")
                    require(parts.size == 2 && parts[0].length == 2 && parts[1].length == 2) { "Use HH:mm, for example 08:00." }
                    val h = parts[0].toInt(); val m = parts[1].toInt()
                    require(h in 0..23 && m in 0..59) { "Enter a valid time." }
                    val duration = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
                    require(duration in 1..10080) { "Duration must be between 1 minute and 7 days." }
                    require(!daily || duration <= 1440) { "Daily schedules must be 24 hours or less." }
                    require(chosen.isNotEmpty()) { "Select at least one app." }
                    onSave(chosen, h, m, duration, daily)
                } catch (e: Exception) { error = e.message ?: "Check your schedule details." }
            }) { Text("Save schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
