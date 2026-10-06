@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package lt.bettertamo.data

import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.UseSerializers
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.Duration
import java.time.temporal.TemporalAdjusters

@Serializable
data class Lesson(
    val id: String,
    val subjectId: String,
    val subject: String,
    val teacherId: String,
    val teacher: String,
    val weekday: Int,
    val slot: Int,
    val room: String,
    val topic: String = "",
    val date: LocalDate? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val sid: String = "",
    val details: List<LessonDetail> = emptyList(),
    val canRename: Boolean = true,
    val assessment: String = "",
    val label: String = "",
    val important: Boolean = false,
    val note: String = "",
    val average: String = "",
    val trend: String = "",
    val pendingFormatives: List<DiaryEntry>? = null,
) {
    val slotKey get() = "$weekday:$slot"
    val key get() = date?.let { "$id@$it" } ?: id
    val start get() = startTime ?: bellTimes.getOrNull(slot - 1)?.first.orEmpty()
    val end get() = endTime ?: bellTimes.getOrNull(slot - 1)?.second.orEmpty()
    val formatives get() = details.filter { it.key == "formative" }.map { it.badge }.filter { it.isNotBlank() }
    val hasHomework get() = details.any { it.key == "homework" }
    val highlighted get() = important || label.isNotBlank()
}

@Serializable
data class Homework(
    val id: String,
    val lessonId: String,
    val dueDay: Int,
    val text: String,
    val assignedDay: Int,
    val dueDate: LocalDate? = null,
    val assignedDate: LocalDate? = null,
    val subject: String = "",
    val completed: Boolean = false,
    val files: List<SchoolFile> = emptyList(),
)

private val webLink = Regex("""(?i)\b(?:https?://|www\.)[^\s<>"']+""")

fun webLinks(text: String): List<Pair<IntRange, String>> = webLink.findAll(text).mapNotNull { match ->
    val value = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '»', '"')
    if (value.length < 8) return@mapNotNull null
    val range = match.range.first until match.range.first + value.length
    range to if (value.startsWith("www.", ignoreCase = true)) "https://$value" else value
}.toList()

fun isPlaceholderHomework(text: String): Boolean =text.trim().let { it.length <= 1 || it.none(Char::isLetterOrDigit) }

@Serializable
data class LessonDetail(val title: String, val text: String, val files: List<SchoolFile> = emptyList(), val key: String = "", val badge: String = "", val label: String = "")
@Serializable
data class SchoolFile(val sid: String, val name: String, val legacy: Boolean = false)

enum class MatchMode { SLOTS, TEACHER }

@Serializable
data class LessonRule(
    val id: String,
    val subjectId: String,
    val name: String,
    val mode: MatchMode,
    val slots: Set<String> = emptySet(),
    val teacherId: String = "",
) {
    fun matches(lesson: Lesson): Boolean = lesson.canRename && subjectId == lesson.subjectId && when (mode) {
        MatchMode.SLOTS -> lesson.slot > 0 && lesson.slotKey in slots
        MatchMode.TEACHER -> teacherId.isNotBlank() && teacherId == lesson.teacherId
    }
}

@Serializable
data class CustomEvent(
    val id: String,
    val title: String,
    val weekdays: Set<Int>,
    val start: String,
    val end: String,
    val room: String = "",
    val note: String = "",
    val slot: Int = 0,
)

@Serializable
data class PlannerState(
    val rules: List<LessonRule> = emptyList(),
    val events: List<CustomEvent> = emptyList(),
    val completedHomework: Set<String> = emptySet(),
    val theme: String = "system",
    val ruleSources: List<RuleSource> = emptyList(),
)

@Serializable
data class RuleSource(val subjectId: String, val subject: String, val teacherId: String, val teacher: String, val weekday: Int, val slot: Int, val start: String) {
    fun lesson() = Lesson("saved:$subjectId:$weekday:$slot:$teacherId", subjectId, subject, teacherId, teacher, weekday, slot, "", startTime = start)
    companion object {
        fun from(lesson: Lesson) = RuleSource(lesson.subjectId, lesson.subject, lesson.teacherId, lesson.teacher, lesson.weekday, lesson.slot, lesson.start)
    }
}

