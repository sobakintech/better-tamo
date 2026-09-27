package lt.bettertamo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import lt.bettertamo.data.*
import lt.bettertamo.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class PlannerInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun teacherRenameSavesStableSubjectAndTeacher() {
        val lesson = Lesson("lesson", "science", "Gamtos mokslai", "teacher", "Mokytoja", 1, 2, "")
        var saved: LessonRule? = null
        compose.setContent {
            BetterTamoTheme {
                CompositionLocalProvider(LocalSchoolData provides SchoolData(lessons = listOf(lesson))) {
                    RuleEditor("science", null, lesson, emptyList(), {}, { saved = it }, {})
                }
            }
        }
        compose.onNode(hasText("Naujas pavadinimas") and hasSetTextAction()).performTextInput("Biologija")
        compose.onNodeWithText("Pagal mokytoją").performClick()
        compose.onNodeWithText("Išsaugoti").performClick()
        compose.runOnIdle {
            assertEquals("science", saved?.subjectId)
            assertEquals("teacher", saved?.teacherId)
            assertEquals(MatchMode.TEACHER, saved?.mode)
            assertEquals("Biologija", resolveSubject(lesson, listOf(saved!!)).name)
        }
    }

    @Test fun emptyEventCannotSaveAndNewEventRemainsRecurring() {
        var saved: CustomEvent? = null
        compose.setContent { BetterTamoTheme { EventEditor(null, 1, {}, { saved = it }, {}) } }
        compose.onNodeWithText("Išsaugoti").assertIsNotEnabled()
        compose.onNodeWithText("Pavadinimas").performTextInput("Klasės valandėlė")
        compose.onNodeWithText("Išsaugoti").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(setOf(1), saved?.weekdays)
            assertTrue(validEvent(saved!!))
            assertEquals(1, eventsOn(LocalDate.of(2026, 9, 14), listOf(saved!!)).size)
            assertEquals(1, eventsOn(LocalDate.of(2026, 9, 21), listOf(saved!!)).size)
        }
    }

    @Test fun calendarSelectsExactDateAndShowsSchoolHoliday() {
        val date = LocalDate.of(2026, 11, 2)
        val event = SchoolCalendarEvent("holiday", "Rudens atostogos", date, date.plusDays(6), SchoolDayKind.BREAK)
        var selected by mutableStateOf(date.minusDays(1))
        compose.setContent { BetterTamoTheme { CompositionLocalProvider(LocalSchoolData provides SchoolData(calendarEvents = listOf(event))) { SchoolMonthGrid(YearMonth.from(date), selected, { selected = it }) } } }
        compose.onNodeWithTag("calendar-date-2026-11-02").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(date, selected) }
    }

    @Test fun loginRequiresBothFieldsAndPassesPasswordOnlyOnSubmit() {
        var submitted = false
        compose.setContent { BetterTamoTheme { LoginScreen(false, null) { username, password -> submitted = username == "test-user" && password == "test-password" } } }
        compose.onNode(hasText("Prisijungti") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Naudotojo vardas").performTextInput("test-user")
        compose.onNodeWithText("Slaptažodis").performTextInput("test-password")
        compose.onNode(hasText("Prisijungti") and hasClickAction()).performClick()
        compose.runOnIdle { assertTrue(submitted) }
        compose.onNode(hasText("Prisijungti") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun loginKeyboardActionSubmitsAndClearsPassword() {
        var submitted = false
        compose.setContent { BetterTamoTheme { LoginScreen(false, null) { username, password -> submitted = username == "student" && password == "password" } } }
        compose.onNodeWithText("Naudotojo vardas").performTextInput("  student  ")
        compose.onNodeWithText("Slaptažodis").performTextInput("password")
        compose.onNodeWithText("Slaptažodis").performImeAction()
        compose.runOnIdle { assertTrue(submitted) }
        compose.onNode(hasText("Prisijungti") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun renameEditorUsesSavedSourcesWhenCurrentWeekIsEmpty() {
        val lesson = Lesson("old", "science", "Gamtos mokslai", "teacher", "Mokytoja", 1, 2, "")
        val existing = LessonRule("rule", "science", "Biologija", MatchMode.TEACHER, teacherId = "teacher")
        var saved: LessonRule? = null
        compose.setContent {
            BetterTamoTheme {
                RuleEditor("science", existing, null, listOf(existing), {}, { saved = it }, {}, listOf(RuleSource.from(lesson)))
            }
        }
        compose.onNode(hasText("Naujas pavadinimas") and hasSetTextAction()).performTextReplacement("Chemija")
        compose.onNodeWithText("Išsaugoti").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Chemija", saved?.name); assertEquals("teacher", saved?.teacherId) }
    }

    @Test fun unknownTeacherCannotSaveTeacherRule() {
        val lesson = Lesson("lesson", "science", "Gamtos mokslai", "", "", 1, 2, "")
        compose.setContent {
            BetterTamoTheme {
                CompositionLocalProvider(LocalSchoolData provides SchoolData(lessons = listOf(lesson))) {
                    RuleEditor("science", null, lesson, emptyList(), {}, {}, {})
                }
            }
        }
        compose.onNode(hasText("Naujas pavadinimas") and hasSetTextAction()).performTextInput("Biologija")
        compose.onNodeWithText("Pagal mokytoją").performClick()
        compose.onNodeWithText("Išsaugoti").assertIsNotEnabled()
    }

    @Test fun monthControlsRemainUsableWithLargeTextInDarkTheme() {
        var chosen by mutableStateOf(YearMonth.of(2026, 9))
        var refreshed = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                BetterTamoTheme("dark") {
                    Column(Modifier.requiredWidth(360.dp)) {
                        MonthNavigation(chosen, { chosen = it }, { refreshed = true }, "Atnaujinti pažymius")
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Kitas mėnuo").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(YearMonth.of(2026, 10), chosen) }
        compose.onNodeWithContentDescription("Ankstesnis mėnuo").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Atnaujinti pažymius").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(refreshed); assertEquals(YearMonth.of(2026, 9), chosen) }
    }

    @Test fun timetableCanPullToRefreshWithNoLessons() {
        var refreshCount = 0
        var refreshing by mutableStateOf(false)
        compose.setContent {
            BetterTamoTheme {
                TimetableList(refreshing, { refreshCount++; refreshing = true }) { }
            }
        }
        compose.onNodeWithTag("timetable-list").performTouchInput { swipeDown() }
        compose.waitUntil { refreshCount == 1 }
        compose.onNodeWithTag("timetable-list").performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, refreshCount); refreshing = false }
        compose.waitForIdle()
        compose.onNodeWithTag("timetable-list").performTouchInput { swipeDown() }
        compose.waitUntil { refreshCount == 2 }
    }
}
