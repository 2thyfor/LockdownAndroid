package com.lockdown.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Calendar
import kotlin.math.abs

data class BlockSchedule(
    val id: Long,
    val packages: Set<String>,
    val hour: Int,
    val minute: Int,
    val durationMinutes: Int,
    val repeatDaily: Boolean,
    val nextStart: Long
) {
    val end: Long get() = nextStart + durationMinutes * 60_000L
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now >= nextStart && now < end
}

object LockdownController {
    private const val PREFS = "lockdown_settings"
    private const val KEY_SCHEDULES = "schedules_json"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_FIRST_SESSION_COMPLETED = "first_session_completed"
    private const val KEY_LEGACY_PACKAGES = "packages"
    private const val KEY_LEGACY_START = "start_at"
    private const val KEY_LEGACY_END = "end_at"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun initialize(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_PIN_HASH)) regeneratePin(context)
        migrateLegacySchedule(context)
    }

    private fun migrateLegacySchedule(context: Context) {
        val p = prefs(context)
        if (p.contains(KEY_SCHEDULES)) return
        val packages = p.getStringSet(KEY_LEGACY_PACKAGES, emptySet())?.toSet() ?: emptySet()
        val start = p.getLong(KEY_LEGACY_START, 0L)
        val end = p.getLong(KEY_LEGACY_END, 0L)
        if (packages.isNotEmpty() && start > 0L && end > start) {
            val c = Calendar.getInstance().apply { timeInMillis = start }
            val item = BlockSchedule(System.currentTimeMillis(), packages, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), ((end-start)/60000L).toInt().coerceAtLeast(1), false, start)
            saveSchedules(context, listOf(item))
            scheduleAlarms(context, item)
        } else saveSchedules(context, emptyList())
    }

    fun schedules(context: Context): List<BlockSchedule> {
        val raw = prefs(context).getString(KEY_SCHEDULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val apps = o.getJSONArray("packages")
                BlockSchedule(
                    id = o.getLong("id"),
                    packages = (0 until apps.length()).map { apps.getString(it) }.toSet(),
                    hour = o.getInt("hour"), minute = o.getInt("minute"),
                    durationMinutes = o.getInt("duration"), repeatDaily = o.getBoolean("daily"),
                    nextStart = o.getLong("nextStart")
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun saveSchedules(context: Context, list: List<BlockSchedule>) {
        val arr = JSONArray()
        list.forEach { s ->
            val apps = JSONArray(); s.packages.sorted().forEach { apps.put(it) }
            arr.put(JSONObject().put("id", s.id).put("packages", apps).put("hour", s.hour)
                .put("minute", s.minute).put("duration", s.durationMinutes)
                .put("daily", s.repeatDaily).put("nextStart", s.nextStart))
        }
        prefs(context).edit().putString(KEY_SCHEDULES, arr.toString()).apply()
    }

    fun activeSchedules(context: Context): List<BlockSchedule> = schedules(context).filter { it.isActive() }
    fun isAnyActive(context: Context): Boolean = activeSchedules(context).isNotEmpty()
    fun scheduleForPackage(context: Context, packageName: String): BlockSchedule? =
        activeSchedules(context).firstOrNull { packageName in it.packages }

    fun addSchedule(context: Context, packages: Set<String>, hour: Int, minute: Int, durationMinutes: Int, daily: Boolean): Long {
        require(packages.isNotEmpty()) { "Choose at least one app." }
        require(hour in 0..23 && minute in 0..59) { "Enter a valid start time." }
        require(durationMinutes in 1..10080) { "Duration must be between 1 minute and 7 days." }
        require(!daily || durationMinutes <= 1440) { "Daily schedules must be 24 hours or less." }
        val start = nextStart(hour, minute)
        val item = BlockSchedule(abs(SecureRandom().nextLong()).coerceAtLeast(1L), packages, hour, minute, durationMinutes, daily, start)
        val updated = schedules(context) + item
        saveSchedules(context, updated)
        scheduleAlarms(context, item)
        return item.id
    }

    fun deleteSchedule(context: Context, id: Long) {
        cancelAlarms(context, id)
        saveSchedules(context, schedules(context).filterNot { it.id == id })
    }

    fun replaceSchedule(context: Context, id: Long, packages: Set<String>, hour: Int, minute: Int, durationMinutes: Int, daily: Boolean) {
        require(packages.isNotEmpty()) { "Choose at least one app." }
        require(hour in 0..23 && minute in 0..59) { "Enter a valid start time." }
        require(durationMinutes in 1..10080) { "Duration must be between 1 minute and 7 days." }
        require(!daily || durationMinutes <= 1440) { "Daily schedules must be 24 hours or less." }
        cancelAlarms(context, id)
        val item = BlockSchedule(id, packages, hour, minute, durationMinutes, daily, nextStart(hour, minute))
        saveSchedules(context, schedules(context).map { if (it.id == id) item else it })
        scheduleAlarms(context, item)
    }

    private fun nextStart(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        return (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    private fun alarmIntent(context: Context, id: Long, start: Boolean): PendingIntent {
        val action = if (start) "com.lockdown.app.START" else "com.lockdown.app.END"
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(action).putExtra("schedule_id", id)
        val request = ((id xor (id ushr 32)).toInt() * 2) + if (start) 1 else 2
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun scheduleOne(context: Context, id: Long, at: Long, start: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context, id, start)
        if (Build.VERSION.SDK_INT >= 23) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else alarm.set(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun scheduleAlarms(context: Context, s: BlockSchedule) {
        scheduleOne(context, s.id, s.nextStart, true)
        scheduleOne(context, s.id, s.end, false)
    }

    private fun cancelAlarms(context: Context, id: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(alarmIntent(context, id, true)); alarm.cancel(alarmIntent(context, id, false))
    }

    fun handleAlarm(context: Context, id: Long, isEnd: Boolean) {
        val item = schedules(context).firstOrNull { it.id == id } ?: return
        if (!isEnd) return
        prefs(context).edit().putBoolean(KEY_FIRST_SESSION_COMPLETED, true).apply()
        if (item.repeatDaily) {
            val next = Calendar.getInstance().apply {
                timeInMillis = item.nextStart
                add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
            val updated = item.copy(nextStart = next)
            saveSchedules(context, schedules(context).map { if (it.id == id) updated else it })
            scheduleAlarms(context, updated)
        } else {
            cancelAlarms(context, id)
            saveSchedules(context, schedules(context).filterNot { it.id == id })
        }
    }

    fun restoreAfterBoot(context: Context) {
        val now = System.currentTimeMillis()
        val updated = schedules(context).mapNotNull { s ->
            if (!s.repeatDaily && now >= s.end) null
            else if (s.repeatDaily && now >= s.end) {
                val cal = Calendar.getInstance().apply { timeInMillis = s.nextStart }
                while (cal.timeInMillis + s.durationMinutes * 60_000L <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
                s.copy(nextStart = cal.timeInMillis)
            } else s
        }
        saveSchedules(context, updated)
        updated.forEach { scheduleAlarms(context, it) }
        if (updated.size < schedules(context).size) prefs(context).edit().putBoolean(KEY_FIRST_SESSION_COMPLETED, true).apply()
    }

    fun canChangePin(context: Context): Boolean = !isAnyActive(context)
    fun canUnlockEarly(context: Context): Boolean = prefs(context).getBoolean(KEY_FIRST_SESSION_COMPLETED, false)

    fun regeneratePin(context: Context): String {
        val pin = String.format("%06d", SecureRandom().nextInt(1_000_000))
        setPin(context, pin)
        return pin
    }
    fun setPin(context: Context, pin: String) {
        require(pin.length == 6 && pin.all(Char::isDigit))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(context).edit().putString(KEY_PIN_SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(KEY_PIN_HASH, hash(pin, salt)).apply()
    }
    fun verifyPin(context: Context, pin: String): Boolean {
        if (pin.length != 6 || !pin.all(Char::isDigit)) return false
        val p = prefs(context); val saltHex = p.getString(KEY_PIN_SALT, null) ?: return false
        val expected = p.getString(KEY_PIN_HASH, null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }
    private fun hash(pin: String, salt: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()).joinToString("") { "%02x".format(it) }
}
