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
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONObject
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private companion object {
        const val APP_ID = "com.passador.replay"
        const val APP_VERSION_CODE = 1
        const val VALIDATE_URL = "https://ghostcheats.de.deplexo.com/api/validate"
        const val APP_SLUG = "stirlax-modz-m1nx-oficial"
        const val APP_VERSION = "2.0"
    }

    private val PKG_NORMAL = "com.dts.freefireth"
    private val PKG_MAX = "com.dts.freefiremax"
    private val PATH_NORMAL = "/storage/emulated/0/Android/data/com.dts.freefireth/files/MReplays/"
    private val PATH_MAX = "/storage/emulated/0/Android/data/com.dts.freefiremax/files/MReplays/"
    private val REQ_NOTIFICATION = 1001

    private lateinit var screenConnect: View
    private lateinit var screenLogin: View
    private lateinit var screenDirection: View
    private lateinit var screenTransfer: View
    private lateinit var logBox: TextView
    private lateinit var btnPassar: Button
    private lateinit var btnBypass: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvVersionNormal: TextView
    private lateinit var tvVersionMax: TextView
    private lateinit var etKey: EditText
    private lateinit var btnValidateKey: Button
    private lateinit var tvKeyStatus: TextView
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
    private var currentKey = ""
    private var currentSessionToken = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        screenConnect = findViewById(R.id.screenConnect)
        screenLogin = findViewById(R.id.screenLogin)
        screenDirection = findViewById(R.id.screenDirection)
        screenTransfer = findViewById(R.id.screenTransfer)
        logBox = findViewById(R.id.logBox)
        btnPassar = findViewById(R.id.btnPassar)
        btnBypass = findViewById(R.id.btnBypass)
        tvStatus = findViewById(R.id.tvStatus)
        tvVersionNormal = findViewById(R.id.tvVersionNormal)
        tvVersionMax = findViewById(R.id.tvVersionMax)
        etKey = findViewById(R.id.etKey)
        btnValidateKey = findViewById(R.id.btnValidateKey)
        tvKeyStatus = findViewById(R.id.tvKeyStatus)

        // Pede as duas permissões assim que abre
        requestNotificationPermission()
        requestAllFilesPermission()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        requestShizukuPermission()

        findViewById<Button>(R.id.btnConectar).setOnClickListener { openShizuku() }

        findViewById<Button>(R.id.btnJaConectei).setOnClickListener {
            verifyShizukuAndContinue()
        }

        btnValidateKey.setOnClickListener { validateKeyAndOpenApp() }

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


        screenConnect.alpha = 0f
        screenConnect.animate().alpha(1f).setDuration(500).start()
    }

    private fun openShizuku() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                startActivity(Intent("moe.shizuku.manager.action.MAIN_SETTINGS"))
            }
            android.widget.Toast.makeText(
                this,
                "No Shizuku, inicie o serviço e autorize o Priv Replay. Depois volte e toque em JÁ CONECTEI.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            android.widget.Toast.makeText(
                this,
                "Não foi possível abrir o Shizuku. Verifique se ele está instalado.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
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

    private fun verifyShizukuAndContinue() {
        try {
            if (!Shizuku.pingBinder()) {
                android.widget.Toast.makeText(
                    this,
                    "Shizuku não está conectado. Toque em ABRIR SHIZUKU e inicie o serviço.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                android.widget.Toast.makeText(
                    this,
                    "Autorize o Priv Replay no Shizuku e toque novamente em JÁ CONECTEI.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                return
            }

            // O vínculo do UserService é feito somente ao iniciar a transferência.
            // Aqui validamos apenas o Binder do Shizuku e a autorização do aplicativo,
            // evitando que a tela fique aguardando um callback de bind.
            continueAfterShizukuVerification()
        } catch (e: Throwable) {
            log("Falha ao verificar o Shizuku: ${e.message}")
            android.widget.Toast.makeText(
                this,
                "Não foi possível validar o serviço do Shizuku. Abra-o novamente e tente outra vez.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun continueAfterShizukuVerification() {
        android.widget.Toast.makeText(
            this,
            "Shizuku conectado e autorizado.",
            android.widget.Toast.LENGTH_SHORT
        ).show()
        fadeOut(screenConnect)
        fadeIn(screenLogin)
    }

    private data class KeyValidation(
        val authorized: Boolean,
        val message: String,
        val sessionToken: String
    )

    private fun validateKeyAndOpenApp() {
        val key = etKey.text.toString().trim()
        if (key.isEmpty()) {
            tvKeyStatus.text = "Digite sua Key para continuar."
            tvKeyStatus.setTextColor(0xFFFF5252.toInt())
            return
        }
        validateKey(
            key,
            onSuccess = {
                fadeOut(screenLogin)
                fadeIn(screenDirection)
                detectVersions()
            }
        )
    }

    private fun validateKey(
        key: String,
        onSuccess: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        btnValidateKey.isEnabled = false
        tvKeyStatus.text = "Validando acesso..."
        tvKeyStatus.setTextColor(0xFFAAAAAA.toInt())
        Thread {
            val result = requestKeyValidation(key)
            runOnUiThread {
                btnValidateKey.isEnabled = true
                if (result.authorized) {
                    currentKey = key
                    currentSessionToken = result.sessionToken
                    tvKeyStatus.text = result.message.ifBlank { "Key autorizada." }
                    tvKeyStatus.setTextColor(0xFF66FF99.toInt())
                    onSuccess()
                } else {
                    tvKeyStatus.text = result.message.ifBlank {
                        "Key inválida, expirada, pausada ou HWID não corresponde."
                    }
                    tvKeyStatus.setTextColor(0xFFFF5252.toInt())
                    onFailure()
                }
            }
        }.start()
    }

    private fun requestKeyValidation(key: String): KeyValidation {
        var connection: HttpURLConnection? = null
        return try {
            val hwid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                ?: "unknown-device"
            val body = JSONObject()
                .put("key", key)
                .put("hwid", hwid)
                .put("appSlug", APP_SLUG)
                .put("appVersion", APP_VERSION)
            if (currentSessionToken.isNotBlank()) {
                body.put("sessionToken", currentSessionToken)
            }

            connection = (URL(VALIDATE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (currentSessionToken.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer $currentSessionToken")
                    setRequestProperty("X-Session-Token", currentSessionToken)
                }
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val responseText = stream.bufferedReader().use { it.readText() }
            val response = JSONObject(responseText)
            val authorized = response.optBoolean("success", false) &&
                response.optString("status") == "active" &&
                !response.optBoolean("killed", false) &&
                !response.optBoolean("banned", false)
            KeyValidation(
                authorized = authorized,
                message = response.optString("message"),
                sessionToken = response.optString("sessionToken", currentSessionToken)
            )
        } catch (e: Exception) {
            KeyValidation(false, "Erro de conexão com o servidor: ${e.message}", "")
        } finally {
            connection?.disconnect()
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
        Thread {
            val result = try {
                val method = Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                method.isAccessible = true
                val process = method.invoke(
                    null,
                    arrayOf("sh", "-c", command),
                    null,
                    null
                ) as Process

                val stdout = process.inputStream.bufferedReader().use { it.readText() }
                val stderr = process.errorStream.bufferedReader().use { it.readText() }
                buildString {
                    append(stdout)
                    if (stderr.isNotBlank()) append(stderr)
                }
            } catch (e: Throwable) {
                log("Erro ao executar shell via Shizuku: ${e.message}")
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

    private fun detectVersions() {
        val normalVersion = try {
            packageManager.getPackageInfo(PKG_NORMAL, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val maxVersion = try {
            packageManager.getPackageInfo(PKG_MAX, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        val normalOk = normalVersion != null
        val maxOk = maxVersion != null

        tvVersionNormal.text = if (normalOk) {
            "Normal: INSTALADO ($normalVersion)"
        } else {
            "Normal: NÃO INSTALADO ($PKG_NORMAL)"
        }
        tvVersionMax.text = if (maxOk) {
            "Max: INSTALADO ($maxVersion)"
        } else {
            "Max: NÃO INSTALADO ($PKG_MAX)"
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
        title.text = if (directionMaxToNormal) {
            "TRANSFERIR FF MAX  →  FF NORMAL"
        } else {
            "TRANSFERIR FF NORMAL  →  FF MAX"
        }
        logBox.text = ""
        btnBypass.visibility = View.GONE
        btnPassar.visibility = View.VISIBLE
        btnPassar.isEnabled = true
    }

    private fun startTransfer() {
        btnPassar.isEnabled = false
        if (currentKey.isBlank()) {
            btnPassar.isEnabled = true
            fadeOut(screenTransfer)
            fadeIn(screenLogin)
            tvKeyStatus.text = "Valide sua Key antes de transferir."
            tvKeyStatus.setTextColor(0xFFFF5252.toInt())
            return
        }
        log("Revalidando autorização no servidor...")
        validateKey(
            currentKey,
            onSuccess = { executeReplayTransfer() },
            onFailure = {
                log("A API não autorizou esta transferência.")
                btnPassar.isEnabled = true
            }
        )
    }

    private fun executeReplayTransfer() {
        val srcDir = if (directionMaxToNormal) PATH_MAX else PATH_NORMAL
        val dstDir = if (directionMaxToNormal) PATH_NORMAL else PATH_MAX
        val targetVersion = if (directionMaxToNormal) "1.132.21" else "2.132.21"
        log("Verificando Shizuku...")
        log("Origem: $srcDir")
        log("Destino: $dstDir")

        executePrivileged(buildTransferCommand(srcDir, dstDir, targetVersion)) { result ->
            if (result != null) {
                result.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { line -> log(line) }

                if (result.contains("[RESULT] TRANSFER_OK")) {
                    log("Transferência concluída com sucesso.")
                    log("Version aplicada: $targetVersion")
                    btnPassar.visibility = View.GONE
                    btnBypass.visibility = View.VISIBLE
                    btnBypass.alpha = 0f
                    btnBypass.animate().alpha(1f).setDuration(350).start()
                } else {
                    log("Falha: a transferência não foi concluída.")
                    btnPassar.isEnabled = true
                }
            } else {
                log("Falha: Shizuku não executou o comando.")
                btnPassar.isEnabled = true
            }
        }
    }

    private fun buildTransferCommand(srcDir: String, dstDir: String, targetVersion: String): String {
        return """
            set -u
            SRC_DIR='$srcDir'
            DST_DIR='$dstDir'
            TARGET_VERSION='$targetVersion'
            MARKER="${'$'}DST_DIR/.transfer_interna"
            RECENT_BIN=''
            RECENT_JSON=''
            JSON_FILE=''
            JSON_TMP=''

            fail() {
                echo "[ERROR] ${'$'}1"
                exit 1
            }

            cleanup() {
                rm -f "${'$'}MARKER" "${'$'}JSON_TMP" 2>/dev/null || true
            }

            trap cleanup EXIT INT TERM HUP

            echo "[PROGRESS] Verificando pasta de origem"
            [ -d "${'$'}SRC_DIR" ] || fail "Pasta de origem não existe"

            echo "[PROGRESS] Procurando replay mais recente"
            RECENT_BIN=${'$'}(ls -1t "${'$'}SRC_DIR"/*_rep.bin 2>/dev/null | head -n 1)
            [ -n "${'$'}RECENT_BIN" ] || fail "Nenhum arquivo _rep.bin encontrado"

            RECENT_JSON="${'$'}{RECENT_BIN%_rep.bin}_rep.json"
            [ -f "${'$'}RECENT_JSON" ] || fail "JSON correspondente não encontrado"

            echo "[PROGRESS] Verificando BIN e JSON"
            BIN_NAME=${'$'}(basename "${'$'}RECENT_BIN")
            JSON_NAME=${'$'}(basename "${'$'}RECENT_JSON")
            JSON_FILE="${'$'}DST_DIR/${'$'}JSON_NAME"
            JSON_TMP="${'$'}JSON_FILE.tmp"

            echo "[PROGRESS] Criando pasta de destino"
            mkdir -p "${'$'}DST_DIR" || fail "Não foi possível criar a pasta de destino"

            echo "[PROGRESS] Ativando marcador"
            touch "${'$'}MARKER" || fail "Não foi possível criar o marcador"

            echo "[PROGRESS] Copiando arquivos"
            cp -f "${'$'}RECENT_BIN" "${'$'}DST_DIR/${'$'}BIN_NAME" || fail "Falha ao copiar o BIN"
            cp -f "${'$'}RECENT_JSON" "${'$'}JSON_FILE" || fail "Falha ao copiar o JSON"

            echo "[PROGRESS] Atualizando Version"
            VERSION_COUNT=${'$'}(grep -Eo '"[Vv]ersion"[[:space:]]*:' "${'$'}JSON_FILE" 2>/dev/null | wc -l | tr -d ' ')
            [ "${'$'}VERSION_COUNT" = "1" ] || fail "Campo Version ausente ou duplicado no JSON"
            sed -E "s/(\"[Vv]ersion\"[[:space:]]*:[[:space:]]*)\"[^\"]*\"/\\1\"${'$'}TARGET_VERSION\"/" "${'$'}JSON_FILE" > "${'$'}JSON_TMP" || fail "Falha ao atualizar o JSON"
            grep -Eq '"[Vv]ersion"[[:space:]]*:[[:space:]]*"'"${'$'}TARGET_VERSION"'"' "${'$'}JSON_TMP" || fail "Version não foi atualizada"
            mv -f "${'$'}JSON_TMP" "${'$'}JSON_FILE" || fail "Falha ao salvar o JSON atualizado"

            echo "[PROGRESS] Ajustando permissões e datas"
            chmod 666 "${'$'}DST_DIR/${'$'}BIN_NAME" "${'$'}JSON_FILE" 2>/dev/null || echo "[INFO] chmod não permitido; arquivos mantidos"
            touch -r "${'$'}RECENT_BIN" "${'$'}DST_DIR/${'$'}BIN_NAME" || fail "Falha ao preservar a data do BIN"
            touch -r "${'$'}RECENT_JSON" "${'$'}JSON_FILE" || fail "Falha ao preservar a data do JSON"

            echo "[PROGRESS] Validando arquivos"
            [ -s "${'$'}DST_DIR/${'$'}BIN_NAME" ] || fail "BIN de destino vazio ou ausente"
            [ -s "${'$'}JSON_FILE" ] || fail "JSON de destino vazio ou ausente"
            SRC_BIN_SIZE=${'$'}(stat -c %s "${'$'}RECENT_BIN" 2>/dev/null) || fail "Não foi possível ler o tamanho do BIN original"
            DST_BIN_SIZE=${'$'}(stat -c %s "${'$'}DST_DIR/${'$'}BIN_NAME" 2>/dev/null) || fail "Não foi possível ler o tamanho do BIN copiado"
            [ "${'$'}SRC_BIN_SIZE" = "${'$'}DST_BIN_SIZE" ] || fail "O tamanho do BIN copiado não confere"

            echo "[PROGRESS] Removendo marcador"
            rm -f "${'$'}MARKER" || fail "Não foi possível remover o marcador"
            echo "[RESULT] TRANSFER_OK"
        """.trimIndent()
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
