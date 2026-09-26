package lt.bettertamo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

class LiveShapeTest {
    private val mapper = TamoMapper()
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    private val week = json("""{
        "grades":[{"key":"p;7","items":[{"key":"grade","icon":{"content":"5"},"title":{"content":"Praktinis darbas"}}]}],
        "formatives":[{"key":"p;7","items":[{"lessonId":2,"subjectId":7,"subject":"Gamtos mokslai","date":"2026-09-23","type":"Savarankiškas darbas","title":"6"}]}],
        "days":[
          {"date":"2026-09-23","events":[{"id":2,"schoolSubjectId":7,"sid":"s2","eventIcon":{"content":"2"},"eventTitle":{"content":"Gamtos mokslai"},"references":{"grades":"p;7","formatives":"p;7"},"rightIconsMiddle":{"contentType":"icon","content":"icon_note_negative"}}]},
          {"date":"2026-09-24","events":[{"id":3,"schoolSubjectId":7,"sid":"s3","eventIcon":{"content":"2"},"eventTitle":{"content":"Gamtos mokslai"},"references":{"grades":"p;7","formatives":"p;7"},"rightIconsTop":{"contentType":"text","content":"5"},
            "eventDetails":[{"key":"grade","icon":{"content":"5"},"title":{"content":"Praktinis darbas"},"body":{"content":"Penki"}}]}]},
          {"date":"2026-09-25","events":[{"id":4,"schoolSubjectId":7,"sid":"s4","eventIcon":{"content":"1"},"eventTitle":{"content":"Gamtos mokslai"},"references":{"grades":"p;7","formatives":"p;7"},
            "eventLabel":{"key":"lesson_type","content":"Atsiskaitymas","styleRef":"event_label_important"},"eventHighlight":{"styleRef":"event_important"},
            "bottomIconsRight":[{"key":"trend","contentType":"icon","content":"icon_arrow_right"},{"key":"average","contentType":"text","content":"5"}]}]}
        ]}""")

    @Test fun subjectGradeGroupsStayOnTheirOwnLesson() {
        val lessons = mapper.week(week, emptyList()).associateBy { it.id }
        assertEquals("5", lessons.getValue("3").assessment)
        assertEquals(1, lessons.getValue("3").details.count { it.key == "grade" })
        assertEquals("", lessons.getValue("2").assessment)
        assertEquals("", lessons.getValue("4").assessment)
        assertTrue(lessons.getValue("4").details.none { it.key == "grade" })
    }

    @Test fun formativesJoinByLessonIdAndIconsBecomeNotes() {
        val lessons = mapper.week(week, emptyList()).associateBy { it.id }
        assertEquals(listOf("6"), lessons.getValue("2").formatives)
        assertTrue(lessons.getValue("3").formatives.isEmpty())
        assertEquals("negative", lessons.getValue("2").note)
        assertEquals(listOf("6"), lessons.getValue("4").unusedFormatives.map { it.badge })
        assertEquals("Įrašyta į 09.23 pamoką", lessons.getValue("4").unusedFormatives.single().label)
        assertEquals("", lessons.getValue("2").assessment)
    }

    @Test fun lessonLabelsAndHighlightsAreKept() {
        val lesson = mapper.week(week, emptyList()).single { it.id == "4" }
        assertEquals("Atsiskaitymas", lesson.label)
        assertTrue(lesson.important)
        assertFalse(mapper.week(week, emptyList()).single { it.id == "2" }.important)
    }

