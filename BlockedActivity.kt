package com.lockdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPackage = intent.getStringExtra("blocked_package") ?: "Blocked app"
        val scheduleId = intent.getLongExtra("schedule_id", -1L)
        val schedule = LockdownController.schedules(this).firstOrNull { it.id == scheduleId }
        val timeText = schedule?.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(it.nextStart)) } ?: "Scheduled"
        val duration = schedule?.let { formatDuration(it.durationMinutes) } ?: ""
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var code by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(24.dp))
                        Text("Blocked app", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))
                        Text("Time: $timeText", style = MaterialTheme.typography.titleMedium)
                        Text("Duration: $duration", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(24.dp))
                        Text("This app is currently blocked.", style = MaterialTheme.typography.bodyMedium)
                        if (LockdownController.canUnlockEarly(this@BlockedActivity)) {
                            Spacer(Modifier.height(20.dp))
                            OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, label = { Text("PIN") }, singleLine = true)
                            Button(onClick = {
                                if (LockdownController.verifyPin(this@BlockedActivity, code)) {
                                    // Early unlock ends only the schedule responsible for this blocked app.
                                    if (scheduleId >= 0L) LockdownController.deleteSchedule(this@BlockedActivity, scheduleId)
                                    finish()
                                } else error = "Incorrect PIN."
                            }, modifier = Modifier.fillMaxWidth()) { Text("End this schedule with PIN") }
                        }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
                    }
                }
            }
        }
    }
    private fun formatDuration(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        return if (m == 0) "$h hours" else if (h == 0) "$m minutes" else "$h hours $m minutes"
    }
}
