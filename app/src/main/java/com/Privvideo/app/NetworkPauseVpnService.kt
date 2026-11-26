package com.Privvideo.app

import android.app.Notification
import android.graphics.BitmapFactory
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat

/**
 * Local network pause controller.
 * Keeps a local VPN session established. In normal mode it installs no default routes,
 * so Android uses its regular network. In pause mode it routes IPv4/IPv6 through the
 * local TUN and intentionally does not forward packets. This is a local traffic
 * blocker, not a VPN provider/tunnel to a remote server.
 */
class NetworkPauseVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.Privvideo.app.action.NETWORK_START"
        const val ACTION_PAUSE = "com.Privvideo.app.action.NETWORK_PAUSE"
        const val ACTION_RESUME = "com.Privvideo.app.action.NETWORK_RESUME"
        const val ACTION_STOP = "com.Privvideo.app.action.NETWORK_STOP"
        const val EXTRA_PAUSED = "paused"
        private const val CHANNEL_ID = "ghost_network_pause"
        private const val NOTIFICATION_ID = 7301
    }

    private var tunnel: ParcelFileDescriptor? = null
    private var paused = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        when (intent?.action) {
            ACTION_PAUSE -> pauseNetwork()
            ACTION_RESUME -> resumeNetwork()
            ACTION_STOP -> {
                stopVpnSession()
                return START_NOT_STICKY
            }
            ACTION_START, null -> keepVpnSessionActive()
        }
        return START_STICKY
    }

    private fun stopVpnSession() {
        paused = false
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        sendBroadcast(
            Intent(FloatingPanelService.ACTION_STATE)
                .setPackage(packageName)
                .putExtra(EXTRA_PAUSED, false)
        )
        stopSelf()
    }

    // Mantém a sessão VPN estabelecida durante todo o tempo. Sem rotas padrão,
    // o tráfego segue pela rede normal; ao pausar, recria o TUN com rotas padrão,
    // capturando os pacotes e descartando-os localmente.
    private fun buildTunnel(blockAllTraffic: Boolean): ParcelFileDescriptor? {
        val builder = Builder()
            .setSession("Ghost Priv - Controle de tráfego")
            .setMtu(1500)
            .addAddress("10.111.0.1", 32)
        if (blockAllTraffic) {
            builder.addRoute("0.0.0.0", 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.addAddress("fd00:1111:2222::1", 128).addRoute("::", 0)
            }
        }
        return builder.establish()
    }

    private fun keepVpnSessionActive() {
        if (tunnel != null) return
        try {
            tunnel = buildTunnel(false)
            paused = false
        } catch (_: Exception) { paused = false }
        updateNotification()
    }

    private fun pauseNetwork() {
        if (paused && tunnel != null) return
        try {
            val replacement = buildTunnel(true)
            if (replacement != null) {
                try { tunnel?.close() } catch (_: Exception) { }
                tunnel = replacement
                paused = true
            }
        } catch (_: Exception) { paused = false }
        updateNotification()
    }

    private fun resumeNetwork() {
        try {
            val replacement = buildTunnel(false)
            if (replacement != null) {
                try { tunnel?.close() } catch (_: Exception) { }
                tunnel = replacement
            }
            paused = false
        } catch (_: Exception) {
            try { tunnel?.close() } catch (_: Exception) { }
            tunnel = null
            paused = false
        }
        updateNotification()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Controle de conexão", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val status = if (paused) "Internet pausada" else "Controle pronto · internet liberada"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capcut_notification)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle("Ghost Priv")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
        sendBroadcast(Intent(FloatingPanelService.ACTION_STATE).setPackage(packageName).putExtra(EXTRA_PAUSED, paused))
    }

    override fun onRevoke() {
        stopVpnSession()
        super.onRevoke()
    }

    override fun onDestroy() {
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
        paused = false
        super.onDestroy()
    }
}
