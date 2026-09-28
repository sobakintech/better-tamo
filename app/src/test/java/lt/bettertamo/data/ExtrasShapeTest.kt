package lt.bettertamo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.time.LocalDate

class ExtrasShapeTest {
    private val mapper = TamoMapper()
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject
    private fun items(value: String) = Json.parseToJsonElement(value).jsonArray.map { it.jsonObject }

    @Test fun upcomingEventsParseRangesAndSkipBlanks() {
        val events = mapper.upcoming(items("""[
            {"id":0,"eventDateFrom":"2026-11-02","eventDateTo":"2026-11-08","eventTitle":"Rudens atostogos","eventBody":"Nuo 2026-11-02 iki 2026-11-08","eventType":3},
            {"id":7,"eventDateFrom":"2026-10-01T00:00:00","eventDateTo":"2026-10-01T00:00:00","eventTitle":"Atsiskaitymas: Gamtos mokslai","eventType":1},
            {"id":8,"eventDateFrom":"","eventTitle":"Be datos"},
            {"id":9,"eventDateFrom":"2026-10-02","eventTitle":""}
        ]"""))
        assertEquals(2, events.size)
        assertEquals(LocalDate.of(2026, 10, 1), events[0].start)
        assertNull(events[0].end)
        assertFalse(events[0].holiday)
        assertEquals(LocalDate.of(2026, 11, 8), events[1].end)
        assertTrue(events[1].holiday)
    }

    @Test fun lessonRecordsKeepHomeworkAndClasswork() {
        val records = mapper.lessonRecords(items("""[
            {"id":55,"subjectDate":"2026-09-25","subjectName":"Geografija","teacherName":"Vilma","subjectTheme":"Oro judėjimas","homeWork":"Vad. 12 psl.","classWork":"","hwDeadline":"2026-10-01"},
            {"id":56,"subjectDate":"bad","subjectName":"X"}
        ]"""))
        val record = records.single()
        assertEquals("55", record.id)
        assertEquals("Vad. 12 psl.", record.homework)
        assertEquals(LocalDate.of(2026, 10, 1), record.deadline)
    }

    @Test fun additionalMenuFlattensGroupsWithLinks() {
        val links = mapper.menu(json("""{"menuGroups":[{"title":"Analitika","groupItems":[
            {"id":"a","title":"Apžvalga","menuUrl":"/Analytics/Summary","doAuth":true},
            {"id":"b","title":"Be nuorodos","menuUrl":""}]}]}"""))
        assertEquals(1, links.size)
        assertEquals(MenuLink("a", "Analitika", "Apžvalga", "/Analytics/Summary", true), links.single())
    }

    @Test fun messageHeadersDistinguishReadStarredAndSent() {
        val received = mapper.messageHeaders(items("""[
            {"id":1,"sid":"1::2::x","messageTypeId":1,"subject":"Konsultacija","date":"2026-09-18T10:00:00.1234567","senderPerson":"Viktorija","senderPersonTitle":"Mokytojas","senderAvatar":"VG","senderAvatarType":"text","readDate":null,"isStarred":true,"isImportant":true,"hasAttachments":true},
            {"id":2,"sid":"2::3::y","messageTypeId":1,"subject":"Sistema","senderPerson":"TAMO","senderAvatar":"https://x/owl.png","senderAvatarType":"image","readDate":"2026-09-17T09:00:00"},
            {"id":5,"sid":"5::6::w","subject":"Apsauga","senderPerson":"TAMO","senderAvatar":"https://content.tamo.lt/images/tamo.svg","senderAvatarType":"url"},
            {"id":3,"subject":"Be sid"}
        ]"""), sent = false)
        assertEquals(3, received.size)
        assertFalse(received[0].read)
        assertTrue(received[0].starred && received[0].important && received[0].attachments)
        assertEquals("VG", received[0].avatar)
        assertEquals("", received[1].avatar)
        assertTrue(received[1].read)
        assertFalse(received[0].tamoLogo || received[1].tamoLogo)
        assertTrue(received[2].tamoLogo)
        val sent = mapper.messageHeaders(items("""[{"id":4,"sid":"4::z","subject":"Pažymiai","senderAvatar":"IB","recipientSets":[{"title":"Mano mokytojai > Auksė"}],"recipientCount":1,"readCount":1}]"""), sent = true).single()
        assertEquals("Auksė", sent.person)
        assertEquals("", sent.avatar)
        assertTrue(sent.read && sent.sent)
        assertEquals(1, sent.readCount)
    }

    @Test fun messageDetailReadsAttachmentsBesideItem() {
        val header = mapper.messageHeaders(items("""[{"id":1,"sid":"s","subject":"A","readDate":null}]"""), false).single()
        val detail = mapper.messageDetail(json("""{"item":{"body":"<p>Labas</p>","recipientCount":63},"attachments":[{"sid":"f1","name":"forma.docx"},{"sid":"","name":"tuščias"}]}"""), header)
        assertEquals("<p>Labas</p>", detail.body)
        assertEquals(listOf(SchoolFile("f1", "forma.docx")), detail.files)
        assertTrue(detail.header.read)
        assertEquals(63, detail.recipientCount)
    }

    @Test fun webUrlCarriesOnlyPathAndSelectedStudent() {
        val session = SchoolSession("tok en", "9", "Vardas", 2, listOf(SchoolRole("r1", "Mokinys", "", "77")), "r1")
        val url = TamoApi().webUrl(session, "https://dienynas.tamo.lt/Analytics/Summary?x=1")
        assertTrue(url.startsWith("https://dienynas.tamo.lt/MobileServiceV3/NavigateDirect?"))
        val query = url.substringAfter("?").split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals("/Analytics/Summary?x=1", query["url"])
        assertEquals("77", query["childStudentId"])
        assertEquals("tok en", query["authtoken"])
    }
}
