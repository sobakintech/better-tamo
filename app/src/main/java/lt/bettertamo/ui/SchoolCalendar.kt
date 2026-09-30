package lt.bettertamo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import lt.bettertamo.data.*
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private fun Modifier.calendarSwipe(key: Any, previous: () -> Unit, next: () -> Unit) = pointerInput(key) {
    var distance = 0f
    detectHorizontalDragGestures(
        onDragStart = { distance = 0f },
        onDragCancel = { distance = 0f },
        onDragEnd = { if (distance > 48.dp.toPx()) previous() else if (distance < -48.dp.toPx()) next() },
        onHorizontalDrag = { change, amount -> change.consume(); distance += amount },
    )
}

@Composable
fun TimetableMonthHeader(month: YearMonth, expanded: Boolean, onToggle: () -> Unit, previous: () -> Unit, next: () -> Unit, today: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Surface(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 4.dp).testTag(if (expanded) "close-calendar" else "toggle-calendar").semantics { stateDescription = if (expanded) "Išskleistas kalendorius" else "Suskleistas kalendorius" },
                shape = CircleShape, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.primaryContainer), contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                    Text(month.format(DateTimeFormatter.ofPattern(if (month.year == LocalDate.now().year) "LLLL" else "yyyy LLLL", Locale.forLanguageTag("lt"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("lt")) },
                        Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "Uždaryti kalendorių" else "Atidaryti kalendorių", Modifier.size(20.dp))
                }
            }
        }
        IconButton(onClick = previous) { Icon(Icons.Outlined.ChevronLeft, if (expanded) "Ankstesnis mėnuo" else "Ankstesnė savaitė") }
        IconButton(onClick = next) { Icon(Icons.Outlined.ChevronRight, if (expanded) "Kitas mėnuo" else "Kita savaitė") }
        IconButton(onClick = today) { Icon(Icons.Outlined.Today, "Šiandien") }
        trailing()
    }
}

