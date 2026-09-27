package lt.bettertamo.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.BuildConfig
import lt.bettertamo.data.AppRelease
import lt.bettertamo.data.AppUpdate
import lt.bettertamo.data.AppUpdater
import java.util.Locale

internal fun updateSummary(update: AppUpdate): String = when (update) {
    AppUpdate.Idle -> "Ieškoti naujos versijos"
    AppUpdate.Checking -> "Tikrinama…"
    AppUpdate.Latest -> "Įdiegta naujausia versija"
    is AppUpdate.Available -> "Galima atnaujinti į ${update.release.version}"
    is AppUpdate.Downloading -> "Atsisiunčiama" + (update.progress?.let { " ${(it * 100).toInt()} %" } ?: "…")
    is AppUpdate.Confirm, is AppUpdate.Installing -> "Diegiama…"
    is AppUpdate.Failed -> update.message
}

private fun describe(release: AppRelease) = buildString {
    append("Išleista Better TAMO ${release.version}. Dabar įdiegta ${BuildConfig.VERSION_NAME}.")
    if (release.size > 0) append(String.format(Locale.forLanguageTag("lt"), " Atsisiuntimas %.1f MB.", release.size / 1_048_576.0))
}

@Composable
fun AppUpdatePrompt() {
    val context = LocalContext.current
    val update by AppUpdater.state.collectAsStateWithLifecycle()
    val open by AppUpdater.dialog.collectAsStateWithLifecycle()
    RefreshOnResume(Unit) { if (!BuildConfig.DEBUG) AppUpdater.check(context, manual = false) }
    LaunchedEffect(update) {
        (update as? AppUpdate.Confirm)?.let {
            runCatching { context.startActivity(it.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            AppUpdater.confirming(it)
        }
    }
    val allowed = { context.packageManager.canRequestPackageInstalls() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (allowed()) AppUpdater.install(context) }
    fun install() {
        if (allowed()) AppUpdater.install(context)
        else permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
    }
    if (!open) return
    val current = update
    when (current) {
        is AppUpdate.Available -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss(context) },
            title = { Text("Yra nauja versija") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(describe(current.release))
                    if (!allowed()) Text("Android paprašys leisti Better TAMO diegti programas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = ::install) { Text("Atnaujinti") } },
            dismissButton = { TextButton(onClick = { AppUpdater.dismiss(context) }) { Text("Vėliau") } },
        )
        is AppUpdate.Downloading, is AppUpdate.Confirm, is AppUpdate.Installing -> AlertDialog(
            onDismissRequest = { AppUpdater.dialog.value = false },
            title = { Text(if (current is AppUpdate.Downloading) "Atsisiunčiama" else "Diegiama") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(updateSummary(current))
                    val progress = (current as? AppUpdate.Downloading)?.progress
                    if (progress != null) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { AppUpdater.dialog.value = false }) { Text("Slėpti") } },
            dismissButton = { if (current is AppUpdate.Downloading) TextButton(onClick = AppUpdater::cancel) { Text("Atšaukti") } },
        )
        is AppUpdate.Failed -> AlertDialog(
            onDismissRequest = { AppUpdater.dismiss(context) },
            title = { Text("Atnaujinti nepavyko") },
            text = { Text(current.message) },
            confirmButton = { if (current.release != null) TextButton(onClick = ::install) { Text("Bandyti dar kartą") } else TextButton(onClick = { AppUpdater.dismiss(context) }) { Text("Gerai") } },
            dismissButton = { if (current.release != null) TextButton(onClick = { AppUpdater.dismiss(context) }) { Text("Uždaryti") } },
        )
        else -> {}
    }
}
