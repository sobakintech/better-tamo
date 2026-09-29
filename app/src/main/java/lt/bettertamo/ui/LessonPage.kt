@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.*
import java.time.format.DateTimeFormatter

private val sheetKeys = setOf("grade", "formative", "attendance", "comment", "homework", "homework_next")
private val entryOrder = listOf("grade", "formative", "attendance", "comment", "homework", "homework_next", "classwork")
private val gradeWords = setOf("vienas", "du", "trys", "keturi", "penki", "šeši", "septyni", "aštuoni", "devyni", "dešimt")

fun lessonEntries(lesson: Lesson, homework: List<Homework>): List<LessonDetail> {
    fun normalized(text: String) = text.replace(Regex("\\s+"), " ").trim()
    val marks = lesson.details.filter { it.key == "grade" }.map { it.badge }
    val attendance = lesson.assessment.split(" · ").map { it.trim() }.filter { it.isNotBlank() && it.toIntOrNull() == null && it !in marks }
        .map { LessonDetail(attendanceLabel(it), "", key = "attendance", badge = it) }
    val assigned = homework.filter { work -> work.lessonId == lesson.id && lesson.details.none { normalized(work.text).isNotEmpty() && normalized(work.text) in normalized(it.text) } }
        .map { work -> LessonDetail("", work.text, key = "homework_next", label = work.dueDate?.let { "Atlikti iki ${it.format(DateTimeFormatter.ofPattern("MM.dd"))}" }.orEmpty()) }
    return (lesson.details + attendance + assigned).sortedBy { entryOrder.indexOf(it.key).takeIf { index -> index >= 0 } ?: entryOrder.size }
}

@Composable
fun LessonHeaderCard(lesson: Lesson, state: PlannerState, container: Color) {
    val accent = MaterialTheme.colorScheme.error
    Surface(shape = RoundedCornerShape(20.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind { if (lesson.highlighted) drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
            .padding(horizontal = 18.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (lesson.start.isNotBlank()) {
                Column(Modifier.widthIn(min = 52.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text(lesson.start, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (lesson.slot > 0) Text(lesson.slot.toString(), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 8.dp))
                    Text(lesson.end, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (lesson.label.isNotBlank()) Text(lesson.label.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = if (lesson.highlighted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                Text(resolveSubject(lesson, state.rules).name, style = MaterialTheme.typography.titleLarge)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    lesson.date?.let { DetailRow(Icons.Outlined.Event, "${dayLong[it.dayOfWeek.value - 1]}, ${it.format(DateTimeFormatter.ofPattern("MM.dd"))}") }
                    if (lesson.teacher.isNotBlank()) DetailRow(Icons.Outlined.Person, lesson.teacher)
                }
                if (lesson.topic.isNotBlank()) SelectionContainer(Modifier.padding(top = 6.dp)) {
                    Text(lesson.topic, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
fun EntryCard(container: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = container, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun LessonDetailItem(detail: LessonDetail, title: String = defaultDetailTitle(detail)) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            detail.key == "grade" && detail.badge.isNotBlank() -> MarkBadge(detail.badge, DiaryKind.GRADE)
            detail.key == "formative" -> MarkBadge(detail.badge, DiaryKind.FORMATIVE)
            detail.key == "attendance" -> MarkBadge(detail.badge, DiaryKind.ATTENDANCE)
            detail.key == "homework" -> HomeworkChip(due = true)
            detail.key == "homework_next" -> HomeworkChip(due = false)
            detail.key == "comment" -> NoteIcon(remarkOf(detail.badge), Modifier.padding(horizontal = 8.dp))
            detail.badge.isNotBlank() && !detail.badge.startsWith("icon_") -> SmallTag(detail.badge, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.titleMedium)
            if (detail.label.isNotBlank()) Text(detail.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (detail.text.isNotBlank() && !(detail.key == "grade" && detail.text.trim().lowercase() in gradeWords)) SelectionContainer { Text(detail.text) }
    detail.files.forEach { file -> AttachmentRow(file) }
}

fun defaultDetailTitle(detail: LessonDetail) = when (detail.key) {
    "homework" -> "Namų darbai šiai pamokai"
    "homework_next" -> "Užduota šioje pamokoje"
    else -> detail.title
}

fun remarkOf(badge: String) = when { "negative" in badge -> "negative"; "positive" in badge -> "positive"; else -> "comment" }

@Composable
private fun AttachmentRow(file: SchoolFile) {
    val vm = LocalPlanner.current
    val opening by vm.openingFile.collectAsStateWithLifecycle()
    Surface(onClick = { vm.openFile(file) }, enabled = opening == null, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.AttachFile, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(file.name.ifBlank { "Priedas" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (opening == file.sid) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.OpenInNew, "Atidaryti priedą", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun LessonSheet(lesson: Lesson, state: PlannerState, onDismiss: () -> Unit, openSubject: () -> Unit) {
    val vm = LocalPlanner.current
    val feed by vm.feed.collectAsStateWithLifecycle()
    val remarks by vm.remarks.collectAsStateWithLifecycle()
    fun normalized(text: String) = text.replace(Regex("\\s+"), " ").trim()
    val entries = lessonEntries(lesson, LocalSchoolData.current.homework).filter { it.key in sheetKeys }.map { detail ->
        if (detail.key != "comment") return@map detail
        val text = normalized(detail.text)
        val date = (feed + remarks).firstOrNull { it.kind in setOf(NoticeKind.PRAISE, NoticeKind.REMARK, NoticeKind.COMMENT) && text.isNotEmpty() && normalized(it.text) == text }?.date ?: lesson.date
        date?.let { detail.copy(label = listOf(detail.label, "${it.format(DateTimeFormatter.ofPattern("MM.dd"))} ${dayShort[it.dayOfWeek.value - 1]}").filter(String::isNotBlank).joinToString(" · ")) } ?: detail
    }
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LessonHeaderCard(lesson, state, container)
            entries.forEach { detail -> EntryCard(container) { LessonDetailItem(detail) } }
            if (entries.isEmpty()) Text("Šiai pamokai namų darbų, pažymių ar pastabų nėra.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            Spacer(Modifier.height(4.dp))
            if (lesson.canRename) Button(onClick = openSubject, modifier = Modifier.fillMaxWidth()) { Text("Visa dalyko informacija") }
        }
    }
}
