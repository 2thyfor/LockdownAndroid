package com.dailygrowth.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.VpnService
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class AdultDnsVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var bluetoothReceiverRegistered = false
    private val carBluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val deviceName = try {
                if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                device.name
            } catch (_: SecurityException) { null }
            if (!deviceName.equals(TARGET_BLUETOOTH_NAME, ignoreCase = true)) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> setCarMode(true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> setCarMode(false)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopFiltering(); stopSelf(); return START_NOT_STICKY }
        createChannel()
        registerCarBluetoothReceiver()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Daily Growth")
            .setContentText("Background protection is active")
            .setOngoing(true).setSilent(true).build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(41, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else startForeground(41, notification)
        if (!running.get()) startFiltering()
        return START_STICKY
    }

    private fun registerCarBluetoothReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        try {
            ContextCompat.registerReceiver(this, carBluetoothReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
            bluetoothReceiverRegistered = true
        } catch (_: Exception) { /* Bluetooth permission may not have been granted yet. */ }
    }

    private fun setCarMode(connected: Boolean) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CAR_CONNECTED, connected).apply()
    }

    private fun startFiltering() {
        try {
            tun = Builder().setSession("Daily Growth")
                .addAddress("10.77.0.2", 32)
                .addDnsServer(InetAddress.getByName("10.77.0.1"))
                .addRoute(InetAddress.getByName("10.77.0.1"), 32)
                .setMtu(1500).establish()
            val fd = tun ?: return
            running.set(true)
            getSharedPreferences("daily_growth_private", MODE_PRIVATE).edit().putBoolean("protection_active", true).apply()
            worker = Thread {
                val input = FileInputStream(fd.fileDescriptor)
                val output = FileOutputStream(fd.fileDescriptor)
                val buffer = ByteArray(32767)
                while (running.get()) {
                    try {
                        val n = input.read(buffer)
                        if (n <= 0) continue
                        val packet = buffer.copyOf(n)
                        processPacket(packet, output)
                    } catch (_: Exception) { if (running.get()) continue }
                }
            }.also { it.name = "DailyGrowth-DNS"; it.start() }
        } catch (_: Exception) { stopFiltering() }
    }

    private fun processPacket(packet: ByteArray, output: FileOutputStream) {
        if (packet.size < 28 || (packet[0].toInt() ushr 4 and 0xF) != 4) return
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < 20 || packet.size < ihl + 8 || packet[9].toInt() != 17) return
        val total = u16(packet, 2).coerceAtMost(packet.size)
        val srcPort = u16(packet, ihl); val dstPort = u16(packet, ihl + 2)
        if (dstPort != 53 || total < ihl + 8 + 12) return
        val dnsStart = ihl + 8
        val dns = packet.copyOfRange(dnsStart, total)
        val domain = queryName(dns) ?: return
        val answer = if (isBlocked(domain)) blockedResponse(dns) else forwardDns(dns) ?: return
        val response = makeIpv4UdpResponse(packet, ihl, srcPort, dstPort, answer)
        output.write(response)
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) null else {
                    socket.soTimeout = 3500
                    val carConnected = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CAR_CONNECTED, false)
                    // In car mode use standard Cloudflare DNS and skip the local adult-domain list.
                    val address = InetAddress.getByName(if (carConnected) "1.1.1.1" else "1.1.1.3")
                    socket.send(DatagramPacket(query, query.size, address, 53))
                    val buf = ByteArray(4096)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    buf.copyOf(reply.length)
                }
            }
        } catch (_: Exception) { null }
    }

    private fun queryName(dns: ByteArray): String? {
        return try {
            if (dns.size < 17 || (dns[2].toInt() and 0x80) != 0) null else {
                var i = 12
                val labels = mutableListOf<String>()
                var valid = true
                while (i < dns.size && valid) {
                    val len = dns[i++].toInt() and 0xFF
                    if (len == 0) break
                    if (len > 63 || i + len > dns.size) valid = false
                    else { labels += String(dns, i, len, Charsets.US_ASCII); i += len }
                }
                if (!valid || labels.isEmpty()) null else labels.joinToString(".").lowercase(Locale.ROOT).trimEnd('.')
            }
        } catch (_: Exception) { null }
    }

    private fun isBlocked(host: String): Boolean {
        if (getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CAR_CONNECTED, false)) return false
        return BlocklistRepository.isBlocked(this, host)
    }

    private fun blockedResponse(query: ByteArray): ByteArray {
        val out = query.copyOf()
        out[2] = 0x81.toByte(); out[3] = 0x83.toByte() // response + recursion available + NXDOMAIN
        out[6] = 0; out[7] = 0; out[8] = 0; out[9] = 0; out[10] = 0; out[11] = 0
        return out
    }

    private fun makeIpv4UdpResponse(original: ByteArray, ihl: Int, sourcePort: Int, destPort: Int, dns: ByteArray): ByteArray {
        val out = ByteArray(ihl + 8 + dns.size)
        System.arraycopy(original, 0, out, 0, ihl)
        // Swap source and destination IPv4 addresses.
        for (i in 0..3) { out[12 + i] = original[16 + i]; out[16 + i] = original[12 + i] }
        put16(out, 2, out.size); out[8] = 64; out[10] = 0; out[11] = 0
        put16(out, 10, checksum(out, 0, ihl))
        put16(out, ihl, destPort); put16(out, ihl + 2, sourcePort)
        put16(out, ihl + 4, 8 + dns.size); put16(out, ihl + 6, 0)
        System.arraycopy(dns, 0, out, ihl + 8, dns.size)
        return out
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L; var i = offset
        while (i < offset + length) {
            val word = ((data[i].toInt() and 0xFF) shl 8) + if (i + 1 < offset + length) data[i + 1].toInt() and 0xFF else 0
            sum += word; i += 2
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv().toInt() and 0xFFFF
    }
    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)
    private fun put16(b: ByteArray, i: Int, v: Int) { b[i] = (v ushr 8).toByte(); b[i + 1] = v.toByte() }

    private fun stopFiltering() {
        running.set(false)
        runCatching { tun?.close() }; tun = null
        worker?.interrupt(); worker = null
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("protection_active", false).apply()
    }
    override fun onDestroy() {
        stopFiltering()
        if (bluetoothReceiverRegistered) runCatching { unregisterReceiver(carBluetoothReceiver) }
        bluetoothReceiverRegistered = false
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Background status", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
    }

    companion object {
        const val ACTION_STOP = "com.dailygrowth.app.STOP_FILTER"
        private const val CHANNEL = "daily_growth_status"
        private const val PREFS = "daily_growth_private"
        private const val KEY_CAR_CONNECTED = "car_bluetooth_connected"
        private const val TARGET_BLUETOOTH_NAME = "smartBox-8441"

    }
}
