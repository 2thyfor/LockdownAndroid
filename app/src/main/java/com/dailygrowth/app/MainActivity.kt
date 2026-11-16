package com.dailygrowth.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.Date
import kotlinx.coroutines.delay

private val Navy = Color(0xFF172D38)
private val Green = Color(0xFF72D6A0)

class MainActivity : ComponentActivity() {
    private var firstRunPin: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRunPin = Accountability.initialize(this)
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 8441)
        }
        StreakWidgetProvider.updateAll(this)
        scheduleDailyCheckIn()
        scheduleBlocklistUpdates()
        setContent { DailyGrowthScreen(firstRunPin = firstRunPin, onPinAcknowledged = { firstRunPin = null }) }
    }

    private fun scheduleBlocklistUpdates() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic = PeriodicWorkRequestBuilder<BlocklistUpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-growth-blocklist-refresh", ExistingPeriodicWorkPolicy.KEEP, periodic
        )
        if (!BlocklistRepository.isUpdateAvailable(this)) {
            val initial = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(this).enqueueUniqueWork(
                "daily-growth-blocklist-initial", ExistingWorkPolicy.KEEP, initial
            )
        }
    }

    private fun scheduleDailyCheckIn() {
        val request = PeriodicWorkRequestBuilder<DailyCheckInWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily-growth-checkin", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyGrowthScreen(firstRunPin: String?, onPinAcknowledged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var days by remember { mutableLongStateOf(Accountability.days(context)) }
    var best by remember { mutableLongStateOf(Accountability.best(context)) }
    var protection by remember { mutableStateOf(context.getSharedPreferences("daily_growth_private", 0).getBoolean("protection_active", false)) }
    var blocklistCount by remember { mutableIntStateOf(BlocklistRepository.domainCount(context)) }
    var blocklistUpdatedAt by remember { mutableLongStateOf(BlocklistRepository.lastUpdated(context)) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinPurpose by remember { mutableStateOf("reset") }
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showSetup by remember { mutableStateOf(firstRunPin != null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        while (true) {
            val prefs = context.getSharedPreferences("daily_growth_private", 0)
            protection = prefs.getBoolean("protection_active", false)
            blocklistCount = BlocklistRepository.domainCount(context)
            blocklistUpdatedAt = BlocklistRepository.lastUpdated(context)
            delay(2000)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            context.startForegroundService(Intent(context, AdultDnsVpnService::class.java))
            protection = true
            context.getSharedPreferences("daily_growth_private", 0).edit().putBoolean("protection_enabled", true).apply()
            message = "Background protection is on."
        } else message = "Protection permission was not granted."
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Green, onPrimary = Navy, secondary = Color(0xFFB8CBD1),
        background = Color(0xFF101D24), surface = Navy, onSurface = Color(0xFFF2F6F5),
        onBackground = Color(0xFFF2F6F5)
    )) {
        Scaffold(containerColor = Color(0xFF101D24), topBar = {
            TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Spa, contentDescription = null, tint = Green)
                    Column { Text("Daily Growth", fontWeight = FontWeight.SemiBold); Text("Personal wellness", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8CBD1)) }
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White))
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("YOUR CURRENT STREAK", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB8CBD1))
                        Text("$days", style = MaterialTheme.typography.displayLarge, color = Green, fontWeight = FontWeight.Bold)
                        Text(if (days == 1L) "day without looking" else "days without looking", color = Color.White)
                        Text("One day at a time.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8CBD1))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Personal best", "$best days", Modifier.weight(1f))
                    StatCard("Total tracked", "${Accountability.totalTracked(context)} days", Modifier.weight(1f))
                }
                Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Green)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Background protection", fontWeight = FontWeight.SemiBold)
                                Text(if (protection) "Filtering is enabled" else "Not currently enabled", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8CBD1))
                            }
                            Switch(checked = protection, onCheckedChange = { enable ->
                                if (enable) {
                                    val prepare = VpnService.prepare(context)
                                    if (prepare == null) {
                                        context.startForegroundService(Intent(context, AdultDnsVpnService::class.java)); protection = true
                                        context.getSharedPreferences("daily_growth_private", 0).edit().putBoolean("protection_enabled", true).apply()
                                    } else vpnLauncher.launch(prepare)
                                } else { pinPurpose = "disable"; pin = ""; showPinDialog = true }
                            })
                        }
                        Text("Filters common adult-site domains through your phone's DNS requests. It does not collect browsing history.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8CBD1))
                        Text("When smartBox-8441 connects, website filtering temporarily pauses. It resumes when that device disconnects. GERALD SENTRA is ignored.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF91A6AE))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Website list", fontWeight = FontWeight.SemiBold)
                        Text(if (blocklistCount > 30) "$blocklistCount domains in the local filter" else "Starter domains are active; the expanded list is downloading.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8CBD1))
                        Text(if (blocklistUpdatedAt > 0L) "Last updated: ${java.text.DateFormat.getDateTimeInstance().format(Date(blocklistUpdatedAt))}" else "Last update: waiting for first download", style = MaterialTheme.typography.labelSmall, color = Color(0xFF91A6AE))
                        OutlinedButton(onClick = {
                            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                            val request = OneTimeWorkRequestBuilder<BlocklistUpdateWorker>().setConstraints(constraints).build()
                            WorkManager.getInstance(context).enqueueUniqueWork("daily-growth-blocklist-manual", ExistingWorkPolicy.REPLACE, request)
                            message = "A blocklist update has been requested. Keep an internet connection available."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Update website list now") }
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Lock, contentDescription = null, tint = Green); Spacer(Modifier.width(10.dp)); Text("Accountability", fontWeight = FontWeight.SemiBold) }
                        Text("Your access code is held by your accountability partner. It is needed to reset your streak or turn protection off.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB8CBD1))
                        OutlinedButton(onClick = { pinPurpose = "reset"; pin = ""; showPinDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset streak") }
                    }
                }
                if (message.isNotBlank()) Text(message, color = Green, style = MaterialTheme.typography.bodySmall)
                Text("Notifications use neutral wording. App and lock-screen labels avoid explicit content references.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF91A6AE))
            }
        }
    }

    if (showSetup && firstRunPin != null) {
        AlertDialog(onDismissRequest = {}, title = { Text("Set up your access code") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Share this one-time code with your trusted accountability partner. It will not be shown again.")
                Text(firstRunPin, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("The app stores only a salted hash of the code. If nobody else has it, you may be unable to change protected settings.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { Accountability.markPinConfirmed(context); showSetup = false; onPinAcknowledged() }) { Text("I've saved the code") } })
    }

    if (showPinDialog) {
        AlertDialog(onDismissRequest = { showPinDialog = false }, title = { Text("Enter access code") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (pinPurpose == "reset") "Confirm the reset with your accountability partner." else "Confirm that protection should be turned off.")
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(6) }, label = { Text("6-digit code") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), visualTransformation = PasswordVisualTransformation())
            }
        }, confirmButton = { TextButton(onClick = {
            if (Accountability.verify(context, pin)) {
                if (pinPurpose == "reset") { Accountability.reset(context); days = Accountability.days(context); best = Accountability.best(context); StreakWidgetProvider.updateAll(context); message = "A new streak has started. Keep going." }
                else {
                    context.stopService(Intent(context, AdultDnsVpnService::class.java)); protection = false
                    context.getSharedPreferences("daily_growth_private", 0).edit().putBoolean("protection_enabled", false).apply()
                    message = "Protection is off."
                }
                showPinDialog = false
            } else message = "That code wasn't accepted."
        }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } })
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB8CBD1))
            Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}
