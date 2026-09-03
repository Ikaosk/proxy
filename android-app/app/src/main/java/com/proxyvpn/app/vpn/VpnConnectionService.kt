package com.proxyvpn.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.proxyvpn.app.MainActivity

/**
 * Заготовка VPN-сервиса. Настоящий разбор и пересылка IP-пакетов
 * (tun2socks поверх SOCKS5 из ../web-proxy/) добавляется отдельным шагом —
 * см. README ("Что дальше: VPN-ядро").
 *
 * Сейчас сервис только получает системное разрешение на VPN (через
 * VpnService.prepare() в CabinetScreen) и держит foreground-уведомление —
 * TUN-интерфейс не создаётся (Builder().establish() не вызывается), поэтому
 * реальный трафик устройства этот сервис никак не затрагивает.
 */
class VpnConnectionService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        // TODO(vpn-core): establish() TUN-интерфейса (Builder().addAddress/addRoute)
        // и запуск tun2socks, направленного на SOCKS5 web-proxy.
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW)
            )
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VPN (заготовка)")
            .setContentText("Разрешение получено, туннелирование пока не реализовано")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "vpn_service"
        private const val NOTIFICATION_ID = 1
    }
}
