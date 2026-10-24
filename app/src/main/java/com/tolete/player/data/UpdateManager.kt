package com.tolete.player.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualizador interno: consulta a API pública do GitHub Releases (sem servidor próprio),
 * baixa o .apk do release e abre o instalador do Android.
 */
object UpdateManager {

    private const val REPO = "PGCR-gui/Lyron-Music-Player"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val USER_AGENT = "LyronMusicPlayer-Updater"
    private const val PREFS = "tolete"
    private const val KEY_SKIPPED_TAG = "update_skipped_tag"

    data class ReleaseInfo(
        val tag: String,
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val pageUrl: String
    )

    sealed interface State {
        data object Idle : State
        data class Checking(val manual: Boolean) : State
        data class Available(val info: ReleaseInfo) : State
        data object UpToDate : State
        data class Failed(val message: String, val manual: Boolean) : State
        data class Downloading(val info: ReleaseInfo, val progress: Float) : State
        data class ReadyToInstall(val info: ReleaseInfo, val file: File) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Volta para Idle (fecha o diálogo). */
    fun dismiss() {
        if (_state.value is State.Downloading) return
        _state.value = State.Idle
    }

    /** "Ignorar esta versão": não volta a avisar sozinho sobre esta tag. */
    fun skipVersion(context: Context, info: ReleaseInfo) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SKIPPED_TAG, info.tag).apply()
        _state.value = State.Idle
    }

    fun installedVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (_: Exception) { "0" }

    @Suppress("DEPRECATION")
    fun installedCode(context: Context): Long = try {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    } catch (_: Exception) { 0L }

    /**
     * Consulta o último release. [manual] = true quando o usuário tocou em "Verificar atualizações"
     * (aí mostramos também "já está atualizado" e erros; no automático ficamos em silêncio).
     */
    fun check(context: Context, manual: Boolean) {
        val s = _state.value
        if (s is State.Checking || s is State.Downloading) return
        val app = context.applicationContext
        _state.value = State.Checking(manual)
        job = scope.launch {
            try {
                val info = fetchLatest()
                val skipped = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY_SKIPPED_TAG, null)
                _state.value = when {
                    info == null -> State.UpToDate
                    compareVersions(info.version, installedVersion(app)) <= 0 -> State.UpToDate
                    !manual && info.tag == skipped -> State.Idle
                    else -> State.Available(info)
                }
            } catch (e: Exception) {
                _state.value = State.Failed("Não foi possível verificar atualizações.", manual)
            }
        }
    }

    fun download(context: Context, info: ReleaseInfo) {
        if (_state.value is State.Downloading) return
        val app = context.applicationContext
        _state.value = State.Downloading(info, 0f)
        job = scope.launch {
            try {
                val dir = File(app.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }           // limpa APKs antigos
                val part = File(dir, "Lyron-${info.version}.apk.part")
                val target = File(dir, "Lyron-${info.version}.apk")

                val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true      // GitHub redireciona para objects.githubusercontent.com
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "application/octet-stream")
                }
                try {
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.apkSize
                    var done = 0L
                    var lastReport = 0f
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = (done.toFloat() / total).coerceIn(0f, 1f)
                                    if (p - lastReport >= 0.01f) {
                                        lastReport = p
                                        _state.value = State.Downloading(info, p)
                                    }
                                }
                            }
                        }
                    }
                    if (total > 0 && done != total) error("Download incompleto")
                } finally {
                    conn.disconnect()
                }

                if (!part.renameTo(target)) error("Falha ao salvar o arquivo")

                // Segurança: o APK precisa ser deste mesmo app (mesmo applicationId)
                val archive = app.packageManager.getPackageArchiveInfo(target.absolutePath, 0)
                if (archive?.packageName != app.packageName) {
                    target.delete()
                    error("O arquivo baixado não é um APK válido do Lyron")
                }
                _state.value = State.ReadyToInstall(info, target)
            } catch (e: Exception) {
                _state.value = State.Failed("Falha no download. Tente novamente.", true)
            }
        }
    }

    /**
     * Abre o instalador do sistema. Se o app ainda não tem a permissão "Instalar apps
     * desconhecidos", abre a tela de ajustes para o usuário liberar e permanece em
     * ReadyToInstall (é só tocar em "Instalar" de novo ao voltar).
     * @return true se o instalador foi aberto.
     */
    fun install(context: Context, file: File): Boolean {
        val pm = context.packageManager
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }

    // ---------------------------------------------------------------- internals

    private fun fetchLatest(): ReleaseInfo? {
        val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null           // repositório ainda sem releases
            if (code !in 200..299) error("HTTP $code")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            if (tag.isBlank()) return null
            val assets = o.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
            }
            // Release sem .apk anexado: nada para instalar por aqui
            if (apkUrl.isNullOrBlank()) return null
            return ReleaseInfo(
                tag = tag,
                version = tag.trim().removePrefix("v").removePrefix("V"),
                notes = o.optString("body", ""),
                apkUrl = apkUrl,
                apkSize = apkSize,
                pageUrl = o.optString("html_url", "https://github.com/$REPO/releases")
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Compara "0.44.3" com "0.44" etc. Retorna >0 se a > b. */
    internal fun compareVersions(a: String, b: String): Int {
        fun parts(s: String) = s.trim().removePrefix("v").removePrefix("V")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }
        val pa = parts(a); val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }
}
