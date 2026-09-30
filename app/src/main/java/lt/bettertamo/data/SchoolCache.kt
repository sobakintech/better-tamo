@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package lt.bettertamo.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object YearMonthSerializer : KSerializer<YearMonth> {
    override val descriptor = PrimitiveSerialDescriptor("YearMonth", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: YearMonth) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): YearMonth = YearMonth.parse(decoder.decodeString())
}

@Serializable
data class SchoolSnapshot(
    val scope: String,
    val lessons: List<Lesson> = emptyList(),
    val homework: List<Homework> = emptyList(),
    val homeworkLoaded: String? = null,
    val calendarEvents: List<SchoolCalendarEvent> = emptyList(),
    val badges: Map<LocalDate, List<String>> = emptyMap(),
    val diary: List<DiaryEntry> = emptyList(),
    val diaryMonths: Set<YearMonth> = emptySet(),
    val loadedWeeks: Set<LocalDate> = emptySet(),
    val periods: List<SchoolPeriod> = emptyList(),
    val semesterPeriod: String? = null,
    val semester: List<SemesterSubject> = emptyList(),
    val feed: List<SchoolNotice>? = null,
    val dayIcons: Map<LocalDate, List<String>> = emptyMap(),
) {
    fun school() = SchoolData(lessons = lessons, homework = homework, homeworkLoaded = homeworkLoaded, calendarEvents = calendarEvents, badges = badges, dayIcons = dayIcons, diary = diary, diaryMonths = diaryMonths, loadedWeeks = loadedWeeks)

    companion object {
        fun of(scope: String, school: SchoolData, periods: List<SchoolPeriod>, semesterPeriod: String?, semester: List<SemesterSubject>, feed: List<SchoolNotice>?, today: LocalDate = LocalDate.now()): SchoolSnapshot {
            val window = today.minusWeeks(8)..today.plusWeeks(8)
            return SchoolSnapshot(scope,
                school.lessons.filter { it.date == null || it.date in window },
                school.homework, school.homeworkLoaded,
                school.calendarEvents.filter { !it.end.isBefore(window.start) && !it.start.isAfter(window.endInclusive) },
                school.badges.filterKeys { it in window },
                school.diary.filter { !it.date.isBefore(schoolYearStart(today).minusYears(1)) }, school.diaryMonths,
                school.loadedWeeks.filter { it in window }.toSet(),
                periods, semesterPeriod, semester, feed, school.dayIcons.filterKeys { it in window })
        }
    }
}

class SchoolCache(context: Context) {
    private val file = SealedFile(context, "school-cache.enc")
    private val codec = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }
    fun read(scope: String): SchoolSnapshot? = runCatching { file.read()?.let { codec.decodeFromString<SchoolSnapshot>(it) } }.getOrNull()?.takeIf { it.scope == scope }
    fun write(snapshot: SchoolSnapshot) = file.write(codec.encodeToString(snapshot))
    fun clear() = file.clear()
}
