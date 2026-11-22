package com.passador.replay

import android.content.Context
import android.util.Log

/**
 * Gerenciador de conexão local do aplicativo.
 *
 * A conexão ADB sem fio precisa ser pareada e autorizada pelo usuário nas
 * configurações do Android. O projeto não embute uma implementação de ADB
 * proprietária nem executa shell remoto arbitrário.
 */
class AdbManager(private val context: Context) {

    fun pair(host: String, port: Int, code: String): Boolean {
        Log.i(TAG, "Pareamento solicitado para $host:$port")
        return host.isNotBlank() && port in 1..65535 && code.length == 6
    }

    fun connect(): Boolean {
        Log.i(TAG, "Conexão ADB deve ser autorizada pelo usuário nas configurações do Android")
        return false
    }

    fun executeCommand(command: String): String? {
        Log.w(TAG, "Execução de shell remoto não está habilitada: $command")
        return null
    }

    fun disconnect() {
        Log.i(TAG, "Conexão encerrada")
    }

    private companion object {
        const val TAG = "AdbManager"
    }
}
