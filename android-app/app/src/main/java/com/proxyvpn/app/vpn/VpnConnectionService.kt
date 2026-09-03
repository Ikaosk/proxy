package com.proxyvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.proxyvpn.app.MainActivity

/**
 * Настоящий VPN-туннель: поднимает TUN-интерфейс через VpnService и
 * прокачивает через него весь трафик устройства в SOCKS5 web-proxy
 * (../web-proxy/) с помощью hev-socks5-tunnel (HevSocks5Tunnel.kt).
 *
 * Трафик самого приложения (соединение нативной библиотеки с SOCKS5-сервером)
 * исключён из туннеля через addDisallowedApplication(packageName) — иначе
 * получился бы бесконечный цикл маршрутизации через свой же TUN.
 *
 * Известное ограничение: наш web-proxy поддерживает только SOCKS5 CONNECT
 * (TCP), не UDP ASSOCIATE, поэтому UDP-режим релея — 'tcp' (см. buildConfig).
 * DNS и другой UDP-трафик может работать хуже, чем TCP/HTTPS — это стоит
 * проверить на реальном устройстве.
 */
class VpnConnectionService : VpnService() {

    private var tunFd: ParcelFileDescriptor? = null
    private var tunnelThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTunnel()
            stopSelf()
            return START_NOT_STICKY
        }

        val host = intent?.getStringExtra(EXTRA_HOST)
        val port = intent?.getIntExtra(EXTRA_SOCKS_PORT, -1) ?: -1
        if (host.isNullOrEmpty() || port <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val login = intent.getStringExtra(EXTRA_LOGIN).orEmpty()
        val password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()

        startForeground(NOTIFICATION_ID, buildNotification("Подключение к $host…"))
        startTunnel(host, port, login, password)
        return START_NOT_STICKY
    }

    private fun startTunnel(host: String, port: Int, login: String, password: String) {
        try {
            val pfd = Builder()
                .setSession("HarrisVPN")
                .addAddress(TUN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)
                .addDnsServer(DNS_SERVER)
                .setMtu(TUN_MTU)
                .addDisallowedApplication(packageName)
                .establish()

            if (pfd == null) {
                updateNotification("Не удалось создать VPN-интерфейс")
                stopSelf()
                return
            }
            tunFd = pfd

            val config = buildConfig(host, port, login, password)
            tunnelThread = Thread({ HevSocks5Tunnel.nativeStart(config, pfd.fd) }, "hev-socks5-tunnel")
                .also { it.isDaemon = true; it.start() }

            updateNotification("Подключено: $host")
        } catch (e: Exception) {
            updateNotification("Ошибка подключения: ${e.message}")
            stopTunnel()
            stopSelf()
        }
    }

    private fun buildConfig(host: String, port: Int, login: String, password: String): String =
        buildString {
            append("tunnel:\n")
            append("  name: tun0\n")
            append("  mtu: $TUN_MTU\n")
            append("  ipv4: $TUN_ADDRESS\n")
            append("socks5:\n")
            append("  address: '${yamlEscape(host)}'\n")
            append("  port: $port\n")
            // Наш web-proxy не поддерживает UDP ASSOCIATE — только TCP CONNECT.
            append("  udp: 'tcp'\n")
            if (login.isNotEmpty()) {
                append("  username: '${yamlEscape(login)}'\n")
                append("  password: '${yamlEscape(password)}'\n")
            }
        }

    private fun yamlEscape(value: String): String = value.replace("'", "''")

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun stopTunnel() {
        HevSocks5Tunnel.nativeStop()
        tunnelThread?.join(2000)
        tunnelThread = null
        tunFd?.close()
        tunFd = null
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "HarrisVPN", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("HarrisVPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_STOP = "com.proxyvpn.app.vpn.STOP"
        const val EXTRA_HOST = "host"
        const val EXTRA_SOCKS_PORT = "socks_port"
        const val EXTRA_LOGIN = "login"
        const val EXTRA_PASSWORD = "password"

        private const val CHANNEL_ID = "vpn_service"
        private const val NOTIFICATION_ID = 1
        private const val TUN_ADDRESS = "198.18.0.1"
        private const val DNS_SERVER = "1.1.1.1"
        private const val TUN_MTU = 8500
    }
}
