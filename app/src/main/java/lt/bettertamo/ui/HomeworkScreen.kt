@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import lt.bettertamo.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeworkScreen(state: PlannerState, toggle: (Homework) -> Unit) {
    val vm = LocalPlanner.current
    val school = LocalSchoolData.current
    val loading by vm.loading.collectAsStateWithLifecycle()
    val errors by vm.readErrors.collectAsStateWithLifecycle()
    var past by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    RefreshOnResume(past) { vm.loadHomework(past) }
    val today = LocalDate.now()
    val from = if (past) today.minusDays(30) else today
    val to = if (past) today.minusDays(1) else today.plusDays(30)
    val loaded = school.homeworkLoaded == "$from:$to"
    val complete = readComplete("homework", "$from:$to")
    fun completed(work: Homework) = work.completed || work.id in state.completedHomework
    val allWork = if (loaded) school.homework else emptyList()
    val done = allWork.count { completed(it) }
    val work = allWork.filter { when (filter) { 1 -> !completed(it); 2 -> completed(it); else -> true } }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Column {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Namų darbai", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                    Box {
                        Surface(onClick = { menu = true }, shape = CircleShape, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = "Laikotarpis: ${if (past) "Praėję" else "Artimiausi"}, $from–$to" }) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(if (past) Icons.Outlined.History else Icons.Outlined.Event, null, Modifier.size(18.dp))
                                Text(if (past) "Praėję" else "Artimiausi", style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            listOf(false to "Artimiausi", true to "Praėję").forEach { (value, title) ->
                                val rangeFrom = if (value) today.minusDays(30) else today
                                val rangeTo = if (value) today.minusDays(1) else today.plusDays(30)
                                val format = DateTimeFormatter.ofPattern("MM.dd")
                                DropdownMenuItem(text = {
                                    Column {
                                        Text(title)
                                        Text("${rangeFrom.format(format)}–${rangeTo.format(format)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }, onClick = { past = value; menu = false },
                                    trailingIcon = if (past == value) ({ Icon(Icons.Outlined.Check, null) }) else null)
                            }
                        }
                    }
                    KeepScreenOnButton()
                    ProfileButton()
                }
                HeaderFilters(listOf("Visi" to allWork.size, "Neatlikti" to allWork.size - done, "Atlikti" to done).map { (label, count) -> label to count.takeIf { loaded } }, filter) { filter = it }
            }
        }
        if ("homework" in errors) ReadStatus("homework", showProgress = false) { vm.loadHomework(past, true) }
        PullToRefreshBox(
            isRefreshing = "homework" in loading,
            onRefresh = { vm.loadHomework(past, true) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("Atnaujinti namų darbus") {
                    if ("homework" in loading) false else { vm.loadHomework(past, true); true }
                })
            }, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (complete && work.isEmpty()) item {
                    EmptyPanel(Icons.Outlined.TaskAlt, when (filter) { 1 -> "Viskas atlikta"; 2 -> "Atliktų darbų nėra"; else -> "Namų darbų nėra" },
                        when { allWork.isEmpty() -> "Pasirinktu laikotarpiu užduočių nėra."; filter == 1 -> "Visos šio laikotarpio užduotys pažymėtos atliktomis."; else -> "Atliktą užduotį pažymėk varnele." })
                }
                work.groupBy { it.dueDate!! }.toSortedMap(if (past) reverseOrder() else naturalOrder()).forEach { (date, entries) ->
                    item("day-$date") { DayHeading(date) }
                    items(entries, key = { it.id }) { homework ->
                        val lesson = school.origin(homework.lessonId)
                        val subject = lesson?.let { resolveSubject(it, state.rules).name } ?: homework.subject
                        HomeworkCard(homework, subject, lesson?.teacher.orEmpty(), completed(homework)) { toggle(homework) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DayHeading(date: LocalDate) {
    val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date)
    val relative = when {
        days == 0L -> "Šiandien"; days == 1L -> "Rytoj"; days == 2L -> "Poryt"; days == -1L -> "Vakar"; days == -2L -> "Užvakar"
        days > 0 -> "Po $days d."; else -> "Prieš ${-days} d."
    }
    val soon = days in 0..1
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${dayLong[date.dayOfWeek.value - 1]}, ${date.format(DateTimeFormatter.ofPattern("MM.dd"))}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(shape = CircleShape, color = if (soon) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (soon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
            Text(relative, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
}

@Composable
private fun HomeworkCard(homework: Homework, subject: String, teacher: String, done: Boolean, onToggle: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)) {
            Checkbox(done, { onToggle() }, modifier = Modifier.semantics { contentDescription = "Atlikta: $subject" })
            Column(Modifier.weight(1f).padding(top = 12.dp).alpha(if (done) 0.6f else 1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subject, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (done) TextDecoration.LineThrough else null)
                if (homework.text.isNotBlank()) SelectionContainer { Text(homework.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                homework.files.forEach { AttachmentRow(it) }
                val meta = listOfNotNull(homework.assignedDate?.let { "Užduota ${it.format(DateTimeFormatter.ofPattern("MM.dd"))}" }, teacher.takeIf { it.isNotBlank() })
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
