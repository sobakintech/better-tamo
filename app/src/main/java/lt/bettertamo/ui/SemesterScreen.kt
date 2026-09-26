@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.*

@Composable
fun SubjectsOverview(openSubject: (String) -> Unit) {
    val vm = LocalPlanner.current
    val school = LocalSchoolData.current
    val periods by vm.periods.collectAsStateWithLifecycle()
    val rawSubjects by vm.semesterSubjects.collectAsStateWithLifecycle()
    val loaded by vm.loadedRequests.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val chosen by vm.chosenPeriod.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val selected = periods.find { it.id == chosen } ?: periods.find { it.selected } ?: periods.firstOrNull()
    val semester = rawSubjects.takeIf { loaded["semester"] == selected?.id }.orEmpty()
    RefreshOnResume(Unit) { vm.loadPeriods(); vm.loadSchoolYear() }
    RefreshOnResume(selected?.id) { selected?.let { vm.loadSemester(it.id) } }
    val yearDiary = school.diary.filter { it.date >= schoolYearStart() }
    val subjects = subjectOverviews(semester, yearDiary, school.lessons)
    val overall = overallAverage(subjects)
    val semesterComplete = selected?.let { readComplete("semester", it.id) } ?: false
    val yearComplete = readComplete("year", schoolYearStart().toString())
    val periodsComplete = readComplete("periods")
    PullToRefreshBox(
        isRefreshing = listOf("periods", "semester", "year").any { it in loading },
        onRefresh = { vm.loadPeriods(true); selected?.let { vm.loadSemester(it.id, true) }; vm.loadSchoolYear(true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize().testTag("grades-list"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        Surface(onClick = { menu = true }, enabled = periods.isNotEmpty(), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp).semantics { contentDescription = "Laikotarpis: ${selected?.title ?: "nepasirinktas"}" }) {
                            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.DateRange, null, Modifier.size(18.dp))
                                Text(selected?.title ?: "Laikotarpis", style = MaterialTheme.typography.titleSmall)
                                Icon(Icons.Outlined.ExpandMore, null, Modifier.size(20.dp))
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            periods.forEach { period ->
                                DropdownMenuItem(text = { Text(period.title) }, onClick = { vm.chosenPeriod.value = period.id; menu = false },
                                    trailingIcon = if (period.id == selected?.id) ({ Icon(Icons.Outlined.Check, null) }) else null)
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    ReadStatus("periods", showProgress = false) { vm.loadPeriods(true) }
                    ReadStatus("semester", showProgress = false) { selected?.let { vm.loadSemester(it.id, true) } }
                    ReadStatus("year", showProgress = false) { vm.loadSchoolYear(true) }
                }
            }
            if (subjects.isNotEmpty()) item { OverviewCard(overall, subjects.count { it.average != null }, yearDiary) }
            items(subjects, key = { it.name }) { subject -> SubjectRow(subject) { openSubject(subject.name) } }
            if (subjects.isEmpty() && (semesterComplete || (periods.isEmpty() && periodsComplete)) && yearComplete) item {
                EmptyPanel(Icons.Outlined.School, "Pažymių nėra", "Pasirinktu laikotarpiu dalykų suvestinės nėra.")
            }
        }
    }
}

@Composable
private fun OverviewCard(overall: Double?, graded: Int, diary: List<DiaryEntry>) {
    val grades = diary.count { it.kind == DiaryKind.GRADE }
    val formatives = diary.count { it.kind == DiaryKind.FORMATIVE }
    val missed = diary.count { it.missed }
    Card(Modifier.padding(bottom = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatAverage(overall), style = MaterialTheme.typography.headlineLarge, color = averageColor(overall), modifier = Modifier.testTag("overall-average"))
                Text("Bendras vidurkis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Įvertinti dalykai: $graded", style = MaterialTheme.typography.bodyMedium)
                Text("Pažymiai: $grades · kaupiamieji: $formatives", style = MaterialTheme.typography.bodyMedium)
                Text("Praleistos pamokos: $missed", style = MaterialTheme.typography.bodyMedium, color = if (missed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text("Šiais mokslo metais", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SubjectRow(subject: SubjectOverview, onClick: () -> Unit) {
    val recent = subject.entries.filter { it.kind != DiaryKind.ATTENDANCE }.take(6)
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(subject.name, style = MaterialTheme.typography.titleMedium)
                if (subject.teacher.isNotBlank()) Text(subject.teacher, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (recent.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    recent.forEach { entry ->
                        if (entry.kind == DiaryKind.FORMATIVE) SmallTag("(${entry.value})", MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.primary)
                        else SmallTag(entry.value, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatAverage(subject.average), style = MaterialTheme.typography.titleLarge, color = averageColor(subject.average))
                if (subject.finalGrade.isNotBlank()) Text("Išvesta: ${subject.finalGrade}", style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