    @Test fun diaryIncludesFormativesAttendanceAndColors() {
        val entries = mapper.diary(json("""{"items":[
            {"subject":"Dailė","subjectDate":"2026-09-08","assessmentValue":"10","assessmentType":"Klasės darbas","assessmentColor":"#007E7E","attendanceValue":"p"},
            {"subject":"Matematika","subjectDate":"2026-09-18","assessmentValue":"","attendanceValue":"nl"}],
            "formativeGrades":[{"lessonId":9,"subject":"Gamtos mokslai","date":"2026-09-23","type":"Savarankiškas darbas","title":"6"}]}"""))
        assertEquals(4, entries.size)
        val grade = entries.single { it.kind == DiaryKind.GRADE }
        assertEquals(10, grade.numeric)
        assertEquals(0xFF007E7E, grade.color)
        assertEquals(listOf("p", "nl"), entries.filter { it.kind == DiaryKind.ATTENDANCE }.map { it.value })
        assertTrue(entries.single { it.value == "nl" }.missed)
        assertFalse(entries.single { it.value == "p" }.missed)
        val formative = entries.single { it.kind == DiaryKind.FORMATIVE }
        assertEquals("6", formative.value)
        assertNull(formative.numeric)
    }

    @Test fun feedItemsAreClassified() {
        val notices = mapper.notices(listOf(
            json("""{"id":1,"eventId":4,"date":"2026-09-25T13:15:24","eventDetails":{"HomeWork":"Vad. 12 psl.","ThingName":"Geografija","Date":"2026-09-25 00:00","Deadline":"2026-10-01"}}"""),
            json("""{"id":2,"eventId":1,"date":"2026-09-24T12:37:12","eventDetails":{"ThingName":"Gamtos mokslai","Date":"2026-09-24 00:00","Type":"1","Value":"5"}}"""),
            json("""{"id":3,"eventId":8,"date":"2026-09-24T12:19:50","eventDetails":{"Type":"Pastaba","Value":"Neatlikti namų darbai","ThingName":"Lietuvių kalba"}}"""),
            json("""{"id":4,"eventId":10,"date":"2026-09-24T07:38:10","eventDetails":{"ThingName":"Anglų","Date":"2026-09-22 00:00","Vertinimas":"9","Tipas":"Savarankiškas darbas"}}"""),
            json("""{"id":5,"eventId":8,"date":"2026-09-23T12:56:28","eventDetails":{"Type":"Pagyrimas","Value":"Gerai dirbo.","ThingName":"Gyvenimo įgūdžiai"}}"""),
            json("""{"id":6,"eventId":2,"date":"2026-09-22T12:55:32","eventDetails":{"ThingName":"Verslo simuliacijos","Type":"1"}}"""),
        ), false).associateBy { it.id }
        assertEquals(NoticeKind.HOMEWORK, notices.getValue("1").kind)
        assertEquals(LocalDate.of(2026, 10, 1), notices.getValue("1").deadline)
        assertEquals(NoticeKind.GRADE, notices.getValue("2").kind)
        assertEquals("5", notices.getValue("2").value)
        assertEquals(LocalDate.of(2026, 9, 24), notices.getValue("2").lessonDate)
        assertEquals(NoticeKind.REMARK, notices.getValue("3").kind)
        assertEquals(NoticeKind.FORMATIVE, notices.getValue("4").kind)
        assertEquals("9", notices.getValue("4").value)
        assertEquals(NoticeKind.PRAISE, notices.getValue("5").kind)
        assertFalse("6" in notices)
    }

    @Test fun remarksUseAwardTypeAndLessonDate() {
        val remark = mapper.notices(listOf(json("""{"teacherName":"Mokytoja","subject":"Matematika","subjectDate":"2026-09-09","awardTypeName":"Komentaras","awardValue":"Aplenkti vadovėlį","awardDateTime":"2026-09-10 13:14:38"}""")), true).single()
        assertEquals(NoticeKind.COMMENT, remark.kind)
        assertEquals(LocalDate.of(2026, 9, 9), remark.date)
    }

