package app.geeklab.audiencelab.sample

import app.geeklab.audiencelab.sdk.RequestDebugEntry

object RequestLogStore {
    private const val MAX_ENTRIES = 100
    private val entries = ArrayDeque<RequestDebugEntry>()

    @Synchronized
    fun add(entry: RequestDebugEntry) {
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(entry)
    }

    @Synchronized
    fun snapshot(): List<RequestDebugEntry> = entries.toList().asReversed()

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
