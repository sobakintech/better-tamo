package lt.bettertamo.data

import java.time.LocalDate

object DemoGrades {
    val grades: List<Grade> = DemoData.lessons.mapIndexed { index, lesson ->
        Grade("sample-$index", lesson.id, if (lesson.subjectId == "pe") null else 6 + index % 5,
            mondayOf(LocalDate.now()).minusWeeks(1).plusDays((lesson.weekday - 1).toLong()),
            if (index % 3 == 0) "Savarankiškas darbas" else "Darbas pamokoje",
            if (index % 3 == 0) "Užduotys atliktos savarankiškai. Kitą kartą pateik išsamesnius paaiškinimus." else "")
    }
}
