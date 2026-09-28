package lt.bettertamo.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import lt.bettertamo.data.DiaryEntry
import lt.bettertamo.data.DiaryKind
import lt.bettertamo.data.dayLong
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RefreshOnResume(key: Any?, refresh: () -> Unit) {
    val action by rememberUpdatedState(refresh)
    LifecycleResumeEffect(key) {
        action()
        onPauseOrDispose { }
    }
}

@Composable
fun readComplete(channel: String, identity: String = channel): Boolean {
    val vm = LocalPlanner.current
    val loaded by vm.loadedRequests.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val errors by vm.readErrors.collectAsStateWithLifecycle()
    return loaded[channel] == identity && channel !in loading && channel !in errors
}

@Composable
fun MonthNavigation(month: YearMonth, select: (YearMonth) -> Unit, refresh: () -> Unit, refreshLabel: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { select(month.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, "Ankstesnis mėnuo") }
        Text(month.format(DateTimeFormatter.ofPattern("yyyy LLLL", Locale.forLanguageTag("lt"))),
            Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = { select(month.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, "Kitas mėnuo") }
        IconButton(onClick = { select(YearMonth.now()) }, enabled = month != YearMonth.now()) { Icon(Icons.Outlined.Today, "Šis mėnuo") }
        IconButton(onClick = refresh) { Icon(Icons.Outlined.Refresh, refreshLabel) }
    }
}

@Composable
fun MarkBadge(value: String, kind: DiaryKind, modifier: Modifier = Modifier) {
    Box(modifier.widthIn(min = 40.dp), contentAlignment = Alignment.Center) {
        when (kind) {
            DiaryKind.FORMATIVE -> Surface(shape = CircleShape, color = Color.Transparent, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }
            DiaryKind.GRADE -> Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            DiaryKind.ATTENDANCE -> Text(value, style = MaterialTheme.typography.titleLarge, color = if (value.trim().lowercase().startsWith("n")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun DiaryRow(entry: DiaryEntry, showSubject: Boolean = true, trailing: @Composable (() -> Unit)? = null) {
    val description = when (entry.kind) {
        DiaryKind.FORMATIVE -> listOf("Kaupiamasis", entry.title).filter { it.isNotBlank() }.joinToString(" · ")
        DiaryKind.ATTENDANCE -> attendanceLabel(entry.value)
        DiaryKind.GRADE -> entry.title
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MarkBadge(entry.value, entry.kind, Modifier.width(44.dp))
            VerticalDivider(Modifier.fillMaxHeight().heightIn(min = 28.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (showSubject) entry.subject else description.ifBlank { entry.subject }, style = MaterialTheme.typography.titleMedium)
                if (showSubject && description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!showSubject) Text(entry.date.format(DateTimeFormatter.ofPattern("MM.dd")) + " · " + dayLong[entry.date.dayOfWeek.value - 1].lowercase(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
        }
    }
}

fun attendanceLabel(value: String) = value.trim().lowercase().let { code ->
    when {
        code == "nl" -> "Praleista dėl ligos"
        code.startsWith("n") -> "Praleista pamoka"
        code == "p" -> "Pavėluota"
        else -> "Lankomumas"
    }
}

@Composable
fun averageColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
    value >= 8.5 -> StatusColors.good
    value >= 6.0 -> StatusColors.fair
    else -> MaterialTheme.colorScheme.error
}

fun formatAverage(value: Double?) = value?.let { String.format(Locale.forLanguageTag("lt"), "%.2f", it) } ?: "—"

@Composable
fun HomeworkChip(due: Boolean, modifier: Modifier = Modifier, description: String = if (due) "Namų darbai šiai pamokai" else "Šioje pamokoje užduoti namų darbai") {
    val accent = MaterialTheme.colorScheme.tertiary
    Surface(modifier.semantics { contentDescription = description }, shape = RoundedCornerShape(6.dp),
        color = if (due) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
        contentColor = if (due) MaterialTheme.colorScheme.onTertiaryContainer else accent,
        border = if (due) null else BorderStroke(1.dp, accent)) {
        Text("ND", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

val LocalOpenMore = staticCompositionLocalOf<(() -> Unit)?> { null }

@Composable
fun ProfileButton() {
    val open = LocalOpenMore.current ?: return
    val vm = LocalPlanner.current
    val account by vm.session.collectAsStateWithLifecycle()
    val initials = account?.name.orEmpty().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
    IconButton(onClick = open, modifier = Modifier.semantics { contentDescription = "Daugiau ir nustatymai" }) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(34.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun KeepScreenOnButton() {
    val view = LocalView.current
    var on by remember { mutableStateOf(false) }
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
    IconToggleButton(checked = on, onCheckedChange = {
        on = it
        if (it) android.widget.Toast.makeText(view.context, "Ekranas neužges", android.widget.Toast.LENGTH_SHORT).show()
    }, colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = MaterialTheme.colorScheme.primary, checkedContentColor = MaterialTheme.colorScheme.onPrimary)) {
        Icon(if (on) Icons.Filled.Coffee else Icons.Outlined.Coffee, "Neleisti ekranui užgesti")
    }
}

@Composable
fun ScreenHeader(title: String, back: (() -> Unit)? = null, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = if (back != null) 4.dp else 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Atgal") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = if (back != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), maxLines = 1)
                }
                actions()
                if (back == null) ProfileButton()
            }
            content()
        }
    }
}

@Composable
fun HeaderFilters(options: List<Pair<String, Int?>>, selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, (label, count) ->
            val active = index == selected
            val container by animateColorAsState(if (active) colors.primary else colors.onPrimaryContainer.copy(alpha = 0.1f).compositeOver(colors.primaryContainer), label = "filter")
            val content by animateColorAsState(if (active) colors.onPrimary else colors.onPrimaryContainer, label = "filterText")
            Surface(selected = active, onClick = { onSelect(index) }, shape = CircleShape, color = container, contentColor = content) {
                Row(Modifier.heightIn(min = 36.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    if (count != null) Text("$count", style = MaterialTheme.typography.labelLarge, color = content.copy(alpha = 0.7f))
                }
            }
        }
    }
}
