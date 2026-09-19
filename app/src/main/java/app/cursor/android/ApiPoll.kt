package app.cursor.android

import app.cursor.android.data.isActiveRun

/**
 * How often Pointer talks to Cursor while a chat is on screen.
 *
 * Open chat used to hit getAgent + listRuns + getConversation every 2s (~90
 * calls/min). Status and transcript already arrive over SSE while a run is
 * live, so REST is only a backstop for idle chats and for attaching to a run
 * that started elsewhere.
 */
internal object ApiPoll {
    const val STREAMING_MS = 10_000L
    const val ACTIVE_MS = 5_000L
    const val IDLE_MS = 12_000L

    fun watchDelayMs(streaming: Boolean, agentStatus: String?): Long = when {
        streaming -> STREAMING_MS
        agentStatus.isActiveRun() -> ACTIVE_MS
        else -> IDLE_MS
    }

    fun shouldFetchConversation(
        streaming: Boolean,
        agentActive: Boolean,
        fingerprintChanged: Boolean,
    ): Boolean {
        if (streaming) return false
        return agentActive || fingerprintChanged
    }
}
