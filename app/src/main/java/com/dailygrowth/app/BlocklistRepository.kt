package com.dailygrowth.app

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/** Local, updateable domain set. A failed update never replaces the last known-good list. */
object BlocklistRepository {
    private const val PREFS = "daily_growth_blocklist"
    private const val FILE_NAME = "adult-domains.txt"
    @Volatile private var cachedDomains: Set<String> = emptySet()
    @Volatile private var cachedModified: Long = Long.MIN_VALUE

    private val builtInDomains = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "tube8.com", "brazzers.com", "bangbros.com", "realitykings.com",
        "onlyfans.com", "chaturbate.com", "stripchat.com", "livejasmin.com", "bongacams.com",
        "myfreecams.com", "hentaihaven.xxx", "nhentai.net", "rule34.xxx", "e621.net",
        "motherless.com", "fapello.com", "literotica.com", "adultfriendfinder.com", "camsoda.com",
        "redgifs.com", "theporndude.com", "javhd.com", "javlibrary.com", "thisvid.com"
    )

    fun isBlocked(context: Context, host: String): Boolean {
        val normalized = normalize(host) ?: return false
        val domains = domains(context)
        if (domains.contains(normalized)) return true
        val labels = normalized.split('.')
        for (index in 1 until labels.size) {
            if (domains.contains(labels.drop(index).joinToString("."))) return true
        }
        return false
    }

    fun domainCount(context: Context): Int = domains(context).size

    fun lastUpdated(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("updated_at", 0L)

    fun isUpdateAvailable(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("has_remote_list", false)

    fun saveDownloadedDomains(context: Context, domains: Set<String>) {
        val file = File(context.filesDir, FILE_NAME)
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        temp.writeText(domains.sorted().joinToString("\n"), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        cachedModified = Long.MIN_VALUE // Force the next lookup to merge built-in and custom entries.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("updated_at", System.currentTimeMillis())
            .putInt("domain_count", domains.size)
            .putBoolean("has_remote_list", true)
            .apply()
    }

    private fun domains(context: Context): Set<String> {
        val file = File(context.filesDir, FILE_NAME)
        val modified = if (file.exists()) file.lastModified() else 0L
        if (cachedModified != modified || cachedDomains.isEmpty()) {
            synchronized(this) {
                if (cachedModified != modified || cachedDomains.isEmpty()) {
                    val downloaded = if (file.exists()) runCatching {
                        file.readLines(Charsets.UTF_8).mapNotNull(::normalize).toSet()
                    }.getOrDefault(emptySet()) else emptySet()
                    val custom = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .getStringSet("custom_domains", emptySet()).orEmpty().mapNotNull(::normalize).toSet()
                    cachedDomains = builtInDomains + downloaded + custom
                    cachedModified = modified
                }
            }
        }
        return cachedDomains
    }

    private fun normalize(raw: String): String? {
        val value = raw.trim().lowercase(Locale.ROOT).trimEnd('.')
            .removePrefix("http://").removePrefix("https://").substringBefore('/')
            .substringBefore(':')
        if (value.isBlank() || value.length > 253 || value.startsWith("#")) return null
        if (value == "localhost" || value == "broadcasthost" || value.endsWith(".local")) return null
        val labels = value.split('.')
        if (labels.size < 2 || labels.any { it.isBlank() || it.length > 63 || !it.matches(Regex("[a-z0-9-]+")) || it.startsWith('-') || it.endsWith('-') }) return null
        return value
    }
}
