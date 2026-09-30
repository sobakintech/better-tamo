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

    @Test fun `ongoing lesson reports progress and minutes left`() {
        val day = LocalDate.of(2026, 9, 30)
        val progress = lessonProgress(day, "14:15", "15:00", day.atTime(14, 48, 30))!!
        assertEquals(0.744f, progress.fraction, 0.001f)
        assertEquals(12L, progress.minutesLeft)
        assertNull(lessonProgress(day, "14:15", "15:00", day.atTime(15, 0)))
        assertNull(lessonProgress(day, "14:15", "15:00", day.atTime(14, 14)))
        assertNull(lessonProgress(day.minusDays(1), "14:15", "15:00", day.atTime(14, 30)))
        assertNull(lessonProgress(day, "", "", day.atTime(14, 30)))
    }

    @Test fun `break between lessons reports progress and ignores overlaps`() {
        val day = LocalDate.of(2026, 9, 30)
        val spans = listOf("09:05" to "09:50", "08:10" to "08:55", "10:00" to "10:45")
        val (start, progress) = breakProgress(day, spans, day.atTime(9, 55))!!
        assertEquals("09:50", start)
        assertEquals(0.5f, progress.fraction, 0.001f)
        assertEquals(5L, progress.minutesLeft)
        assertNull(breakProgress(day, spans, day.atTime(9, 30)))
        assertNull(breakProgress(day, listOf("08:00" to "10:00", "08:30" to "08:45", "10:00" to "10:45"), day.atTime(9, 0)))
        assertNull(breakProgress(day, spans, day.atTime(7, 30)))
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
}
