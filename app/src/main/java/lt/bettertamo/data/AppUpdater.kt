package lt.bettertamo.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.SystemClock
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import lt.bettertamo.BuildConfig
import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection

data class AppRelease(val version: String, val url: String, val size: Long)

sealed interface AppUpdate {
    data object Idle : AppUpdate
    data object Checking : AppUpdate
    data object Latest : AppUpdate
    data class Available(val release: AppRelease) : AppUpdate
    data class Downloading(val release: AppRelease, val progress: Float?) : AppUpdate
    data class Confirm(val release: AppRelease, val intent: Intent) : AppUpdate
    data class Installing(val release: AppRelease) : AppUpdate
    data class Failed(val message: String, val release: AppRelease?) : AppUpdate
}

private val versionPattern = Regex("""\d{4}\.\d{2}\.\d{2}\.\d{4}""")

internal fun newerVersion(candidate: String, installed: String) =
    versionPattern.matches(candidate) && versionPattern.matches(installed) && candidate > installed

internal fun parseRelease(json: JsonObject): AppRelease? {
    if (json["draft"] == JsonPrimitive(true) || json["prerelease"] == JsonPrimitive(true)) return null
    val version = json.string("tag_name").removePrefix("v").takeIf { versionPattern.matches(it) } ?: return null
    val asset = json.list("assets").firstOrNull { it.string("name").endsWith(".apk") && it.string("browser_download_url").startsWith("https://github.com/") } ?: return null
    return AppRelease(version, asset.string("browser_download_url"), asset.string("size").toLongOrNull() ?: 0L)
}

object AppUpdater {
    const val REPOSITORY = "https://github.com/sobakintech/better-tamo"
    private const val LATEST = "https://api.github.com/repos/sobakintech/better-tamo/releases/latest"
    val state = MutableStateFlow<AppUpdate>(AppUpdate.Idle)
    val dialog = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var checkedAt = 0L

    private fun prefs(context: Context) = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
    private fun busy(update: AppUpdate) = update is AppUpdate.Checking || update is AppUpdate.Downloading || update is AppUpdate.Confirm || update is AppUpdate.Installing

    fun check(context: Context, manual: Boolean, force: Boolean = false) {
        if (busy(state.value)) return
        if (!manual && checkedAt != 0L && SystemClock.elapsedRealtime() - checkedAt < 3_600_000) return
        val app = context.applicationContext
        val previous = state.value
        state.value = AppUpdate.Checking
        job = scope.launch {
            state.value = try {
                val release = latest()
                checkedAt = SystemClock.elapsedRealtime()
                if (release != null && (force || newerVersion(release.version, BuildConfig.VERSION_NAME))) {
                    if (manual || prefs(app).getString("dismissed", null) != release.version) dialog.value = true
                    AppUpdate.Available(release)
                } else AppUpdate.Latest
            } catch (e: CancellationException) { state.value = previous; throw e }
            catch (_: Exception) { if (manual) AppUpdate.Failed("Nepavyko patikrinti atnaujinimų. Patikrinkite interneto ryšį.", null) else previous }
        }
    }

    fun dismiss(context: Context) {
        dialog.value = false
        when (val update = state.value) {
            is AppUpdate.Available -> prefs(context).edit { putString("dismissed", update.release.version) }
            is AppUpdate.Failed -> state.value = update.release?.let { AppUpdate.Available(it) } ?: AppUpdate.Idle
            else -> {}
        }
    }

    fun cancel() {
        if (state.value is AppUpdate.Downloading) job?.cancel()
    }

    fun install(context: Context) {
        val release = when (val update = state.value) {
            is AppUpdate.Available -> update.release
            is AppUpdate.Failed -> update.release
            else -> null
        } ?: return
        val app = context.applicationContext
        state.value = AppUpdate.Downloading(release, null)
        job = scope.launch {
            val installer = app.packageManager.packageInstaller
            var session = -1
            try {
                session = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    if (release.size > 0) setSize(release.size)
                })
                val callback = Intent(app, UpdateReceiver::class.java).setPackage(app.packageName)
                val pending = PendingIntent.getBroadcast(app, session, callback, PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                installer.openSession(session).use {
                    write(it, release)
                    state.value = AppUpdate.Installing(release)
                    it.commit(pending.intentSender)
                }
            } catch (e: CancellationException) {
                if (session != -1) runCatching { installer.abandonSession(session) }
                state.value = AppUpdate.Available(release)
                throw e
            } catch (_: Exception) {
                if (session != -1) runCatching { installer.abandonSession(session) }
                state.value = AppUpdate.Failed("Nepavyko atsisiųsti atnaujinimo. Bandykite dar kartą.", release)
            }
        }
    }

    fun confirming(update: AppUpdate.Confirm) {
        if (state.value == update) state.value = AppUpdate.Installing(update.release)
    }

    internal fun result(intent: Intent) {
        val release = when (val update = state.value) {
            is AppUpdate.Installing -> update.release
            is AppUpdate.Confirm -> update.release
            else -> return
        }
        state.value = when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                ?.let { AppUpdate.Confirm(release, it) } ?: AppUpdate.Failed("Nepavyko įdiegti atnaujinimo.", release)
            PackageInstaller.STATUS_SUCCESS -> AppUpdate.Latest
            PackageInstaller.STATUS_FAILURE_ABORTED -> AppUpdate.Available(release)
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                AppUpdate.Failed("Šis atnaujinimas pasirašytas kitu raktu nei įdiegta programėlė. Atsisiųskite jį iš GitHub.", release)
            PackageInstaller.STATUS_FAILURE_STORAGE -> AppUpdate.Failed("Įrenginyje nepakanka vietos atnaujinimui.", release)
            else -> AppUpdate.Failed("Nepavyko įdiegti atnaujinimo.", release)
        }
    }

    private fun latest(): AppRelease? {
        val connection = URI(LATEST).toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "BetterTAMO/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode == 404) return null
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return parseRelease(Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject)
        } finally { connection.disconnect() }
    }

    private suspend fun write(session: PackageInstaller.Session, release: AppRelease) {
        val connection = URI(release.url).toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "BetterTAMO/${BuildConfig.VERSION_NAME}")
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
            session.openWrite("base.apk", 0, if (total > 0) total else -1).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var percent = -1
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0 && (done * 100 / total).toInt() != percent) {
                            percent = (done * 100 / total).toInt()
                            state.value = AppUpdate.Downloading(release, done.toFloat() / total)
                        }
                    }
                    if (total > 0 && done != total) throw IOException("Incomplete download")
                }
                session.fsync(output)
            }
        } finally { connection.disconnect() }
    }
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = AppUpdater.result(intent)
}
