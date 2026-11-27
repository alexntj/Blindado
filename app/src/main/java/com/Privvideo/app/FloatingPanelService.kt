package com.Privvideo.app

import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.widget.ImageView
import android.os.SystemClock
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/** Fundo preto com rastros brancos descendo, desenhados em Canvas sem criar views extras. */
private class DigitalRainDrawable(private val cornerRadius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boundsF = RectF()
    private val clip = Path()
    private val startedAt = SystemClock.uptimeMillis()
    private val frame = Runnable { invalidateSelf() }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        boundsF.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        paint.color = Color.rgb(5, 8, 14)
        paint.alpha = 245
        canvas.drawRoundRect(boundsF, cornerRadius, cornerRadius, paint)
        val save = canvas.save()
        clip.reset(); clip.addRoundRect(boundsF, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clip)
        val elapsed = (SystemClock.uptimeMillis() - startedAt).toFloat()
        val w = boundsF.width(); val h = boundsF.height()
        paint.color = Color.WHITE
        for (i in 0..17) {
            val x = boundsF.left + ((i * 47f + 13f) % (w.coerceAtLeast(1f)))
            val speed = 34f + (i % 5) * 17f
            val y = boundsF.top + ((elapsed * speed / 1000f + i * 29f) % (h + 34f)) - 34f
            paint.alpha = 35 + (i % 4) * 22
            paint.strokeWidth = if (i % 3 == 0) 1.5f else 0.8f
            canvas.drawLine(x, y, x, y + 9f + (i % 4) * 5f, paint)
            if (i % 4 == 0) {
                paint.alpha = 18
                canvas.drawLine(x + 2f, y - 12f, x + 2f, y + 18f, paint)
            }
        }
        canvas.restoreToCount(save)
        scheduleSelf(frame, SystemClock.uptimeMillis() + 32L)
    }

    override fun setAlpha(alpha: Int) { invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    override fun onBoundsChange(bounds: android.graphics.Rect) { super.onBoundsChange(bounds); invalidateSelf() }
}

