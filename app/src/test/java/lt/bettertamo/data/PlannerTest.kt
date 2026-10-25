package lt.bettertamo.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val biology = DemoData.lessons.first { it.id == "1-2" }
    private val chemistry = DemoData.lessons.first { it.id == "2-2" }

    @Test fun `different teachers split one official subject`() {
        assertEquals("Biologija", resolveSubject(biology, DemoData.initialState.rules).name)
        assertEquals("Chemija", resolveSubject(chemistry, DemoData.initialState.rules).name)
        assertEquals("Gamtos mokslai", resolveSubject(biology, emptyList()).name)
    }

    @Test fun `homework subject comes from originating lesson despite same due day`() {
        val homework = DemoData.homework.filter { it.dueDay == 4 && it.id in setOf("hw-bio", "hw-chem") }
        val names = homework.map { work -> resolveSubject(DemoData.lessons.first { it.id == work.lessonId }, DemoData.initialState.rules).name }
        assertEquals(listOf("Biologija", "Chemija"), names)
    }

    @Test fun `unrecorded lessons sharing an id survive loading the next week`() {
        val lastFriday = Lesson("77", "geo", "Geografija", "", "", 5, 5, "", date = LocalDate.of(2026, 10, 2))
        val friday = lastFriday.copy(date = LocalDate.of(2026, 10, 9))
        val merged = mergeWeek(listOf(lastFriday), mondayOf(friday.date!!), listOf(friday))
        assertEquals(listOf(lastFriday.date, friday.date), merged.map { it.date })
        assertEquals(listOf(friday), mergeWeek(merged, mondayOf(lastFriday.date!!), emptyList()))
    }

    @Test fun `same teacher and subject split by weekly lesson slots`() {
        val grammar = DemoData.lessons.first { it.id == "1-3" }
        val literature = DemoData.lessons.first { it.id == "2-1" }
        assertEquals(grammar.teacherId, literature.teacherId)
        assertEquals("Gramatika", resolveSubject(grammar, DemoData.initialState.rules).name)
        assertEquals("Literatūra", resolveSubject(literature, DemoData.initialState.rules).name)
    }

    @Test fun `slot rule overrides teacher rule regardless of insertion order`() {
        val slotRule = LessonRule("specific", "science", "Pasirinkta pamoka", MatchMode.SLOTS, setOf(biology.slotKey))
        val rules = listOf(slotRule) + DemoData.initialState.rules
        assertEquals("Pasirinkta pamoka", resolveSubject(biology, rules).name)
        assertEquals("Chemija", resolveSubject(chemistry, rules).name)
    }

    @Test fun `teacher rename cannot spill into another subject`() {
        val otherSubject = biology.copy(subjectId = "other", subject = "Kitas dalykas")
        assertEquals("Kitas dalykas", resolveSubject(otherSubject, DemoData.initialState.rules).name)
    }

    @Test fun `same-priority overlaps are rejected but editing own rule is allowed`() {
        val rule = DemoData.initialState.rules.first()
        assertTrue(ruleConflict(rule.copy(id = "duplicate"), DemoData.initialState.rules))
        assertFalse(ruleConflict(rule.copy(name = "Naujas"), DemoData.initialState.rules))
        val slots = DemoData.initialState.rules.first { it.mode == MatchMode.SLOTS }
        assertTrue(ruleConflict(slots.copy(id = "overlap", slots = setOf("1:3")), listOf(slots)))
        assertFalse(ruleConflict(slots.copy(id = "different", slots = setOf("5:6")), listOf(slots)))
    }

    @Test fun `custom event repeats across weeks and year boundaries`() {
        val event = CustomEvent("test", "Klasės valandėlė", setOf(1, 4), "15:10", "15:55")
        val monday = LocalDate.of(2026, 12, 28)
        assertEquals(listOf(event), eventsOn(monday, listOf(event)))
        assertEquals(listOf(event), eventsOn(monday.plusWeeks(1), listOf(event)))
        assertEquals(listOf(event), eventsOn(monday.plusDays(3), listOf(event)))
        assertTrue(eventsOn(monday.plusDays(1), listOf(event)).isEmpty())
    }

    @Test fun `custom events hide on lesson-free holidays unless kept`() {
        val event = CustomEvent("test", "Būrelis", setOf(1), "15:10", "15:55")
        val kept = event.copy(onHolidays = true)
        val monday = LocalDate.of(2026, 10, 26)
        val lesson = Lesson("1", "s", "Istorija", "t", "T", 1, 1, "", date = monday, startTime = "08:10", endTime = "08:55")
        assertEquals(listOf(kept), eventsOn(monday, listOf(event, kept), dayOff = true))
        assertEquals(listOf(event), eventsOn(monday, listOf(event), listOf(lesson), dayOff = true))
    }

    @Test fun `invalid event ranges days and blank names are rejected`() {
        val valid = CustomEvent("test", "Būrelis", setOf(2), "15:10", "15:55")
        assertTrue(validEvent(valid))
        assertFalse(validEvent(valid.copy(end = "15:10")))
        assertFalse(validEvent(valid.copy(end = "14:55")))
        assertFalse(validEvent(valid.copy(start = "25:00")))
        assertFalse(validEvent(valid.copy(weekdays = emptySet())))
        assertFalse(validEvent(valid.copy(weekdays = setOf(0))))
        assertFalse(validEvent(valid.copy(title = "  ")))
    }

    @Test fun `local customization round trips without losing unicode or selection`() {
        val saved = DemoData.initialState.copy(theme = "dark", completedHomework = setOf("hw-bio", "hw-chem"))
        assertEquals(saved, Json.decodeFromString<PlannerState>(Json.encodeToString(saved)))
    }

    @Test fun `missing teacher and unknown lesson slot cannot create broad renames`() {
        val teacherRule = LessonRule("test", "science", "Biologija", MatchMode.TEACHER)
        assertFalse(validRule(teacherRule))
        assertFalse(teacherRule.matches(biology.copy(teacherId = "")))
        assertFalse(validRule(teacherRule.copy(mode = MatchMode.SLOTS, slots = setOf("1:0"))))
        assertFalse(validRule(teacherRule.copy(mode = MatchMode.SLOTS, slots = setOf("8:1"))))
        assertFalse(validRule(teacherRule.copy(mode = MatchMode.SLOTS, slots = setOf("invalid"))))
        assertTrue(validRule(teacherRule.copy(mode = MatchMode.SLOTS, slots = setOf("1:2"))))
        assertFalse(teacherRule.copy(teacherId = biology.teacherId).matches(biology.copy(canRename = false)))
    }

    @Test fun `rule sources survive restart without inventing timetable dates`() {
        val source = RuleSource.from(biology)
        val saved = PlannerState(ruleSources = listOf(source))
        val restored = Json.decodeFromString<PlannerState>(Json.encodeToString(saved))
        val lesson = restored.ruleSources.single().lesson()
        assertNull(lesson.date)
        assertEquals(biology.subjectId, lesson.subjectId)
        assertEquals(biology.teacherId, lesson.teacherId)
        assertEquals(biology.slotKey, lesson.slotKey)
        assertEquals("Biologija", resolveSubject(lesson, DemoData.initialState.rules).name)
    }

    @Test fun `ongoing lesson reports how much has passed`() {
        val day = LocalDate.of(2026, 9, 30)
        assertEquals(0.744f, lessonProgress(day, "14:15", "15:00", day.atTime(14, 48, 30))!!, 0.001f)
        assertNull(lessonProgress(day, "14:15", "15:00", day.atTime(15, 0)))
        assertNull(lessonProgress(day, "14:15", "15:00", day.atTime(14, 14)))
        assertNull(lessonProgress(day.minusDays(1), "14:15", "15:00", day.atTime(14, 30)))
        assertNull(lessonProgress(day, "", "", day.atTime(14, 30)))
    }

    @Test fun `now marker moves through lessons and breaks and ignores overlaps`() {
        val day = LocalDate.of(2026, 9, 30)
        val spans = listOf("08:10" to "08:55", "" to "", "09:05" to "09:50", "10:00" to "10:45")
        assertEquals(NowMarker(2, 2, 0.5f), nowMarker(day, spans, day.atTime(9, 27, 30)))
        assertEquals(NowMarker(0, 2, 0.5f), nowMarker(day, spans, day.atTime(9, 0)))
        assertNull(nowMarker(day, spans, day.atTime(7, 30)))
        assertNull(nowMarker(day, spans, day.atTime(11, 0)))
        assertNull(nowMarker(day.plusDays(1), spans, day.atTime(9, 0)))
        assertEquals(NowMarker(0, 0, 0.5f), nowMarker(day, listOf("08:00" to "10:00", "08:30" to "08:45", "10:00" to "10:45"), day.atTime(9, 0)))
    }

    @Test fun `web links are found without trailing punctuation`() {
        val text = "Žr. https://example.com/a?b=1. Taip pat www.example.org), ne example.com."
        val links = webLinks(text)
        assertEquals(listOf("https://example.com/a?b=1", "https://www.example.org"), links.map { it.second })
        assertEquals("https://example.com/a?b=1", text.substring(links[0].first))
        assertEquals("www.example.org", text.substring(links[1].first))
    }

    @Test fun `old preferences remain compatible without saved rule sources`() {
        assertTrue(Json.decodeFromString<PlannerState>("{\"theme\":\"dark\"}").ruleSources.isEmpty())
    }

    @Test fun `relative days name the weekday and week`() {
        val today = LocalDate.of(2026, 10, 22)
        fun day(month: Int, day: Int) = relativeDay(LocalDate.of(2026, month, day), today)
        assertEquals(listOf("šiandien, ketvirtadienį", "vakar, trečiadienį", "užvakar, antradienį", "pirmadienį", "praėjusį penktadienį", "užpraėjusį antradienį", "antradienį", "kitą antradienį"),
            listOf(day(10, 22), day(10, 21), day(10, 20), day(10, 19), day(10, 16), day(10, 6), day(9, 29), day(10, 27)))
        fun lesson(month: Int, day: Int) = relativeLesson(LocalDate.of(2026, month, day), today)
        assertEquals(listOf("šiandienos pamoką", "vakarykštę pamoką", "pirmadienio pamoką", "praėjusio pirmadienio pamoką", "antradienio pamoką"),
            listOf(lesson(10, 22), lesson(10, 21), lesson(10, 19), lesson(10, 12), lesson(9, 29)))
        assertEquals("vakarykštę pamoką (10.21, trečiadienis)", lessonLabel(LocalDate.of(2026, 10, 21), today))
        assertEquals("praėjusio pirmadienio pamoką (10.12)", lessonLabel(LocalDate.of(2026, 10, 12), today))
    }

    @Test fun `homework takes its time from the matching feed entry`() {
        val today = LocalDate.of(2026, 10, 22)
        val monday = LocalDate.of(2026, 10, 19)
        val work = Homework("1:x", "1", 1, "Pabaigti darbą", 1, dueDate = LocalDate.of(2026, 10, 26), assignedDate = monday, subject = "Gamtos mokslai")
        val entry = SchoolNotice("9", monday, "Gamtos mokslai", "Pabaigti darbą", deadline = work.dueDate, kind = NoticeKind.HOMEWORK, lessonDate = monday, time = "13:15")
        val other = entry.copy(id = "10", text = "Kitas darbas", time = "14:00")
        assertEquals("Užduota 10.19 13:15 (pirmadienį)", work.assignedLabel(listOf(entry), today))
        assertEquals("Užduota 10.19 13:15 (pirmadienį)", work.assignedLabel(listOf(other, entry), today))
        assertEquals("Užduota 10.19 (pirmadienį)", work.assignedLabel(listOf(entry.copy(date = LocalDate.of(2026, 10, 20))), today))
        assertEquals("Užduota 10.19 (pirmadienį)", work.assignedLabel(listOf(entry.copy(subject = "Matematika")), today))
        assertEquals("Užduota 10.19 12:01 (pirmadienį)", work.copy(assignedTime = "12:01").assignedLabel(listOf(entry), today))
    }
}