    @Test fun overviewMatchesSemesterAndDiary() {
        val diary = listOf(
            DiaryEntry("1", "Gamtos mokslai", LocalDate.of(2026, 9, 24), "5", "Praktinis darbas"),
            DiaryEntry("2", "Gamtos mokslai", LocalDate.of(2026, 9, 23), "6", "", DiaryKind.FORMATIVE),
            DiaryEntry("3", "Istorija", LocalDate.of(2026, 9, 18), "nl", "", DiaryKind.ATTENDANCE),
        )
        val semester = listOf(SemesterSubject("Dailė", "A", 10.0, "", listOf(10), emptyList()), SemesterSubject("Gamtos mokslai", "B", 5.0, "", listOf(5), emptyList()), SemesterSubject("Etika", "C", null, "", emptyList(), emptyList()))
        val subjects = subjectOverviews(semester, diary, emptyList())
        assertEquals(listOf("Dailė", "Etika", "Gamtos mokslai", "Istorija"), subjects.map { it.name })
        assertEquals(7.5, overallAverage(subjects)!!, 0.001)
        assertEquals(2, subjects.single { it.name == "Gamtos mokslai" }.entries.size)
        assertNull(subjects.single { it.name == "Istorija" }.average)
    }

    @Test fun schoolYearChunksRespectTheRangeLimit() {
        val chunks = schoolYearChunks(LocalDate.of(2027, 6, 10))
        assertEquals(LocalDate.of(2026, 9, 1), chunks.first().first)
        assertEquals(LocalDate.of(2027, 6, 30), chunks.last().second)
        assertTrue(chunks.all { ChronoUnit.DAYS.between(it.first, it.second) <= 62 })
        assertEquals(listOf(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30)), schoolYearChunks(LocalDate.of(2026, 9, 25)))
        assertEquals(LocalDate.of(2025, 9, 1), schoolYearStart(LocalDate.of(2026, 8, 31)))
    }

    @Test fun snapshotRoundTripsAndDropsOldWeeks() {
        val today = LocalDate.of(2026, 9, 25)
        val lesson = mapper.week(week, emptyList()).first()
        val old = lesson.copy(id = "old", date = today.minusWeeks(20))
        val school = SchoolData(lessons = listOf(lesson, old), diary = listOf(DiaryEntry("1", "Dailė", today, "10", "")), diaryMonths = setOf(YearMonth.of(2026, 9)), badges = mapOf(today to listOf("grade")))
        val codec = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }
        val snapshot = SchoolSnapshot.of("scope", school, emptyList(), null, emptyList(), null, today)
        val restored = codec.decodeFromString(SchoolSnapshot.serializer(), codec.encodeToString(SchoolSnapshot.serializer(), snapshot))
        assertEquals(listOf(lesson), restored.lessons)
        assertEquals(school.diary, restored.diary)
        assertEquals(school.badges, restored.badges)
        assertEquals(setOf(YearMonth.of(2026, 9)), restored.school().diaryMonths)
    }

    @Test fun runningAverageAndTrendAreKept() {
        val lesson = mapper.week(week, emptyList()).single { it.id == "4" }
        assertEquals("5", lesson.average)
        assertEquals("flat", lesson.trend)
        assertEquals("", mapper.week(week, emptyList()).single { it.id == "2" }.trend)
    }

    @Test fun gapLessonsHideWhenTamoFillsTheSlot() {
        val tuesday = LocalDate.of(2026, 9, 8)
        val gapLesson = CustomEvent("e", "Modulis", setOf(2), "09:05", "09:50", slot = 2)
        val weekly = CustomEvent("w", "Būrelis", setOf(2), "09:05", "09:50")
        val real = Lesson("1", "s", "Istorija", "t", "T", 2, 2, "", date = tuesday, startTime = "09:05", endTime = "09:50")
        assertEquals(listOf("e", "w"), eventsOn(tuesday, listOf(gapLesson, weekly)).map { it.id }.sorted())
        assertEquals(listOf("w"), eventsOn(tuesday, listOf(gapLesson, weekly), listOf(real)).map { it.id })
    }
}
