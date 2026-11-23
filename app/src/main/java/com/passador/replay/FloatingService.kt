package com.passador.replay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingService : Service() {
    companion object {
        const val EXTRA_SCREEN = "floating_screen"
        private const val CHANNEL_ID = "priv_bypass_floating"
        private const val NOTIFICATION_ID = 7341
    }

    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun addBubble() {
        if (bubble != null) return
        val view = TextView(this).apply {
            text = "PB"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFFE51C3E.toInt(), 28f, 0xFFFF6B7F.toInt(), 2)
            elevation = 12f
            setOnClickListener { togglePanel() }
        }
        val params = overlayParams(58.dp(), 58.dp()).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12.dp()
            y = (resources.displayMetrics.heightPixels / 2) - 29.dp()
        }
        view.setOnTouchListener(DragTouchListener(view, params) { x, y ->
            params.x = x
            params.y = y
            bubbleParams = params
        })
        windowManager.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    private fun togglePanel() {
        if (panel == null) addPanel() else removePanel()
    }

    private fun addPanel() {
        val source = bubbleParams ?: return
        val panelWidth = 286.dp()
        val maxX = (resources.displayMetrics.widthPixels - panelWidth - 8.dp()).coerceAtLeast(8.dp())
        val startX = source.x.coerceIn(8.dp(), maxX)
        val startY = source.y.coerceAtLeast(8.dp())
        val params = overlayParams(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 14.dp(), 18.dp(), 14.dp())
            background = rounded(0xF5120D14.toInt(), 22f, 0xFFE51C3E.toInt(), 2)
            elevation = 18f
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8.dp())
        }
        val headerText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "PRIV BYPASS"
            textSize = 12f
            setTextColor(0xFFFF6B7F.toInt())
            letterSpacing = 0.16f
        }
        val subtitle = TextView(this).apply {
            text = "Painel flutuante"
            textSize = 19f
            setTextColor(Color.WHITE)
            setPadding(0, 3.dp(), 0, 0)
        }
        headerText.addView(title, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        headerText.addView(subtitle, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT))
        header.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        val minimize = topButton("−") { removePanel() }
        val close = topButton("×") { stopSelf() }
        header.addView(minimize, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { marginStart = 6.dp() })
        header.addView(close, LinearLayout.LayoutParams(42.dp(), 42.dp()).apply { marginStart = 6.dp() })
        headerText.setOnTouchListener(DragTouchListener(root, params) { x, y ->
            val maxPanelX = (resources.displayMetrics.widthPixels - panelWidth - 8.dp()).coerceAtLeast(8.dp())
            params.x = x.coerceIn(8.dp(), maxPanelX)
            params.y = y.coerceAtLeast(8.dp())
            bubbleParams?.x = params.x
            bubbleParams?.y = params.y
            panelParams = params
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        addAction(root, "PASSADOR DE REPLAY") { openScreen("replay") }
        addAction(root, "TEXTURAS") { openScreen("textures") }
        addAction(root, "BYPASS SS") { openScreen("bypass") }

        windowManager.addView(root, params)
        panel = root
        panelParams = params
        bubble?.visibility = View.GONE
    }

    private fun topButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 20f
        setTextColor(Color.WHITE)
        background = rounded(0xFF2B111A.toInt(), 13f, 0xFFB52A48.toInt(), 1)
        setPadding(0, 0, 0, 1.dp())
        setOnClickListener { action() }
    }

    private fun addAction(root: LinearLayout, label: String, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded(0xFF2B111A.toInt(), 14f, 0xFF7F263A.toInt(), 1)
            setOnClickListener { action() }
        }
        root.addView(button, LinearLayout.LayoutParams(-1, 50.dp()).apply { topMargin = 8.dp() })
    }

    private fun openScreen(screen: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_SCREEN, screen)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        startActivity(intent)
        removePanel()
    }

    private fun removePanel() {
        panelParams?.let { params ->
            bubbleParams?.x = params.x
            bubbleParams?.y = params.y
        }
        panel?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        panel = null
        panelParams = null
        bubble?.visibility = View.VISIBLE
        bubbleParams?.let { params ->
            try { windowManager.updateViewLayout(bubble, params) } catch (_: Exception) { }
        }
    }

    override fun onDestroy() {
        removePanel()
        bubble?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        bubble = null
        bubbleParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun rounded(color: Int, radius: Float, strokeColor: Int, strokeWidth: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.dp().toFloat()
        setStroke(strokeWidth.dp(), strokeColor)
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp(): Float = this * resources.displayMetrics.density

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Priv Bypass flutuante", NotificationManager.IMPORTANCE_LOW)
            channel.description = "Mantém o painel flutuante disponível"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(applicationInfo.icon)
        .setContentTitle("Priv Bypass flutuante ativo")
        .setContentText("Toque na bolha para abrir o painel")
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()

    private class DragTouchListener(
        private val windowView: View,
        private val params: WindowManager.LayoutParams,
        private val onMove: (Int, Int) -> Unit
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    val nextX = startX + dx.toInt()
                    val nextY = startY + dy.toInt()
                    onMove(nextX, nextY)
                    try {
                        (windowView.context.getSystemService(WINDOW_SERVICE) as WindowManager)
                            .updateViewLayout(windowView, params)
                    } catch (_: IllegalArgumentException) {
                        return true
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) v.performClick()
                    return true
                }
            }
            return false
        }
    }
}
