package com.dailygrowth.app

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

class BlocklistUpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val connection = (URL(BLOCKLIST_URL).openConnection() as HttpURLConnection)
        return try {
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "DailyGrowth-Android/1.1")
            connection.setRequestProperty("Accept", "text/plain")
            connection.connect()
            if (connection.responseCode !in 200..299) return Result.retry()

            val domains = LinkedHashSet<String>()
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.substringBefore('#').trim()
                    if (line.isEmpty()) return@forEach
                    val parts = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    // Hosts format: 0.0.0.0 domain.tld (also accept a plain domain line).
                    val candidates = if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::" || parts[0] == "::1")) parts.drop(1) else if (parts.size == 1) parts else emptyList()
                    candidates.forEach { candidate ->
                        val normalized = candidate.trim().lowercase().trimEnd('.')
                        if (isValidDomain(normalized)) domains.add(normalized)
                    }
                }
            }
            // Reject empty/truncated/unexpected responses; preserve the previous list.
            if (domains.size < MINIMUM_EXPECTED_DOMAINS) {
                Log.w(TAG, "Downloaded list was unexpectedly small: ${domains.size}")
                return Result.retry()
            }
            BlocklistRepository.saveDownloadedDomains(applicationContext, domains)
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Blocklist refresh failed", e)
            Result.retry()
        } finally {
            connection.disconnect()
        }
    }

    private fun isValidDomain(value: String): Boolean {
        if (value.length !in 3..253 || value.startsWith('#')) return false
        if (value == "localhost" || value.endsWith(".local")) return false
        val labels = value.split('.')
        return labels.size >= 2 && labels.all {
            it.isNotBlank() && it.length <= 63 && it.matches(Regex("[a-z0-9-]+")) &&
                !it.startsWith('-') && !it.endsWith('-')
        }
    }

    companion object {
        const val BLOCKLIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts"
        private const val MINIMUM_EXPECTED_DOMAINS = 10_000
        private const val TAG = "DailyGrowthBlocklist"
    }
}
