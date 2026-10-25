package lt.bettertamo.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import lt.bettertamo.data.*
import java.time.LocalDate

@Composable
fun SettingsExportDialog(state: PlannerState, accent: String?, onDismiss: () -> Unit) {
    val vm = LocalPlanner.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember(state, accent) { settingsExport(state, accent, SettingsPart.entries.toSet(), "") }
    val shown = SettingsPart.entries.filter { available.count(it) != 0 }
    var parts by remember { mutableStateOf(shown.toSet()) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            if (vm.exportSettings(uri, parts)) {
                android.widget.Toast.makeText(context, "Nustatymai eksportuoti", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Eksportuoti nustatymus") }, text = {
        PartChoices("Pasirink, ką įtraukti. Failą galėsi importuoti kitame įrenginyje.", available, shown, parts) { parts = it }
    }, confirmButton = {
        TextButton(onClick = { save.launch("better-tamo-nustatymai-${LocalDate.now()}.json") }, enabled = parts.isNotEmpty()) { Text("Eksportuoti") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } })
}

@Composable
fun SettingsImportDialog(export: SettingsExport, onDismiss: () -> Unit) {
    val vm = LocalPlanner.current
    val context = LocalContext.current
    var parts by remember(export) { mutableStateOf(export.parts) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Importuoti nustatymus") }, text = {
        PartChoices("Pasirinktos dalys pakeis dabartines.", export, SettingsPart.entries.filter(export::has), parts) { parts = it }
    }, confirmButton = {
        TextButton(onClick = {
            vm.applySettings(export, parts)
            android.widget.Toast.makeText(context, "Nustatymai importuoti", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        }, enabled = parts.isNotEmpty()) { Text("Importuoti") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Atšaukti") } })
}

@Composable
fun rememberSettingsImport(onRead: (SettingsExport) -> Unit): () -> Unit {
    val vm = LocalPlanner.current
    val scope = rememberCoroutineScope()
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { vm.readSettings(uri)?.let(onRead) }
    }
    return { open.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
}

@Composable
private fun PartChoices(description: String, source: SettingsExport, shown: List<SettingsPart>, selected: Set<SettingsPart>, onChange: (Set<SettingsPart>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
        shown.forEach { part ->
            val checked = part in selected
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) { onChange(if (it) selected + part else selected - part) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked, null)
                Text(part.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                source.count(part)?.let { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}
