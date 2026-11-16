package com.dailygrowth.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyCheckInWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("daily_growth_checkin", "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false) })
        val notification = NotificationCompat.Builder(applicationContext, "daily_growth_checkin")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Your daily check-in is ready")
            .setContentText("Take a moment to reflect and keep moving forward.")
            .setAutoCancel(true).build()
        nm.notify(73, notification)
        return Result.success()
    }
}
