package lt.bettertamo.data

import java.time.LocalDate

object DemoData {
    private data class Subject(val id: String, val name: String, val teacherId: String, val teacher: String, val room: String)
    private val subjects = mapOf(
        "bio" to Subject("science", "Gamtos mokslai", "bio-teacher", "Rasa B.", "214"),
        "physics" to Subject("science", "Gamtos mokslai", "physics-teacher", "Tomas V.", "208"),
        "chem" to Subject("science", "Gamtos mokslai", "chem-teacher", "Lina K.", "212"),
        "lt" to Subject("lithuanian", "Lietuvių kalba ir literatūra", "lt-teacher", "Aistė M.", "305"),
        "math" to Subject("math", "Matematika", "math-teacher", "Ieva S.", "201"),
        "geography" to Subject("geography", "Geografija", "geo-teacher", "Mantas J.", "110"),
        "history" to Subject("history", "Istorija", "history-teacher", "Dalia P.", "112"),
        "english" to Subject("english", "Anglų kalba", "english-teacher", "Eglė R.", "302"),
        "tech" to Subject("technology", "Technologijos", "tech-teacher", "Rūta D.", "105"),
        "pe" to Subject("pe", "Fizinis ugdymas", "pe-teacher", "Jonas A.", "Sporto salė"),
        "media" to Subject("media", "Medijų raštingumas", "media-teacher", "Laura T.", "204"),
        "art" to Subject("art", "Dailė", "art-teacher", "Viltė L.", "103"),
        "business" to Subject("business", "Verslo simuliacijos", "business-teacher", "Mantas J.", "110"),
    )
    private val week = listOf(
        listOf("math", "bio", "lt", "english", "history", "pe"),
        listOf("lt", "chem", "math", "geography", "art", "english"),
        listOf("physics", "math", "lt", "bio", "english", "tech"),
        listOf("tech", "chem", "lt", "geography", "pe", "media", "business"),
        listOf("math", "history", "physics", "english", "geography", "lt", "pe"),
    )
    val lessons = week.flatMapIndexed { day, keys ->
        keys.mapIndexed { index, key ->
            val subject = subjects.getValue(key)
            Lesson("${day + 1}-${index + 1}", subject.id, subject.name, subject.teacherId, subject.teacher,
                day + 1, index + 1, subject.room, when (key) {
                    "bio" -> "Ląstelės sandara ir funkcijos"
                    "physics" -> "Medžiagos tankis"
                    "chem" -> "Laboratoriniai indai ir saugus darbas"
                    "lt" -> if (day == 1 || day == 4) "Vasarą skaitytų knygų aptarimas" else "Sakinio dalys"
                    "math" -> "Veiksmai su racionaliaisiais skaičiais"
                    "geography" -> "Geografinės koordinatės"
                    "tech" -> "Lietuvos etnografiniai regionai"
                    "media" -> "Kaip atpažinti patikimą šaltinį?"
                    else -> ""
                })
        }
    }
    val homework = listOf(
        Homework("hw-bio", "1-2", 4, "Perskaityti 24–26 puslapius. Sąsiuvinyje nupiešti augalo ląstelę ir pažymėti pagrindines jos dalis.", 1),
        Homework("hw-chem", "2-2", 4, "Išmokti laboratorinių indų pavadinimus ir jų paskirtį. Pasiruošti trumpai apklausai.", 2),
        Homework("hw-tech", "3-6", 4, "Atsinešti spalvoto popieriaus, žirkles ir klijus. Surinkti informaciją apie pasirinkto regiono tradicinius patiekalus.", 3),
        Homework("hw-math", "3-2", 5, "Atlikti 31 ir 32 uždavinius, 9 psl. Užrašyti visą sprendimo eigą.", 3),
        Homework("hw-literature", "2-1", 5, "Pasiruošti pristatyti vasarą skaitytą knygą: autorius, pagrindiniai veikėjai ir labiausiai įsiminusi mintis.", 2),
        Homework("hw-physics", "3-1", 5, "Apskaičiuoti trijų pasirinktų daiktų tankį. Užpildyti lentelę pratybų 8 puslapyje.", 3),
        Homework("hw-geography", "4-4", 5, "Vadovėlio 8–9 psl., pratybų 2 psl. Mokėti nustatyti taško geografines koordinates.", 4),
        Homework("hw-grammar", "1-3", 3, "Atlikti 4 pratimą. Sakiniuose pabraukti veiksnį ir tarinį.", 1),
    )
    val initialState = PlannerState(
        rules = listOf(
            LessonRule("demo-bio", "science", "Biologija", 0, MatchMode.TEACHER, teacherId = "bio-teacher"),
            LessonRule("demo-physics", "science", "Fizika", 1, MatchMode.TEACHER, teacherId = "physics-teacher"),
            LessonRule("demo-chem", "science", "Chemija", 2, MatchMode.TEACHER, teacherId = "chem-teacher"),
            LessonRule("demo-grammar", "lithuanian", "Gramatika", 3, MatchMode.SLOTS, setOf("1:3", "3:3", "4:3")),
            LessonRule("demo-literature", "lithuanian", "Literatūra", 4, MatchMode.SLOTS, setOf("2:1", "5:6")),
        ),
        events = listOf(CustomEvent("demo-class", "Klasės valandėlė", setOf(1, 4), "15:10", "15:55", "305", "Savaitės planai ir klasės reikalai.")),
        completedHomework = setOf("hw-grammar"),
    )
}
