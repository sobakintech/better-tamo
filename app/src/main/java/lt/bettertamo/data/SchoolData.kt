@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package lt.bettertamo.data

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.YearMonth

enum class DiaryKind { GRADE, FORMATIVE, ATTENDANCE }
enum class NoticeKind { HOMEWORK, GRADE, FORMATIVE, PRAISE, REMARK, COMMENT, OTHER }

@Serializable
data class DiaryEntry(
    val id: String, val subject: String, val date: LocalDate, val value: String, val title: String, val kind: DiaryKind = DiaryKind.GRADE, val color: Long? = null,
    val converted: Boolean? = null, val system: String = "", val percents: Int? = null,
) {
    val numeric get() = value.trim().toIntOrNull()?.takeIf { it in 1..10 && kind == DiaryKind.GRADE }
    val missed get() = kind == DiaryKind.ATTENDANCE && value.trim().lowercase().startsWith("n")
}
@Serializable
data class SchoolPeriod(val id: String, val title: String, val selected: Boolean, val personId: String)
@Serializable
data class SemesterSubject(val name: String, val teacher: String, val average: Double?, val finalGrade: String, val marks: List<Int>, val textMarks: List<String>)
@Serializable
data class SchoolNotice(
    val id: String,
    val date: LocalDate?,
    val subject: String,
    val text: String,
    val teacher: String = "",
    val deadline: LocalDate? = null,
    val kind: NoticeKind = NoticeKind.OTHER,
    val value: String = "",
    val lessonDate: LocalDate? = null,
)

data class SchoolData(
    val lessons: List<Lesson> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val origins: List<Lesson> = emptyList(),
    val calendarEvents: List<SchoolCalendarEvent> = emptyList(),
    val badges: Map<LocalDate, List<String>> = emptyMap(),
    val diary: List<DiaryEntry> = emptyList(),
    val diaryMonths: Set<YearMonth> = emptySet(),
    val grades: List<Grade> = emptyList(),
    val weekLoaded: LocalDate? = null,
    val monthLoaded: String? = null,
    val homeworkLoaded: String? = null,
    val loadedWeeks: Set<LocalDate> = emptySet(),
) {
    fun origin(id: String) = lessons.find { it.id == id } ?: origins.find { it.id == id }
}

fun schoolYearStart(today: LocalDate = LocalDate.now()): LocalDate =
    LocalDate.of(if (today.monthValue >= 9) today.year else today.year - 1, 9, 1)

fun schoolYearChunks(today: LocalDate = LocalDate.now()): List<Pair<LocalDate, LocalDate>> {
    val start = YearMonth.from(schoolYearStart(today))
    val end = YearMonth.from(today)
    return generateSequence(start) { it.plusMonths(2) }.takeWhile { it <= end }
        .map { it.atDay(1) to minOf(it.plusMonths(1), end).atEndOfMonth() }.toList()
}

data class SubjectOverview(val name: String, val teacher: String, val average: Double?, val finalGrade: String, val marks: List<String>, val entries: List<DiaryEntry>, val fromSemester: Boolean)

fun subjectOverviews(semester: List<SemesterSubject>, diary: List<DiaryEntry>, lessons: List<Lesson>): List<SubjectOverview> {
    val byName = diary.groupBy { it.subject }
    val fromSemester = semester.groupBy { it.name }.map { (name, rows) ->
        val entries = byName[name].orEmpty().sortedByDescending { it.date }
        val averages = rows.mapNotNull { it.average }
        SubjectOverview(name, rows.map { it.teacher }.filter { it.isNotBlank() }.distinct().joinToString(", "),
            averages.takeIf { it.isNotEmpty() }?.average() ?: localAverage(entries),
            rows.map { it.finalGrade }.filter { it.isNotBlank() }.distinct().joinToString(", "),
            rows.flatMap { row -> row.marks.map(Int::toString) + row.textMarks }, entries, true)
    }
    val known = fromSemester.map { it.name }.toSet()
    val teachers = lessons.filter { it.teacher.isNotBlank() }.groupBy { it.subject }.mapValues { (_, list) -> list.map { it.teacher }.distinct().joinToString(", ") }
    val extra = (byName.keys + lessons.filter { it.sid.isNotBlank() }.map { it.subject }).filter { it.isNotBlank() && it !in known }.distinct().map { name ->
        val entries = byName[name].orEmpty().sortedByDescending { it.date }
        SubjectOverview(name, teachers[name].orEmpty(), localAverage(entries), "", entries.filter { it.kind == DiaryKind.GRADE }.sortedBy { it.date }.map { it.value }, entries, false)
    }
    return (fromSemester + extra).sortedBy { it.name.lowercase() }
}

fun localAverage(entries: List<DiaryEntry>): Double? = entries.mapNotNull { it.numeric }.takeIf { it.isNotEmpty() }?.average()

fun overallAverage(subjects: List<SubjectOverview>): Double? = subjects.mapNotNull { it.average }.takeIf { it.isNotEmpty() }?.average()

@Serializable
data class UpcomingEvent(val id: String, val start: LocalDate, val end: LocalDate?, val time: String, val title: String, val body: String, val type: Int = 0) {
    val holiday get() = "atostog" in title.lowercase() || "šventė" in title.lowercase() || "nedirb" in title.lowercase()
}
@Serializable
data class LessonRecord(val id: String, val date: LocalDate, val subject: String, val teacher: String, val topic: String, val homework: String, val classwork: String, val deadline: LocalDate?)
@Serializable
data class MenuLink(val id: String, val group: String, val title: String, val url: String, val auth: Boolean)

enum class MessageFolder(val title: String) { RECEIVED("Gauti"), STARRED("Pažymėti"), SENT("Išsiųsti"), GROUP("Grupių"), DELETED("Ištrinti") }
data class MessageHeader(val id: String, val sid: String, val typeId: String, val subject: String, val date: java.time.LocalDateTime?, val person: String, val personTitle: String, val avatar: String,
                         val read: Boolean, val starred: Boolean, val important: Boolean, val attachments: Boolean, val sent: Boolean, val replyMode: Int = 0, val readCount: Int? = null, val recipientCount: Int? = null,
                         val tamoLogo: Boolean = false, val closable: Boolean = true, val deleted: Boolean = false)
data class MessageDetail(val header: MessageHeader, val body: String, val files: List<SchoolFile>, val recipients: List<String>, val recipientCount: Int?)

fun subjectFormatives(diary: List<DiaryEntry>, pending: List<DiaryEntry>?): List<DiaryEntry> {
    fun DiaryEntry.key() = Triple(date, value.trim(), title)
    val pendingKeys = pending?.map { it.key() }?.toSet()
    val known = diary.filter { it.kind == DiaryKind.FORMATIVE }
        .map { if (it.converted != null || pendingKeys == null) it else it.copy(converted = it.key() !in pendingKeys) }
    val knownKeys = known.map { it.key() }.toSet()
    return (known + pending.orEmpty().filter { it.key() !in knownKeys }).sortedByDescending { it.date }
}
