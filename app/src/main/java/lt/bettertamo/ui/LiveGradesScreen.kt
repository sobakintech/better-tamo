@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.*
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun LiveGradesScreen(openSubject: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column {
        ScreenHeader("Pažymiai") {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Dienynas") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Dalykai") })
            }
        }
        if (tab == 0) DiaryTab() else SubjectsOverview(openSubject)
    }
}

@Composable
private fun DiaryTab() {
    val vm = LocalPlanner.current
    val school = LocalSchoolData.current
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    RefreshOnResume(month) { vm.loadDiary(month) }
    val rules = vm.state.collectAsStateWithLifecycle().value?.rules.orEmpty()
    val yearLessons by vm.yearLessons.collectAsStateWithLifecycle()
    RefreshOnResume(rules.isNotEmpty()) { if (rules.isNotEmpty()) vm.loadYearLessons() }
    val diary = school.diary.filter { YearMonth.from(it.date) == month }
    val names = remember(diary, school.lessons, yearLessons, rules) { entryLessonNames(diary, school.lessons, yearLessons, rules) }
    val complete = readComplete("diary", month.toString())
    Column {
        MonthNavigation(month, { monthText = it.toString() }, { vm.loadDiary(month, true) }, "Atnaujinti pažymius")
        ReadStatus("diary") { vm.loadDiary(month, true) }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            diary.groupBy { it.date }.toSortedMap(reverseOrder()).forEach { (date, entries) ->
                item("day-$date") { DayHeading(date) }
                items(entries.sortedWith(compareBy({ it.kind.ordinal }, { it.subject })), key = { it.id }) { entry -> DiaryRow(entry, lessonName = names[entry.id]) }
            }
            if (complete && diary.isEmpty()) item { EmptyPanel(Icons.Outlined.School, "Įrašų nėra", "Šį mėnesį pažymių ir lankomumo įrašų nėra. Pasirink kitą mėnesį.") }
        }
    }
}

