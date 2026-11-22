package com.passador.replay

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.RemoteException
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private companion object {
        const val APP_ID = "com.passador.replay"
        const val APP_VERSION_CODE = 1
    }

    private val PKG_NORMAL = "com.dts.freefireth"
    private val PKG_MAX = "com.dts.freefiremax"
    private val PATH_NORMAL = "/storage/emulated/0/Android/data/com.dts.freefireth/files/MReplays/"
    private val PATH_MAX = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/MReplays/"
    private val REQ_NOTIFICATION = 1001

    private lateinit var screenConnect: View
    private lateinit var screenDirection: View
    private lateinit var screenTransfer: View
    private lateinit var logBox: TextView
    private lateinit var btnPassar: Button
    private lateinit var btnBypass: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvVersionNormal: TextView
    private lateinit var tvVersionMax: TextView
    private val adbManager by lazy { AdbManager(this) }
    private val SHIZUKU_REQUEST_CODE = 2001
    private var privilegedService: IPrivilegedService? = null
    private var pendingCommand: String? = null
    private var pendingCallback: ((String?) -> Unit)? = null

    private val shizukuArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(APP_ID, PrivilegedService::class.java.name)
        ).daemon(false)
            .processNameSuffix("privileged")
            .debuggable(true)
            .version(APP_VERSION_CODE)
    }

    private val shizukuConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            privilegedService = IPrivilegedService.Stub.asInterface(binder)
            val command = pendingCommand
            val callback = pendingCallback
            pendingCommand = null
            pendingCallback = null
            if (command != null && callback != null) executePrivilegedNow(command, callback)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            privilegedService = null
        }
    }

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                val message = if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    "Shizuku autorizado para este aplicativo."
                } else {
                    "Permissão do Shizuku recusada."
                }
                android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
            }
        }

    private var directionMaxToNormal = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        screenConnect = findViewById(R.id.screenConnect)
        screenDirection = findViewById(R.id.screenDirection)
        screenTransfer = findViewById(R.id.screenTransfer)
        logBox = findViewById(R.id.logBox)
        btnPassar = findViewById(R.id.btnPassar)
        btnBypass = findViewById(R.id.btnBypass)
        tvStatus = findViewById(R.id.tvStatus)
        tvVersionNormal = findViewById(R.id.tvVersionNormal)
        tvVersionMax = findViewById(R.id.tvVersionMax)

        // Pede as duas permissões assim que abre
        requestNotificationPermission()
        requestAllFilesPermission()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        requestShizukuPermission()

        findViewById<Button>(R.id.btnConectar).setOnClickListener {
            openDeveloperOptions()
        }

        findViewById<Button>(R.id.btnJaConectei).setOnClickListener {
            if (hasShizukuPermission()) {
                fadeOut(screenConnect)
                fadeIn(screenDirection)
                detectVersions()
            }
        }

        findViewById<Button>(R.id.btnMaxToNormal).setOnClickListener {
            directionMaxToNormal = true
            fadeOut(screenDirection)
            fadeIn(screenTransfer)
            updateTransferUI()
        }

        findViewById<Button>(R.id.btnNormalToMax).setOnClickListener {
            directionMaxToNormal = false
            fadeOut(screenDirection)
            fadeIn(screenTransfer)
            updateTransferUI()
        }

        btnPassar.setOnClickListener { startTransfer() }
        btnBypass.setOnClickListener { doBypass() }


        findViewById<Button>(R.id.btnParear).setOnClickListener { openWirelessPairingSettings() }

        screenConnect.alpha = 0f
        screenConnect.animate().alpha(1f).setDuration(500).start()
    }

    private fun openWirelessPairingSettings() {
        try {
            startActivity(Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"))
        } catch (_: Exception) {
            openDeveloperOptions()
        }
        android.widget.Toast.makeText(
            this,
            "Faça o pareamento pelo Android. O código aparecerá na notificação/sistema; depois toque em JÁ CONECTEI.",
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    private fun hasShizukuPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) {
                android.widget.Toast.makeText(
                    this,
                    "Inicie o serviço do Shizuku e tente novamente.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                false
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                android.widget.Toast.makeText(
                    this,
                    "Permita este aplicativo no Shizuku.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                false
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                false
            }
        } catch (e: Throwable) {
            log("Shizuku indisponível: ${e.message}")
            false
        }
    }

    private fun requestShizukuPermission() {
        if (!Shizuku.isPreV11()) hasShizukuPermission()
    }

    private fun executePrivileged(command: String, callback: (String?) -> Unit) {
        if (!hasShizukuPermission()) {
            callback(null)
            return
        }
        if (privilegedService != null) {
            executePrivilegedNow(command, callback)
            return
        }
        pendingCommand = command
        pendingCallback = callback
        try {
            Shizuku.bindUserService(shizukuArgs, shizukuConnection)
        } catch (e: Throwable) {
            pendingCommand = null
            pendingCallback = null
            log("Falha ao iniciar serviço Shizuku: ${e.message}")
            callback(null)
        }
    }

    private fun executePrivilegedNow(command: String, callback: (String?) -> Unit) {
        Thread {
            val result = try {
                privilegedService?.execute(command)
            } catch (e: RemoteException) {
                log("Erro no serviço Shizuku: ${e.message}")
                null
            }
            runOnUiThread { callback(result) }
        }.start()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        try {
            Shizuku.unbindUserService(shizukuArgs, shizukuConnection, true)
        } catch (_: Throwable) {
        }
        super.onDestroy()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_NOTIFICATION
                )
            }
        }
    }

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun openDeveloperOptions() {
        try {
            val intent = Intent("com.android.settings.WIRELESS_DEBUGGING")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                return
            } catch (e2: Exception) {}
        }
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {}
    }

    private fun detectVersions() {
        val pm = packageManager
        var normalOk = false
        var maxOk = false

        try {
            val infoN = pm.getPackageInfo(PKG_NORMAL, 0)
            tvVersionNormal.text = "Normal: ${infoN.versionName}"
            normalOk = true
        } catch (e: PackageManager.NameNotFoundException) {
            tvVersionNormal.text = "Normal: NÃO INSTALADO"
        }

        try {
            val infoM = pm.getPackageInfo(PKG_MAX, 0)
            tvVersionMax.text = "Max: ${infoM.versionName}"
            maxOk = true
        } catch (e: PackageManager.NameNotFoundException) {
            tvVersionMax.text = "Max: NÃO INSTALADO"
        }

        if (!normalOk || !maxOk) {
            tvStatus.text = "⚠ Os dois Free Fire precisam estar instalados"
            tvStatus.setTextColor(0xFFFF5252.toInt())
            btnPassar.isEnabled = false
            btnPassar.alpha = 0.4f
        } else {
            tvStatus.text = "Tudo pronto"
            tvStatus.setTextColor(0xFFFF1A1A.toInt())
            btnPassar.isEnabled = true
            btnPassar.alpha = 1f
        }
    }

    private fun updateTransferUI() {
        val title = findViewById<TextView>(R.id.tvTransferTitle)
        title.text = if (directionMaxToNormal) "FF MAX  →  FF NORMAL" else "FF NORMAL  →  FF MAX"
        logBox.text = ""
        btnBypass.visibility = View.GONE
        btnPassar.visibility = View.VISIBLE
        btnPassar.isEnabled = true
    }

    private fun startTransfer() {
        btnPassar.isEnabled = false
        log("Iniciando transferência via Shizuku...")

        val srcDir = if (directionMaxToNormal) PATH_MAX else PATH_NORMAL
        val dstDir = if (directionMaxToNormal) PATH_NORMAL else PATH_MAX
        val cmd = "mkdir -p '$dstDir' && cp -r '${srcDir}'* '$dstDir'/"

        executePrivileged(cmd) { result ->
            if (result != null) {
                log("Comando executado com Shizuku.")
                if (result.isNotEmpty()) log(result)
                btnPassar.visibility = View.GONE
                btnBypass.visibility = View.VISIBLE
                btnBypass.alpha = 0f
                btnBypass.animate().alpha(1f).setDuration(350).start()
            } else {
                log("Falha: Shizuku não executou o comando.")
                btnPassar.isEnabled = true
            }
        }
    }

    private fun doBypass() {
        log("BYPASS iniciado...")
        val targetPkg = if (directionMaxToNormal) PKG_NORMAL else PKG_MAX
        try {
            val launch = packageManager.getLaunchIntentForPackage(targetPkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
                log("Abrindo $targetPkg")
            }
        } catch (e: Exception) {
            log("Falha ao abrir o jogo: ${e.message}")
        }

        try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            log("Falha ao desinstalar: ${e.message}")
        }

        try {
            cacheDir.deleteRecursively()
            codeCacheDir.deleteRecursively()
        } catch (_: Exception) {}

        finishAffinity()
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        runOnUiThread {
            logBox.append("[$time] $msg\n")
            val scroll = findViewById<ScrollView>(R.id.logScroll)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun fadeOut(v: View) {
        v.animate().alpha(0f).setDuration(260).withEndAction { v.visibility = View.GONE }.start()
    }

    private fun fadeIn(v: View) {
        v.alpha = 0f
        v.visibility = View.VISIBLE
        v.animate().alpha(1f).setDuration(300).start()
    }
}
