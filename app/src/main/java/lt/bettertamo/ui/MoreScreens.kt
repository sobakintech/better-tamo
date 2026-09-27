@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package lt.bettertamo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import lt.bettertamo.data.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("MM.dd")

@Composable
private fun MenuGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, modifier: Modifier = Modifier, subtitle: String? = null, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick: (() -> Unit)?, trailing: @Composable () -> Unit = { if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }) {
    val row = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing()
        }
    }
    if (onClick != null) Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = modifier) { row() } else Box(modifier) { row() }
}

@Composable
fun MoreScreen(back: () -> Unit, open: (String) -> Unit) {
    val vm = LocalPlanner.current
    val account by vm.session.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val menu by vm.menu.collectAsStateWithLifecycle()
    val changingAccount by vm.changingAccount.collectAsStateWithLifecycle()
    var signOut by remember { mutableStateOf(false) }
    var switchAccount by remember { mutableStateOf(false) }
    RefreshOnResume(Unit) { vm.loadMenu() }
    val role = account?.roles?.firstOrNull { it.id == account?.selectedRole }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Daugiau", back)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(52.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(account?.name.orEmpty().split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(account?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            role?.let {
                                Text(it.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (it.subtitle.isNotBlank()) Text(it.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (account?.roles.orEmpty().size > 1) IconButton(onClick = { switchAccount = true }, enabled = !changingAccount) { Icon(Icons.Outlined.SwitchAccount, "Keisti paskyrą") }
                    }
                }
                if (changingAccount) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            }
            item {
                MenuGroup("Mokykla") {
                    MenuRow(Icons.AutoMirrored.Outlined.EventNote, "Artimiausi įvykiai", subtitle = "Atsiskaitymai, kontroliniai ir atostogos", onClick = { open("upcoming") })
                    MenuRow(Icons.AutoMirrored.Outlined.MenuBook, "Pamokų istorija", subtitle = "Pamokų temos, klasės ir namų darbai", onClick = { open("history") })
                    MenuRow(Icons.Outlined.ViewWeek, "Savaitės tvarkaraštis", subtitle = "Visa savaitė su mokytojais", onClick = { open("schedule") })
                }
            }
            menu.groupBy { it.group }.forEach { (group, links) ->
                item("menu-$group") {
                    MenuGroup(group.ifBlank { "TAMO" }) {
                        links.forEach { link ->
                            MenuRow(if ("anal" in group.lowercase() || "anal" in link.title.lowercase()) Icons.Outlined.Insights else Icons.Outlined.Language, link.title, onClick = { open("web:${link.id}") })
                        }
                    }
                }
            }
            item {
                MenuGroup("Nustatymai") {
                    MenuRow(Icons.Outlined.Tune, "Mano pamokos", Modifier.testTag("open-personalization"), subtitle = "Pamokų pavadinimai ir tavo įvykiai", onClick = { open("personalization") })
                    NotificationSetting()
                    ThemeSetting(state?.theme ?: "system", vm::setTheme)
                }
            }
            item {
                MenuGroup("Apie") {
                    MenuRow(Icons.Outlined.Info, "Better TAMO", subtitle = lt.bettertamo.BuildConfig.VERSION_NAME, onClick = null)
                    UpdateSetting()
                }
            }
            item {
                MenuGroup(null) {
                    MenuRow(Icons.AutoMirrored.Outlined.Logout, "Atsijungti", tint = MaterialTheme.colorScheme.error, onClick = { if (!changingAccount) signOut = true }, trailing = {})
                }
            }
        }
    }
    if (switchAccount) AlertDialog(onDismissRequest = { switchAccount = false }, title = { Text("Pasirinkite paskyrą") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
            account?.roles.orEmpty().forEach { item ->
                Row(Modifier.fillMaxWidth().selectable(item.id == account?.selectedRole, enabled = !changingAccount, role = Role.RadioButton, onClick = { switchAccount = false; vm.selectRole(item.id) }).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(item.id == account?.selectedRole, null)
                    Spacer(Modifier.width(12.dp))
                    Column { Text(item.title); if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { switchAccount = false }) { Text("Uždaryti") } })
    if (signOut) AlertDialog(onDismissRequest = { signOut = false }, title = { Text("Atsijungti?") }, confirmButton = { TextButton(onClick = { signOut = false; vm.signOut() }) { Text("Atsijungti") } }, dismissButton = { TextButton(onClick = { signOut = false }) { Text("Atšaukti") } })
}

@Composable
private fun ThemeSetting(theme: String, setTheme: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Išvaizda", style = MaterialTheme.typography.bodyLarge)
        }
        val options = listOf("system" to "Pagal įrenginį", "light" to "Šviesi", "dark" to "Tamsi")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = theme == value, onClick = { setTheme(value) }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun UpdateSetting() {
    val context = LocalContext.current
    val update by AppUpdater.state.collectAsStateWithLifecycle()
    val busy = update is AppUpdate.Checking || update is AppUpdate.Downloading || update is AppUpdate.Confirm || update is AppUpdate.Installing
    MenuRow(Icons.Outlined.Update, "Atnaujinimai", subtitle = updateSummary(update), tint = if (update is AppUpdate.Available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = {
            when {
                busy -> AppUpdater.dialog.value = update !is AppUpdate.Checking
                update is AppUpdate.Available || (update as? AppUpdate.Failed)?.release != null -> AppUpdater.dialog.value = true
                else -> AppUpdater.check(context, manual = true)
            }
        }, trailing = { if (update is AppUpdate.Checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) })
}

@Composable
internal fun NotificationSetting() {
    val vm = LocalPlanner.current
    val context = LocalContext.current
    val enabled by vm.notifications.collectAsStateWithLifecycle()
    val push by vm.pushActive.collectAsStateWithLifecycle()
    val busy by vm.notificationsBusy.collectAsStateWithLifecycle()
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.setNotifications(true)
    }
    fun toggle(value: Boolean) {
        if (value && android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else vm.setNotifications(value)
    }
    val subtitle = when {
        busy -> "Jungiamasi prie TAMO…"
        enabled && push -> "Tiesiogiai iš TAMO, kaip oficialioje programėlėje."
        enabled -> "TAMO tiesioginiai pranešimai nepasiekiami, todėl tikrinama kas 15 minučių."
        else -> "Nauji pažymiai, pastabos, namų darbai ir pranešimai."
    }
    MenuRow(Icons.Outlined.NotificationsActive, "Pranešimai telefone", subtitle = subtitle,
        modifier = Modifier.toggleable(enabled, enabled = !busy, role = Role.Switch, onValueChange = ::toggle), onClick = null, trailing = { Switch(checked = enabled, onCheckedChange = null, enabled = !busy) })
    if (enabled && push && !busy) MenuRow(Icons.Outlined.Campaign, "Bandomasis pranešimas", subtitle = "Paprašyti TAMO atsiųsti pranešimą", onClick = vm::testNotification, trailing = {})
}

@Composable
fun UpcomingScreen(back: () -> Unit, openDate: (LocalDate) -> Unit) {
    val vm = LocalPlanner.current
    val events by vm.upcoming.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    RefreshOnResume(Unit) { vm.loadUpcoming() }
    val complete = readComplete("upcoming", today.toString())
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Artimiausi įvykiai", back, "${today.format(shortDate)}–${today.plusDays(61).format(shortDate)}")
        ReadStatus("upcoming", showProgress = false) { vm.loadUpcoming(true) }
        PullToRefreshBox(isRefreshing = "upcoming" in loading, onRefresh = { vm.loadUpcoming(true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (complete && events.isEmpty()) item { EmptyPanel(Icons.Outlined.EventAvailable, "Įvykių nėra", "Artimiausiu metu atsiskaitymų ar atostogų nesuplanuota.") }
                events.groupBy { it.start }.forEach { (date, entries) ->
                    item("day-$date") { DayHeading(date) }
                    items(entries, key = { it.id }) { event -> UpcomingCard(event) { openDate(event.start) } }
                }
            }
        }
    }
}

@Composable
internal fun UpcomingCard(event: UpcomingEvent, showDate: Boolean = false, onClick: () -> Unit) {
    val split = event.title.indexOf(": ")
    val type = if (split > 0 && !event.holiday) event.title.take(split) else ""
    val title = if (type.isNotEmpty()) event.title.drop(split + 2) else event.title
    val accent = if (event.holiday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f), contentColor = accent, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(if (event.holiday) Icons.Outlined.BeachAccess else Icons.Outlined.Quiz, null, Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (type.isNotEmpty()) Text(type.uppercase(Locale.forLanguageTag("lt")), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp), color = accent)
                Text(title, style = MaterialTheme.typography.titleMedium)
                val days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), event.start)
                val meta = listOfNotNull(if (showDate && event.end == null) "${dayLong[event.start.dayOfWeek.value - 1]}, ${event.start.format(shortDate)} · " + when (days) { 0L -> "šiandien"; 1L -> "rytoj"; 2L -> "poryt"; else -> "po $days d." } else null, event.end?.let { "${event.start.format(shortDate)}–${it.format(shortDate)}" }, event.time.takeIf { it.isNotBlank() })
                if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (event.body.isNotBlank() && event.body != event.title && !(event.end != null && event.body.startsWith("Nuo "))) Text(event.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LessonHistoryScreen(state: PlannerState, back: () -> Unit) {
    val vm = LocalPlanner.current
    val records by vm.history.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    RefreshOnResume(month) { vm.loadHistory(month) }
    val complete = readComplete("history", month.toString())
    val shown = records.takeIf { vm.loadedRequests.collectAsStateWithLifecycle().value["history"] == month.toString() }.orEmpty()
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Pamokų istorija", back)
        MonthNavigation(month, { monthText = it.toString() }, { vm.loadHistory(month, true) }, "Atnaujinti pamokas")
        ReadStatus("history", showProgress = false) { vm.loadHistory(month, true) }
        PullToRefreshBox(isRefreshing = "history" in loading, onRefresh = { vm.loadHistory(month, true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (complete && shown.isEmpty()) item { EmptyPanel(Icons.AutoMirrored.Outlined.MenuBook, "Pamokų nėra", "Šį mėnesį pamokų įrašų nėra. Pasirink kitą mėnesį.") }
                shown.groupBy { it.date }.toSortedMap(reverseOrder()).forEach { (date, entries) ->
                    item("day-$date") { DayHeading(date) }
                    items(entries, key = { it.id }) { record -> LessonRecordCard(record, LocalSchoolData.current.origin(record.id)?.let { resolveSubject(it, state.rules).name } ?: record.subject) }
                }
            }
        }
    }
}

@Composable
private fun LessonRecordCard(record: LessonRecord, subject: String) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(subject, style = MaterialTheme.typography.titleMedium)
            if (record.teacher.isNotBlank()) Text(record.teacher, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record.topic.isNotBlank()) SelectionContainer { Text(record.topic, style = MaterialTheme.typography.bodyMedium) }
            if (record.classwork.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Klasės darbas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SelectionContainer { Text(record.classwork, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            if (record.homework.isNotBlank()) Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HomeworkChip(due = false)
                        Text("Namų darbai" + (record.deadline?.let { " · atlikti iki ${it.format(shortDate)}" } ?: ""), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SelectionContainer { Text(record.homework, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
fun WeekScheduleScreen(state: PlannerState, back: () -> Unit) {
    val vm = LocalPlanner.current
    val school = LocalSchoolData.current
    val loading by vm.loading.collectAsStateWithLifecycle()
    var mondayText by rememberSaveable { mutableStateOf(mondayOf(LocalDate.now()).toString()) }
    val monday = LocalDate.parse(mondayText)
    RefreshOnResume(monday) { vm.loadWeek(monday) }
    val lessons = school.lessons.filter { it.date != null && mondayOf(it.date) == monday }
    val complete = readComplete("week", monday.toString())
    val thisWeek = monday == mondayOf(LocalDate.now())
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Savaitės tvarkaraštis", back, (if (thisWeek) "Ši savaitė · " else "") + "${monday.format(shortDate)}–${monday.plusDays(6).format(shortDate)}", actions = {
            IconButton(onClick = { mondayText = monday.minusWeeks(1).toString() }) { Icon(Icons.Outlined.ChevronLeft, "Ankstesnė savaitė") }
            IconButton(onClick = { mondayText = monday.plusWeeks(1).toString() }) { Icon(Icons.Outlined.ChevronRight, "Kita savaitė") }
        })
        ReadStatus("week", showProgress = false) { vm.loadWeek(monday, true) }
        PullToRefreshBox(isRefreshing = "week" in loading, onRefresh = { vm.loadWeek(monday, true) }, modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (complete && lessons.isEmpty()) item { EmptyPanel(Icons.Outlined.WbSunny, "Pamokų nėra", "Šią savaitę pamokų nėra.") }
                lessons.groupBy { it.date!! }.toSortedMap().forEach { (date, entries) ->
                    item("day-$date") {
                        Text("${dayLong[date.dayOfWeek.value - 1]}, ${date.format(shortDate)}", style = MaterialTheme.typography.titleSmall,
                            color = if (date == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 10.dp))
                    }
                    item("lessons-$date") {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                            Column(Modifier.padding(vertical = 6.dp)) {
                                entries.sortedWith(compareBy({ it.start }, { it.slot })).forEachIndexed { index, lesson ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text(if (lesson.slot > 0) lesson.slot.toString() else "", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(30.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(resolveSubject(lesson, state.rules).name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                            if (lesson.teacher.isNotBlank()) Text(lesson.teacher, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        if (lesson.start.isNotBlank()) Text("${lesson.start}\n${lesson.end}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WebPageScreen(link: MenuLink, back: () -> Unit) {
    val holder = remember(link.id) { WebHolder() }
    DisposableEffect(holder) { onDispose { holder.destroy() } }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(link.title, back, link.group.takeIf { it.isNotBlank() }, actions = {
            IconButton(onClick = { reloadWeb(holder) }) { Icon(Icons.Outlined.Refresh, "Atnaujinti") }
        })
        TamoWebContent(holder, link.url, Modifier.weight(1f), auth = link.auth, onClose = back)
    }
}

