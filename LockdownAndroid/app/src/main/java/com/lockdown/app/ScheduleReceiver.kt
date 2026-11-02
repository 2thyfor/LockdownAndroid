package com.lockdown.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("schedule_id", -1L)
        when (intent.action) {
            "com.lockdown.app.START" -> Unit // Accessibility service enforces blocks when a selected app opens.
            "com.lockdown.app.END" -> if (id >= 0L) LockdownController.handleAlarm(context, id, true)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = LockdownController.restoreAfterBoot(context)
}
