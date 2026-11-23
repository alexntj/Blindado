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
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.passador.replay.R
import rikka.shizuku.Shizuku
import java.io.File

class FloatingService : Service() {
    companion object {
        const val EXTRA_SCREEN = "floating_screen"
        private const val CHANNEL_ID = "priv_bypass_floating"
        private const val NOTIFICATION_ID = 7341
        private const val PKG_NORMAL = "com.dts.freefireth"
        private const val PKG_MAX = "com.dts.freefiremax"
    }

    private data class TexturePack(val name: String, val folder: String, val image: Int)

    private val packs = arrayOf(
        TexturePack("BAKI", "2 BAKI", R.drawable.thumb_baki),
        TexturePack("BAKUGO", "1 BAKUGO", R.drawable.thumb_bakugo),
        TexturePack("GOKU BLACK", "3 GOKU BLACK", R.drawable.thumb_goku_black),
        TexturePack("SUPER SAYAJIN 3", "4 GOKU SUPER SAYAJIN 3", R.drawable.thumb_ssj3),
        TexturePack("LUFFY", "5 LUFFY", R.drawable.thumb_luffy),
        TexturePack("LUFFY GEAR 5", "6 LUFFY GEAR SIX", R.drawable.thumb_luffy_gear6),
        TexturePack("PANTERA COR DE ROSA", "7 PANTERA COR DE ROSA", R.drawable.thumb_pantera),
        TexturePack("GOJO", "8 GOJO", R.drawable.thumb_gojo),
        TexturePack("CHAMA", "10 CHAMA", R.drawable.thumb_chama),
        TexturePack("FINN", "11 FINN", R.drawable.thumb_finn),
        TexturePack("SONIC", "9 SONIC", R.drawable.thumb_sonic),
        TexturePack("CJ", "12 CJ", R.drawable.thumb_cj)
    )

