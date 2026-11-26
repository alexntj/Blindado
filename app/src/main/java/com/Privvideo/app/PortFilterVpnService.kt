package com.Privvideo.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.celzero.firestack.backend.DNSOpts
import com.celzero.firestack.backend.DNSSummary
import com.celzero.firestack.backend.DomainOpts
import com.celzero.firestack.backend.ServerSummary
import com.celzero.firestack.backend.Tab
import com.celzero.firestack.intra.Bridge
import com.celzero.firestack.intra.FlowSummary
import com.celzero.firestack.intra.Intra
import com.celzero.firestack.intra.Mark
import com.celzero.firestack.intra.PreMark
import com.celzero.firestack.intra.Tunnel

/**
 * Rootless, system-consented VPN firewall. Firestack forwards ordinary traffic;
 * the bridge drops only TCP/UDP flows whose remote port is 53017 (or every flow
 * while the explicit pause mode is enabled).
 */
class PortFilterVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.Privvideo.app.action.NETWORK_START"
        const val ACTION_PAUSE = "com.Privvideo.app.action.NETWORK_PAUSE"
        const val ACTION_RESUME = "com.Privvideo.app.action.NETWORK_RESUME"
        const val ACTION_SET_PORT_BLOCKED = "com.Privvideo.app.action.SET_PORT_BLOCKED"
        const val ACTION_STOP = "com.Privvideo.app.action.NETWORK_STOP"
        const val EXTRA_PAUSED = "paused"
        const val EXTRA_PORT_BLOCKED = "port_53017_blocked"
        const val EXTRA_PORT_BLOCKED_REQUEST = "port_53017_blocked_request"
        const val EXTRA_VPN_ACTIVE = "vpn_active"
        const val EXTRA_ERROR = "vpn_error"

        private const val CHANNEL_ID = "ghost_port_firewall"
        private const val NOTIFICATION_ID = 7301
        private const val MTU = 1500
        private const val BLOCK_PROXY = "Block"
        private const val EXIT_PROXY = "Exit"
        private const val PORT_TO_BLOCK = 53017
        private const val TAG = "GhostPortVPN"
        private val TCP_UDP_PROTOCOLS = setOf(6, 17)
    }

    @Volatile private var paused = false
    @Volatile private var port53017Blocked = false
    @Volatile private var vpnActive = false
    private var tun: ParcelFileDescriptor? = null
    private var tunnel: Tunnel? = null

    private val bridge = object : Bridge {
        override fun flow(
            protocol: Int,
            uid: Int,
            cid: String,
            src: String,
            dst: String,
            realIps: String,
            domains: String,
            probableDomains: String,
            blocklists: String,
            isAlg: Boolean
        ): Mark {
            val block = paused || (
                port53017Blocked && protocol in TCP_UDP_PROTOCOLS &&
                    (hasPort(src, PORT_TO_BLOCK) || hasPort(dst, PORT_TO_BLOCK))
                )
            return mark(if (block) BLOCK_PROXY else EXIT_PROXY, cid, uid)
        }

        override fun inflow(protocol: Int, uid: Int, cid: String, src: String, dst: String): Mark {
            val block = paused || (
                port53017Blocked && protocol in TCP_UDP_PROTOCOLS &&
                    (hasPort(src, PORT_TO_BLOCK) || hasPort(dst, PORT_TO_BLOCK))
                )
            return mark(if (block) BLOCK_PROXY else EXIT_PROXY, cid, uid)
        }

        override fun preflow(protocol: Int, uid: Int, cid: String, src: String, dst: String): PreMark =
            PreMark().apply {
                setUID(uid.toString())
                setIsUidSelf(uid == applicationInfo.uid)
            }

        override fun flowing(mark: Mark) = Unit
        override fun postflow(summary: FlowSummary) = Unit

        // Firestack's exit sockets use the device's normal underlying network. These
        // callbacks are intentionally no-ops; protect() below prevents VPN recursion.
        override fun bind4(who: String, addrport: String, fd: Long) = Unit
        override fun bind6(who: String, addrport: String, fd: Long) = Unit
        override fun protect(who: String, fd: Long) {
            if (!this@PortFilterVpnService.protect(fd.toInt())) {
                Log.e(TAG, "Não foi possível proteger o socket do túnel: $who")
            }
        }

        // No custom DNS policy is applied; Firestack's system-DNS fallback is used.
        override fun onPrequery(origin: String, fid: String, uid: String, fqdn: String, qtype: Long): DomainOpts? = null
        override fun onQuery(origin: String, fid: String, uid: String, fqdn: String, qtype: Long): DNSOpts = DNSOpts()
        override fun onUpstreamAnswer(
            id: String,
            fid: String,
            summary: DNSSummary,
            receivedOpts: DNSOpts,
            ips: String
        ): DNSOpts = receivedOpts
        override fun onResponse(summary: DNSSummary) = Unit
        override fun onDNSAdded(id: String) = Unit
        override fun onDNSRemoved(id: String) = Unit
        override fun onDNSStopped() = Unit

        override fun onProxiesStopped() = Unit
        override fun onProxyAdded(id: String, handle: String) = Unit
        override fun onProxyRemoved(id: String, handle: String) = Unit
        override fun onProxyStopped(id: String, handle: String) = Unit
        override fun onProxyUpdated(id: String, handle: String) = Unit
        override fun onSvcComplete(summary: ServerSummary) = Unit
        override fun svcRoute(sid: String, pid: String, network: String, srcPort: String, dstPort: String): Tab = Tab()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        when (intent?.action) {
            ACTION_STOP -> {
                stopVpnSession()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> paused = true
            ACTION_RESUME -> paused = false
            ACTION_SET_PORT_BLOCKED -> {
                val desired = intent?.getBooleanExtra(EXTRA_PORT_BLOCKED_REQUEST, false) ?: false
                if (!ensureTunnel()) return START_NOT_STICKY
                port53017Blocked = desired
            }
            ACTION_START, null -> Unit
            else -> Log.w(TAG, "Ação desconhecida: ${intent?.action}")
        }

        if (!ensureTunnel()) return START_NOT_STICKY
        updateNotification()
        return START_STICKY
    }

    private fun ensureTunnel(): Boolean {
        if (tunnel != null && tun != null) {
            vpnActive = true
            return true
        }

        var newTun: ParcelFileDescriptor? = null
        return try {
            val builder = Builder()
                .setSession("Ghost Priv · Firewall de porta")
                .setMtu(MTU)
                .addAddress("10.111.222.1", 24)
                .addAddress("fd66:f83a:c650::1", 120)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)

            newTun = builder.establish()
                ?: throw IllegalStateException("O Android não estabeleceu a VPN. Verifique a autorização do sistema.")

            val engine = Intra.connect3(newTun.fd.toLong(), MTU.toLong(), bridge)
            tun = newTun
            tunnel = engine
            vpnActive = true
            Log.i(TAG, "VPN seletiva inicializada; tráfego não correspondente é encaminhado pelo Firestack")
            true
        } catch (e: Exception) {
            try { tunnel?.disconnect() } catch (_: Exception) { }
            tunnel = null
            try { newTun?.close() } catch (_: Exception) { }
            tun = null
            vpnActive = false
            paused = false
            port53017Blocked = false
            Log.e(TAG, "Falha ao iniciar o encaminhamento VPN", e)
            broadcastState("Falha ao iniciar a VPN de firewall: ${e.message ?: e.javaClass.simpleName}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            false
        }
    }

    private fun hasPort(address: String?, port: Int): Boolean {
        if (address.isNullOrBlank()) return false
        return address.substringAfterLast(':').toIntOrNull() == port
    }

    private fun mark(proxy: String, cid: String, uid: Int): Mark = Mark().apply {
        setPIDCSV(proxy)
        setCID(cid)
        setUID(uid.toString())
    }

    private fun stopVpnSession() {
        paused = false
        port53017Blocked = false
        vpnActive = false
        try { tunnel?.disconnect() } catch (e: Exception) { Log.w(TAG, "Erro ao fechar o motor VPN", e) }
        tunnel = null
        try { tun?.close() } catch (e: Exception) { Log.w(TAG, "Erro ao fechar o TUN", e) }
        tun = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastState()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Firewall VPN", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val message = when {
            !vpnActive -> "Iniciando firewall VPN…"
            paused -> "Internet pausada"
            port53017Blocked -> "Porta 53017 bloqueada · TCP/UDP · todos os apps"
            else -> "VPN ativa · encaminhando tráfego normalmente"
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capcut_notification)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
            .setContentTitle("Ghost Priv · Firewall")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        vpnActive = tunnel != null
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        broadcastState()
    }

    private fun broadcastState(error: String? = null) {
        val stateIntent = Intent(FloatingPanelService.ACTION_STATE)
            .setPackage(packageName)
            .putExtra(EXTRA_PAUSED, paused)
            .putExtra(EXTRA_PORT_BLOCKED, port53017Blocked)
            .putExtra(EXTRA_VPN_ACTIVE, vpnActive)
        if (!error.isNullOrBlank()) stateIntent.putExtra(EXTRA_ERROR, error)
        sendBroadcast(stateIntent)
    }

    override fun onRevoke() {
        stopVpnSession()
        super.onRevoke()
    }

    override fun onDestroy() {
        try { tunnel?.disconnect() } catch (_: Exception) { }
        tunnel = null
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        vpnActive = false
        super.onDestroy()
    }
}