@Composable
fun SubjectDetailScreen(name: String, openDate: (java.time.LocalDate) -> Unit, rename: (Lesson) -> Unit) {
    val vm = LocalPlanner.current
    val school = LocalSchoolData.current
    val periods by vm.periods.collectAsStateWithLifecycle()
    val rawSemester by vm.semesterSubjects.collectAsStateWithLifecycle()
    val loaded by vm.loadedRequests.collectAsStateWithLifecycle()
    val chosen by vm.chosenPeriod.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val feed by vm.feed.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val yearLessons by vm.yearLessons.collectAsStateWithLifecycle()
    val rankings by vm.rankings.collectAsStateWithLifecycle()
    var allLessons by rememberSaveable(name) { mutableStateOf(false) }
    val period = periods.find { it.id == chosen } ?: periods.find { it.selected } ?: periods.firstOrNull()
    val semester = rawSemester.takeIf { loaded["semester"] == period?.id }.orEmpty()
    RefreshOnResume(Unit) { vm.loadPeriods(); vm.loadSchoolYear(); vm.loadFeed(); vm.loadUpcoming(); vm.loadYearLessons(); vm.loadRankings() }
    RefreshOnResume(period?.id) { period?.let { vm.loadSemester(it.id) } }
    val subject = subjectOverviews(semester, school.diary.filter { it.date >= schoolYearStart() }, school.lessons).find { it.name == name }
    val entries = subject?.entries.orEmpty().sortedByDescending { it.date }
    val marks = entries.filter { it.kind == DiaryKind.GRADE }
    val attendance = entries.filter { it.kind == DiaryKind.ATTENDANCE }
    val formatives = subjectFormatives(entries, school.lessons.filter { it.subject == name && it.date != null }.maxByOrNull { it.date!! }?.pendingFormatives)
    val unused = formatives.filter { it.converted == false }
    val rules = vm.state.collectAsStateWithLifecycle().value?.rules.orEmpty()
    val names = remember(entries, formatives, school.lessons, yearLessons, rules) { entryLessonNames(entries + formatives, school.lessons, yearLessons, rules) }
    val split = lessonSplit(name, marks, names)
    val lessonNames = remember(yearLessons, school.lessons, rules) { recordLessonNames(yearLessons, school.lessons, rules) }
    val tests = upcoming.filter { name.lowercase() in it.title.lowercase() }
    val testNames = remember(tests, school.lessons, yearLessons, rules) { upcomingLessonNames(tests, name, school.lessons, yearLessons, rules) }
    val lessons = yearLessons.filter { it.subject == name }.sortedByDescending { it.date }
    val today = java.time.LocalDate.now()
    val subjectLessons = school.lessons.filter { it.subject == name && it.date != null }
    val week = subjectLessons.map { mondayOf(it.date!!) }.distinct().minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(mondayOf(today), it)) }
    val weekly = subjectLessons.filter { mondayOf(it.date!!) == week }.sortedWith(compareBy({ it.date }, { it.start }))
    val remarks = feed.filter { it.kind in setOf(NoticeKind.PRAISE, NoticeKind.REMARK, NoticeKind.COMMENT) && it.subject == name }.sortedByDescending { it.date }
    val yearComplete = readComplete("year", schoolYearStart().toString())
    val container = MaterialTheme.colorScheme.surfaceContainerLowest
    PullToRefreshBox(
        isRefreshing = "year" in loading || "semester" in loading,
        onRefresh = { vm.loadSchoolYear(true); period?.let { vm.loadSemester(it.id, true) }; vm.loadFeed(true); vm.loadUpcoming(true); vm.loadYearLessons(true); vm.loadRankings(true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall)
                    subject?.teacher?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Outlined.Person, it) }
                }
            }
            item {
                Column {
                    ReadStatus("semester", showProgress = false) { period?.let { vm.loadSemester(it.id, true) } }
                    ReadStatus("year", showProgress = false) { vm.loadSchoolYear(true) }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = container) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Vidurkis", style = MaterialTheme.typography.titleMedium)
                                Text(if (subject?.fromSemester == true) period?.title.orEmpty() else "Pagal dienyno pažymius", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formatAverage(subject?.average), style = MaterialTheme.typography.headlineLarge, color = averageColor(subject?.average), modifier = Modifier.testTag("subject-average"))
                        }
                        subject?.average?.let { LinearProgressIndicator(progress = { (it / 10.0).toFloat() }, modifier = Modifier.fillMaxWidth(), color = averageColor(it), drawStopIndicator = {}) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubjectStat("Pažymiai", marks.size.toString(), Modifier.weight(1f))
                            SubjectStat("Kaupiamieji", formatives.size.toString(), Modifier.weight(1f))
                            SubjectStat("Praleista", attendance.count { it.missed }.toString(), Modifier.weight(1f))
                        }
                        if (subject?.finalGrade?.isNotBlank() == true) Text("Išvesta: ${subject.finalGrade}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            split?.let { item { LessonSplitCard(it, container) } }
            rankings[rankingKey(name)]?.let { ranking -> item { RankingCard(ranking, container) } }
            if (weekly.isNotEmpty()) {
                item { SubjectSection("Savaitės pamokos") }
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = container) {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            weekly.forEachIndexed { index, lesson ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text(dayLong[lesson.weekday - 1], style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                                        color = if (lesson.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    if (lesson.slot > 0) Text("${lesson.slot} pamoka", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (lesson.start.isNotBlank()) Text("${lesson.start}–${lesson.end}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
            if (tests.isNotEmpty()) {
                item { SubjectSection("Artimiausi atsiskaitymai") }
                items(tests, key = { "test-${it.id}" }) { event -> SubjectTestRow(event, name, testNames[event.id]) { openDate(event.start) } }
            }
            if (formatives.isNotEmpty()) {
                item { SubjectSection("Kaupiamieji") }
                if (unused.isNotEmpty()) item {
                    EntryCard(container) {
                        Text("Nepanaudoti", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            formativeStats(unused).forEach { (label, value) -> SubjectStat(label, value, Modifier.weight(1f)) }
                        }
                    }
                }
                items(formatives, key = { "formative-${it.id}" }) { entry ->
                    DiaryRow(entry, showSubject = false, lessonName = names[entry.id]) {
                        when (entry.converted) {
                            false -> SmallTag("Nepanaudotas", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            true -> SmallTag("Panaudotas", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
                            null -> {}
                        }
                    }
                }
            }
            item { SubjectSection("Pažymiai šiais mokslo metais") }
            items(marks, key = { "mark-${it.id}" }) { entry -> DiaryRow(entry, showSubject = false, lessonName = names[entry.id]) }
            if (marks.isEmpty() && yearComplete) item { SubjectEmpty("Šio dalyko pažymių dar nėra.") }
            if (lessons.isNotEmpty() || "year-lessons" in loading) {
                item { SubjectSection("Pamokų istorija" + if (lessons.isNotEmpty()) " (${lessons.size})" else "") }
                item { ReadStatus("year-lessons") { vm.loadYearLessons(true) } }
                items(if (allLessons) lessons else lessons.take(5), key = { "lesson-${it.id}" }) { record -> SubjectLessonRow(record, lessonNames[record.id]?.takeIf { it != name }) { openDate(record.date) } }
                if (lessons.size > 5) item {
                    TextButton(onClick = { allLessons = !allLessons }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (allLessons) "Rodyti mažiau" else "Rodyti visas pamokas (${lessons.size})")
                    }
                }
            }
            if (remarks.isNotEmpty()) {
                item { SubjectSection("Pastabos ir pagyrimai") }
                items(remarks, key = { "remark-${it.id}" }) { notice -> NoticeRow(notice, showDate = true) }
            }
            if (attendance.isNotEmpty()) {
                item { SubjectSection("Lankomumas") }
                items(attendance, key = { "attendance-${it.id}" }) { entry -> DiaryRow(entry, showSubject = false, lessonName = names[entry.id]) }
            }
            school.lessons.filter { it.subject == name && it.canRename }.maxByOrNull { it.date ?: java.time.LocalDate.MIN }?.let { lesson ->
                item {
                    TextButton(onClick = { rename(lesson) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pervadinti pamokas")
                    }
                }
            }
        }
    }
}

@Composable
internal fun RankingCard(ranking: Ranking, container: Color) {
    val primary = MaterialTheme.colorScheme.primary
    val others = MaterialTheme.colorScheme.outlineVariant
    Surface(shape = RoundedCornerShape(20.dp), color = container) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Vieta klasėje", style = MaterialTheme.typography.titleMedium)
                    Text("Pagal vidurkį", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${ranking.position}", style = MaterialTheme.typography.headlineLarge, color = primary)
                Text(" iš ${ranking.total}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val low = (ranking.averages.minOrNull() ?: 0.0).coerceAtMost(ranking.averages.maxOrNull() ?: 0.0)
            val floor = (low - 1.0).coerceAtLeast(0.0)
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(56.dp).clearAndSetSemantics {}) {
                val gap = 2.dp.toPx()
                val width = (size.width - gap * (ranking.total - 1)) / ranking.total
                val top = ranking.averages.maxOrNull() ?: 10.0
                ranking.averages.forEachIndexed { index, value ->
                    val share = if (top <= floor) 1f else ((value - floor) / (top - floor)).toFloat().coerceIn(0.08f, 1f)
                    val height = size.height * share
                    drawRoundRect(if (index == ranking.position - 1) primary else others, androidx.compose.ui.geometry.Offset(index * (width + gap), size.height - height),
                        androidx.compose.ui.geometry.Size(width, height), androidx.compose.ui.geometry.CornerRadius(width / 3))
                }
            }
            Text(listOfNotNull(ranking.average?.let { "Tavo vidurkis ${formatAverage(it)}" }, "aukščiausias ${formatAverage(ranking.averages.maxOrNull())}", "žemiausias ${formatAverage(ranking.averages.minOrNull())}").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubjectLessonRow(record: LessonRecord, lessonName: String?, onClick: () -> Unit) {
    val today = java.time.LocalDate.now()
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(min = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(record.date.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd")), style = MaterialTheme.typography.titleSmall,
                    color = if (record.date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(dayShort[record.date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lessonName?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(record.topic.ifBlank { "Tema neįrašyta" }, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = if (record.topic.isBlank() || record.date.isAfter(today)) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                if (record.homework.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomeworkChip(due = false)
                    Text(record.homework, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SubjectTestRow(event: UpcomingEvent, subject: String, lessonName: String?, onClick: () -> Unit) {
    val split = event.title.indexOf(": ")
    val type = if (split > 0) event.title.take(split) else ""
    val title = (if (type.isNotEmpty()) event.title.drop(split + 2) else event.title).let { if (lessonName != null) it.replace(subject, lessonName, ignoreCase = true) else it }
    val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), event.start)
    val meta = listOf(when (days) { 0L -> "Šiandien"; 1L -> "Rytoj"; 2L -> "Poryt"; else -> "Po $days d." }) + listOfNotNull(event.time.takeIf { it.isNotBlank() })
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(min = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(event.start.format(java.time.format.DateTimeFormatter.ofPattern("MM.dd")), style = MaterialTheme.typography.titleSmall,
                    color = if (days == 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                Text(dayShort[event.start.dayOfWeek.value - 1], style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (type.isNotEmpty()) Text(type.uppercase(java.util.Locale.forLanguageTag("lt")), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), color = MaterialTheme.colorScheme.error)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.body.isNotBlank() && event.body != event.title) Text(event.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formativeStats(unused: List<DiaryEntry>): List<Pair<String, String>> {
    val values = unused.map { it.value.trim() }
    val count = "Kiekis" to unused.size.toString()
    return when {
        unused.any { it.system == "PLIUSAI_MINUSAI" } || values.all { it in setOf("+", "-", "−") } ->
            listOf("Pliusai" to values.count { it == "+" }.toString(), "Minusai" to values.count { it != "+" }.toString())
        unused.any { it.percents != null } -> listOf("Vidurkis" to "${unused.mapNotNull { it.percents }.average().roundToInt()} %", count)
        else -> listOf("Vidurkis" to formatAverage(values.mapNotNull { it.replace(',', '.').toDoubleOrNull()?.takeIf { value -> value in 1.0..10.0 } }.takeIf { it.isNotEmpty() }?.average()), count)
    }
}

@Composable
private fun LessonSplitCard(split: LessonSplit, container: Color) {
    EntryCard(container) {
        Column {
            Text("Pagal pamokas", style = MaterialTheme.typography.titleMedium)
            Text("Programos vidurkis iš šių mokslo metų pažymių", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        split.averages.forEach { lesson ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lesson.name, style = MaterialTheme.typography.titleSmall)
                    Text("${lesson.count} ${gradeWord(lesson.count)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(formatAverage(lesson.average), style = MaterialTheme.typography.titleLarge, color = averageColor(lesson.average))
            }
        }
        if (split.unlinked > 0) Text("Nepriskirta pamokai: ${split.unlinked}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun gradeWord(count: Int) = when {
    count % 10 == 1 && count % 100 != 11 -> "pažymys"
    count % 10 in 2..9 && count % 100 !in 12..19 -> "pažymiai"
    else -> "pažymių"
}

@Composable
private fun SubjectSection(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 12.dp).semantics { heading() })
}

@Composable
private fun SubjectEmpty(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
}

@Composable
private fun SubjectStat(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
