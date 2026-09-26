package lt.bettertamo.data

import java.time.LocalDate

data class Grade(val id: String, val lessonId: String, val value: Int?, val date: LocalDate, val title: String, val comment: String = "")

fun subjectGroup(lesson: Lesson, rules: List<LessonRule>): String =
    resolveSubject(lesson, rules).rule?.let { "rule:${it.id}" } ?: "subject:${lesson.subjectId}"

fun gradeAverage(grades: List<Int>): Double? {
    require(grades.all { it in 1..10 })
    return if (grades.isEmpty()) null else grades.average()
}