fun validRule(rule: LessonRule): Boolean = rule.name.isNotBlank() && rule.subjectId.isNotBlank() && when (rule.mode) {
    MatchMode.TEACHER -> rule.teacherId.isNotBlank()
    MatchMode.SLOTS -> rule.slots.isNotEmpty() && rule.slots.all { slot ->
        val parts = slot.split(":")
        parts.size == 2 && parts[0].toIntOrNull() in 1..7 && parts[1].toIntOrNull() in 1..30
    }
}

data class ResolvedSubject(val name: String, val rule: LessonRule?)

fun resolveSubject(lesson: Lesson, rules: List<LessonRule>): ResolvedSubject {
    val rule = rules.lastOrNull { it.mode == MatchMode.SLOTS && it.matches(lesson) }
        ?: rules.lastOrNull { it.mode == MatchMode.TEACHER && it.matches(lesson) }
    return ResolvedSubject(rule?.name ?: lesson.subject, rule)
}

fun ruleConflict(candidate: LessonRule, rules: List<LessonRule>): Boolean = rules.any {
    it.id != candidate.id && it.subjectId == candidate.subjectId && it.mode == candidate.mode &&
        when (candidate.mode) {
            MatchMode.SLOTS -> it.slots.intersect(candidate.slots).isNotEmpty()
            MatchMode.TEACHER -> it.teacherId == candidate.teacherId
        }
}

fun validEvent(event: CustomEvent): Boolean {
    if (event.title.isBlank() || event.weekdays.isEmpty() || event.weekdays.any { it !in 1..7 }) return false
    return runCatching {
        Regex("\\d{2}:\\d{2}").matches(event.start) && Regex("\\d{2}:\\d{2}").matches(event.end) &&
            LocalTime.parse(event.end).isAfter(LocalTime.parse(event.start))
    }.getOrDefault(false)
}

fun eventsOn(date: LocalDate, events: List<CustomEvent>, lessons: List<Lesson> = emptyList()) =
    events.filter { event ->
        date.dayOfWeek.value in event.weekdays && lessons.none { lesson ->
            val overlaps = lesson.start.isNotBlank() && lesson.start < event.end && lesson.end > event.start
            if (event.slot > 0) lesson.slot == event.slot || overlaps
            else overlaps && lesson.subject.trim().equals(event.title.trim(), ignoreCase = true)
        }
    }.sortedBy { it.start }

data class Ranking(val position: Int, val averages: List<Double>) {
    val total get() = averages.size
    val average get() = averages.getOrNull(position - 1)
}

fun rankingKey(subject: String) = subject.trim().lowercase()

fun lessonProgress(date: LocalDate?, start: String, end: String, now: LocalDateTime): Float? {
    if (date != now.toLocalDate()) return null
    val from = runCatching { LocalTime.parse(start) }.getOrNull() ?: return null
    val to = runCatching { LocalTime.parse(end) }.getOrNull() ?: return null
    val time = now.toLocalTime()
    if (!to.isAfter(from) || time.isBefore(from) || !time.isBefore(to)) return null
    return Duration.between(from, time).seconds / Duration.between(from, to).seconds.toFloat()
}

data class NowMarker(val from: Int, val to: Int, val fraction: Float)

fun nowMarker(date: LocalDate, spans: List<Pair<String, String>>, now: LocalDateTime): NowMarker? {
    var end: String? = null
    var previous = -1
    spans.forEachIndexed { index, (start, finish) ->
        if (start.isBlank() || finish.isBlank()) return@forEachIndexed
        end?.let { last -> lessonProgress(date, last, start, now)?.let { return NowMarker(previous, index, it) } }
        lessonProgress(date, start, finish, now)?.let { return NowMarker(index, index, it) }
        if (end == null || finish > end!!) end = finish
        previous = index
    }
    return null
}

fun mergeWeek(lessons: List<Lesson>, monday: LocalDate, week: List<Lesson>): List<Lesson> =
    (lessons.filterNot { it.date != null && mondayOf(it.date) == monday } + week).distinctBy { it.key }

fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

val dayShort = listOf("Pr", "An", "Tr", "Kt", "Pn", "Št", "Sk")
val dayLong = listOf("Pirmadienis", "Antradienis", "Trečiadienis", "Ketvirtadienis", "Penktadienis", "Šeštadienis", "Sekmadienis")
val bellTimes = listOf("08:10" to "08:55", "09:05" to "09:50", "10:00" to "10:45", "11:15" to "12:00", "12:25" to "13:10", "13:20" to "14:05", "14:15" to "15:00")
