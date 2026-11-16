package com.dailygrowth.app

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Accountability {
    private const val PREF = "daily_growth_private"
    private const val HASH = "pin_hash"
    private const val SALT = "pin_salt"
    private const val START = "streak_start"
    private const val FIRST_TRACKED = "first_tracked"
    private const val BEST = "best_streak"
    private const val PIN_SHOWN = "pin_shown"
    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun initialize(c: Context): String? {
        val p = prefs(c)
        if (!p.contains(START)) p.edit().putString(START, LocalDate.now().toString()).apply()
        if (!p.contains(FIRST_TRACKED)) p.edit().putString(FIRST_TRACKED, LocalDate.now().toString()).apply()
        if (!p.contains(HASH)) {
            val pin = String.format("%06d", SecureRandom().nextInt(1_000_000))
            setPin(c, pin)
            return pin
        }
        return null
    }

    fun hasPin(c: Context) = prefs(c).contains(HASH)
    fun hasShownPin(c: Context) = prefs(c).getBoolean(PIN_SHOWN, false)
    fun markPinShown(c: Context) { prefs(c).edit().putBoolean(PIN_SHOWN, true).apply() }

    private fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString(SALT, salt.joinToString("") { "%02x".format(it) })
            .putString(HASH, hash(pin, salt))
            .apply()
    }

    fun verify(c: Context, pin: String): Boolean {
        if (pin.length != 6 || pin.any { !it.isDigit() }) return false
        val p = prefs(c)
        val saltHex = p.getString(SALT, null) ?: return false
        val expected = p.getString(HASH, null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return MessageDigest.isEqual(expected.toByteArray(), hash(pin, salt).toByteArray())
    }

    private fun hash(pin: String, salt: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(salt + pin.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun days(c: Context): Long {
        val start = runCatching { LocalDate.parse(prefs(c).getString(START, LocalDate.now().toString())) }.getOrDefault(LocalDate.now())
        return ChronoUnit.DAYS.between(start, LocalDate.now()).coerceAtLeast(0)
    }
    fun best(c: Context): Long = maxOf(prefs(c).getLong(BEST, 0L), days(c))
    fun totalTracked(c: Context): Long {
        val first = runCatching { LocalDate.parse(prefs(c).getString(FIRST_TRACKED, LocalDate.now().toString())) }.getOrDefault(LocalDate.now())
        return (ChronoUnit.DAYS.between(first, LocalDate.now()) + 1).coerceAtLeast(1)
    }
    fun reset(c: Context) {
        val p = prefs(c)
        val current = days(c)
        p.edit().putLong(BEST, maxOf(p.getLong(BEST, 0L), current))
            .putString(START, LocalDate.now().toString()).apply()
    }
    fun markPinConfirmed(c: Context) = markPinShown(c)
}
