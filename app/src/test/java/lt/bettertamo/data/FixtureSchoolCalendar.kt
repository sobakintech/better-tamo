package lt.bettertamo.data

import java.time.LocalDate

object DemoSchoolCalendar {
    // Fixed preview fixtures based on the official app's observed November 2026 view.
    val events = listOf(
        SchoolCalendarEvent("demo-all-saints", "Visų šventųjų diena", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), SchoolDayKind.HOLIDAY),
        SchoolCalendarEvent("demo-autumn-break", "Rudens atostogos", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8), SchoolDayKind.BREAK),
        SchoolCalendarEvent("demo-all-souls", "Vėlinės", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), SchoolDayKind.HOLIDAY),
    )
}
