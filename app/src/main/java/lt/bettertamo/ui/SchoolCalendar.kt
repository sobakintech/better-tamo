@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package lt.bettertamo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import lt.bettertamo.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
                    Text(month.format(DateTimeFormatter.ofPattern("yyyy LLLL", Locale.forLanguageTag("lt"))).replaceFirstChar { it.titlecase(Locale.forLanguageTag("lt")) }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
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
    val badges = LocalSchoolData.current.badges
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().calendarSwipe(date, { onDate(date.minusWeeks(1)) }, { onDate(date.plusWeeks(1)) }).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val monday = mondayOf(date)
        repeat(7) { index ->
            val day = monday.plusDays(index.toLong())
            val isSelected = day == date
            val isToday = day == today
            val ink = if (index >= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer
            Surface(
                onClick = { onDate(day) },
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = day.format(DateTimeFormatter.ofPattern("yyyy MMMM d, EEEE", Locale.forLanguageTag("lt")))
                    selected = isSelected
                    stateDescription = listOfNotNull(
                        "Šiandien".takeIf { isToday },
                        "Yra dienyno įrašų".takeIf { badges[day].orEmpty().isNotEmpty() },
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
                    if (badges[day].orEmpty().isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp)) {
                        badges[day].orEmpty().take(3).forEach { badge -> Box(Modifier.size(4.dp).background(if ("homework" in badge) MaterialTheme.colorScheme.tertiary else ink, RoundedCornerShape(2.dp))) }
                    } else Spacer(Modifier.height(7.dp))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth(0.7f).height(3.dp).background(if (isSelected) ink else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(2.dp)))
                }
            }
        }
    }
}

@Composable
fun SchoolCalendarPanel(month: YearMonth, date: LocalDate, onDate: (LocalDate) -> Unit, previous: () -> Unit, next: () -> Unit, onDismiss: () -> Unit) {
    val school = LocalSchoolData.current
    val vm = LocalPlanner.current
    Column(Modifier.testTag("calendar-flyout").calendarSwipe(month, previous, next)) {
        TimetableMonthHeader(month, true, onDismiss, previous, next, { onDate(LocalDate.now()) })
        LazyColumn(modifier = Modifier.testTag("calendar-scroll"), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ReadStatus("month") { vm.loadMonth(month, true) } }
            item { SchoolMonthGrid(month, date, onDate) }
            val events = school.calendarEvents.filter { it.overlaps(month) }
            items(events, key = { it.id }) { event ->
                SchoolEventCard(event) { onDate(maxOf(event.start, month.atDay(1))) }
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
                        Surface(
                            onClick = { onDate(date) },
                            modifier = Modifier.weight(1f).testTag("calendar-date-$date").semantics { contentDescription = description.joinToString(", "); selected = isSelected },
                            shape = RoundedCornerShape(12.dp),
                            color = when { isSelected && weekend -> MaterialTheme.colorScheme.errorContainer; isSelected -> MaterialTheme.colorScheme.primary; kinds.isNotEmpty() -> MaterialTheme.colorScheme.secondaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerLowest },
                            contentColor = when { isSelected && weekend -> MaterialTheme.colorScheme.onErrorContainer; weekend -> MaterialTheme.colorScheme.error; isSelected -> MaterialTheme.colorScheme.onPrimary; !currentMonth -> MaterialTheme.colorScheme.outline; else -> MaterialTheme.colorScheme.onSurface },
                            border = if (date == today && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                        ) {
                            Column(Modifier.heightIn(min = 52.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleSmall)
                                Text(kinds.joinToString(" ") { it.marker }, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("A · Atostogos", style = MaterialTheme.typography.labelMedium)
                Text("V · Valstybinė šventė", style = MaterialTheme.typography.labelMedium)
            }
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
