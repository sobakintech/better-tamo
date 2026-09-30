@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package lt.bettertamo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import lt.bettertamo.data.*
import java.time.YearMonth
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ltLocale = Locale.forLanguageTag("lt")
private val timetableDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", ltLocale)
private val dateFormat = DateTimeFormatter.ofPattern("MM.dd", ltLocale)

@Composable
fun PlannerApp(vm: PlannerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val account by vm.session.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val signingIn by vm.signingIn.collectAsStateWithLifecycle()
    val loginError by vm.loginError.collectAsStateWithLifecycle()
    val school by vm.school.collectAsStateWithLifecycle()
    val changingAccount by vm.changingAccount.collectAsStateWithLifecycle()
    val accent by vm.accent.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(vm) {
        vm.fileLinks.collect { (url, name) ->
            runCatching {
                val fileName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { android.webkit.URLUtil.guessFileName(url, null, null) }
                val request = android.app.DownloadManager.Request(url.toUri()).setTitle(fileName)
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                (context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager).enqueue(request)
                android.widget.Toast.makeText(context, "Atsisiunčiama: $fileName", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure { vm.error.value = "Priedo atsisiųsti nepavyko." }
        }
    }
    BetterTamoTheme(state?.theme ?: vm.initialTheme, Accent.from(accent)) {
        val data = state
        if (data == null || !ready) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (error == null) CircularProgressIndicator() else Text(error!!, Modifier.padding(32.dp))
                }
            }
        } else if (account == null) {
            LoginScreen(signingIn, loginError, vm::login)
        } else if (account?.selectedRole == null) {
            RoleScreen(account!!, vm::selectRole, changingAccount, vm::signOut)
        } else {
            CompositionLocalProvider(LocalSchoolData provides school, LocalPlanner provides vm) {
                key(account!!.scope) { PlannerContent(data, vm) }
            }
        }
        if (error != null && data != null) AlertDialog(
            onDismissRequest = { vm.error.value = null },
            title = { Text("Nepavyko atlikti veiksmo") }, text = { Text(error!!) },
            confirmButton = { TextButton(onClick = { vm.error.value = null }) { Text("Gerai") } },
        )
        if (data != null && ready) AppUpdatePrompt()
    }
}

