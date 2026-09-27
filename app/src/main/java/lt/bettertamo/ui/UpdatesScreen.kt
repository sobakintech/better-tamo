@file:OptIn(ExperimentalMaterial3Api::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.DiaryKind
import lt.bettertamo.data.NoticeKind
import lt.bettertamo.data.SchoolNotice
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val remarkKinds = setOf(NoticeKind.PRAISE, NoticeKind.REMARK, NoticeKind.COMMENT)

@Composable
fun EventsScreen() {
    val vm = LocalPlanner.current
    val feed by vm.feed.collectAsStateWithLifecycle()
    val remarks by vm.remarks.collectAsStateWithLifecycle()
    val loaded by vm.loadedRequests.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val errors by vm.readErrors.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val remarkView = filter == 3
    RefreshOnResume(Unit) { vm.loadFeed() }
    if (remarkView) RefreshOnResume(month) { vm.loadRemarks(month) }
    val remarksReady = loaded["remarks"] == month.toString()
    val remarksUnavailable = "remarks" in errors && !remarksReady
    val items = when (filter) {
        1 -> feed.filter { it.kind == NoticeKind.GRADE || it.kind == NoticeKind.FORMATIVE }
        2 -> feed.filter { it.kind == NoticeKind.HOMEWORK }
        3 -> if (remarksUnavailable) feed.filter { it.kind in remarkKinds } else remarks.takeIf { remarksReady }.orEmpty()
        else -> feed
    }
    val channel = if (remarkView) "remarks" else "feed"
    val complete = if (remarkView) remarksReady && "remarks" !in loading else loaded["feed"] == "feed" && "feed" !in loading
    fun refresh() = if (remarkView) vm.loadRemarks(month, true) else vm.loadFeed(true)
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Naujausi įvykiai") {
            HeaderFilters(listOf("Visi", "Pažymiai", "Namų darbai", "Pastabos").map { it to null }, filter) { filter = it }
        }
        if (remarkView) MonthNavigation(month, { monthText = it.toString() }, { vm.loadRemarks(month, true) }, "Atnaujinti pastabas")
        ReadStatus(channel, showProgress = false) { refresh() }
        PullToRefreshBox(isRefreshing = channel in loading, onRefresh = { refresh() }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().semantics {
                customActions = listOf(CustomAccessibilityAction("Atnaujinti įvykius") { refresh(); true })
            }, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.groupBy { it.date }.entries.sortedByDescending { it.key }.forEach { (date, entries) ->
                    if (date != null) item("day-$date") { DayHeading(date) }
                    items(entries, key = { it.id }) { notice -> NoticeRow(notice) }
                }
                if (items.isEmpty() && complete) item {
                    EmptyPanel(Icons.Outlined.NotificationsNone, "Įrašų nėra", when (filter) {
                        1 -> "Naujausių pažymių nėra."
                        2 -> "Naujų namų darbų nėra."
                        3 -> "Šį mėnesį pagyrimų ir pastabų nėra."
                        else -> "Naujausių įvykių sąrašas tuščias."
                    })
                }
            }
        }
    }
}

@Composable
internal fun NoticeRow(notice: SchoolNotice) {
    val format = DateTimeFormatter.ofPattern("MM.dd")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(44.dp).padding(top = 2.dp), contentAlignment = Alignment.TopCenter) { NoticeMark(notice) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (notice.subject.isNotBlank()) Text(notice.subject, style = MaterialTheme.typography.titleMedium)
                if (notice.kind in remarkKinds && notice.value.isNotBlank()) Text(notice.value, style = MaterialTheme.typography.labelLarge, color = noticeColor(notice.kind))
                if (notice.teacher.isNotBlank()) Text(notice.teacher, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val text = when (notice.kind) {
                    NoticeKind.GRADE -> notice.lessonDate?.let { "Įrašyta į ${it.format(format)} pamoką" }.orEmpty()
                    NoticeKind.FORMATIVE -> listOf("Kaupiamasis", notice.text).filter { it.isNotBlank() }.joinToString(" · ") + (notice.lessonDate?.let { " · ${it.format(format)} pamoka" } ?: "")
                    else -> notice.text
                }
                if (text.isNotBlank()) SelectionContainer { Text(text, color = if (notice.kind == NoticeKind.GRADE || notice.kind == NoticeKind.FORMATIVE) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface) }
                notice.deadline?.let { Text("Atlikti iki ${it.format(format)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun NoticeMark(notice: SchoolNotice) {
    when (notice.kind) {
        NoticeKind.GRADE -> MarkBadge(notice.value, DiaryKind.GRADE)
        NoticeKind.FORMATIVE -> MarkBadge(notice.value, DiaryKind.FORMATIVE)
        NoticeKind.HOMEWORK -> HomeworkChip(due = true)
        NoticeKind.PRAISE -> Icon(Icons.Outlined.MarkChatRead, "Pagyrimas", tint = noticeColor(notice.kind))
        NoticeKind.REMARK -> Icon(Icons.Outlined.Feedback, "Pastaba", tint = noticeColor(notice.kind))
        NoticeKind.COMMENT -> Icon(Icons.AutoMirrored.Outlined.Chat, "Komentaras", tint = noticeColor(notice.kind))
        NoticeKind.OTHER -> Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun noticeColor(kind: NoticeKind) = when (kind) {
    NoticeKind.PRAISE -> StatusColors.good
    NoticeKind.REMARK -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
