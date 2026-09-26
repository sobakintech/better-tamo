package lt.bettertamo.data

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.plannerStore by preferencesDataStore("demo_planner_v1")
private val codec = Json { ignoreUnknownKeys = true }

class PlannerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = application.plannerStore
    private val vault = SessionVault(application)
    private val cache = SchoolCache(application)
    private var saveJob: Job? = null
    private val api = TamoApi()
    val session = MutableStateFlow<SchoolSession?>(null)
    val ready = MutableStateFlow(false)
    val signingIn = MutableStateFlow(false)
    val loginError = MutableStateFlow<String?>(null)
    val school = MutableStateFlow(SchoolData())
    val loading = MutableStateFlow(emptySet<String>())
    val readErrors = MutableStateFlow(emptyMap<String, String>())
    val periods = MutableStateFlow(emptyList<SchoolPeriod>())
    val semesterSubjects = MutableStateFlow(emptyList<SemesterSubject>())
    val chosenPeriod = MutableStateFlow<String?>(null)
    val feed = MutableStateFlow(emptyList<SchoolNotice>())
    val remarks = MutableStateFlow(emptyList<SchoolNotice>())
    val upcoming = MutableStateFlow(emptyList<UpcomingEvent>())
    val history = MutableStateFlow(emptyList<LessonRecord>())
    val yearLessons = MutableStateFlow(emptyList<LessonRecord>())
    val menu = MutableStateFlow(emptyList<MenuLink>())
    val messages = MutableStateFlow(emptyList<MessageHeader>())
    val messagesEnd = MutableStateFlow(false)
    val unreadMessages = MutableStateFlow(0)
    val message = MutableStateFlow<MessageDetail?>(null)
    private val jobs = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String, String>()
    private val freshness = ReadFreshness(clock = { android.os.SystemClock.elapsedRealtime() })
    val loadedRequests = MutableStateFlow(emptyMap<String, String>())
    val changingAccount = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val openTab = MutableStateFlow<Int?>(null)
    val notifications = MutableStateFlow(SchoolUpdates.enabled(application))
    val pushActive = MutableStateFlow(TamoPush.active(application))
    val notificationsBusy = MutableStateFlow(false)
    private var lastTestNotification = 0L
    val state = combine(store.data, session) { prefs, account ->
        val key = stringPreferencesKey("account:${account?.scope.orEmpty()}")
        prefs[key]?.let { codec.decodeFromString<PlannerState>(it) } ?: PlannerState(theme = prefs[stringPreferencesKey("appearance")] ?: prefs[stringPreferencesKey("planner")]?.let { runCatching { codec.decodeFromString<PlannerState>(it).theme }.getOrNull() } ?: "system")
    }.catch { error.value = "Nepavyko įkelti išsaugotų pakeitimų. Pabandykite iš naujo paleisti programėlę." }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val ui = application.getSharedPreferences("ui", Context.MODE_PRIVATE)
    val initialTheme: String = ui.getString("theme", null) ?: "system"

    init {
        if (SchoolUpdates.enabled(application)) SchoolUpdates.setEnabled(application, true)
        viewModelScope.launch {
            state.collect { value -> value?.theme?.takeIf { it != ui.getString("theme", null) }?.let { ui.edit { putString("theme", it) } } }
        }
        viewModelScope.launch {
            session.filterNotNull().map { it.scope }.distinctUntilChanged().collect {
                if (SchoolUpdates.enabled(application) && TamoPush.active(application)) syncPush(false)
            }
        }
        viewModelScope.launch {
            try {
                val (account, snapshot) = withContext(Dispatchers.IO) { vault.read().let { it to it?.let { account -> cache.read(account.scope) } } }
                snapshot?.let(::restore)
                session.value = account
            }
            catch (_: Exception) { loginError.value = "Nepavyko atkurti prisijungimo. Prisijunkite iš naujo." }
            ready.value = true
        }
    }

    fun login(username: String, password: String) {
        if (signingIn.value) return
        signingIn.value = true
        loginError.value = null
        viewModelScope.launch {
            try {
                val account = api.roles(api.login(username.trim(), password))
                withContext(Dispatchers.IO) { vault.save(account) }
                session.value = account
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { loginError.value = (e as? TamoFailure)?.userMessage ?: "Prisijungti nepavyko. Bandykite dar kartą." }
            finally { signingIn.value = false }
        }
    }

    fun selectRole(id: String) {
        val account = session.value ?: return
        if (changingAccount.value || account.selectedRole == id || account.roles.none { it.id == id }) return
        changingAccount.value = true
        viewModelScope.launch {
            try {
                val selected = account.copy(selectedRole = id)
                val snapshot = withContext(Dispatchers.IO) { vault.save(selected); cache.read(selected.scope) }
                clearReads()
                snapshot?.let(::restore)
                session.value = selected
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Nepavyko išsaugoti pasirinktos paskyros." }
            finally { changingAccount.value = false }
        }
    }

    private suspend fun syncPush(force: Boolean) {
        val account = session.value ?: return
        try { TamoPush.register(getApplication(), account, force) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) {}
        pushActive.value = TamoPush.active(getApplication())
    }

    fun setNotifications(enabled: Boolean) {
        if (notificationsBusy.value) return
        notificationsBusy.value = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                if (enabled) {
                    syncPush(true)
                    SchoolUpdates.setEnabled(app, true)
                } else {
                    SchoolUpdates.setEnabled(app, false)
                    TamoPush.unregister(app, session.value)
                }
                notifications.value = enabled
            } finally {
                pushActive.value = TamoPush.active(app)
                notificationsBusy.value = false
            }
        }
    }

    fun testNotification() {
        val account = session.value ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastTestNotification != 0L && now - lastTestNotification < 60_000) {
            error.value = "Bandomąjį pranešimą galima siųsti kartą per minutę."
            return
        }
        lastTestNotification = now
        viewModelScope.launch {
            try { api.testNotification(account) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = (e as? TamoFailure)?.userMessage ?: "Nepavyko išsiųsti bandomojo pranešimo." }
        }
    }

    fun signOut() {
        if (changingAccount.value) return
        changingAccount.value = true
        viewModelScope.launch {
            try {
                TamoPush.unregister(getApplication(), session.value)
                withContext(Dispatchers.IO) { vault.clear(); cache.clear(); SchoolUpdates.clear(getApplication()) }
                notifications.value = false
                pushActive.value = false
                runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null); android.webkit.WebStorage.getInstance().deleteAllData() }
                clearReads()
                session.value = null
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Atsijungti nepavyko. Bandykite dar kartą." }
            finally { changingAccount.value = false }
        }
    }

    private fun restore(snapshot: SchoolSnapshot) {
        school.value = snapshot.school()
        periods.value = snapshot.periods
        semesterSubjects.value = snapshot.semester
        snapshot.feed?.let { feed.value = it }
        loadedRequests.value = buildMap {
            if (snapshot.periods.isNotEmpty()) put("periods", "periods")
            snapshot.semesterPeriod?.let { put("semester", it) }
            if (snapshot.feed != null) put("feed", "feed")
        }
    }

    private fun persist() {
        val scope = session.value?.scope ?: return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1_000)
            val loaded = loadedRequests.value
            val snapshot = SchoolSnapshot.of(scope, school.value, periods.value, loaded["semester"], semesterSubjects.value.takeIf { loaded["semester"] != null }.orEmpty(), feed.value.takeIf { loaded["feed"] == "feed" })
            withContext(Dispatchers.IO) { runCatching { cache.write(snapshot) } }
        }
    }

    private fun clearReads() {
        saveJob?.cancel()
        jobs.values.forEach { it.cancel() }; jobs.clear()
        school.value = SchoolData(); periods.value = emptyList(); semesterSubjects.value = emptyList(); chosenPeriod.value = null; feed.value = emptyList(); remarks.value = emptyList()
        upcoming.value = emptyList(); history.value = emptyList(); yearLessons.value = emptyList(); menu.value = emptyList()
        messages.value = emptyList(); messagesEnd.value = false; unreadMessages.value = 0; message.value = null
        loading.value = emptySet(); readErrors.value = emptyMap()
        requests.clear(); freshness.clear(); loadedRequests.value = emptyMap()
    }

    private fun read(key: String, identity: String = key, force: Boolean = false, block: suspend (SchoolSession) -> Unit) {
        val account = session.value?.takeIf { it.selectedRole != null } ?: return
        if (!force && requests[key] == identity && jobs[key]?.isActive == true) return
        jobs[key]?.cancel()
        requests[key] = identity
        readErrors.value -= key
        if (!force && freshness.isFresh(key, identity)) {
            loading.value -= key
            return
        }
        loading.value += key
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block(account)
                ensureActive()
                freshness.complete(key, identity)
                loadedRequests.value += key to identity
                persist()
            }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                ensureActive()
                if ((e as? TamoFailure)?.expired == true) {
                    loginError.value = e.userMessage
                    signOut()
                } else readErrors.value += key to ((e as? TamoFailure)?.userMessage ?: "Duomenų įkelti nepavyko. Bandykite dar kartą.")
            } finally {
                if (jobs[key] === kotlinx.coroutines.currentCoroutineContext()[Job]) {
                    loading.value -= key
                    jobs.remove(key)
                }
            }
        }
        jobs[key] = job
        job.start()
    }

    fun loadWeek(date: LocalDate, force: Boolean = false) {
        val monday = mondayOf(date)
        read("week", monday.toString(), force) { account ->
            val calendar = api.week(account, monday)
            val lessons = api.mapper.week(calendar, emptyList())
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            school.value = school.value.copy(lessons = (school.value.lessons.filterNot { it.date != null && mondayOf(it.date) == monday } + lessons).distinctBy { it.id }, weekLoaded = monday, loadedWeeks = school.value.loadedWeeks + monday)
        }
    }

    fun loadMonth(month: YearMonth, force: Boolean = false) {
        read("month", month.toString(), force) { account ->
            val events = api.calendar(account, month)
            val badges = api.badges(account, month)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            school.value = school.value.copy(calendarEvents = (school.value.calendarEvents.filterNot { it.overlaps(month) } + events).distinctBy { it.id }, badges = school.value.badges.filterKeys { YearMonth.from(it) != month } + badges, monthLoaded = month.toString())
        }
    }

    fun loadHomework(past: Boolean, force: Boolean = false) {
        val today = LocalDate.now()
        val from = if (past) today.minusDays(30) else today
        val to = if (past) today.minusDays(1) else today.plusDays(30)
        val key = "$from:$to"
        read("homework", key, force) { account ->
            val work = api.homework(account, from, to)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            freshness.invalidate("homework")
            loadedRequests.value -= "homework"
            school.value = school.value.copy(homework = work, homeworkLoaded = key)
            val weeks = work.filter { school.value.lessons.none { lesson -> lesson.id == it.lessonId } }.mapNotNull { it.assignedDate?.let(::mondayOf) }.distinct().filter { it !in school.value.loadedWeeks }
            val sourceLessons = mutableListOf<Lesson>()
            for (week in weeks) {
                sourceLessons += api.mapper.week(api.week(account, week), emptyList())
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            school.value = school.value.copy(homework = work, lessons = (school.value.lessons + sourceLessons).distinctBy { it.id }, homeworkLoaded = key, loadedWeeks = school.value.loadedWeeks + weeks)
        }
    }

    private fun mergeDiary(entries: List<DiaryEntry>, from: LocalDate, to: LocalDate) {
        val months = generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(to) }.toSet()
        school.value = school.value.copy(diary = school.value.diary.filterNot { YearMonth.from(it.date) in months } + entries, diaryMonths = school.value.diaryMonths + months)
    }

    fun loadDiary(month: YearMonth, force: Boolean = false) = read("diary", month.toString(), force) { account ->
        val entries = api.diary(account, month.atDay(1), month.atEndOfMonth())
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        mergeDiary(entries, month.atDay(1), month.atEndOfMonth())
    }

    fun loadSchoolYear(force: Boolean = false) = read("year", schoolYearStart().toString(), force) { account ->
        for ((from, to) in schoolYearChunks()) {
            val entries = api.diary(account, from, to)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            mergeDiary(entries, from, to)
        }
    }

    val openingFile = MutableStateFlow<String?>(null)
    val fileLinks = kotlinx.coroutines.flow.MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 1)

    fun openFile(file: SchoolFile) {
        val account = session.value?.takeIf { it.selectedRole != null } ?: return
        if (openingFile.value != null) return
        openingFile.value = file.sid
        viewModelScope.launch {
            try { fileLinks.emit(api.fileUrl(account, file.sid) to file.name) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = (e as? TamoFailure)?.userMessage ?: "Priedo atidaryti nepavyko." }
            finally { openingFile.value = null }
        }
    }

    fun loadPeriods(force: Boolean = false) = read("periods", force = force) { account ->
        val result = api.periods(account)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        periods.value = result
    }

    fun loadSemester(id: String, force: Boolean = false) {
        val period = periods.value.find { it.id == id } ?: return
        read("semester", id, force) { account ->
            val result = api.semester(account, period)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            semesterSubjects.value = result
        }
    }

    fun loadFeed(force: Boolean = false) = read("feed", force = force) { account ->
        val result = api.notices(account, false, YearMonth.now())
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        feed.value = result
        withContext(Dispatchers.IO) { SchoolUpdates.markSeen(getApplication(), account.scope, result) }
    }

    fun loadRemarks(month: YearMonth, force: Boolean = false) = read("remarks", month.toString(), force) { account ->
        val result = api.notices(account, true, month)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        remarks.value = result
    }

    fun loadUpcoming(force: Boolean = false) {
        val today = LocalDate.now()
        read("upcoming", today.toString(), force) { account ->
            val result = api.upcoming(account, today, today.plusDays(61))
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            upcoming.value = result
        }
    }

    fun loadHistory(month: YearMonth, force: Boolean = false) = read("history", month.toString(), force) { account ->
        val to = minOf(month.atEndOfMonth(), LocalDate.now().plusDays(7))
        val result = if (to.isBefore(month.atDay(1))) emptyList() else api.lessonHistory(account, month.atDay(1), to)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        history.value = result
    }

    fun loadYearLessons(force: Boolean = false) = read("year-lessons", schoolYearStart().toString(), force) { account ->
        val result = mutableListOf<LessonRecord>()
        for ((from, to) in schoolYearChunks()) {
            result += api.lessonHistory(account, from, to)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
        }
        yearLessons.value = result.distinctBy { it.id }
    }

    fun loadMenu(force: Boolean = false) = read("menu", force = force) { account ->
        val result = api.menu(account)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        menu.value = result
    }

    fun loadMessages(folder: MessageFolder, search: String, force: Boolean = false) = read("messages", "$folder:$search", force) { account ->
        val result = api.messages(account, folder, 1, search)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        jobs["messages-more"]?.cancel()
        messages.value = result
        messagesEnd.value = folder == MessageFolder.GROUP || result.size < 30
        if (folder == MessageFolder.RECEIVED && search.isBlank()) {
            unreadMessages.value = result.count { !it.read }
            withContext(Dispatchers.IO) { SchoolUpdates.markMessagesSeen(getApplication(), account.scope, result) }
        }
    }

    fun loadUnread(force: Boolean = false) = read("unread", force = force) { account ->
        val result = api.messages(account, MessageFolder.RECEIVED, 1, "")
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        unreadMessages.value = result.count { !it.read }
        withContext(Dispatchers.IO) { SchoolUpdates.markMessagesSeen(getApplication(), account.scope, result) }
    }

    fun loadMoreMessages() {
        val identity = requests["messages"] ?: return
        if (messagesEnd.value || jobs["messages-more"]?.isActive == true || "messages" in loading.value) return
        val folder = MessageFolder.valueOf(identity.substringBefore(":"))
        val search = identity.substringAfter(":")
        val page = messages.value.size / 30 + 1
        read("messages-more", "$identity:$page", force = true) { account ->
            val result = api.messages(account, folder, page, search)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (requests["messages"] != identity) return@read
            messages.value = (messages.value + result).distinctBy { it.sid }
            messagesEnd.value = result.isEmpty()
        }
    }

    fun openMessage(header: MessageHeader) {
        if (message.value?.header?.sid != header.sid) message.value = null
        read("message", header.sid, force = true) { account ->
            val detail = api.message(account, header)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            message.value = detail
            if (!header.read) {
                messages.value = messages.value.map { if (it.sid == header.sid) it.copy(read = true) else it }
                unreadMessages.value = (unreadMessages.value - 1).coerceAtLeast(0)
            }
        }
    }

    private fun changeMessage(sid: String, transform: (MessageHeader) -> MessageHeader, call: suspend (SchoolSession) -> Unit) {
        val account = session.value?.takeIf { it.selectedRole != null } ?: return
        val before = messages.value
        val detail = message.value
        messages.value = before.map { if (it.sid == sid) transform(it) else it }
        if (detail?.header?.sid == sid) message.value = detail.copy(header = transform(detail.header))
        viewModelScope.launch {
            try { call(account) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                messages.value = before
                if (detail?.header?.sid == sid) message.value = detail
                error.value = (e as? TamoFailure)?.userMessage ?: "Pakeitimo išsaugoti nepavyko."
            }
        }
    }

    fun starMessage(header: MessageHeader) = changeMessage(header.sid, { it.copy(starred = !header.starred) }) { api.starMessage(it, header.sid, !header.starred) }
    fun markUnread(header: MessageHeader) {
        changeMessage(header.sid, { it.copy(read = false) }) { api.markUnread(it, header.sid) }
        if (header.read) unreadMessages.value += 1
    }

    suspend fun webUrl(target: String): String? {
        var account = session.value?.takeIf { it.selectedRole != null } ?: return null
        if (account.roles.find { it.id == account.selectedRole }?.studentId.isNullOrBlank()) {
            runCatching { api.roles(account) }.getOrNull()?.takeIf { it.selectedRole == account.selectedRole }?.let { refreshed ->
                withContext(Dispatchers.IO) { runCatching { vault.save(refreshed) } }
                session.value = refreshed
                account = refreshed
            }
        }
        return api.webUrl(account, target)
    }

    private fun update(transform: (PlannerState) -> PlannerState) {
        val scope = session.value?.scope ?: return
        val stateKey = stringPreferencesKey("account:$scope")
        viewModelScope.launch {
            runCatching {
                store.edit { prefs ->
                    val current = prefs[stateKey]?.let { codec.decodeFromString<PlannerState>(it) } ?: PlannerState(theme = state.value?.theme ?: "system")
                    prefs[stateKey] = codec.encodeToString(transform(current))
                }
            }.onFailure {
                if (it is CancellationException) throw it
                error.value = "Pakeitimų išsaugoti nepavyko. Bandykite dar kartą."
            }
        }
    }

    fun saveRule(rule: LessonRule) = update { current ->
        require(validRule(rule) && !ruleConflict(rule, current.rules))
        val sources = school.value.lessons.filter { it.subjectId == rule.subjectId && it.canRename }.map(RuleSource::from)
        val savedRules = current.rules.filterNot { it.id == rule.id } + rule
        current.copy(rules = savedRules,
            ruleSources = (sources + current.ruleSources).filter { source -> savedRules.any { it.subjectId == source.subjectId } }.distinctBy { Triple(it.subjectId, it.weekday to it.slot, it.teacherId) })
    }
    fun deleteRule(id: String) = update {
        val rules = it.rules.filterNot { rule -> rule.id == id }
        it.copy(rules = rules, ruleSources = it.ruleSources.filter { source -> rules.any { rule -> rule.subjectId == source.subjectId } })
    }
    fun saveEvent(event: CustomEvent) = update {
        require(validEvent(event))
        it.copy(events = it.events.filterNot { saved -> saved.id == event.id } + event)
    }
    fun deleteEvent(id: String) = update { it.copy(events = it.events.filterNot { event -> event.id == id }) }
    fun toggleHomework(id: String) = update {
        it.copy(completedHomework = if (id in it.completedHomework) it.completedHomework - id else it.completedHomework + id)
    }
    fun setTheme(theme: String) {
        update { it.copy(theme = theme) }
        viewModelScope.launch {
            try { store.edit { it[stringPreferencesKey("appearance")] = theme } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "Išvaizdos išsaugoti nepavyko. Bandykite dar kartą." }
        }
    }
}
