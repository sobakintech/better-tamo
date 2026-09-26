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
}
