package lt.bettertamo.data

import org.junit.Assert.*
import org.junit.Test

class GradesTest {
    @Test fun `average stays absent for no numeric marks`() {
        assertNull(gradeAverage(emptyList()))
        assertNull(gradeAverage(DemoGrades.grades.filter { it.value == null }.mapNotNull { it.value }))
    }

    @Test fun `hypothetical grades affect only the projected average`() {
        val actual = listOf(6, 7)
        assertEquals(6.5, gradeAverage(actual)!!, 0.0001)
        assertEquals(23.0 / 3, gradeAverage(actual + 10)!!, 0.0001)
        assertEquals(6.5, gradeAverage(actual)!!, 0.0001)
        assertEquals(1.0, gradeAverage(listOf(1))!!, 0.0001)
        assertEquals(10.0, gradeAverage(listOf(10, 10))!!, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero is not a grade`() { gradeAverage(listOf(0)) }

    @Test(expected = IllegalArgumentException::class)
    fun `grade cannot exceed ten`() { gradeAverage(listOf(11)) }

    @Test fun `group identity follows rules rather than equal display names`() {
        val biology = DemoData.lessons.first { it.id == "1-2" }
        val chemistry = DemoData.lessons.first { it.id == "2-2" }
        val sameNames = DemoData.initialState.rules.map { it.copy(name = "Tas pats") }
        assertNotEquals(subjectGroup(biology, sameNames), subjectGroup(chemistry, sameNames))
        assertEquals(subjectGroup(biology, DemoData.initialState.rules), subjectGroup(biology, sameNames))
        assertEquals(subjectGroup(biology, emptyList()), subjectGroup(chemistry, emptyList()))
    }

    @Test fun `each grade belongs to exactly one group including same teacher splits`() {
        val rules = DemoData.initialState.rules
        val groups = DemoData.lessons.groupBy { subjectGroup(it, rules) }
        val gradesByGroup = groups.mapValues { (_, lessons) -> DemoGrades.grades.filter { grade -> lessons.any { it.id == grade.lessonId } } }
        assertEquals(DemoGrades.grades.size, gradesByGroup.values.flatten().distinctBy { it.id }.size)
        assertEquals(DemoGrades.grades.size, gradesByGroup.values.sumOf { it.size })
        assertTrue(gradesByGroup.getValue("rule:demo-grammar").all { it.lessonId in setOf("1-3", "3-3", "4-3") })
        assertTrue(gradesByGroup.getValue("rule:demo-literature").all { it.lessonId in setOf("2-1", "5-6") })
    }

    private val tuesday = java.time.LocalDate.of(2025, 10, 7)
    private fun lesson(id: String, date: java.time.LocalDate, slot: Int, teacher: String = "Ona Mokytoja") =
        Lesson(id, "phy", "Fizika", teacher, teacher, date.dayOfWeek.value, slot, "", date = date)
    private fun record(id: String, date: java.time.LocalDate, slot: Int, teacher: String = "Ona Mokytoja") =
        LessonRecord(id, date, "Fizika", teacher, "", "", "", null, slot)

    @Test fun `grade entries take the name of the lesson they were given in`() {
        val lessons = listOf(lesson("a", tuesday, 3), lesson("b", tuesday.plusDays(2), 5))
        val rules = listOf(LessonRule("r", "phy", "Astronomija", MatchMode.SLOTS, setOf("2:3")))
        val entries = listOf(
            DiaryEntry("g1", "Fizika", tuesday, "9", "Kontrolinis"),
            DiaryEntry("g2", "Fizika", tuesday.plusDays(2), "7", ""),
            DiaryEntry("f:b:0", "Fizika", tuesday, "+", "", DiaryKind.FORMATIVE),
            DiaryEntry("g4", "Chemija", tuesday, "10", ""),
        )
        assertEquals(mapOf("g1" to "Astronomija", "g2" to "Fizika", "f:b:0" to "Fizika"), entryLessonNames(entries, lessons, emptyList(), rules))
        assertTrue(entryLessonNames(entries, lessons, emptyList(), emptyList()).isEmpty())
        assertFalse("g1" in entryLessonNames(entries, lessons + lesson("d", tuesday, 6), emptyList(), rules))
    }

    @Test fun `unloaded weeks use the lesson history and never guess`() {
        val lessons = listOf(lesson("a", tuesday, 3))
        val later = tuesday.plusDays(14)
        val slotRule = listOf(LessonRule("r", "phy", "Astronomija", MatchMode.SLOTS, setOf("2:3")))
        val entries = listOf(DiaryEntry("g1", "Fizika", later, "8", ""), DiaryEntry("g2", "Fizika", later.plusDays(1), "6", ""))
        assertEquals(mapOf("g1" to "Astronomija"), entryLessonNames(entries, lessons, listOf(record("x", later, 3)), slotRule))
        assertEquals(mapOf("g1" to "Fizika"), entryLessonNames(entries, lessons, listOf(record("x", later, 5)), slotRule))
        assertTrue(entryLessonNames(entries, lessons, listOf(record("x", later, 0)), slotRule).isEmpty())
        assertTrue(entryLessonNames(entries, lessons, emptyList(), slotRule).isEmpty())
    }

    @Test fun `teacher renames split one subject by who taught the lesson`() {
        val lessons = listOf(lesson("a", tuesday, 3, "Ona Mokytoja"), lesson("b", tuesday.plusDays(1), 2, "Rūta Kita"))
        val rules = listOf(LessonRule("r", "phy", "Biologija", MatchMode.TEACHER, teacherId = "Ona Mokytoja"))
        val later = tuesday.plusDays(14)
        val entries = listOf(DiaryEntry("g1", "Fizika", later, "8", ""), DiaryEntry("g2", "Fizika", later.plusDays(1), "6", ""), DiaryEntry("g3", "Fizika", later.plusDays(2), "5", ""))
        val records = listOf(record("x", later, 3, "Mokytoja Ona"), record("y", later.plusDays(1), 2, "Rūta Kita"), record("z", later.plusDays(2), 4, "Nauja Mokytoja"))
        assertEquals(mapOf("g1" to "Biologija", "g2" to "Fizika"), entryLessonNames(entries, lessons, records, rules))
    }

}
