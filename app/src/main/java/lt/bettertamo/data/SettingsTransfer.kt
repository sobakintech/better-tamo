package lt.bettertamo.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SettingsPart(val label: String) {
    RULES("Pamokų pavadinimai"),
    EVENTS("Mano įvykiai"),
    APPEARANCE("Išvaizda"),
}

@Serializable
data class SettingsAppearance(val theme: String, val accent: String? = null)

@Serializable
data class SettingsExport(
    val app: String = APP,
    val format: Int = FORMAT,
    val version: String = "",
    val rules: List<LessonRule>? = null,
    val ruleSources: List<RuleSource>? = null,
    val events: List<CustomEvent>? = null,
    val appearance: SettingsAppearance? = null,
) {
    val parts get() = SettingsPart.entries.filter { has(it) }.toSet()

    fun has(part: SettingsPart) = when (part) {
        SettingsPart.RULES -> rules != null
        SettingsPart.EVENTS -> events != null
        SettingsPart.APPEARANCE -> appearance != null
    }

    fun count(part: SettingsPart): Int? = when (part) {
        SettingsPart.RULES -> rules?.size
        SettingsPart.EVENTS -> events?.size
        SettingsPart.APPEARANCE -> null
    }

    companion object {
        const val APP = "better-tamo"
        const val FORMAT = 1
    }
}

class SettingsFormatException(message: String) : Exception(message)

private val settingsJson = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; explicitNulls = false }

fun settingsExport(state: PlannerState, accent: String?, parts: Set<SettingsPart>, version: String) = SettingsExport(
    version = version,
    rules = state.rules.takeIf { SettingsPart.RULES in parts },
    ruleSources = state.ruleSources.takeIf { SettingsPart.RULES in parts },
    events = state.events.takeIf { SettingsPart.EVENTS in parts },
    appearance = SettingsAppearance(state.theme, accent).takeIf { SettingsPart.APPEARANCE in parts },
)

fun encodeSettings(export: SettingsExport): String = settingsJson.encodeToString(SettingsExport.serializer(), export)

fun decodeSettings(text: String): SettingsExport {
    val export = runCatching { settingsJson.decodeFromString(SettingsExport.serializer(), text) }
        .getOrElse { throw SettingsFormatException("Tai ne Better TAMO nustatymų failas.") }
    if (export.app != SettingsExport.APP) throw SettingsFormatException("Tai ne Better TAMO nustatymų failas.")
    if (export.format > SettingsExport.FORMAT) throw SettingsFormatException("Šis failas sukurtas naujesne Better TAMO versija. Atnaujinkite programėlę.")
    return export
}

fun importSettings(state: PlannerState, export: SettingsExport, parts: Set<SettingsPart>): PlannerState {
    var result = state
    if (SettingsPart.RULES in parts && export.rules != null) {
        val rules = export.rules.filter(::validRule).fold(emptyList<LessonRule>()) { kept, rule ->
            if (kept.any { it.id == rule.id } || ruleConflict(rule, kept)) kept else kept + rule
        }
        val sources = export.ruleSources.orEmpty().filter { source -> rules.any { it.subjectId == source.subjectId } }
            .distinctBy { Triple(it.subjectId, it.weekday to it.slot, it.teacherId) }
        result = result.copy(rules = rules, ruleSources = sources)
    }
    if (SettingsPart.EVENTS in parts && export.events != null) result = result.copy(events = export.events.filter(::validEvent).distinctBy { it.id })
    if (SettingsPart.APPEARANCE in parts && export.appearance != null) result = result.copy(theme = export.appearance.theme.takeIf { it in appThemes } ?: result.theme)
    return result
}

val appThemes = setOf("system", "light", "dark")
