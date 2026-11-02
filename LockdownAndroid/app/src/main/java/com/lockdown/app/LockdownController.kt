package com.lockdown.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar

object LockdownController {
    private const val PREFS = "lockdown_settings"
    private const val KEY_PACKAGES = "packages"
    private const val KEY_START = "start_at"
    private const val KEY_END = "end_at"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun selected(context: Context): Set<String> = prefs(context).getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
    fun startAt(context: Context) = prefs(context).getLong(KEY_START, 0L)
    fun endAt(context: Context) = prefs(context).getLong(KEY_END, 0L)
    fun isActive(context: Context): Boolean = System.currentTimeMillis() in (startAt(context) until endAt(context))

    /** Creates a random PIN on first launch. Only a salted hash is retained; the PIN is never displayed. */
    fun initialize(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_PIN_HASH)) regeneratePin(context)
    }

    fun regeneratePin(context: Context) {
        setPin(context, String.format("%06d", SecureRandom().nextInt(1_000_000)))
    }

    fun setPin(context: Context, pin: String) {
        require(pin.length == 6 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_PIN_SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(KEY_PIN_HASH, hash(pin, salt))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        if (pin.length != 6 || !pin.all(Char::isDigit)) return false
        val p = prefs(context)
        val saltHex = p.getString(KEY_PIN_SALT, null) ?: return false
        val expected = p.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun saveSelection(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_PACKAGES, packages).apply()
    }

    fun schedule(context: Context, hour: Int, minute: Int, durationMinutes: Int): Long {
        val now = Calendar.getInstance()
        val start = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
        val end = start + durationMinutes * 60_000L
        prefs(context).edit().putLong(KEY_START, start).putLong(KEY_END, end).apply()
        scheduleAlarm(context, start, true)
        scheduleAlarm(context, end, false)
        return start
    }

    private fun scheduleAlarm(context: Context, at: Long, start: Boolean) {
        val action = if (start) "com.lockdown.app.START" else "com.lockdown.app.END"
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(context, if (start) 101 else 102, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else alarm.set(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun clearSchedule(context: Context) {
        prefs(context).edit().remove(KEY_START).remove(KEY_END).apply()
    }

    fun restoreAfterBoot(context: Context) {
        val start = startAt(context); val end = endAt(context); val now = System.currentTimeMillis()
        if (start == 0L || end == 0L) return
        when {
            now in start until end -> Unit // Accessibility service enforces whenever a blocked app comes to foreground.
            now < start -> { scheduleAlarm(context, start, true); scheduleAlarm(context, end, false) }
            else -> clearSchedule(context)
        }
    }
}
