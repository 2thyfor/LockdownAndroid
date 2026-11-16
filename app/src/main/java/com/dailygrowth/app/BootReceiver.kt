package com.dailygrowth.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.getSharedPreferences("daily_growth_private", Context.MODE_PRIVATE)
        // Never carry a stale car-mode state across a reboot or app replacement.
        prefs.edit().putBoolean("car_bluetooth_connected", false).apply()
        if (prefs.getBoolean("protection_enabled", false)) {
            val service = Intent(context, AdultDnsVpnService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
        }
    }
}
