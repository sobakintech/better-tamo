package lt.bettertamo.data

import java.time.LocalDate

data class Grade(val id: String, val lessonId: String, val value: Int?, val date: LocalDate, val title: String, val comment: String = "")

fun subjectGroup(lesson: Lesson, rules: List<LessonRule>): String =
    resolveSubject(lesson, rules).rule?.let { "rule:${it.id}" } ?: "subject:${lesson.subjectId}"

fun entryLessonNames(entries: List<DiaryEntry>, lessons: List<Lesson>, records: List<LessonRecord>, rules: List<LessonRule>): Map<String, String> {
    if (rules.isEmpty()) return emptyMap()
    val calendar = lessons.filter { it.date != null }.groupBy { subjectKey(it.subject) }
    val history = records.groupBy { subjectKey(it.subject) to it.date }
    return entries.mapNotNull { entry ->
        val known = calendar[subjectKey(entry.subject)] ?: return@mapNotNull null
        val linked = if (entry.kind == DiaryKind.FORMATIVE) entry.id.split(":").getOrNull(1)?.let { id -> known.filter { it.id == id } }.orEmpty() else emptyList()
        val candidates = linked.ifEmpty { known.filter { it.date == entry.date } }.ifEmpty {
            history[subjectKey(entry.subject) to entry.date].orEmpty().map { recordLesson(it, known, rules) ?: return@mapNotNull null }
        }
        candidates.map { resolveSubject(it, rules).name }.distinct().singleOrNull()?.let { entry.id to it }
    }.toMap()
}

fun recordLessonNames(records: List<LessonRecord>, lessons: List<Lesson>, rules: List<LessonRule>): Map<String, String> {
    if (rules.isEmpty()) return emptyMap()
    val byId = lessons.associateBy { it.id }
    val calendar = lessons.groupBy { subjectKey(it.subject) }
    return records.mapNotNull { record ->
        val lesson = byId[record.id] ?: recordLesson(record, calendar[subjectKey(record.subject)].orEmpty(), rules) ?: return@mapNotNull null
        record.id to resolveSubject(lesson, rules).name
    }.toMap()
}

private fun subjectKey(name: String) = name.trim().lowercase()

private fun personKey(name: String) = name.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }.sorted().joinToString(" ")

private fun recordLesson(record: LessonRecord, known: List<Lesson>, rules: List<LessonRule>): Lesson? {
    val sameTeacher = known.filter { personKey(it.teacherId) == personKey(record.teacher) }
    val subjectId = sameTeacher.ifEmpty { known }.map { it.subjectId }.filterNot { it.startsWith("event:") }.distinct().singleOrNull() ?: return null
    val subjectRules = rules.filter { it.subjectId == subjectId }
    if (record.slot <= 0 && subjectRules.any { it.mode == MatchMode.SLOTS }) return null
    val teacher = sameTeacher.firstOrNull()?.teacherId ?: if (subjectRules.any { it.mode == MatchMode.TEACHER }) return null else record.teacher
    return Lesson("record:${record.id}", subjectId, record.subject, teacher, teacher, record.date.dayOfWeek.value, record.slot, "", date = record.date)
}

fun gradeAverage(grades: List<Int>): Double? {
    require(grades.all { it in 1..10 })
    return if (grades.isEmpty()) null else grades.average()
}

