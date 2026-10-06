package lt.bettertamo.data

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.HttpsURLConnection

class TamoFailure(val userMessage: String, val expired: Boolean = false) : Exception(userMessage)

internal fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonObject.obj(key: String) = this[key] as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.list(key: String): List<JsonObject> = (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.requiredList(key: String): List<JsonObject> {
    val list = this[key] as? JsonArray ?: throw TamoFailure("TAMO grąžino neatpažintus duomenis. Bandykite atnaujinti.")
    if (list.any { it !is JsonObject }) throw TamoFailure("TAMO grąžino neatpažintus duomenis.")
    return list.map { it.jsonObject }
}
internal fun validHeader(value: String) = value.isNotBlank() && value.none { it.code < 32 || it.code == 127 }

internal fun checkEnvelope(payload: JsonObject, legacy: Boolean): JsonObject {
    if (legacy) {
        if (payload.string("ErrorMessage") == "User is not authenticated.") throw TamoFailure("Prisijunkite iš naujo.", true)
        if (payload.string("Status") != "1" || payload.string("ErrorCode") != "0") {
            throw TamoFailure(if (payload.string("ErrorCode") == "-13") "TAMO šiems duomenims reikalauja prenumeratos." else "TAMO nepriėmė užklausos. Patikrinkite prisijungimo duomenis ir bandykite dar kartą.")
        }
    } else if (payload.string("message") == "User is not authenticated.") {
        throw TamoFailure("Prisijunkite iš naujo.", true)
    } else if (payload["isSuccess"] != JsonPrimitive(true)) {
        throw TamoFailure("TAMO nepavyko pateikti duomenų. Bandykite dar kartą.")
    }
    return payload
}

class TamoApi {
    val mapper = TamoMapper { text -> if ('<' in text || '&' in text) Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().trim() else text.trim() }

    private suspend fun request(path: String, session: SchoolSession? = null, query: Map<String, String> = emptyMap(), body: JsonObject? = null, legacy: Boolean = false, form: Map<String, String>? = null, envelope: Boolean = true, method: String? = null, ignoreBody: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        val base = if (legacy) "https://dienynas.tamo.lt/MobileServiceV3/" else "https://api.tamo.lt/"
        val parameters = if (legacy && session != null) query + ("authToken" to session.token) else query
        val suffix = parameters.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val connection = URI(base + path + if (suffix.isEmpty()) "" else "?$suffix").toURL().openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (!legacy && session != null) {
                require(validHeader(session.token))
                connection.setRequestProperty("Authorization", "Bearer ${session.token}")
                session.selectedRole?.let { require(validHeader(it)); connection.setRequestProperty("x-selected-role", it) }
            }
            method?.let { connection.requestMethod = it }
            if (form != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val encoded = form.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
                connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            } else if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code == 401) throw TamoFailure("Prisijunkite iš naujo.", true)
            if (code !in 200..299) throw TamoFailure(if (code == 403) "TAMO nesuteikė prieigos prie šių duomenų." else "Nepavyko susisiekti su TAMO (HTTP $code). Bandykite dar kartą.")
            if (ignoreBody) return@withContext JsonObject(emptyMap())
            val raw = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > 4 * 1024 * 1024) throw TamoFailure("TAMO atsakymas per didelis. Pasirinkite trumpesnį laikotarpį.")
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            val textBody = raw.toString(Charsets.UTF_8)
            if (!envelope && textBody.isBlank()) return@withContext JsonObject(emptyMap())
            val payload = Json.parseToJsonElement(textBody) as? JsonObject ?: throw TamoFailure("TAMO grąžino neatpažintą atsakymą.")
            if (envelope) checkEnvelope(payload, legacy) else payload
        } catch (e: CancellationException) { throw e }
        catch (e: TamoFailure) { throw e }
        catch (e: Exception) { throw TamoFailure("Nepavyko įkelti TAMO duomenų. Patikrinkite interneto ryšį ir bandykite dar kartą.") }
        finally { connection.disconnect() }
    }

    suspend fun login(username: String, password: String): SchoolSession {
        val result = request("AuthenticateV2", body = buildJsonObject {
            put("username", username); put("password", password)
            put("dateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            put("typePhoneSystem", "Android"); put("guid", "1166cfd3-1be5-4dca-aa64-5aff7bbb8acc")
        }, legacy = true).obj("Result")
        if (result["userRoleIsAllowed"] != JsonPrimitive(true) || !validHeader(result.string("authToken")) || result.string("personId").isBlank()) {
            throw TamoFailure("Šiai paskyrai TAMO nesuteikė prieigos.")
        }
        return SchoolSession(result.string("authToken"), result.string("personId"), "${result.string("firstName")} ${result.string("lastName")}".trim(), result.string("role").toIntOrNull() ?: 0)
    }

    suspend fun roles(session: SchoolSession): SchoolSession {
        request("core/app/settings/StyleRef", session)
        val result = request("core/app/roles", session)
        val roles = result.requiredList("roles").map {
            val id = it.string("id")
            if (!validHeader(id)) throw TamoFailure("TAMO grąžino netinkamą paskyros vaidmenį.")
            SchoolRole(id, it.string("title"), it.string("subtitle"), it.string("studentId").ifBlank { it.string("childStudentId") }.takeIf { value -> value != "0" }.orEmpty())
        }
        if (roles.isEmpty()) throw TamoFailure("Ši paskyra neturi prieinamų mokyklos duomenų.")
        return session.copy(roles = roles, selectedRole = session.selectedRole?.takeIf { id -> roles.any { it.id == id } } ?: roles.singleOrNull()?.id)
    }

    suspend fun week(session: SchoolSession, date: LocalDate): JsonObject = request("v2/app/calendar/events", session, mapOf("date" to mondayOf(date).toString()))
    suspend fun lessons(session: SchoolSession, from: LocalDate, to: LocalDate): List<Lesson> {
        if (session.legacyRole != 2 || session.roles.size != 1) return emptyList()
        val result = request("GetLessons", session, range(from, to), legacy = true).obj("Result")
        val items = if (result["items"] is JsonArray) result.requiredList("items") else result.requiredList("Items")
        return mapper.origins(items)
    }
    suspend fun homework(session: SchoolSession, from: LocalDate, to: LocalDate) = mapper.homework(request("core/app/darbai", session, range(from, to) + ("workType" to "home")))
    suspend fun setHomeworkDone(session: SchoolSession, studentId: String, lessonId: String, done: Boolean) {
        request("core/app/darbai/namu/atlikimas", session, form = mapOf("MokinioId" to studentId, "PamokosId" to lessonId, "Atliktas" to done.toString()))
    }
    suspend fun calendar(session: SchoolSession, month: YearMonth) = mapper.calendar(request("core/app/calendar/events/allDay", session, range(month.atDay(1), month.atEndOfMonth())), month)
    suspend fun badges(session: SchoolSession, month: YearMonth) = request("core/app/calendar/badges", session, range(month.atDay(1), month.atEndOfMonth())).let { mapper.badges(it) to mapper.dayIcons(it) }
    suspend fun diary(session: SchoolSession, from: LocalDate, to: LocalDate) = mapper.diary(request("core/app/dienynas", session, range(from, to)))
    suspend fun notices(session: SchoolSession, remarks: Boolean, month: YearMonth): List<SchoolNotice> {
        if (!remarks) return mapper.notices(request("core/app/feeds", session).requiredList("result"), false)
        if (session.legacyRole != 2 || session.roles.size != 1) throw TamoFailure("Šios paskyros pastabos nepasiekiamos.")
        val result = request("GetAwards", session, range(month.atDay(1), month.atEndOfMonth()), legacy = true).obj("Result")
        return mapper.notices(if (result["items"] is JsonArray) result.requiredList("items") else result.requiredList("Items"), true)
    }
    suspend fun fileUrl(session: SchoolSession, file: SchoolFile): String {
        val url = if (file.legacy) request("GetFileUrl", session, mapOf("fileId" to file.sid), legacy = true).obj("Result").let { it.string("url").ifBlank { it.string("Url") } }
            else request("files/filedownloadurl", session, form = mapOf("fileSid" to file.sid)).string("url")
        return url.takeIf { runCatching { URI(it).scheme == "https" }.getOrDefault(false) } ?: throw TamoFailure("TAMO negrąžino priedo nuorodos.")
    }
    suspend fun periods(session: SchoolSession): List<SchoolPeriod> {
        val result = request("GetWindowFilters", session, mapOf("windowName" to "periods", "userType" to session.legacyRole.toString()), legacy = true).obj("Result")
        return mapper.periods(result)
    }
    suspend fun semester(session: SchoolSession, period: SchoolPeriod): List<SemesterSubject> {
        if (session.legacyRole != 2 || session.roles.size != 1) throw TamoFailure("Šios paskyros pusmečių duomenys nepasiekiami.")
        val result = request("GetPeriodAssessments", session, mapOf("personId" to period.personId, "periodId" to period.id), legacy = true).obj("Result")
        return mapper.semester(if (result["items"] is JsonArray) result.requiredList("items") else result.requiredList("Items"))
    }
    private fun legacyItems(result: JsonObject) = if (result["items"] is JsonArray) result.requiredList("items") else result.requiredList("Items")
    private fun requireStudent(session: SchoolSession, what: String) {
        if (session.legacyRole != 2 || session.roles.size != 1) throw TamoFailure("Šios paskyros $what nepasiekiami.")
    }
    suspend fun rankingSubjects(session: SchoolSession): List<Pair<String, String>> {
        requireStudent(session, "reitingai")
        return mapper.rankingSubjects(legacyItems(request("GetRatingSubjects", session, mapOf("personId" to session.personId), legacy = true).obj("Result")))
    }
    suspend fun ranking(session: SchoolSession, subjectId: String): Ranking? =
        mapper.ranking(legacyItems(request("GetRatings", session, mapOf("personId" to session.personId, "subjectId" to subjectId), legacy = true).obj("Result")))
    suspend fun upcoming(session: SchoolSession, from: LocalDate, to: LocalDate): List<UpcomingEvent> {
        requireStudent(session, "artimiausi įvykiai")
        return mapper.upcoming(legacyItems(request("GetNextEvents", session, range(from, to), legacy = true).obj("Result")))
    }
    suspend fun lessonHistory(session: SchoolSession, from: LocalDate, to: LocalDate): List<LessonRecord> {
        requireStudent(session, "pamokų įrašai")
        return mapper.lessonRecords(legacyItems(request("GetLessons", session, range(from, to), legacy = true).obj("Result")))
    }
    suspend fun menu(session: SchoolSession): List<MenuLink> = mapper.menu(request("GetAdditionalMenu", session, mapOf("location" to "MoreWnd.BelowStatic"), legacy = true).obj("Result"))
    suspend fun messages(session: SchoolSession, folder: MessageFolder, page: Int, search: String): List<MessageHeader> {
        val query = buildMap {
            if (folder != MessageFolder.GROUP) { put("orderDescending", "true"); put("page", page.toString()) }
            if (search.isNotBlank()) put("searchTerm", search.trim())
            when (folder) {
                MessageFolder.STARRED -> put("isStarred", "true")
                MessageFolder.DELETED -> put("isDeleted", "true")
                MessageFolder.GROUP -> put("isDeleted", "false")
                else -> {}
            }
        }
        val path = when (folder) { MessageFolder.SENT -> "messaging/messages/sent"; MessageFolder.GROUP -> "messaging/messages/group/received"; else -> "messaging/messages/received" }
        return mapper.messageHeaders(request(path, session, query, envelope = false).list("items"), folder == MessageFolder.SENT, folder == MessageFolder.DELETED)
    }
    suspend fun message(session: SchoolSession, header: MessageHeader): MessageDetail {
        val path = if (header.sent) "messaging/messages/sent/${header.id}" else "messaging/messages/received/${header.typeId}/${header.id}"
        return mapper.messageDetail(request(path, session, envelope = false), header)
    }
    suspend fun starMessage(session: SchoolSession, sid: String, starred: Boolean) {
        request("messaging/messages/received/star", session, body = buildJsonObject { put("sid", sid); put("isStarred", starred) }, envelope = false)
    }
    suspend fun markRead(session: SchoolSession, sids: List<String>) = messagingAction("messaging/messages/received/read", session, sids)
    suspend fun markUnread(session: SchoolSession, sids: List<String>) = messagingAction("messaging/messages/received/unread", session, sids)
    suspend fun deleteMessages(session: SchoolSession, sids: List<String>) = messagingAction("messaging/messages/received/removeselected", session, sids)
    suspend fun restoreMessages(session: SchoolSession, sids: List<String>) = messagingAction("messaging/messages/received/restoredeleted", session, sids)
    suspend fun deleteSentMessage(session: SchoolSession, sid: String) = messagingAction("messaging/messages/sent/remove", session, listOf(sid))
    private suspend fun messagingAction(path: String, session: SchoolSession, sids: List<String>) {
        val sid = sids.singleOrNull()?.let(::JsonPrimitive) ?: JsonArray(sids.map(::JsonPrimitive))
        val result = request(path, session, body = buildJsonObject { put("sid", sid) }, envelope = false)
        if (result["isSuccess"] == JsonPrimitive(false)) throw TamoFailure("TAMO pakeitimo neišsaugojo. Bandykite dar kartą.")
    }
    suspend fun registerDevice(session: SchoolSession, installationId: String, token: String) {
        request("core/app/devices/installation", session, body = buildJsonObject {
            putJsonObject("Installation") { put("Id", installationId); put("DeviceId", token) }
        }, ignoreBody = true)
    }
    suspend fun unregisterDevice(session: SchoolSession, installationId: String) {
        request("core/app/devices/installation/${URLEncoder.encode(installationId, "UTF-8")}", session, method = "DELETE", ignoreBody = true)
    }
    suspend fun testNotification(session: SchoolSession) {
        request("core/app/utilities/sendnotification", session, body = JsonObject(emptyMap()))
    }
    fun webUrl(session: SchoolSession, target: String): String {
        val uri = URI(target)
        val path = if (uri.isAbsolute) (uri.rawPath.orEmpty().ifBlank { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")) else target
        val student = session.roles.find { it.id == session.selectedRole }?.studentId.orEmpty()
        val query = listOf("authtoken" to session.token, "hideMenuBar" to "true", "childStudentId" to student, "url" to path)
        return "https://dienynas.tamo.lt/MobileServiceV3/NavigateDirect?" + query.joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
    }
    private fun range(from: LocalDate, to: LocalDate): Map<String, String> {
        require(!to.isBefore(from) && to <= from.plusDays(62))
        return mapOf("dateFrom" to from.toString(), "dateTo" to to.toString())
    }
}

class TamoMapper(private val text: (String) -> String = { it }) {
    private val zone = ZoneId.of("Europe/Vilnius")
    private fun content(obj: JsonObject, key: String): String {
        val value = obj[key] ?: if (key.endsWith("Content")) obj[key.removeSuffix("Content")] else null
        return text(if (value is JsonObject) value.string("content") else (value as? JsonPrimitive)?.contentOrNull.orEmpty())
    }
    private fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
    private fun instant(value: String): ZonedDateTime? = runCatching { ZonedDateTime.parse(value).withZoneSameInstant(zone) }.getOrNull()

    private fun looseDate(value: String): LocalDate? = date(value) ?: runCatching { LocalDate.parse(value.trim().take(10).replace('.', '-')) }.getOrNull()

    fun upcoming(items: List<JsonObject>): List<UpcomingEvent> = items.mapIndexedNotNull { index, item ->
        val start = looseDate(item.string("eventDateFrom")) ?: looseDate(item.string("dateFrom")) ?: return@mapIndexedNotNull null
        val end = looseDate(item.string("eventDateTo")) ?: looseDate(item.string("dateTo"))
        val title = text(item.string("eventTitle")).ifBlank { return@mapIndexedNotNull null }
        UpcomingEvent(item.string("id").takeIf { it.isNotBlank() && it != "0" } ?: "$start:$index", start, end?.takeIf { it.isAfter(start) }, text(item.string("eventTime")), title, text(item.string("eventBody")), item.string("eventType").toIntOrNull() ?: 0)
    }.sortedBy { it.start }

    fun lessonRecords(items: List<JsonObject>): List<LessonRecord> = items.mapIndexedNotNull { index, item ->
        val day = looseDate(item.string("subjectDate")) ?: return@mapIndexedNotNull null
        LessonRecord(item.string("id").takeIf { it.isNotBlank() && it != "0" } ?: "$day:$index", day, text(item.string("subjectName")), text(item.string("teacherName")), text(item.string("subjectTheme")),
            text(item.string("homeWork")), text(item.string("classWork")), looseDate(item.string("hwDeadline")) ?: looseDate(item.string("dateDeadline")))
    }

    private fun dateTime(value: String): LocalDateTime? = runCatching { LocalDateTime.parse(value.take(19)) }.getOrNull()

    fun messageHeaders(items: List<JsonObject>, sent: Boolean, deleted: Boolean = false): List<MessageHeader> = items.mapNotNull { item ->
        val id = item.string("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val sid = item.string("sid").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val to = item.list("recipientSets").map { text(it.string("title")).substringAfterLast(" > ") }.filter { it.isNotBlank() }
        MessageHeader(id, sid, item.string("messageTypeId").ifBlank { "1" }, text(item.string("subject")), dateTime(item.string("date")),
            if (sent) to.joinToString(", ").ifBlank { "Gavėjai: ${item.string("recipientCount")}" } else text(item.string("senderPerson")),
            if (sent) "" else text(item.string("senderPersonTitle")), text(item.string("senderAvatar")).takeIf { !sent && item.string("senderAvatarType").let { type -> type.isBlank() || type == "text" } && it.length <= 3 }.orEmpty(),
            sent || item.string("readDate").isNotBlank(), item["isStarred"] == JsonPrimitive(true), item["isImportant"] == JsonPrimitive(true),
            item["hasAttachments"] == JsonPrimitive(true), sent, item.string("replyModeId").toIntOrNull() ?: 0,
            if (sent) item.string("readCount").toIntOrNull() else null, if (sent) item.string("recipientCount").toIntOrNull() else null,
            !sent && item.string("senderAvatarType") == "url" && item.string("senderAvatar").substringBefore('?').endsWith("/tamo.svg"),
            item["isClosable"] != JsonPrimitive(false), deleted)
    }

    fun messageDetail(payload: JsonObject, header: MessageHeader): MessageDetail {
        val item = payload.obj("item")
        val files = (payload.list("attachments") + item.list("attachments")).mapNotNull { file ->
            file.string("sid").ifBlank { file.string("fileSid") }.takeIf { it.isNotBlank() }?.let { SchoolFile(it, file.string("name").ifBlank { file.string("fileName") }.ifBlank { "Priedas" }) }
        }.distinctBy { it.sid }
        val recipients = item.list("recipientSets").map { text(it.string("title")) }.filter { it.isNotBlank() }
        return MessageDetail(header.copy(read = true), item.string("body").ifBlank { item.string("bodyPlain") }, files, recipients, item.string("recipientCount").toIntOrNull())
    }

    fun rankingSubjects(items: List<JsonObject>): List<Pair<String, String>> = items.firstOrNull()?.list("subjectsInfos").orEmpty().mapNotNull { subject ->
        val id = subject.string("Id").ifBlank { subject.string("id") }
        val name = text(subject.string("subject"))
        (id to name).takeIf { id.isNotBlank() && name.isNotBlank() }
    }

    fun ranking(items: List<JsonObject>): Ranking? {
        val item = items.firstOrNull() ?: return null
        val position = item.string("current").toIntOrNull() ?: return null
        val averages = item.list("ratingInfos").sortedBy { it.string("nr").toIntOrNull() ?: Int.MAX_VALUE }.map { it.string("value").replace(',', '.').toDoubleOrNull() ?: return null }
        return Ranking(position, averages).takeIf { position in 1..averages.size }
    }

    fun menu(result: JsonObject): List<MenuLink> = (result.list("menuGroups") + result.list("MenuGroups")).flatMap { group ->
        (group.list("groupItems") + group.list("GroupItems")).mapNotNull { item ->
            val url = item.string("menuUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = text(item.string("title")).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MenuLink(item.string("id").ifBlank { url }, text(group.string("title")), title, url, item.string("doAuth") == "true")
        }
    }

    fun origins(items: List<JsonObject>): List<Lesson> = items.mapNotNull { item ->
        val id = item.string("id").ifBlank { item.string("subjectId") }.takeIf { it.isNotBlank() && it != "0" } ?: return@mapNotNull null
        val day = date(item.string("subjectDate"))
        val subjectId = if (item.string("id").isNotBlank()) item.string("subjectId") else ""
        val teacher = text(item.string("teacherName"))
        Lesson(id, subjectId, text(item.string("subjectName")), teacher, teacher, day?.dayOfWeek?.value ?: 1, item.string("subjectSerialNumber").toIntOrNull() ?: 0, "", text(item.string("subjectTheme")), date = day, canRename = subjectId.isNotBlank())
    }

    private fun contents(obj: JsonObject, key: String): List<JsonObject> = when (val value = obj[key]) {
        is JsonObject -> listOf(value)
        is JsonArray -> value.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }

    fun week(payload: JsonObject, origins: List<Lesson>): List<Lesson> = payload.requiredList("days").flatMap { day ->
        val dayDate = date(day.string("date")) ?: throw TamoFailure("Neatpažinta tvarkaraščio data.")
        day.requiredList("events").map { event ->
            val sid = event.string("sid")
            val id = event.string("id").takeIf { it.isNotBlank() && it != "0" } ?: sid
            if (id.isBlank()) throw TamoFailure("TAMO negrąžino pamokos identifikatoriaus.")
            val origin = origins.singleOrNull { it.id == id }
            val from = instant(event.string("timeFromUtc"))
            val to = instant(event.string("timeToUtc"))
            val slot = content(event, "eventIcon").trim().toIntOrNull()?.takeIf { it in 1..30 }
                ?: event.list("timeBadges").map { text(it.string("content")).trim() }.firstNotNullOfOrNull { it.toIntOrNull()?.takeIf { number -> number in 1..30 } } ?: 0
            val details = event.list("eventDetails").map { detail ->
                val title = content(detail, "titleContent")
                LessonDetail(title.ifBlank { content(detail, "labelContent") }, content(detail, "bodyContent"), detail.list("files").mapNotNull { file ->
                    file.string("fileSid").takeIf { it.isNotBlank() }?.let { SchoolFile(it, text(file.string("content"))) }
                }, detail.string("key"), content(detail, "iconContent"), content(detail, "labelContent").takeIf { title.isNotBlank() }.orEmpty())
            }.filter { it.title.isNotBlank() || it.text.isNotBlank() || it.files.isNotEmpty() }
                .filterNot { it.key.startsWith("homework") && it.files.isEmpty() && isPlaceholderHomework(it.text) }
            val formativeKey = event.obj("references").string("formatives")
            val formatives = (event.list("formatives") + payload.list("formatives").filter { formativeKey.isNotBlank() && it.string("key") == formativeKey }.flatMap { it.list("items") }
                .filter { it.string("lessonId") == event.string("id") }).distinctBy { listOf(it.string("date"), it.string("type"), it.string("title")) }
            val pendingFormatives = formativeKey.takeIf { it.isNotBlank() }?.let { key ->
                payload.list("formatives").filter { it.string("key") == key }.flatMap { it.list("items") }.mapIndexedNotNull { index, item -> formative(item, index) }.filter { it.converted != true }
            }
            val formativeDetails = formatives.map { LessonDetail(listOf("Kaupiamasis", text(it.string("type"))).filter { part -> part.isNotBlank() }.joinToString(" · "), "", key = "formative", badge = text(it.string("title"))) }
            val right = listOf("rightIconsTop", "rightIconsMiddle", "rightIconsBottom").flatMap { contents(event, it) }
            val marks = right.filter { it.string("contentType") != "icon" }.map { text(it.string("content")) }.filter { it.isNotBlank() }
            val note = right.filter { it.string("contentType") == "icon" }.map { it.string("content") }.firstNotNullOfOrNull {
                when { "negative" in it -> "negative"; "positive" in it -> "positive"; "note" in it || "comment" in it -> "comment"; else -> null }
            }.orEmpty()
            val bottomRight = contents(event, "bottomIconsRight")
            val average = bottomRight.firstOrNull { it.string("key") == "average" }?.let { text(it.string("content")) }.orEmpty()
            val trend = bottomRight.firstOrNull { it.string("key") == "trend" }?.string("content").orEmpty().let {
                when { it.isBlank() -> ""; "up" in it -> "up"; "down" in it -> "down"; else -> "flat" }
            }
            val label = event.obj("eventLabel")
            val important = "important" in event.obj("eventHighlight").string("styleRef") || "important" in label.string("styleRef")
            val subjectId = event.string("schoolSubjectId").takeIf { it.isNotBlank() && it != "0" } ?: origin?.subjectId?.takeIf { it.isNotBlank() }
            val teacher = origin?.teacher?.ifBlank { null } ?: content(event, "eventSubtitle")
            Lesson(id, subjectId ?: "event:$id", content(event, "eventTitle").ifBlank { origin?.subject.orEmpty() }, teacher, teacher, dayDate.dayOfWeek.value, slot, "", content(event, "eventDescription").ifBlank { origin?.topic.orEmpty() }, dayDate, from?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "", to?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "", sid, details + formativeDetails, subjectId != null,
                marks.distinct().joinToString(" · ").ifBlank { details.filter { it.key == "grade" }.joinToString(" · ") { it.badge } }, text(label.string("content")), important, note, average, trend, pendingFormatives)
        }
    }.distinctBy { it.key }

    fun homework(payload: JsonObject): List<Homework> = payload.requiredList("items").mapNotNull { item ->
        val files = item.list("files").mapNotNull { file ->
            val legacyId = file.string("fileId").ifBlank { file.string("id") }
            val name = text(file.string("fileName").ifBlank { file.string("content") }).ifBlank { "Priedas" }
            when {
                legacyId.isNotBlank() -> SchoolFile(legacyId, name, legacy = true)
                file.string("fileSid").isNotBlank() -> SchoolFile(file.string("fileSid"), name)
                else -> null
            }
        }.distinctBy { it.sid }
        val body = text(item.string("homeWork")).takeUnless(::isPlaceholderHomework).orEmpty()
        if (body.isBlank() && files.isEmpty()) return@mapNotNull null
        val due = date(item.string("deadline")) ?: throw TamoFailure("Neatpažinta namų darbo data.")
        val assigned = date(item.string("date"))
        val id = item.string("lessonId")
        if (id.isBlank() || id == "0") throw TamoFailure("TAMO negrąžino namų darbo identifikatoriaus.")
        Homework("$id:$due", id, due.dayOfWeek.value, body, assigned?.dayOfWeek?.value ?: 1, due, assigned, text(item.string("thingName")), item.string("completionDate").isNotBlank(), files)
    }.distinctBy { it.id }

    fun calendar(payload: JsonObject, month: YearMonth): List<SchoolCalendarEvent> = payload.requiredList("allDayEvents").map { event ->
        val dateText = content(event, "eventSubtitle")
        val explicitFrom = instant(event.string("timeFromUtc"))?.toLocalDate() ?: date(event.string("timeFromUtc"))
        val explicitTo = instant(event.string("timeToUtc"))?.toLocalDate() ?: date(event.string("timeToUtc"))
        val dates = if (explicitFrom != null) explicitFrom to maxOf(explicitFrom, explicitTo ?: explicitFrom) else schoolDateRange(dateText, month)
        val from = dates?.first ?: month.atDay(1)
        val to = dates?.second ?: from
        val title = content(event, "eventTitle")
        val label = content(event, "eventLabel").lowercase()
        val kind = when { "atostog" in title.lowercase() || "atostog" in label -> SchoolDayKind.BREAK; "švent" in label || "valstybin" in label -> SchoolDayKind.HOLIDAY; else -> SchoolDayKind.EVENT }
        SchoolCalendarEvent(event.string("sid").ifBlank { "${event.string("id")}:$from:$title" }, title, from, to, kind, dateText, dates != null)
    }.distinctBy { it.id }

    fun badges(payload: JsonObject): Map<LocalDate, List<String>> = payload.requiredList("days").mapNotNull { day ->
        date(day.string("date"))?.let { it to day.list("badges").filter { it.string("contentType") != "icon" }.map { badge -> text(badge.string("content")).ifBlank { badge.string("key") } } }
    }.toMap()

    fun dayIcons(payload: JsonObject): Map<LocalDate, List<String>> = payload.requiredList("days").mapNotNull { day ->
        date(day.string("date"))?.let { it to day.list("badges").filter { badge -> badge.string("contentType") == "icon" }.map { badge -> text(badge.string("content")).ifBlank { badge.string("key") } } }
    }.filter { it.second.isNotEmpty() }.toMap()

    fun diary(payload: JsonObject): List<DiaryEntry> {
        val records = payload.requiredList("items").flatMapIndexed { index, item ->
            val day = date(item.string("subjectDate")) ?: return@flatMapIndexed emptyList()
            val subject = text(item.string("subject"))
            val grade = text(item.string("assessmentValue"))
            val attendance = text(item.string("attendanceValue"))
            listOfNotNull(
                grade.takeIf { it.isNotBlank() }?.let { DiaryEntry("g:$day:$index", subject, day, it, text(item.string("assessmentType")), DiaryKind.GRADE, color(item.string("assessmentColor"))) },
                attendance.takeIf { it.isNotBlank() }?.let { DiaryEntry("a:$day:$index", subject, day, it, "", DiaryKind.ATTENDANCE) },
            )
        }
        val formatives = payload.list("formativeGrades").mapIndexedNotNull { index, item -> formative(item, index) }
        return records + formatives
    }

    private fun formative(item: JsonObject, index: Int): DiaryEntry? {
        val day = date(item.string("date")) ?: return null
        val value = text(item.string("title")).ifBlank { return null }
        val converted = when (item.string("isConverted")) { "true" -> true; "false" -> false; else -> null }
        return DiaryEntry("f:${item.string("lessonId")}:$index", text(item.string("subject")), day, value, text(item.string("type")), DiaryKind.FORMATIVE,
            converted = converted, system = item.string("system"), percents = item.string("percents").toIntOrNull())
    }

    private fun color(value: String): Long? = value.removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    fun periods(result: JsonObject): List<SchoolPeriod> = result.requiredList("groups").filter { it.string("id") == "periods" }.flatMap { group ->
        group.list("filteritems").mapNotNull { item ->
            val values = (item["values"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
            if (values.size != 2 || values.any { it.toLongOrNull() == null }) return@mapNotNull null
            SchoolPeriod(values[1], text(item.string("name")), item["selected"] == JsonPrimitive(true), values[0])
        }
    }

    fun semester(items: List<JsonObject>): List<SemesterSubject> = items.map { item ->
        val marks = item.list("marks").mapNotNull { mark -> mark.string("value").toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && it in 1.0..10.0 }?.toInt() }
        SemesterSubject(text(item.string("subject")), text(item.string("teacherName")), item.string("average").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..10.0 }, text(item.string("main")), marks, (item.list("TextMarks") + item.list("textMarks")).map { text(it.string("value")) }.filter { it.isNotBlank() })
    }

    fun notices(items: List<JsonObject>, remarks: Boolean): List<SchoolNotice> = items.mapIndexedNotNull { index, item ->
        if (remarks) {
            val type = text(item.string("awardTypeName"))
            SchoolNotice(item.string("id").ifBlank { index.toString() }, date(item.string("subjectDate")) ?: date(item.string("awardDateTime")), text(item.string("subject")), text(item.string("awardValue")), text(item.string("teacherName")), kind = remarkKind(type), value = type)
        } else {
            val detail = item.obj("eventDetails")
            fun field(name: String) = text(detail.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.let { (it as? JsonPrimitive)?.contentOrNull }.orEmpty())
            val id = item.string("id").ifBlank { index.toString() }
            val day = date(item.string("date"))
            val subject = field("ThingName")
            val lessonDate = date(field("Date"))
            when (item.string("eventId")) {
                "4" -> field("HomeWork").ifBlank { field("Description") }.takeUnless(::isPlaceholderHomework)?.let { SchoolNotice(id, day, subject, it, deadline = date(field("Deadline")), kind = NoticeKind.HOMEWORK, lessonDate = lessonDate) }
                "1" -> field("Value").takeIf { it.isNotBlank() }?.let { SchoolNotice(id, day, subject, "", kind = NoticeKind.GRADE, value = it, lessonDate = lessonDate) }
                "2" -> SchoolNotice(id, day, subject, "", kind = NoticeKind.ATTENDANCE, value = if (field("Type") == "1") "n" else "p", lessonDate = lessonDate)
                "10" -> field("Vertinimas").takeIf { it.isNotBlank() }?.let { SchoolNotice(id, day, subject, field("Tipas"), kind = NoticeKind.FORMATIVE, value = it, lessonDate = lessonDate) }
                "8" -> SchoolNotice(id, day, subject, field("Value"), kind = remarkKind(field("Type")), value = field("Type"), lessonDate = lessonDate)
                else -> listOf(field("Description"), field("HomeWork"), field("Vertinimas"), field("Value")).filter { it.isNotBlank() }.distinct().joinToString("\n")
                    .takeIf { it.isNotBlank() }?.let { SchoolNotice(id, day, subject, it, deadline = date(field("Deadline")), lessonDate = lessonDate) }
            }
        }
    }

    private fun remarkKind(type: String) = when {
        type.contains("pagyr", ignoreCase = true) -> NoticeKind.PRAISE
        type.contains("pastab", ignoreCase = true) -> NoticeKind.REMARK
        else -> NoticeKind.COMMENT
    }
}
