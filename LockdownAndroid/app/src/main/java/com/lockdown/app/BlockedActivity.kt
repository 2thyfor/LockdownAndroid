package com.lockdown.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class BlockedActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var code by remember { mutableStateOf("") }
                    var error by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("LOCKDOWN", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(16.dp))
                        Text("This app is blocked", style = MaterialTheme.typography.headlineSmall)
                        Text("Your scheduled focus session is active. Access will return when the timer expires.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) }, label = { Text("PIN (optional early unlock)") }, singleLine = true)
                        Button(onClick = {
                            if (!LockdownController.canUnlockEarly(this@BlockedActivity)) {
                                error = "Early unlock is disabled during your first scheduled session."
                            } else if (LockdownController.verifyPin(this@BlockedActivity, code)) {
                                LockdownController.clearSchedule(this@BlockedActivity)
                                finish()
                            } else {
                                error = "Incorrect PIN. Your session remains active."
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Unlock with PIN") }
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(home)
                        }, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
                    }
                }
            }
        }
    }
}
