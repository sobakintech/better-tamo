package lt.bettertamo.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

class AppUpdaterTest {
    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    @Test fun releasePicksApkAsset() {
        val release = parseRelease(json("""{"tag_name":"v2026.10.01.0930","draft":false,"prerelease":false,"assets":[
            {"name":"notes.txt","browser_download_url":"https://github.com/example/app/releases/download/v2026.10.01.0930/notes.txt","size":10},
            {"name":"better-tamo-2026.10.01.0930.apk","browser_download_url":"https://github.com/example/app/releases/download/v2026.10.01.0930/better-tamo-2026.10.01.0930.apk","size":8388608}]}"""))
        assertEquals(AppRelease("2026.10.01.0930", "https://github.com/example/app/releases/download/v2026.10.01.0930/better-tamo-2026.10.01.0930.apk", 8388608), release)
    }

    @Test fun releaseRejectsUnusableEntries() {
        val asset = """[{"name":"a.apk","browser_download_url":"https://github.com/example/a.apk","size":1}]"""
        assertNull(parseRelease(json("""{"tag_name":"v2026.10.01.0930","prerelease":true,"assets":$asset}""")))
        assertNull(parseRelease(json("""{"tag_name":"latest","assets":$asset}""")))
        assertNull(parseRelease(json("""{"tag_name":"v2026.10.01.0930","assets":[]}""")))
        assertNull(parseRelease(json("""{"tag_name":"v2026.10.01.0930","assets":[{"name":"a.apk","browser_download_url":"http://example.com/a.apk"}]}""")))
    }

    @Test fun versionsCompareByBuildTime() {
        assertTrue(newerVersion("2026.10.01.0930", "2026.09.27.1331"))
        assertFalse(newerVersion("2026.09.27.1331", "2026.09.27.1331"))
        assertFalse(newerVersion("2026.09.01.0000", "2026.09.27.1331"))
        assertFalse(newerVersion("2026.10.01", "2026.09.27.1331"))
    }
}