@Composable
fun TimetableWeekStrip(date: LocalDate, onDate: (LocalDate) -> Unit) {
    val school = LocalSchoolData.current
    val badges = school.badges
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().calendarSwipe(date, { onDate(date.minusWeeks(1)) }, { onDate(date.plusWeeks(1)) }).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val monday = mondayOf(date)
        repeat(7) { index ->
            val day = monday.plusDays(index.toLong())
            val isSelected = day == date
            val isToday = day == today
            val ink = if (index >= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            val hasTest = school.lessons.any { it.date == day && it.highlighted }
            val dots = dayDots(school, day, ink)
            Surface(
                onClick = { onDate(day) },
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = day.format(DateTimeFormatter.ofPattern("yyyy MMMM d, EEEE", Locale.forLanguageTag("lt")))
                    selected = isSelected
                    stateDescription = listOfNotNull(
                        "Šiandien".takeIf { isToday },
                        "Yra dienyno įrašų".takeIf { badges[day].orEmpty().isNotEmpty() },
                        "Yra atsiskaitymų".takeIf { hasTest },
                    ).joinToString(", ")
                },
                shape = RoundedCornerShape(12.dp),
                color = if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    .compositeOver(MaterialTheme.colorScheme.primaryContainer)
                    else MaterialTheme.colorScheme.primaryContainer,
                contentColor = ink,
            ) {
                Column(Modifier.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                    Text(dayShort[index], style = MaterialTheme.typography.labelMedium)
                    DayDots(dots, Modifier.padding(top = 3.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(3.dp).background(if (isSelected) ink else Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
private fun dayDots(school: SchoolData, day: LocalDate, ink: Color): List<Color> {
    val dots = school.badges[day].orEmpty().map { if ("homework" in it) MaterialTheme.colorScheme.tertiary else ink }.toMutableList()
    if (school.lessons.any { it.date == day && it.highlighted }) dots.indexOf(ink).let { if (it >= 0) dots[it] = MaterialTheme.colorScheme.error else dots.add(0, MaterialTheme.colorScheme.error) }
    return dots
}

@Composable
private fun DayDots(dots: List<Color>, modifier: Modifier = Modifier) {
    Row(modifier.height(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        dots.take(3).forEach { color -> Box(Modifier.size(4.dp).background(color, RoundedCornerShape(2.dp))) }
    }
}

private val monthEpoch = YearMonth.of(2000, 1)
private val MONTH_PAGES = monthEpoch.until(YearMonth.of(2100, 1), java.time.temporal.ChronoUnit.MONTHS).toInt()
private fun monthPage(month: YearMonth) = monthEpoch.until(month, java.time.temporal.ChronoUnit.MONTHS).toInt().coerceIn(0, MONTH_PAGES - 1)

@Composable
fun SchoolCalendarPanel(month: YearMonth, date: LocalDate, onDate: (LocalDate) -> Unit, onMonth: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    val school = LocalSchoolData.current
    val vm = LocalPlanner.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = monthPage(month)) { MONTH_PAGES }
    val currentMonth by rememberUpdatedState(month)
    val currentOnMonth by rememberUpdatedState(onMonth)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != monthPage(currentMonth)) currentOnMonth(monthEpoch.plusMonths(page.toLong())) }
    }
    LaunchedEffect(month) {
        val target = monthPage(month)
        if (pager.currentPage != target && !pager.isScrollInProgress) pager.animateScrollToPage(target)
    }
    val shownMonth = monthEpoch.plusMonths(pager.targetPage.toLong())
    Column(Modifier.testTag("calendar-flyout")) {
        TimetableMonthHeader(shownMonth, true, onDismiss,
            { scope.launch { pager.animateScrollToPage(pager.targetPage - 1) } },
            { scope.launch { pager.animateScrollToPage(pager.targetPage + 1) } },
            { onDate(LocalDate.now()) })
        HorizontalPager(pager, pageSpacing = 16.dp, key = { it }, verticalAlignment = Alignment.Top) { page ->
            val pageMonth = monthEpoch.plusMonths(page.toLong())
            LazyColumn(modifier = Modifier.fillMaxWidth().then(if (pageMonth == month) Modifier.testTag("calendar-scroll") else Modifier), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pageMonth == month) item { ReadStatus("month") { vm.loadMonth(month, true) } }
                item { SchoolMonthGrid(pageMonth, date, onDate) }
                val events = school.calendarEvents.filter { it.overlaps(pageMonth) }
                items(events, key = { it.id }) { event ->
                    SchoolEventCard(event) { onDate(maxOf(event.start, pageMonth.atDay(1))) }
                }
            }
        }
    }
}

@Composable
fun SchoolMonthGrid(month: YearMonth, selectedDate: LocalDate, onDate: (LocalDate) -> Unit) {
    val school = LocalSchoolData.current
    val today = LocalDate.now()
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                dayShort.forEachIndexed { index, label -> Text(label, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = if (index >= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            monthDates(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { date ->
                        val currentMonth = YearMonth.from(date) == month
                        val isSelected = date == selectedDate
                        val weekend = date.dayOfWeek.value >= 6
                        val kinds = school.calendarEvents.filter { it.contains(date) }.map { it.kind }.distinct()
                        val description = listOf(date.toString()) + kinds.map { it.label }
                        val ink = when { isSelected && weekend -> MaterialTheme.colorScheme.onErrorContainer; weekend -> MaterialTheme.colorScheme.error; isSelected -> MaterialTheme.colorScheme.onPrimary; !currentMonth -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface }
                        val dots = dayDots(school, date, when { weekend -> MaterialTheme.colorScheme.error; !currentMonth -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface })
                        Surface(
                            onClick = { onDate(date) },
                            modifier = Modifier.weight(1f).testTag("calendar-date-$date").semantics { contentDescription = description.joinToString(", "); selected = isSelected },
                            shape = RoundedCornerShape(12.dp),
                            color = when { isSelected && weekend -> MaterialTheme.colorScheme.errorContainer; isSelected -> MaterialTheme.colorScheme.primary; kinds.isNotEmpty() -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerLowest },
                            contentColor = ink,
                            border = if (date == today && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Column(Modifier.heightIn(min = 52.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                                Text(kinds.joinToString(" ") { it.marker }, style = MaterialTheme.typography.labelSmall)
                                DayDots(dots)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BirthdayCard() {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cake, null, tint = MaterialTheme.colorScheme.primary)
            Text("Su gimtadieniu!", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SchoolEventCard(event: SchoolCalendarEvent, onClick: () -> Unit) {
    val dateFormat = DateTimeFormatter.ofPattern("MM.dd")
    Card(onClick = onClick, enabled = event.datesKnown, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallTag(event.kind.marker, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.title, style = MaterialTheme.typography.titleMedium)
                Text(event.dateText.ifBlank { event.start.format(dateFormat) + if (event.end != event.start) "–${event.end.format(dateFormat)}" else "" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
