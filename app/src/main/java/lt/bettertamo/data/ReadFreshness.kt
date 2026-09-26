package lt.bettertamo.data

internal class ReadFreshness(private val clock: () -> Long, private val lifetime: Long = 120_000) {
    private val reads = mutableMapOf<String, Pair<String, Long>>()

    fun isFresh(channel: String, request: String): Boolean {
        val (identity, time) = reads[channel] ?: return false
        return identity == request && clock() - time in 0 until lifetime
    }

    fun complete(channel: String, request: String) { reads[channel] = request to clock() }
    fun invalidate(channel: String) { reads.remove(channel) }
    fun clear() = reads.clear()
}
