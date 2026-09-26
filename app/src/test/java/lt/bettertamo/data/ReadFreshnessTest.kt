package lt.bettertamo.data

import org.junit.Assert.*
import org.junit.Test

class ReadFreshnessTest {
    @Test fun `only successful matching requests are fresh until expiration`() {
        var now = 0L
        val cache = ReadFreshness({ now }, 100)
        assertFalse(cache.isFresh("diary", "September"))
        cache.complete("diary", "September")
        assertTrue(cache.isFresh("diary", "September"))
        assertFalse(cache.isFresh("diary", "October"))
        assertFalse(cache.isFresh("notices", "September"))
        now = 99
        assertTrue(cache.isFresh("diary", "September"))
        now = 100
        assertFalse(cache.isFresh("diary", "September"))
    }

    @Test fun `switching query does not reuse a payload that was replaced`() {
        val cache = ReadFreshness({ 0 })
        cache.complete("semester", "first")
        cache.complete("semester", "second")
        assertFalse(cache.isFresh("semester", "first"))
        assertTrue(cache.isFresh("semester", "second"))
    }

    @Test fun `switching accounts clears every cached read`() {
        val cache = ReadFreshness({ 0 })
        cache.complete("week", "one")
        cache.complete("summary", "math")
        cache.clear()
        assertFalse(cache.isFresh("week", "one"))
        assertFalse(cache.isFresh("summary", "math"))
    }

    @Test fun `partial homework replacement invalidates the previous range`() {
        val cache = ReadFreshness({ 0 })
        cache.complete("homework", "upcoming")
        cache.complete("week", "current")
        cache.invalidate("homework")
        assertFalse(cache.isFresh("homework", "upcoming"))
        assertTrue(cache.isFresh("week", "current"))
    }
}
