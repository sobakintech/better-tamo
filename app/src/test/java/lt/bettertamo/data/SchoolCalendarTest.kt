package lt.bettertamo.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SchoolCalendarTest {
    @org.junit.Test fun parsesReturnedLithuanianHolidayRangesIncludingNewYear() {
        val november = java.time.YearMonth.of(2026, 11)
        org.junit.Assert.assertEquals(LocalDate.of(2026, 11, 2) to LocalDate.of(2026, 11, 8), schoolDateRange("Lapkričio 2d. - 8d.", november))
        org.junit.Assert.assertEquals(LocalDate.of(2026, 11, 1) to LocalDate.of(2026, 11, 1), schoolDateRange("Lapkričio 1d.", november))
        org.junit.Assert.assertEquals(LocalDate.of(2026, 12, 23) to LocalDate.of(2027, 1, 5), schoolDateRange("Gruodžio 23d. - Sausio 5d.", java.time.YearMonth.of(2027, 1)))
        org.junit.Assert.assertNull(schoolDateRange("Lapkričio 31d.", november))
        org.junit.Assert.assertNull(schoolDateRange("Nežinomas formatas", november))
    }
    @Test fun `month grid includes leap day and starts Monday`() {
        val days = monthDates(YearMonth.of(2024, 2))
        assertEquals(LocalDate.of(2024, 1, 29), days.first())
        assertEquals(LocalDate.of(2024, 3, 3), days.last())
        assertTrue(LocalDate.of(2024, 2, 29) in days)
        assertEquals(days.size, days.distinct().size)
    }

    @Test fun `six-week month and year boundary keep all dates`() {
        val month = YearMonth.of(2026, 11)
        val days = monthDates(month)
        assertEquals(42, days.size)
        assertEquals(30, days.count { YearMonth.from(it) == month })
        assertEquals(LocalDate.of(2025, 12, 29), monthDates(YearMonth.of(2026, 1)).first())
    }

    @Test fun `holiday includes both endpoints and overlaps months`() {
        val holiday = SchoolCalendarEvent("break", "Atostogos", LocalDate.of(2026, 12, 24), LocalDate.of(2027, 1, 5), SchoolDayKind.BREAK)
        assertTrue(holiday.contains(holiday.start))
        assertTrue(holiday.contains(holiday.end))
        assertFalse(holiday.contains(holiday.end.plusDays(1)))
        assertFalse(holiday.contains(holiday.start.minusDays(1)))
        assertTrue(holiday.overlaps(YearMonth.of(2026, 12)))
        assertTrue(holiday.overlaps(YearMonth.of(2027, 1)))
        assertFalse(holiday.overlaps(YearMonth.of(2026, 11)))
    }

    @Test fun `public holiday and school break can share one day`() {
        val kinds = DemoSchoolCalendar.events.filter { it.contains(LocalDate.of(2026, 11, 2)) }.map { it.kind }.toSet()
        assertEquals(setOf(SchoolDayKind.BREAK, SchoolDayKind.HOLIDAY), kinds)
    }
}
