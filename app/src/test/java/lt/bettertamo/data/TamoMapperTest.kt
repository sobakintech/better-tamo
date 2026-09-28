package lt.bettertamo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TamoMapperTest {
    private val mapper = TamoMapper()
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    @Test fun semesterSupportsDecimalCommaAndLowercaseTextMarks() {
        val subject = mapper.semester(listOf(json("""{"subject":"Matematika","average":"8,75","main":"9","textMarks":[{"value":"įsk."}]}"""))).single()
        assertEquals(8.75, subject.average!!, 0.001)
        assertEquals(listOf("įsk."), subject.textMarks)
        assertNull(mapper.semester(listOf(json("""{"average":"Infinity"}"""))).single().average)
        assertNull(mapper.semester(listOf(json("""{"average":"11"}"""))).single().average)
    }

    @Test fun modernAndLegacyEnvelopesRequireExplicitSuccess() {
        assertThrows(TamoFailure::class.java) { checkEnvelope(json("{}"), false) }
        assertThrows(TamoFailure::class.java) { checkEnvelope(json("{\"isSuccess\":false}"), false) }
        assertThrows(TamoFailure::class.java) { checkEnvelope(json("{\"Status\":1,\"ErrorCode\":-13}"), true) }
        checkEnvelope(json("{\"isSuccess\":true}"), false)
        checkEnvelope(json("{\"Status\":1,\"ErrorCode\":0}"), true)
        val expired = assertThrows(TamoFailure::class.java) { checkEnvelope(json("{\"ErrorMessage\":\"User is not authenticated.\"}"), true) }
        assertTrue(expired.expired)
    }

    @Test fun malformedCollectionIsNotAnEmptySchoolDay() {
        assertThrows(TamoFailure::class.java) { mapper.week(json("{\"days\":null}"), emptyList()) }
        assertThrows(TamoFailure::class.java) { mapper.homework(json("{}")) }
        assertTrue(mapper.homework(json("{\"items\":[]}")).isEmpty())
    }

    @Test fun exactLessonIdJoinsTeacherAndSubjectAndUsesVilniusTime() {
        val origin = mapper.origins(listOf(json("""{"id":41,"subjectId":"science-3","subjectName":"Gamtos mokslai","teacherName":"Teacher A","subjectDate":"2026-09-07"}"""))).single()
        val payload = json("""{"days":[{"date":"2026-09-07","events":[{"id":41,"sid":"returned-sid","timeFromUtc":"2026-09-07T05:10:00Z","timeToUtc":"2026-09-07T05:55:00Z","timeBadges":[{"content":"2"}],"eventTitle":{"content":"Gamtos mokslai"}}]}]}""")
        val lesson = mapper.week(payload, listOf(origin)).single()
        assertEquals("science-3", lesson.subjectId)
        assertEquals("Teacher A", lesson.teacher)
        assertEquals("08:10", lesson.start)
        assertEquals("08:55", lesson.end)
        assertEquals("1:2", lesson.slotKey)
        assertTrue(lesson.canRename)
        val unmatched = mapper.week(payload, listOf(origin.copy(id = "42"))).single()
        assertFalse(unmatched.canRename)
        assertNotEquals(origin.subjectId, unmatched.subjectId)
    }

    @Test fun homeworkDeadlineDoesNotChooseItsLessonOverride() {
        val work = mapper.homework(json("""{"items":[{"lessonId":41,"date":"2026-09-07","deadline":"2026-09-09","homeWork":"Read chapter 1","thingName":"Gamtos mokslai","completionDate":null}]}""")).single()
        val monday = Lesson("41", "science", "Gamtos mokslai", "teacher", "Teacher A", 1, 2, "")
        val wednesday = monday.copy(id = "99", weekday = 3)
        val rules = listOf(LessonRule("bio", "science", "Biologija", MatchMode.SLOTS, setOf("1:2")), LessonRule("chem", "science", "Chemija", MatchMode.SLOTS, setOf("3:2")))
        val data = SchoolData(lessons = listOf(monday, wednesday), homework = listOf(work))
        assertEquals(LocalDate.of(2026, 9, 9), work.dueDate)
        assertEquals("Biologija", resolveSubject(data.origin(work.lessonId)!!, rules).name)
        assertFalse(work.completed)
    }

    @Test fun unusualLessonNumbersDoNotIndexFixedBellTimes() {
        val lesson = Lesson("1", "subject", "Subject", "teacher", "Teacher", 1, 9, "", startTime = "16:10", endTime = "16:55")
        assertEquals("16:10", lesson.start)
        assertEquals("", lesson.copy(startTime = null).start)
    }

    @Test fun unrecognizedAttendanceIsPreservedAsText() {
        val entry = mapper.diary(json("""{"items":[{"subjectDate":"2026-09-08","subject":"Matematika","attendanceValue":"nt"}]}""")).single()
        assertEquals("nt", entry.value)
    }

    @Test fun headerValuesRejectControlCharacters() {
        assertFalse(validHeader("token\r\nx-injected: yes"))
        assertFalse(validHeader(""))
        assertTrue(validHeader("returned-role-id"))
    }

    @Test fun placeholderHomeworkIsSkipped() {
        val items = listOf("-", " – ", "...", "x", "7 pratimas").mapIndexed { index, text -> """{"lessonId":${index + 1},"date":"2026-09-07","deadline":"2026-09-09","homeWork":"$text","thingName":"Dailė"}""" }
        assertEquals(listOf("7 pratimas"), mapper.homework(json("""{"items":[${items.joinToString(",")}]}""")).map { it.text })
        val payload = json("""{"days":[{"date":"2026-09-08","events":[{"id":41,"eventTitle":{"content":"Dailė"},"eventDetails":[{"key":"homework","title":{"content":"Namų darbas"},"body":{"content":"-"}}]}]}]}""")
        assertFalse(mapper.week(payload, emptyList()).single().hasHomework)
    }

    @Test fun calendarUsesLiveFieldAliasesAndStableSubjectId() {
        val payload = json("""{"days":[{"date":"2026-09-08","events":[{"id":41,"schoolSubjectId":88,"sid":"returned-sid","eventIcon":{"content":"6"},"eventTitle":{"content":"Dailė"},"eventSubtitle":{"content":"Mokytoja"},"eventDetails":[{"key":"homework","title":{"content":"Namų darbas"},"body":{"content":"Atsinešti popieriaus"},"label":{"content":"Užduota 09.01"}}]}]}]}""")
        val lesson = mapper.week(payload, emptyList()).single()
        assertTrue(lesson.canRename)
        assertEquals("88", lesson.subjectId)
        assertEquals(6, lesson.slot)
        assertEquals("Namų darbas", lesson.details.single().title)
        assertEquals("Atsinešti popieriaus", lesson.details.single().text)
        assertEquals("Užduota 09.01", lesson.details.single().label)
    }

    @Test fun periodSelectionUsesReturnedPersonAndPeriodPair() {
        val result = json("""{"groups":[{"id":"periods","filteritems":[{"id":"display-only","name":"1 pusmetis","values":["123","456"]},{"id":"display-only-annual","name":"Metinis","values":["123","0"]}]}]}""")
        val periods = mapper.periods(result)
        assertEquals("123", periods.first().personId)
        assertEquals("456", periods.first().id)
        assertEquals("0", periods.last().id)
    }
}
