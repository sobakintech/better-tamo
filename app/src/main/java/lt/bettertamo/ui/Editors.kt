@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package lt.bettertamo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import lt.bettertamo.data.*
import java.util.UUID

@Composable
fun RuleEditor(subjectId: String, existing: LessonRule?, seedLesson: Lesson?, rules: List<LessonRule>, onDismiss: () -> Unit, onSave: (LessonRule) -> Unit, onDelete: (String) -> Unit, savedSources: List<RuleSource> = emptyList()) {
    val source = LocalSchoolData.current.lessons + savedSources.map { it.lesson() }
    val lessons = remember(subjectId, source) { source.filter { it.subjectId == subjectId && it.canRename }.distinctBy { it.slotKey to it.teacherId } }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var modeName by rememberSaveable { mutableStateOf((existing?.mode ?: MatchMode.SLOTS).name) }
    var slots by rememberSaveable { mutableStateOf(existing?.slots?.toList() ?: listOfNotNull(seedLesson?.slotKey)) }
    var teacher by rememberSaveable { mutableStateOf(existing?.teacherId?.ifBlank { null } ?: seedLesson?.teacherId ?: lessons.firstOrNull { it.teacherId.isNotBlank() }?.teacherId.orEmpty()) }
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    val mode = MatchMode.valueOf(modeName)
    val rule = LessonRule(id, subjectId, name.trim(), mode, slots.toSet(), teacher)
    val matches = lessons.filter { rule.matches(it) }
    val conflict = ruleConflict(rule, rules)
    var confirmDelete by remember { mutableStateOf(false) }

    EditorFrame(if (existing == null) "Pervadinti pamokas" else "Keisti pavadinimą", onDismiss,
        saveEnabled = validRule(rule) && (matches.isNotEmpty() || existing != null) && !conflict,
        onSave = { onSave(rule) },
    ) {
        Text(lessons.firstOrNull()?.subject ?: existing?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = name, onValueChange = { if (it.length <= 60) name = it }, label = { Text("Naujas pavadinimas") }, placeholder = { Text("Pvz., Biologija") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Kurias pamokas pervadinti?", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(MatchMode.SLOTS to "Pagal savaitę", MatchMode.TEACHER to "Pagal mokytoją").forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = mode == value, onClick = { modeName = value.name }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
            }
        }
        if (mode == MatchMode.SLOTS) {
            Text("Pasirink pamokų laikus. Pavadinimas bus taikomas kas savaitę.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lessons.filter { it.slot in 1..30 }.distinctBy { it.slotKey }.groupBy { it.weekday }.toSortedMap().forEach { (day, dayLessons) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(dayLong[day - 1], style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dayLessons.forEach { lesson ->
                            FilterChip(
                                selected = lesson.slotKey in slots,
                                onClick = { slots = if (lesson.slotKey in slots) slots - lesson.slotKey else slots + lesson.slotKey },
                                label = { Text("${lesson.slot} pamoka · ${lesson.start}") },
                                leadingIcon = if (lesson.slotKey in slots) ({ Icon(Icons.Outlined.Check, null, Modifier.size(16.dp)) }) else null,
                                modifier = Modifier.semantics { contentDescription = "${dayLong[day - 1]}, ${lesson.slot} pamoka, ${lesson.start}" },
                            )
                        }
                    }
                }
            }
        } else {
            Text("Visos šio dalyko pamokos su pasirinktu mokytoju.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            lessons.filter { it.teacherId.isNotBlank() }.distinctBy { it.teacherId }.forEach { lesson ->
                Surface(onClick = { teacher = lesson.teacherId }, shape = RoundedCornerShape(16.dp), color = if (teacher == lesson.teacherId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = teacher == lesson.teacherId, onClick = null)
                        Column {
                            Text(lesson.teacher, style = MaterialTheme.typography.titleMedium)
                            Text(lessons.filter { it.teacherId == lesson.teacherId }.joinToString(" · ") { "${dayShort[it.weekday - 1]} ${it.slot}" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (conflict) {
            Text("Šioms pamokoms jau yra tokio tipo taisyklė. Redaguok esamą taisyklę arba pasirink kitas pamokas.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("PERŽIŪRA", style = MaterialTheme.typography.labelMedium)
                Text(name.ifBlank { "Naujas pavadinimas" }, style = MaterialTheme.typography.titleLarge)
                Text("Pasirinkta pamokų per savaitę: ${matches.size}", style = MaterialTheme.typography.bodyMedium)
                Text("Taikoma tvarkaraštyje ir susietuose namų darbuose.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (mode == MatchMode.TEACHER && matches.any { lesson -> rules.any { it.id != rule.id && it.mode == MatchMode.SLOTS && it.matches(lesson) } }) {
            Text("Atskirai pervadinti savaitės laikai turi pirmenybę prieš mokytojo taisyklę.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (existing != null) TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pašalinti taisyklę")
        }
    }
    if (confirmDelete) DeleteDialog("Pašalinti taisyklę?", "Pamokoms vėl bus taikomas originalus pavadinimas arba kita tinkanti taisyklė.", { confirmDelete = false }) { existing?.let { onDelete(it.id) } }
}

@Composable
fun EventEditor(existing: CustomEvent?, defaultDay: Int, onDismiss: () -> Unit, onSave: (CustomEvent) -> Unit, onDelete: (String) -> Unit, defaultStart: String = "15:10", defaultEnd: String = "15:55", defaultSlot: Int = 0) {
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var days by rememberSaveable { mutableStateOf(existing?.weekdays?.toList() ?: listOf(defaultDay)) }
    var start by rememberSaveable { mutableStateOf(existing?.start ?: defaultStart) }
    var end by rememberSaveable { mutableStateOf(existing?.end ?: defaultEnd) }
    val slot = existing?.slot ?: defaultSlot
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var pickTime by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val event = CustomEvent(id, title.trim(), days.toSet(), start, end, existing?.room.orEmpty(), note.trim(), slot)

    EditorFrame(when { existing != null -> "Redaguoti įvykį"; slot > 0 -> "Nauja pamoka"; else -> "Naujas įvykis" }, onDismiss, validEvent(event), { onSave(event) }) {
        Text(if (slot > 0) "$slot pamoka. Pridėk pamoką, kurios nėra TAMO tvarkaraštyje." else "Pridėk tai, ko trūksta tavo savaitėje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value = title, onValueChange = { if (it.length <= 80) title = it }, label = { Text("Pavadinimas") }, placeholder = { Text(if (slot > 0) "Pvz., Biologijos modulis" else "Pvz., Klasės valandėlė") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Kartoti kas savaitę", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dayShort.forEachIndexed { index, label ->
                FilterChip(selected = index + 1 in days, onClick = { days = if (index + 1 in days) days - (index + 1) else days + (index + 1) }, label = { Text(label) }, modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = dayLong[index] })
            }
        }
        if (days.isEmpty()) Text("Pasirink bent vieną savaitės dieną.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TimeField("Pradžia", start, Modifier.weight(1f)) { pickTime = "start" }
            TimeField("Pabaiga", end, Modifier.weight(1f)) { pickTime = "end" }
        }
        if (end <= start) Text("Pabaiga turi būti vėliau už pradžią.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(value = note, onValueChange = { if (it.length <= 1000) note = it }, label = { Text("Pastaba (nebūtina)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Repeat, null)
                Text("Įvykis bus rodomas tvarkaraštyje kiekvieną pasirinktą savaitės dieną.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (existing != null) TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pašalinti įvykį")
        }
    }
    pickTime?.let { which ->
        val initial = (if (which == "start") start else end).split(":")
        val time = rememberTimePickerState(initialHour = initial[0].toInt(), initialMinute = initial[1].toInt(), is24Hour = true)
        AlertDialog(onDismissRequest = { pickTime = null }, title = { Text(if (which == "start") "Pradžia" else "Pabaiga") },
            text = { TimeInput(state = time) },
            confirmButton = { TextButton(onClick = {
                val value = "%02d:%02d".format(time.hour, time.minute)
                if (which == "start") start = value else end = value
                pickTime = null
            }) { Text("Pasirinkti") } },
            dismissButton = { TextButton(onClick = { pickTime = null }) { Text("Atšaukti") } },
        )
    }
    if (confirmDelete) DeleteDialog("Pašalinti įvykį?", "Įvykis bus pašalintas iš visų savaičių.", { confirmDelete = false }) { existing?.let { onDelete(it.id) } }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = modifier.semantics { contentDescription = "$label, $value" }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun EditorFrame(title: String, onDismiss: () -> Unit, saveEnabled: Boolean, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = lightBars
                    isAppearanceLightNavigationBars = lightBars
                }
            }
        }
        Scaffold(
            modifier = Modifier.fillMaxSize().imePadding(),
            topBar = { TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Atšaukti") } }) },
            bottomBar = {
                Surface(shadowElevation = 2.dp) {
                    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.widthIn(max = 672.dp).fillMaxWidth().heightIn(min = 52.dp)) { Text("Išsaugoti") }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            }
        }
    }
}

@Composable
private fun DeleteDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = confirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Pašalinti") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Atšaukti") } },
    )
}
