package app.mediacentaur.tv.bridge

/** How long to wait before each reconnect: doubling from 1 s to a 30 s ceiling, back to 1 s once connected. */
class Backoff {
    private var nextMs = FIRST_MS

    fun next(): Long {
        val delay = nextMs
        nextMs = minOf(nextMs * 2, CEILING_MS)
        return delay
    }

    fun reset() {
        nextMs = FIRST_MS
    }

    private companion object {
        const val FIRST_MS = 1_000L
        const val CEILING_MS = 30_000L
    }
}