/** Painel, botão de abertura e botão fantasma são janelas independentes e arrastáveis. */
class FloatingPanelService : Service() {
    companion object {
        const val ACTION_STATE = "com.Privvideo.app.action.NETWORK_STATE"
        const val ACTION_HOLOGRAM_INJECT = "com.Privvideo.app.action.HOLOGRAM_INJECT"
        const val ACTION_HOLOGRAM_STATUS = "com.Privvideo.app.action.HOLOGRAM_STATUS"
        const val EXTRA_HOLOGRAM_STATUS = "hologram_status"
        const val ACTION_BYPASS_SS_UNINSTALL = "com.Privvideo.app.action.BYPASS_SS_UNINSTALL"
        const val ACTION_CLOSE = "com.Privvideo.app.action.OVERLAY_CLOSE"
        private const val TAB_FAKE_LAG = 0
        private const val TAB_TEXTURES = 1
        private const val TAB_BYPASS_SS = 2
        private const val PREFS = "ghost_overlay_positions"
    }

    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var body: LinearLayout? = null
    private var launcher: ImageView? = null
    private var ghost: ImageView? = null
    private var status: TextView? = null
    private var fakeLagSwitch: Button? = null
    private var hologramStatus: TextView? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var launcherParams: WindowManager.LayoutParams? = null
    private var ghostParams: WindowManager.LayoutParams? = null
    private var panelVisible = false
    private var fakeLagEnabled = false
    private var paused = false
    private var currentTab = TAB_FAKE_LAG
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }

    private var portCommandBusy = false
    private var pendingPortBlock: Boolean? = null
    private var portBlocked = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_STATE -> {
                    paused = intent.getBooleanExtra(PortFilterVpnService.EXTRA_PAUSED, false)
                    portBlocked = intent.getBooleanExtra(PortFilterVpnService.EXTRA_PORT_BLOCKED, portBlocked)
                    val retryPortState = pendingPortBlock
                    pendingPortBlock = null
                    portCommandBusy = false
                    intent.getStringExtra(PortFilterVpnService.EXTRA_ERROR)?.let { error ->
                        android.widget.Toast.makeText(this@FloatingPanelService, error, android.widget.Toast.LENGTH_LONG).show()
                    }
                    renderNetworkState()
                    if (retryPortState != null && retryPortState != portBlocked) {
                        setPort53017Blocked(retryPortState)
                    }
                }
                ACTION_HOLOGRAM_STATUS -> hologramStatus?.text = intent.getStringExtra(EXTRA_HOLOGRAM_STATUS) ?: "Aguardando ação"
                ACTION_CLOSE -> stopSelf()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startPanelForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        fakeLagEnabled = prefs.getBoolean("fake_lag_enabled", false)
        val filter = IntentFilter().apply { addAction(ACTION_STATE); addAction(ACTION_HOLOGRAM_STATUS); addAction(ACTION_CLOSE) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        val vpnStateIntent = Intent(this, PortFilterVpnService::class.java).setAction(PortFilterVpnService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(vpnStateIntent) else startService(vpnStateIntent)
        createLauncher()
        createPanel()
        showPanel()
    }

    private fun startPanelForeground() {
        val id = "ghost_overlay_panel"
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(id, "Painel flutuante", NotificationManager.IMPORTANCE_LOW))
        val appLogo = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        startForeground(7302, NotificationCompat.Builder(this, id)
            .setSmallIcon(R.drawable.ic_capcut_notification)
            .setLargeIcon(appLogo)
            .setContentTitle("Ghost Priv")
            .setContentText("Controles flutuantes ativos")
            .setOngoing(true).setOnlyAlertOnce(true).build())
    }

    private fun overlayType() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
    private fun params(width: Int, height: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        width, height, overlayType(), WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun createLauncher() {
        launcher = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "Abrir painel"
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = orbBackground(0xFF8B5CF6.toInt(), 0xFF3B0764.toInt())
            clipToOutline = true
            elevation = dp(12).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { showPanel() }
        }
        launcherParams = params(dp(52), dp(52), prefs.getInt("x", dp(18)), prefs.getInt("y", dp(150)))
        addWindow(launcher!!, launcherParams!!)
        launcher?.visibility = View.GONE
        attachDrag(launcher!!, launcherParams!!, "launcher")
    }

    private fun createPanel() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = DigitalRainDrawable(dp(22).toFloat())
            if (Build.VERSION.SDK_INT >= 23) foreground = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0xFF8B5CF6.toInt())
            }
            elevation = dp(20).toFloat()
            clipToOutline = true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_capcut_mark)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        top.addView(logo, LinearLayout.LayoutParams(dp(27), dp(27)).apply { marginEnd = dp(8) })
        val title = label("GHOST PRIV", 15f, Color.WHITE, true)
        top.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        top.addView(actionButton("—", false) { minimizePanel() }, LinearLayout.LayoutParams(dp(42), dp(36)))
        root.addView(top)
        root.addView(label("PAINEL DE CONTROLE  ·  ONLINE", 9f, 0xFFC4B5FD.toInt(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, dp(4)) })
        val separator = View(this).apply { setBackgroundColor(0x447C3AED) }
        root.addView(separator, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(7) })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(8)) }
        tabs.addView(tab("FAKE LAG") { showTab(TAB_FAKE_LAG) }, weightParams())
        tabs.addView(tab("TEXTURAS") { showTab(TAB_TEXTURES) }, weightParams())
        tabs.addView(tab("BYPASS SS") { showTab(TAB_BYPASS_SS) }, weightParams())
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(4), dp(8), dp(4), dp(4)) }
        root.addView(tabs); root.addView(body)
        panel = root
        panelParams = params(dp(282), WindowManager.LayoutParams.WRAP_CONTENT, prefs.getInt("x", dp(18)), prefs.getInt("y", dp(150)))
        attachDrag(root, panelParams!!, "panel", onlyHandle = true)
        showTab(TAB_FAKE_LAG)
    }

    private fun addWindow(view: View, lp: WindowManager.LayoutParams) { try { wm.addView(view, lp) } catch (_: Exception) { } }

    private fun showPanel() {
        val p = panel ?: return
        if (!panelVisible) {
            val lp = panelParams ?: return
            val bubble = launcherParams
            if (bubble != null) { lp.x = bubble.x; lp.y = bubble.y }
            addWindow(p, lp); panelVisible = true
        }
        launcher?.visibility = View.GONE
        if (fakeLagEnabled) showGhost() else hideGhost()
    }

    private fun minimizePanel() {
        if (panelVisible) try { panel?.let { wm.removeView(it) } } catch (_: Exception) { }
        panelVisible = false
        val lp = launcherParams ?: return
        panelParams?.let { lp.x = it.x; lp.y = it.y }
        launcher?.visibility = View.VISIBLE
        if (fakeLagEnabled) showGhost()
    }

    private fun showGhost() {
        if (ghost != null) return
        ghost = ImageView(this).apply {
            setImageResource(R.drawable.ic_ghost_mark)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = if (portBlocked) "Liberar porta 53017 para todos os apps" else "Bloquear porta 53017 para todos os apps"
            setPadding(dp(11), dp(11), dp(11), dp(11))
            background = ghostBackground(portBlocked)
            clipToOutline = true
            elevation = dp(16).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener {
                togglePort53017()
            }
        }
        ghostParams = params(dp(56), dp(56), prefs.getInt("gx", dp(18)), prefs.getInt("gy", dp(230)))
        addWindow(ghost!!, ghostParams!!)
        attachDrag(ghost!!, ghostParams!!, "ghost")
        renderNetworkState()
    }

    private fun hideGhost() {
        ghost?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        ghost = null; ghostParams = null
        setPort53017Blocked(false)
    }

    private fun togglePort53017() {
        setPort53017Blocked(!portBlocked)
    }

    private fun setPort53017Blocked(block: Boolean) {
        if (portCommandBusy) {
            pendingPortBlock = block
            return
        }
        portCommandBusy = true
        val intent = Intent(this, PortFilterVpnService::class.java)
            .setAction(PortFilterVpnService.ACTION_SET_PORT_BLOCKED)
            .putExtra(PortFilterVpnService.EXTRA_PORT_BLOCKED_REQUEST, block)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        } catch (e: Exception) {
            portCommandBusy = false
            android.widget.Toast.makeText(
                this,
                "Falha ao ${if (block) "ativar" else "desativar"} o bloqueio VPN: ${e.message ?: e.javaClass.simpleName}",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showTab(tab: Int) {
        currentTab = tab
        val box = body ?: return
        box.removeAllViews()
        when (tab) {
            TAB_FAKE_LAG -> {
                box.addView(label("FAKE LAG", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
                fakeLagSwitch = actionButton(if (fakeLagEnabled) "FAKE LAG: ATIVADO" else "FAKE LAG: DESATIVADO", false) {
                    fakeLagEnabled = !fakeLagEnabled
                    prefs.edit().putBoolean("fake_lag_enabled", fakeLagEnabled).apply()
                    renderFakeLagSwitch()
                    if (fakeLagEnabled) showGhost() else hideGhost()
                }
                renderFakeLagSwitch()
                box.addView(fakeLagSwitch, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
                status = label(if (portBlocked) "PORTA 53017 BLOQUEADA" else "PORTA 53017 LIBERADA", 10f, 0xFF65E69A.toInt(), true).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, 0) }
                box.addView(status)
                box.addView(label("Ative o Fake Lag para exibir o botão fantasma. O botão bloqueia globalmente TCP e UDP na porta 53017 pela VPN, encaminhando o restante do tráfego.", 9f, 0xFF9CA3AF.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) })
            }
            TAB_TEXTURES -> {
                box.addView(label("HOLOGRAMA · FREE FIRE MAX", 11f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
                val v = try { packageManager.getPackageInfo("com.dts.freefiremax", 0).versionName ?: "desconhecida" } catch (_: Exception) { "não instalado" }
                box.addView(label("Versão do FF MAX: $v", 9f, 0xFF9CA3AF.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(5), 0, 0) })
                hologramStatus = label("Aguardando ação do holograma", 10f, 0xFF9CA3AF.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(10)) }
                box.addView(hologramStatus)
                box.addView(actionButton("APLICAR HOLOGRAMA") { sendBroadcast(Intent(ACTION_HOLOGRAM_INJECT).setPackage(packageName)) }, LinearLayout.LayoutParams(-1, dp(44)))
            }
            else -> {
                box.addView(label("BYPASS SS", 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
                box.addView(label("Encerra a sessão VPN e abre a confirmação de desinstalação do aplicativo.", 9f, 0xFF9CA3AF.toInt(), false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(10)) })
                box.addView(actionButton("DESLIGAR VPN E DESINSTALAR") {
                    val intent = Intent(this, MainActivity::class.java).apply {
                        action = ACTION_BYPASS_SS_UNINSTALL
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this, "Não foi possível abrir a confirmação de desinstalação.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }, LinearLayout.LayoutParams(-1, dp(44)))
            }
        }
    }

    private fun renderNetworkState() {
        status?.text = if (portBlocked) "PORTA 53017 BLOQUEADA" else "PORTA 53017 LIBERADA"
        status?.setTextColor(if (portBlocked) 0xFFFF7777.toInt() else 0xFF65E69A.toInt())
        ghost?.background = ghostBackground(portBlocked)
        ghost?.contentDescription = if (portBlocked) "Liberar porta 53017 para todos os apps" else "Bloquear porta 53017 para todos os apps"
        ghost?.alpha = 1f
    }

    private fun renderFakeLagSwitch() {
        fakeLagSwitch?.apply {
            text = if (fakeLagEnabled) "FAKE LAG: ATIVADO" else "FAKE LAG: DESATIVADO"
            setTextColor(if (fakeLagEnabled) Color.BLACK else 0xFFE5E7EB.toInt())
            background = rounded(if (fakeLagEnabled) Color.WHITE else 0xFF111318.toInt(), 10)
        }
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams, key: String, onlyHandle: Boolean = false) {
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val touch = View.OnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false
                    if (key == "ghost") { v.animate().cancel(); v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(65).start() }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - sx).toInt(); val dy = (e.rawY - sy).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    lp.x = (ox + dx).coerceAtLeast(0); lp.y = (oy + dy).coerceAtLeast(0)
                    try { wm.updateViewLayout(view, lp) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (key == "ghost") v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    prefs.edit().putInt(if (key == "ghost") "gx" else "x", lp.x).putInt(if (key == "ghost") "gy" else "y", lp.y).apply()
                    if (key == "panel") { launcherParams?.x = lp.x; launcherParams?.y = lp.y }
                    if (!moved && !onlyHandle) v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (key == "ghost") v.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                    true
                }
                else -> false
            }
        }
        if (onlyHandle) {
            val handle = (view as LinearLayout).getChildAt(0)
            handle.setOnTouchListener(touch)
        } else view.setOnTouchListener(touch)
    }

    private fun tab(text: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 8f; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(0xFF30394A.toInt(), 9); setPadding(dp(2), 0, dp(2), 0); setOnClickListener { action() } }
    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 10f; isAllCaps = false; setTextColor(Color.WHITE); background = rounded(if (primary) 0xFF6D28D9.toInt() else 0xFF30394A.toInt(), 10); setOnClickListener { action() } }
    private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply { this.text = text; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD }
    private fun weightParams() = LinearLayout.LayoutParams(0, dp(34), 1f).apply { marginEnd = dp(3) }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun orbBackground(start: Int, end: Int) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(2), 0xCCDDD6FE.toInt())
    }
    private fun ghostBackground(active: Boolean) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (active) intArrayOf(Color.WHITE, 0xFFF0F0F2.toInt()) else intArrayOf(0xFF050507.toInt(), 0xFF17131D.toInt())
    ).apply {
        shape = GradientDrawable.OVAL
        setStroke(dp(2), if (active) 0xFF4B4652.toInt() else 0xFF817197.toInt())
    }
    private fun capCutMark(sizePx: Int): Drawable = resources.getDrawable(R.drawable.ic_capcut_mark, theme).mutate().apply {
        setTint(Color.WHITE)
        setBounds(0, 0, sizePx, sizePx)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    @Suppress("DEPRECATION")
    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) { }
        listOfNotNull(panel, launcher, ghost).forEach { try { wm.removeView(it) } catch (_: Exception) { } }
        panel = null; launcher = null; ghost = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        super.onDestroy()
    }
}
