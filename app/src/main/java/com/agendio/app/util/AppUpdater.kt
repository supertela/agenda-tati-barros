package com.agendio.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String
)

object AppUpdater {

    /**
     * Substitua esta URL pelo arquivo versao.json hospedado por você.
     * Exemplo com GitHub:
     * https://raw.githubusercontent.com/SEU_USUARIO/SEU_REPOSITORIO/main/versao.json
     */
    const val UPDATE_JSON_URL =
        "https://raw.githubusercontent.com/SEU_USUARIO/SEU_REPOSITORIO/main/versao.json"

    fun checkForUpdate(
        context: Context,
        onResult: (UpdateInfo?, String?) -> Unit
    ) {
        Thread {
            try {
                if (UPDATE_JSON_URL.contains("SEU_USUARIO")) {
                    Handler(Looper.getMainLooper()).post {
                        onResult(null, "Configure a URL de atualização em AppUpdater.UPDATE_JSON_URL.")
                    }
                    return@Thread
                }
                val json = fetchText(UPDATE_JSON_URL)
                val obj = JSONObject(json)
                val info = UpdateInfo(
                    versionCode = obj.getInt("versionCode"),
                    versionName = obj.optString("versionName", "2.0"),
                    apkUrl = obj.getString("apkUrl"),
                    sha256 = obj.optString("sha256", "")
                )
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val current = PackageInfoCompat.getLongVersionCode(packageInfo).toInt()
                Handler(Looper.getMainLooper()).post {
                    if (info.versionCode > current) onResult(info, null) else onResult(null, null)
                }
            } catch (exception: Exception) {
                Handler(Looper.getMainLooper()).post {
                    onResult(null, exception.message ?: "Não foi possível verificar a atualização.")
                }
            }
        }.start()
    }

    fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        onStatus: (String) -> Unit
    ) {
        Thread {
            try {
                if (info.apkUrl.isBlank()) {
                    notify(onStatus, "URL do APK não informada.")
                    return@Thread
                }
                if (info.sha256.isBlank()) {
                    notify(onStatus, "Hash SHA-256 não informado. Atualização cancelada.")
                    return@Thread
                }

                notify(onStatus, "Baixando atualização...")
                val updateDir = File(context.cacheDir, "update").apply { mkdirs() }
                val apkFile = File(updateDir, "AgendaTatiBarros-update.apk").apply {
                    if (exists()) delete()
                }
                download(info.apkUrl, apkFile)

                notify(onStatus, "Verificando arquivo...")
                if (!verifySha256(apkFile, info.sha256)) {
                    apkFile.delete()
                    notify(onStatus, "O arquivo baixado não passou na verificação de segurança.")
                    return@Thread
                }

                notify(onStatus, "Abrindo instalador...")
                install(context, apkFile)
            } catch (exception: Exception) {
                notify(onStatus, "Falha na atualização: ${exception.message ?: "erro desconhecido"}")
            }
        }.start()
    }

    private fun notify(onStatus: (String) -> Unit, message: String) {
        Handler(Looper.getMainLooper()).post { onStatus(message) }
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "AgendaTatiBarros-Updater")
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Servidor retornou ${connection.responseCode}")
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun download(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "AgendaTatiBarros-Updater")
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Servidor retornou ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
    }

    private fun verifySha256(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        return actual.equals(expected.trim(), ignoreCase = true)
    }

    private fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Autorize a instalação de apps desconhecidos e tente novamente.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
