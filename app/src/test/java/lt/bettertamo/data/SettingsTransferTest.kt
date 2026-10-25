package lt.bettertamo.data

import org.junit.Assert.*
import org.junit.Test

class SettingsTransferTest {
    private val rule = LessonRule("r1", "phy", "Astronomija", MatchMode.SLOTS, setOf("2:3"))
    private val source = RuleSource("phy", "Fizika", "Ona Mokytoja", "Ona Mokytoja", 2, 3, "10:00")
    private val event = CustomEvent("e1", "Būrelis", setOf(3), "15:10", "15:55")
    private val state = PlannerState(listOf(rule), listOf(event), "dark", listOf(source))

    @Test fun `export round trips only the chosen parts`() {
        val export = decodeSettings(encodeSettings(settingsExport(state, "jade", setOf(SettingsPart.RULES, SettingsPart.APPEARANCE), "2026.10.25.1200")))
        assertEquals(setOf(SettingsPart.RULES, SettingsPart.APPEARANCE), export.parts)
        assertEquals(listOf(rule), export.rules)
        assertEquals(listOf(source), export.ruleSources)
        assertNull(export.events)
        assertEquals(SettingsAppearance("dark", "jade"), export.appearance)
        assertFalse("events" in encodeSettings(export))
    }

    @Test fun `import replaces chosen parts and keeps the rest`() {
        val current = PlannerState(listOf(rule.copy(id = "old", slots = setOf("4:1"))), listOf(event.copy(id = "mine")), "light")
        val export = settingsExport(state, null, SettingsPart.entries.toSet(), "")
        val rulesOnly = importSettings(current, export, setOf(SettingsPart.RULES))
        assertEquals(listOf(rule), rulesOnly.rules)
        assertEquals(current.events, rulesOnly.events)
        assertEquals("light", rulesOnly.theme)
        val all = importSettings(current, export, SettingsPart.entries.toSet())
        assertEquals(listOf(event), all.events)
        assertEquals("dark", all.theme)
    }

    @Test fun `import drops invalid and conflicting entries`() {
        val export = SettingsExport(
            rules = listOf(rule, rule.copy(id = "r2"), rule.copy(id = "r3", name = " "), rule.copy(id = "r4", slots = setOf("2:4"))),
            ruleSources = listOf(source, source.copy(subjectId = "gone")),
            events = listOf(event, event.copy(id = "bad", end = "15:00")),
            appearance = SettingsAppearance("neon"),
        )
        val result = importSettings(state.copy(theme = "light"), export, SettingsPart.entries.toSet())
        assertEquals(listOf("r1", "r4"), result.rules.map { it.id })
        assertEquals(listOf(source), result.ruleSources)
        assertEquals(listOf(event), result.events)
        assertEquals("light", result.theme)
    }

    @Test fun `foreign and newer files are rejected`() {
        assertThrows(SettingsFormatException::class.java) { decodeSettings("not json") }
        assertThrows(SettingsFormatException::class.java) { decodeSettings("""{"app":"other","format":1}""") }
        assertThrows(SettingsFormatException::class.java) { decodeSettings("""{"app":"better-tamo","format":99}""") }
        assertEquals(emptySet<SettingsPart>(), decodeSettings("""{"app":"better-tamo","format":1,"extra":true}""").parts)
    }
}
