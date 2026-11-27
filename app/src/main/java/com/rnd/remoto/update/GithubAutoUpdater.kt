package com.rnd.remoto.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "GithubAutoUpdater"
private const val GITHUB_REPO = "RonMarty2/control-android"
private const val LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

data class GithubReleaseUpdate(
    val versionName: String,
    val notes: String,
    val apkDownloadUrl: String
)

/**
 * Consulta automáticamente el repositorio público de GitHub (RonMarty2/control-android)
 * para detectar si hay un nuevo APK publicado en Releases, descargarlo con progreso
 * y abrir el instalador nativo de Android (compatible desde Android 5.1 hasta Android 14+).
 */
object GithubAutoUpdater {

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun currentVersionName(context: Context): String {
        return runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    /**
     * Consulta la última Release de GitHub y devuelve [GithubReleaseUpdate] solo si la versión
     * publicada en GitHub es mayor que la instalada actualmente en el celular.
     */
    suspend fun checkForUpdate(context: Context): GithubReleaseUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val current = currentVersionName(context)
            val req = Request.Builder()
                .url(LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ControlRemoto-Android/$current")
                .get()
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                val tagName = json.optString("tag_name").removePrefix("v").removePrefix("V").trim()
                if (tagName.isBlank()) return@use null
                if (!isNewerVersion(current, tagName)) return@use null

                val assets = json.optJSONArray("assets") ?: return@use null
                var apkUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    val url = asset.optString("browser_download_url")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkUrl = url
                        break
                    }
                }
                val downloadUrl = apkUrl ?: return@use null
                val bodyNotes = json.optString("body").trim()
                GithubReleaseUpdate(
                    versionName = tagName,
                    notes = bodyNotes,
                    apkDownloadUrl = downloadUrl
                )
            }
        }.onFailure { e ->
            Log.d(TAG, "No se pudo verificar actualización en GitHub: ${e.message}")
        }.getOrNull()
    }

    /**
     * Descarga el APK de la Release y lanza el instalador del sistema.
     */
    suspend fun downloadAndInstall(
        context: Context,
        update: GithubReleaseUpdate,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(update.apkDownloadUrl)
                .header("User-Agent", "ControlRemoto-Android")
                .get()
                .build()

            // En Android 5.1 / 6.0 (donde un instalador externo de sistema lee el archivo),
            // externalCacheDir es accesible para PackageInstaller; en Android 7+ usamos FileProvider.
            val baseCache = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                context.externalCacheDir ?: context.cacheDir
            } else {
                context.cacheDir
            }
            val updatesDir = File(baseCache, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "ControlRemoto-${update.versionName}.apk")
            if (targetFile.exists()) targetFile.delete()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Error al descargar actualización (HTTP ${resp.code})")
                val body = resp.body ?: error("Archivo APK vacío")
                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var read: Int
                        var lastReported = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val pct = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReported) {
                                    lastReported = pct
                                    onProgress(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            }

            // En Android < 7 dejar permisos de lectura para el instalador del sistema
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                runCatching { targetFile.setReadable(true, false) }
            }

            withContext(Dispatchers.Main) {
                launchApkInstaller(context, targetFile)
            }
        }
    }

    private fun launchApkInstaller(context: Context, apkFile: File) {
        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
        } else {
            Uri.fromFile(apkFile)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Compara versiones semánticas tipo "1.1.0" vs "1.2.0". */
    internal fun isNewerVersion(current: String, remote: String): Boolean {
        val cParts = current.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val rParts = remote.removePrefix("v").split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(cParts.size, rParts.size)
        for (i in 0 until maxLen) {
            val c = cParts.getOrElse(i) { 0 }
            val r = rParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