    private lateinit var wm: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var selectedTexture = 0
    private var directionMaxToNormal = true
    private var logView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        addBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    private fun addBubble() {
        if (bubble != null) return
        val v = TextView(this).apply {
            text = "PB"
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(0xFFE51C3E.toInt(), 28f, 0xFFFF6B7F.toInt(), 2)
            elevation = 12f
            setOnClickListener { if (panel == null) showHome() else removePanel() }
        }
        val p = overlayParams(58.dp(), 58.dp()).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12.dp()
            y = (resources.displayMetrics.heightPixels / 2 - 29.dp()).coerceAtLeast(8.dp())
        }
        v.setOnTouchListener(DragListener(v, p) { x, y ->
            p.x = x.coerceIn(8.dp(), (screenWidth() - 58.dp()).coerceAtLeast(8.dp()))
            p.y = y.coerceIn(8.dp(), (screenHeight() - 58.dp()).coerceAtLeast(8.dp()))
            bubbleParams = p
        })
        wm.addView(v, p)
        bubble = v
        bubbleParams = p
    }

    private fun showHome() {
        if (panel != null) removePanel()
        val root = createPanel("Painel flutuante")
        addAction(root, "PASSADOR DE REPLAY") { showReplay() }
        addAction(root, "TEXTURAS") { showTextures() }
        addAction(root, "BYPASS SS") { showBypassSs() }
        finishPanel(root)
    }

    private fun showTextures() {
        if (panel != null) removePanel()
        val root = createPanel("Texturas")
        val hint = TextView(this).apply { text = "Selecione e aplique sem sair do flutuante"; setTextColor(0xFFAAAAAA.toInt()); textSize = 11f }
        root.addView(hint, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp() })
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        packs.forEachIndexed { index, pack ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
                background = rounded(if (index == selectedTexture) 0xFF4A1420.toInt() else 0xFF241019.toInt(), 13f, if (index == selectedTexture) 0xFFE51C3E.toInt() else 0xFF5B2637.toInt(), 1)
                setOnClickListener { selectedTexture = index; showTextures() }
            }
            val image = ImageView(this).apply { setImageResource(pack.image); scaleType = ImageView.ScaleType.CENTER_CROP }
            row.addView(image, LinearLayout.LayoutParams(42.dp(), 42.dp()))
            val name = TextView(this).apply { text = pack.name; textSize = 11f; setTextColor(Color.WHITE); setPadding(10.dp(), 0, 0, 0) }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            list.addView(row, LinearLayout.LayoutParams(-1, 50.dp()).apply { bottomMargin = 5.dp() })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addAction(root, "INJETAR TEXTURA") { injectTexture() }
        addAction(root, "ABRIR FREE FIRE NORMAL") { launchPackage(PKG_NORMAL) }
        finishPanel(root)
    }

    private fun showReplay() {
        if (panel != null) removePanel()
        val root = createPanel("Passador de replay")
        val status = TextView(this).apply { text = "Escolha a direção"; setTextColor(0xFFAAAAAA.toInt()); textSize = 12f }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 6.dp() })
        addAction(root, "FF MAX  →  FF NORMAL") { directionMaxToNormal = true; status.text = "Direção: FF MAX → FF NORMAL" }
        addAction(root, "FF NORMAL  →  FF MAX") { directionMaxToNormal = false; status.text = "Direção: FF NORMAL → FF MAX" }
        addAction(root, "INICIAR PASSADOR") { runReplay(status) }
        logView = TextView(this).apply { textSize = 10f; setTextColor(0xFFCCCCCC.toInt()); typeface = android.graphics.Typeface.MONOSPACE; text = "Pronto.\n" }
        root.addView(logView, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 8.dp() })
        addAction(root, "ABRIR JOGO") { launchPackage(if (directionMaxToNormal) PKG_NORMAL else PKG_MAX) }
        finishPanel(root)
    }

    private fun showBypassSs() {
        if (panel != null) removePanel()
        val root = createPanel("Bypass SS")
        val info = TextView(this).apply {
            text = "ANDROID\n${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\nMODELO\n${Build.MANUFACTURER} ${Build.MODEL}\n\nSHIZUKU\n${shizukuState()}"
            setTextColor(Color.WHITE); textSize = 12f
        }
        root.addView(info, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10.dp() })
        logView = TextView(this).apply { text = "[INFO] Painel flutuante ativo.\n[INFO] Operações executadas pelo próprio app.\n"; textSize = 10f; setTextColor(0xFFCCCCCC.toInt()); typeface = android.graphics.Typeface.MONOSPACE }
        root.addView(logView, LinearLayout.LayoutParams(-1, 0, 1f))
        addAction(root, "LIMPAR LOGS INTERNOS") { logView?.text = "[OK] Logs internos do flutuante limpos.\n" }
        finishPanel(root)
    }

    private fun createPanel(titleText: String): LinearLayout {
        val source = bubbleParams ?: return LinearLayout(this)
        val width = 286.dp()
        val height = (screenHeight() * 0.78f).toInt().coerceAtMost(620.dp()).coerceAtLeast(360.dp())
        val x = source.x.coerceIn(8.dp(), (screenWidth() - width - 8.dp()).coerceAtLeast(8.dp()))
        val y = source.y.coerceIn(8.dp(), (screenHeight() - height - 8.dp()).coerceAtLeast(8.dp()))
        val p = overlayParams(width, height).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp()); background = rounded(0xF5120D14.toInt(), 20f, 0xFFE51C3E.toInt(), 2); elevation = 18f }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val text = TextView(this).apply { this.text = titleText; textSize = 18f; setTextColor(Color.WHITE) }
        header.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val min = topButton("−") { removePanel() }
        val close = topButton("×") { stopSelf() }
        header.addView(min, LinearLayout.LayoutParams(38.dp(), 38.dp()).apply { marginStart = 5.dp() })
        header.addView(close, LinearLayout.LayoutParams(38.dp(), 38.dp()).apply { marginStart = 5.dp() })
        header.setOnTouchListener(DragListener(root, p) { nx, ny ->
            p.x = nx.coerceIn(8.dp(), (screenWidth() - width - 8.dp()).coerceAtLeast(8.dp()))
            p.y = ny.coerceIn(8.dp(), (screenHeight() - height - 8.dp()).coerceAtLeast(8.dp()))
            bubbleParams?.x = p.x; bubbleParams?.y = p.y; panelParams = p
        })
        root.addView(header, LinearLayout.LayoutParams(-1, 42.dp()).apply { bottomMargin = 8.dp() })
        panelParams = p
        return root
    }

    private fun finishPanel(root: LinearLayout) {
        val p = panelParams ?: return
        wm.addView(root, p); panel = root; bubble?.visibility = View.GONE
    }

    private fun topButton(label: String, action: () -> Unit) = Button(this).apply { text = label; textSize = 20f; setTextColor(Color.WHITE); background = rounded(0xFF35131F.toInt(), 12f, 0xFFB52A48.toInt(), 1); setPadding(0, 0, 0, 1.dp()); setOnClickListener { action() } }
    private fun addAction(root: LinearLayout, label: String, action: () -> Unit) { val b = Button(this).apply { text = label; textSize = 11f; setTextColor(Color.WHITE); background = rounded(0xFF2B111A.toInt(), 13f, 0xFF7F263A.toInt(), 1); setOnClickListener { action() } }; root.addView(b, LinearLayout.LayoutParams(-1, 46.dp()).apply { topMargin = 6.dp() }) }

    private fun injectTexture() {
        val pack = packs[selectedTexture]
        logView?.text = "[INFO] Preparando textura...\n"
        Thread {
            try {
                val root = File(Environment.getExternalStorageDirectory(), "PrivBypass/texture_packs/${pack.folder}")
                root.mkdirs(); copyAssets("texture_packs/${pack.folder}", root)
                val source = File(root, PKG_NORMAL)
                val command = "set -e; SOURCE_DIR=${quote(source.absolutePath)}; TARGET_DIR=${quote("/storage/emulated/0/Android/data/$PKG_NORMAL")}; test -d \"\$SOURCE_DIR\"; mkdir -p \"\$TARGET_DIR\"; cp -rf \"\$SOURCE_DIR\"/. \"\$TARGET_DIR\"/; sync; echo '[RESULT] OK'"
                runPrivileged(command) { ok -> logView?.text = if (ok) "[OK] Textura aplicada no FF normal.\n" else "[ERRO] Não foi possível aplicar a textura.\n" }
            } catch (_: Exception) { ui { logView?.text = "[ERRO] Falha ao preparar a textura.\n" } }
        }.start()
    }

    private fun runReplay(status: TextView) {
        status.text = "Executando operação privilegiada..."
        val src = if (directionMaxToNormal) "/storage/emulated/0/Android/data/$PKG_MAX/files/MReplays" else "/storage/emulated/0/Android/data/$PKG_NORMAL/files/MReplays"
        val dst = if (directionMaxToNormal) "/storage/emulated/0/Android/data/$PKG_NORMAL/files/MReplays" else "/storage/emulated/0/Android/data/$PKG_MAX/files/MReplays"
        val command = "set -e; SRC=${quote(src)}; DST=${quote(dst)}; mkdir -p \"\$DST\"; B=\$(ls -1t \"\$SRC\"/*_rep.bin 2>/dev/null | head -n 1); test -n \"\$B\"; J=\"\${B%_rep.bin}_rep.json\"; test -f \"\$J\"; cp -f \"\$B\" \"\$DST/\"; cp -f \"\$J\" \"\$DST/\"; echo '[RESULT] OK'"
        runPrivileged(command) { ok -> ui { status.text = if (ok) "Replay sincronizado com sucesso." else "Falha ao sincronizar o replay." } }
    }

    private fun runPrivileged(command: String, callback: (Boolean) -> Unit) {
        if (!try { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }) { ui { callback(false) }; return }
        Thread {
            val ok = try {
                val m = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
                m.isAccessible = true
                val p = m.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                p.inputStream.bufferedReader().readText(); p.errorStream.bufferedReader().readText(); p.waitFor() == 0
            } catch (_: Throwable) { false }
            ui { callback(ok) }
        }.start()
    }

    private fun copyAssets(path: String, dest: File) { val children = assets.list(path) ?: emptyArray(); if (children.isEmpty()) { dest.parentFile?.mkdirs(); assets.open(path).use { input -> dest.outputStream().use { output -> input.copyTo(output) } }; return }; dest.mkdirs(); children.forEach { copyAssets("$path/$it", File(dest, it)) } }
    private fun launchPackage(pkg: String) { packageManager.getLaunchIntentForPackage(pkg)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(it) } }
    private fun shizukuState(): String = try { if (!Shizuku.pingBinder()) "DESCONECTADO" else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) "CONECTADO E AUTORIZADO" else "CONECTADO, SEM AUTORIZAÇÃO" } catch (_: Throwable) { "INDISPONÍVEL" }
    private fun quote(v: String) = "'${v.replace("'", "'\\''")} '".trimEnd()

    private fun ui(block: () -> Unit) { mainHandler.post(block) }

    private fun removePanel() { panelParams?.let { bubbleParams?.x = it.x; bubbleParams?.y = it.y }; panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }; panel = null; panelParams = null; bubble?.visibility = View.VISIBLE; bubbleParams?.let { try { wm.updateViewLayout(bubble, it) } catch (_: Exception) {} } }
    override fun onDestroy() { removePanel(); bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }; bubble = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun screenWidth() = resources.displayMetrics.widthPixels
    private fun screenHeight() = resources.displayMetrics.heightPixels
    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(w, h, if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT)
    private fun rounded(c: Int, r: Float, sc: Int, sw: Int) = GradientDrawable().apply { setColor(c); cornerRadius = r.dp(); setStroke(sw.dp(), sc) }
    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun Float.dp() = this * resources.displayMetrics.density
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL_ID, "Priv Bypass flutuante", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(applicationInfo.icon).setContentTitle("Priv Bypass flutuante ativo").setContentText("Painel flutuante disponível").setOngoing(true).build()

    private class DragListener(private val windowView: View, private val params: WindowManager.LayoutParams, private val move: (Int, Int) -> Unit) : View.OnTouchListener {
        private var dx = 0f; private var dy = 0f; private var sx = 0; private var sy = 0; private var moved = false
        override fun onTouch(v: View, e: MotionEvent): Boolean { when (e.actionMasked) { MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = params.x; sy = params.y; moved = false; return true }; MotionEvent.ACTION_MOVE -> { val x = sx + (e.rawX - dx).toInt(); val y = sy + (e.rawY - dy).toInt(); if (kotlin.math.abs(x - sx) > 8 || kotlin.math.abs(y - sy) > 8) moved = true; move(x, y); try { (windowView.context.getSystemService(WINDOW_SERVICE) as WindowManager).updateViewLayout(windowView, params) } catch (_: IllegalArgumentException) {}; return true }; MotionEvent.ACTION_UP -> { if (!moved) v.performClick(); return true } }; return false }
    }
}
