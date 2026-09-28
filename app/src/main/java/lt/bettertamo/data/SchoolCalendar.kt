@file:UseSerializers(LocalDateSerializer::class, YearMonthSerializer::class)

package lt.bettertamo.data

import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.YearMonth

enum class SchoolDayKind(val label: String, val marker: String) {
    BREAK("Atostogos", "A"), HOLIDAY("Valstybinė šventė", "Š"), EVENT("Įvykis", "•")
}

@Serializable
data class SchoolCalendarEvent(val id: String, val title: String, val start: LocalDate, val end: LocalDate, val kind: SchoolDayKind, val dateText: String = "", val datesKnown: Boolean = true) {
    init { require(!end.isBefore(start)) }
    fun contains(date: LocalDate) = datesKnown && !date.isBefore(start) && !date.isAfter(end)
    fun overlaps(month: YearMonth) = !end.isBefore(month.atDay(1)) && !start.isAfter(month.atEndOfMonth())
}

fun schoolDateRange(value: String, month: YearMonth): Pair<LocalDate, LocalDate>? {
    val names = listOf("sausio", "vasario", "kovo", "balandžio", "gegužės", "birželio", "liepos", "rugpjūčio", "rugsėjo", "spalio", "lapkričio", "gruodžio")
    val months = names.joinToString("|")
    val expression = Regex("^(?:([0-9]{4})\\s*(?:m\\.?)?\\s*)?($months)\\s+([0-9]{1,2})\\s*d\\.?(?:\\s*[-–]\\s*(?:([0-9]{4})\\s*(?:m\\.?)?\\s*)?(?:($months)\\s+)?([0-9]{1,2})\\s*d\\.?)?$", RegexOption.IGNORE_CASE)
    val match = expression.matchEntire(value.trim()) ?: return null
    return runCatching {
        val firstMonth = names.indexOf(match.groupValues[2].lowercase()) + 1
        val firstYear = match.groupValues[1].toIntOrNull() ?: when { firstMonth > month.monthValue + 6 -> month.year - 1; firstMonth < month.monthValue - 6 -> month.year + 1; else -> month.year }
        val first = LocalDate.of(firstYear, firstMonth, match.groupValues[3].toInt())
        val lastMonth = match.groupValues[5].takeIf { it.isNotEmpty() }?.let { names.indexOf(it.lowercase()) + 1 } ?: firstMonth
        val lastYear = match.groupValues[4].toIntOrNull() ?: firstYear + if (lastMonth < firstMonth) 1 else 0
        val last = if (match.groupValues[6].isBlank()) first else LocalDate.of(lastYear, lastMonth, match.groupValues[6].toInt())
        require(last >= first)
        first to last
    }.getOrNull()
}

fun monthDates(month: YearMonth): List<LocalDate> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
    val first = month.atDay(1).minusDays(offset.toLong())
    return List(count) { first.plusDays(it.toLong()) }
}