@Composable
private fun PlannerContent(state: PlannerState, vm: PlannerViewModel) {
    val school = LocalSchoolData.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var lessonSheet by rememberSaveable { mutableStateOf<String?>(null) }
    var eventDetail by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRule by rememberSaveable { mutableStateOf<String?>(null) }
    var editingSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var seedLesson by rememberSaveable { mutableStateOf<String?>(null) }
    var editingEvent by rememberSaveable { mutableStateOf<String?>(null) }
    var eventGap by rememberSaveable { mutableStateOf<String?>(null) }
    var pageStack by rememberSaveable { mutableStateOf("") }
    val pages = pageStack.split("\n").filter { it.isNotEmpty() }
    val page = pages.lastOrNull()
    fun open(next: String) { pageStack = (pages + next).joinToString("\n") }
    fun closePage() { pageStack = pages.dropLast(1).joinToString("\n") }
    val unread by vm.unreadMessages.collectAsStateWithLifecycle()
    RefreshOnResume(Unit) { vm.loadUnread() }
    val day = LocalDate.parse(selectedDate)
    RefreshOnResume(mondayOf(day)) { vm.loadWeek(day) }
    val openTab by vm.openTab.collectAsStateWithLifecycle()
    LaunchedEffect(openTab) {
        openTab?.let {
            tab = it; pageStack = ""
            vm.openTab.value = null
        }
    }
    val tabState = rememberSaveableStateHolder()
    val wideNavigation = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp }
    val titles = listOf("Tvarkaraštis", "Namų darbai", "Įvykiai", "Pranešimai", "Pažymiai")
    val icons = listOf(Icons.Outlined.CalendarMonth, Icons.AutoMirrored.Outlined.Assignment, Icons.Outlined.Notifications, Icons.Outlined.MailOutline, Icons.Outlined.School)
    fun selectTab(index: Int) { tab = index; pageStack = "" }

    fun openRule(subject: String, id: String? = null, lessonId: String? = null) {
        editingSubject = subject
        editingRule = id
        seedLesson = lessonId
    }
    fun openSubject(name: String) { open("subject:$name") }

    fun back() {
        when {
            page != null -> closePage()
            else -> tab = 0
        }
    }
    BackHandler(tab != 0 || page != null) { back() }
    Scaffold(
        topBar = { Spacer(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).windowInsetsTopHeight(WindowInsets.statusBars)) },
        bottomBar = {
            if (!wideNavigation) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                titles.indices.forEach { index ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("tab-$index"),
                        selected = tab == index, onClick = { selectTab(index) },
                        icon = { TabIcon(icons[index], if (index == 3) unread else 0) },
                        label = { Text(titles[index], maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                    )
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (wideNavigation) NavigationRail(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), containerColor = MaterialTheme.colorScheme.surfaceContainer, windowInsets = WindowInsets(0)) {
                titles.indices.forEach { index ->
                    NavigationRailItem(selected = tab == index,
                        onClick = { selectTab(index) },
                        icon = { TabIcon(icons[index], if (index == 3) unread else 0) }, label = { Text(titles[index]) }, modifier = Modifier.testTag("tab-$index"))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
                    CompositionLocalProvider(LocalOpenMore provides { open("more") }) {
                        when {
                            page == "more" -> MoreScreen(::closePage, ::open)
                            page == "personalization" -> Column {
                                ScreenHeader("Mano pamokos", ::closePage)
                                PersonalizationScreen(state, onRule = { openRule(it.subjectId, it.id) }, onNewRule = { openRule(it) }, onEvent = { editingEvent = it })
                            }
                            page == "upcoming" -> UpcomingScreen(::closePage) { date -> selectedDate = date.toString(); tab = 0; pageStack = "" }
                            page == "history" -> LessonHistoryScreen(state, ::closePage)
                            page == "schedule" -> WeekScheduleScreen(state, ::closePage)
                            page?.startsWith("subject:") == true -> Column {
                                ScreenHeader("Dalykas", ::closePage)
                                SubjectDetailScreen(page.removePrefix("subject:"), { date -> selectedDate = date.toString(); tab = 0; pageStack = "" }) { lesson ->
                                    openRule(lesson.subjectId, resolveSubject(lesson, state.rules).rule?.id, lesson.id)
                                }
                            }
                            page?.startsWith("message:") == true -> MessageScreen(page.removePrefix("message:"), ::closePage, ::open)
                            page?.startsWith("compose:") == true -> ComposeMessageScreen(page.removePrefix("compose:"), ::closePage)
                            page?.startsWith("web:") == true -> {
                                val menu by vm.menu.collectAsStateWithLifecycle()
                                val link = menu.find { it.id == page.removePrefix("web:") }
                                if (link != null) WebPageScreen(link, ::closePage) else LaunchedEffect(page) { closePage() }
                            }
                            else -> tabState.SaveableStateProvider(tab) {
                                when (tab) {
                                    0 -> TimetableScreen(day, state, { selectedDate = it.toString() }, { lessonSheet = it }, { eventDetail = it }, { gap -> eventGap = "${gap.start}|${gap.end}|${gap.slot ?: 0}"; editingEvent = "new" })
                                    1 -> HomeworkScreen(state, vm::toggleHomework)
                                    2 -> EventsScreen()
                                    3 -> MessagesScreen(::open)
                                    4 -> LiveGradesScreen(::openSubject)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    lessonSheet?.let { id ->
        val lesson = school.lessons.find { it.id == id }
        if (lesson == null) lessonSheet = null
        else LessonSheet(lesson, state, onDismiss = { lessonSheet = null },
            openSubject = { lessonSheet = null; openSubject(lesson.subject) })
    }
    eventDetail?.let { id ->
        state.events.find { it.id == id }?.let { event ->
            DetailSheet(onDismiss = { eventDetail = null }) {
                SmallTag("Mano įvykis", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                Text(event.title, style = MaterialTheme.typography.headlineMedium)
                Text("${event.start}–${event.end}")
                DetailRow(Icons.Outlined.Repeat, event.weekdays.sorted().joinToString(" · ") { dayShort[it - 1] } + " · kas savaitę")
                if (event.note.isNotBlank()) Text(event.note)
                Button(onClick = { eventDetail = null; editingEvent = event.id }, modifier = Modifier.fillMaxWidth()) { Text("Redaguoti įvykį") }
            }
        }
    }
    editingSubject?.let { subject ->
        RuleEditor(
            subjectId = subject,
            existing = state.rules.find { it.id == editingRule },
            seedLesson = school.lessons.find { it.id == seedLesson },
            rules = state.rules,
            savedSources = state.ruleSources,
            onDismiss = { editingSubject = null },
            onSave = { vm.saveRule(it); editingSubject = null },
            onDelete = { vm.deleteRule(it); editingSubject = null },
        )
    }
    editingEvent?.let { id ->
        val gap = eventGap?.split("|")
        EventEditor(state.events.find { it.id == id }, day.dayOfWeek.value,
            onDismiss = { editingEvent = null; eventGap = null },
            onSave = { vm.saveEvent(it); editingEvent = null; eventGap = null },
            onDelete = { vm.deleteEvent(it); editingEvent = null; eventGap = null },
            defaultStart = gap?.getOrNull(0) ?: "15:10", defaultEnd = gap?.getOrNull(1) ?: "15:55", defaultSlot = gap?.getOrNull(2)?.toIntOrNull() ?: 0,
        )
    }
}

@Composable
private fun TabIcon(icon: ImageVector, count: Int) {
    if (count > 0) BadgedBox(badge = { Badge { Text(if (count > 99) "99+" else count.toString()) } }) { Icon(icon, "Neperskaityti: $count") }
    else Icon(icon, null)
}

@Composable
private fun TimetableScreen(date: LocalDate, state: PlannerState, selectDate: (LocalDate) -> Unit, openLesson: (String) -> Unit, openEvent: (String) -> Unit, addLesson: (Gap) -> Unit) {
    val school = LocalSchoolData.current
    val vm = LocalPlanner.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var monthText by rememberSaveable(date.toString()) { mutableStateOf(YearMonth.from(date).toString()) }
    val month = YearMonth.parse(monthText)
    RefreshOnResume(if (expanded) month else YearMonth.from(date)) { vm.loadMonth(if (expanded) month else YearMonth.from(date)) }
    RefreshOnResume(mondayOf(date)) { vm.loadWeek(date) }
    BackHandler(expanded) { expanded = false }
    val loading by vm.loading.collectAsStateWithLifecycle()
    val readErrors by vm.readErrors.collectAsStateWithLifecycle()
    fun chooseDate(day: LocalDate) {
        selectDate(day)
        expanded = false
    }
    val pager = rememberPagerState(initialPage = dayPage(date)) { DAY_PAGES }
    val currentDate by rememberUpdatedState(date)
    val currentSelect by rememberUpdatedState(selectDate)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != dayPage(currentDate)) currentSelect(pageDay(page)) }
    }
    LaunchedEffect(date) {
        val target = dayPage(date)
        if (pager.currentPage != target) {
            if (kotlin.math.abs(pager.currentPage - target) <= 7) pager.animateScrollToPage(target) else pager.scrollToPage(target)
        }
    }
    val shownDate by remember { derivedStateOf { pageDay(pager.targetPage) } }
    val now by produceState(java.time.LocalDateTime.now()) {
        while (true) {
            value = java.time.LocalDateTime.now()
            kotlinx.coroutines.delay(30_000L - System.currentTimeMillis() % 30_000L)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelHeight = maxHeight * 0.86f
        Column {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                Column {
                    TimetableMonthHeader(YearMonth.from(shownDate), false, { monthText = YearMonth.from(shownDate).toString(); expanded = true }, { selectDate(shownDate.minusWeeks(1)) }, { selectDate(shownDate.plusWeeks(1)) }, { selectDate(LocalDate.now()) }) { KeepScreenOnButton(); ProfileButton() }
                    TimetableWeekStrip(shownDate, selectDate)
                }
            }
            if ("week" in readErrors) ReadStatus("week", showProgress = false) { vm.loadWeek(date, true) }
            else if ("month" in readErrors) ReadStatus("month", showProgress = false) { vm.loadMonth(YearMonth.from(date), true) }
            HorizontalPager(pager, Modifier.weight(1f), pageSpacing = 16.dp, key = { it }) { page ->
                val day = pageDay(page)
                val schoolEvents = school.calendarEvents.filter { it.contains(day) }
                val lessons = school.lessons.filter { it.date == day }
                val events = eventsOn(day, state.events, lessons)
                val weekComplete = readComplete("week", mondayOf(day).toString())
                TimetableList(
                    isRefreshing = "week" in loading || "month" in loading,
                    onRefresh = { vm.loadWeek(day, true); vm.loadMonth(YearMonth.from(day), true) },
                ) {
                    if (schoolEvents.isNotEmpty()) {
                        items(schoolEvents, key = { it.id }) { event -> SchoolEventCard(event) { expanded = true } }
                    }
                    if (weekComplete && lessons.isEmpty() && events.isEmpty() && schoolEvents.isEmpty()) item { EmptyPanel(Icons.Outlined.WbSunny, if (day.dayOfWeek.value >= 6) "Savaitgalis" else "Laisva diena", "Šią dieną pamokų nėra.") }
                    val pause = breakProgress(day, lessons.map { it.start to it.end } + events.map { it.start to it.end }, now)
                    val entries = timetableRows((lessons.map { TimetableEntry(it.start, lesson = it) } + events.map { TimetableEntry(it.start, event = it) }).sortedBy { it.start }, slotTimes(school.lessons), events, pause)
                    items(entries, key = { it.pause?.let { "pause" } ?: it.gap?.let { gap -> "gap-${gap.start}" } ?: it.lesson?.id ?: "event-${it.event!!.id}" }) { entry ->
                        entry.pause?.let { BreakMarker(it) }
                        entry.gap?.let { gap -> FreePeriod(gap) { addLesson(gap) } }
                        entry.lesson?.let { lesson ->
                            val subject = resolveSubject(lesson, state.rules)
                            LessonCard(subject.name, lesson.topic, start = lesson.start, end = lesson.end, slot = lesson.slot,
                                dueHomework = lesson.hasHomework, assignedHomework = lesson.details.any { it.key == "homework_next" } || school.homework.any { it.lessonId == lesson.id },
                                assessment = lesson.assessment, lessonLabel = lesson.label, important = lesson.highlighted, remark = lesson.note, formatives = lesson.formatives, average = lesson.average, trend = lesson.trend,
                                progress = lessonProgress(lesson.date, lesson.start, lesson.end, now)) { openLesson(lesson.id) }
                        }
                        entry.event?.let { event ->
                            LessonCard(event.title, event.note, custom = true, start = event.start, end = event.end, slot = event.slot.takeIf { it > 0 },
                                progress = lessonProgress(day, event.start, event.end, now)) { openEvent(event.id) }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().testTag("calendar-scrim").background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)).clickable(onClickLabel = "Uždaryti kalendorių") { expanded = false })
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(), exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()) {
            Surface(modifier = Modifier.fillMaxWidth().heightIn(max = panelHeight), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 8.dp) {
                SchoolCalendarPanel(month, date, ::chooseDate, { monthText = it.toString() }) { expanded = false }
            }
        }
    }
}

private val pagerEpoch = LocalDate.of(2000, 1, 1)
private val DAY_PAGES = java.time.temporal.ChronoUnit.DAYS.between(pagerEpoch, LocalDate.of(2100, 1, 1)).toInt()
private fun dayPage(date: LocalDate) = java.time.temporal.ChronoUnit.DAYS.between(pagerEpoch, date).toInt().coerceIn(0, DAY_PAGES - 1)
private fun pageDay(page: Int) = pagerEpoch.plusDays(page.toLong())

@Composable
internal fun TimetableList(isRefreshing: Boolean, onRefresh: () -> Unit, content: LazyListScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("timetable-list").semantics {
                customActions = listOf(CustomAccessibilityAction("Atnaujinti tvarkaraštį") {
                    if (isRefreshing) false else { onRefresh(); true }
                })
            },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

data class Gap(val start: String, val end: String, val slot: Int?)

private data class TimetableEntry(val start: String, val lesson: Lesson? = null, val event: CustomEvent? = null, val gap: Gap? = null, val pause: LessonProgress? = null)

private fun slotTimes(lessons: List<Lesson>): Map<Int, Pair<String, String>> = lessons.filter { it.slot > 0 && it.start.isNotBlank() && it.end.isNotBlank() }
    .groupBy { it.slot }.mapValues { (_, list) -> list.groupingBy { it.start to it.end }.eachCount().maxBy { it.value }.key }

private fun timetableRows(entries: List<TimetableEntry>, times: Map<Int, Pair<String, String>>, events: List<CustomEvent>, pause: Pair<String, LessonProgress>?): List<TimetableEntry> {
    val gaps = mutableListOf<Gap>()
    val lessons = entries.mapNotNull { it.lesson }.filter { it.slot > 0 && it.start.isNotBlank() && it.end.isNotBlank() }.sortedBy { it.slot }
    lessons.zipWithNext().forEach { (last, next) ->
        val missing = (last.slot + 1 until next.slot).toList()
        if (missing.isEmpty()) return@forEach
        val known = missing.mapNotNull { slot -> times[slot]?.let { Gap(it.first, it.second, slot) } }
        gaps += if (known.size == missing.size && known.zipWithNext().all { (a, b) -> a.end <= b.start } && known.first().start >= last.end && known.last().end <= next.start) known
            else listOf(Gap(last.end, next.start, missing.singleOrNull()))
    }
    val open = gaps.filter { gap -> events.none { it.start < gap.end && it.end > gap.start } }
    val marker = listOfNotNull(pause?.let { (start, progress) -> TimetableEntry(start, pause = progress) })
    return (entries + marker + open.map { TimetableEntry(it.start, gap = it) }).sortedWith(compareBy({ it.start }, { when { it.pause != null -> 1; it.gap != null -> 2; else -> 0 } }))
}

@Composable
private fun BreakMarker(progress: LessonProgress) {
    val primary = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).padding(horizontal = 8.dp).semantics(mergeDescendants = true) { contentDescription = "Pertrauka, liko ${progress.minutesLeft} min." },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pertrauka", style = MaterialTheme.typography.labelMedium, color = primary)
        Box(Modifier.weight(1f).height(4.dp).background(primary.copy(alpha = 0.16f), CircleShape)) {
            Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(primary, CircleShape))
        }
        Text("liko ${progress.minutesLeft} min.", style = MaterialTheme.typography.labelMedium, color = primary)
    }
}

@Composable
private fun FreePeriod(gap: Gap, onAdd: () -> Unit) {
    Surface(onClick = onAdd, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Langas ${gap.start}–${gap.end}" + (gap.slot?.let { ", $it pamoka" } ?: "") + ". Pridėti pamoką" }) {
        Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(gap.start, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Icon(Icons.Outlined.AddCircleOutline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text(gap.end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NoteIcon(note: String, modifier: Modifier = Modifier) {
    when (note) {
        "positive" -> Icon(Icons.Outlined.MarkChatRead, "Pagyrimas", modifier.size(20.dp), tint = noticeColor(NoticeKind.PRAISE))
        "negative" -> Icon(Icons.Outlined.Feedback, "Pastaba", modifier.size(20.dp), tint = noticeColor(NoticeKind.REMARK))
        "comment" -> Icon(Icons.AutoMirrored.Outlined.Chat, "Komentaras", modifier.size(20.dp), tint = noticeColor(NoticeKind.COMMENT))
    }
}

@Composable
fun LessonCard(title: String, description: String, custom: Boolean = false, start: String? = null, end: String? = null, slot: Int? = null, dueHomework: Boolean = false, assignedHomework: Boolean = false,
    assessment: String = "", lessonLabel: String = "", important: Boolean = false, remark: String = "", formatives: List<String> = emptyList(), average: String = "", trend: String = "",
    teacher: String = "", descriptionLines: Int = 1, containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLowest, progress: LessonProgress? = null, onClick: (() -> Unit)?) {
    val marks = assessment.split(" · ").filter { it.isNotBlank() }
    val hasSide = marks.isNotEmpty() || formatives.isNotEmpty() || remark.isNotBlank() || average.isNotBlank()
    val accent = MaterialTheme.colorScheme.error
    val primary = MaterialTheme.colorScheme.primary
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
                if (important) drawRect(accent, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                if (progress != null) {
                    val bar = 4.dp.toPx()
                    drawRect(primary.copy(alpha = 0.16f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - bar), size = androidx.compose.ui.geometry.Size(size.width, bar))
                    drawRect(primary, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - bar), size = androidx.compose.ui.geometry.Size(size.width * progress.fraction, bar))
                }
            }
            .then(if (progress != null) Modifier.semantics { stateDescription = "Vyksta, liko ${progress.minutesLeft} min." } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (start != null && end != null) {
                Column(Modifier.width((44 * LocalDensity.current.fontScale.coerceIn(1f, 2f)).dp).fillMaxHeight().heightIn(min = 68.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Text(start, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (slot != null && slot > 0) Text(slot.toString(), style = MaterialTheme.typography.titleLarge, color = if (progress != null) primary else androidx.compose.ui.graphics.Color.Unspecified)
                    Text(end, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            }
            Column(Modifier.weight(1f)) {
                if (lessonLabel.isNotBlank()) Text(lessonLabel.uppercase(ltLocale), style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.6.sp, lineHeight = 14.sp), maxLines = 1,
                    color = if (important) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (custom) Icon(Icons.Outlined.EditCalendar, "Mano įvykis", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = descriptionLines, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (teacher.isNotBlank()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(teacher, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (dueHomework || assignedHomework) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dueHomework) HomeworkChip(due = true)
                    if (assignedHomework) HomeworkChip(due = false)
                }
            }
            if (hasSide) {
                VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.widthIn(min = 36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                    marks.forEach { mark ->
                        Text(mark, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                            color = if (mark.toIntOrNull() == null && mark.trim().lowercase().startsWith("n")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                    formatives.forEach { MarkBadge(it, DiaryKind.FORMATIVE) }
                    NoteIcon(remark)
                    if (average.isNotBlank()) Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Vidurkis $average" + when (trend) { "up" -> ", kyla"; "down" -> ", krenta"; "flat" -> ", nekinta"; else -> "" } }, verticalAlignment = Alignment.CenterVertically) {
                        when (trend) {
                            "up" -> Icon(Icons.Outlined.ArrowUpward, null, Modifier.size(14.dp), tint = StatusColors.good)
                            "down" -> Icon(Icons.Outlined.ArrowDownward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                            "flat" -> Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(average, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
    }
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = progress?.let { androidx.compose.foundation.BorderStroke(2.dp, primary) }
    if (onClick != null) Card(onClick = onClick, shape = RoundedCornerShape(16.dp), colors = colors, border = border, content = content)
    else Card(shape = RoundedCornerShape(16.dp), colors = colors, border = border, content = content)
}

@Composable
internal fun PersonalizationScreen(state: PlannerState, onRule: (LessonRule) -> Unit, onNewRule: (String) -> Unit, onEvent: (String) -> Unit) {
    val school = LocalSchoolData.current
    val sources = school.lessons + state.ruleSources.map { it.lesson() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.AutoFixHigh, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Pervadink pagal mokytoją arba pasirinktas savaitės pamokas. Pavadinimai pasikeis ir namų darbuose.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        item { SectionTitle("Pamokų pavadinimai", "${state.rules.size} taisyklės") }
        items(state.rules.filter { rule -> sources.none { it.subjectId == rule.subjectId && it.canRename } }, key = { "saved-rule-${it.id}" }) { rule ->
            Card(onClick = { onRule(rule) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rule.name, style = MaterialTheme.typography.titleMedium)
                        Text(ruleSummary(rule, sources), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Outlined.ChevronRight, null)
                }
            }
        }
        sources.filter { it.canRename }.distinctBy { it.subjectId }.forEach { subject ->
            val rules = state.rules.filter { it.subjectId == subject.subjectId }
            if (rules.isNotEmpty() || subject.subjectId in listOf("science", "lithuanian")) {
                item("subject-${subject.subjectId}") {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(subject.subject, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                            rules.forEach { rule ->
                                Surface(onClick = { onRule(rule) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                            Text(ruleSummary(rule, sources), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            TextButton(onClick = { onNewRule(subject.subjectId) }) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pridėti pavadinimą")
                            }
                        }
                    }
                }
            }
        }
        item {
            var menu by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { menu = true }, enabled = sources.any { it.canRename }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pervadinti kitą dalyką") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    sources.filter { it.canRename }.distinctBy { it.subjectId }.sortedBy { it.subject }.forEach { lesson ->
                        DropdownMenuItem(text = { Text(lesson.subject) }, onClick = { menu = false; onNewRule(lesson.subjectId) })
                    }
                }
            }
        }
        item { SectionTitle("Mano įvykiai", "Kas savaitę") }
        items(state.events, key = { "event-${it.id}" }) { event ->
            LessonCard(event.title, event.weekdays.sorted().joinToString(" · ") { dayShort[it - 1] } + "  ${event.start}–${event.end}", custom = true) { onEvent(event.id) }
        }
        item {
            OutlinedButton(onClick = { onEvent("new") }, modifier = Modifier.fillMaxWidth().testTag("add-event")) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Pridėti savo įvykį") }
        }
    }
}

fun ruleSummary(rule: LessonRule, lessons: List<Lesson>): String = when (rule.mode) {
    MatchMode.TEACHER -> lessons.firstOrNull { it.teacherId == rule.teacherId }?.teacher ?: "Pagal mokytoją"
    MatchMode.SLOTS -> rule.slots.sorted().joinToString(" · ") { slot ->
        val parts = slot.split(":")
        "${dayShort[parts[0].toInt() - 1]} ${parts[1]}"
    }
}

@Composable
fun SmallTag(text: String, background: androidx.compose.ui.graphics.Color, foreground: androidx.compose.ui.graphics.Color) {
    Surface(color = background, contentColor = foreground, shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun DetailRow(icon: ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, caption: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyPanel(icon: ImageVector, title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
internal fun DetailSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}
