package app.cursor.android

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Buffers notification navigation until the Compose navigation graph is ready. */
internal class AgentNavigationRequests {
    private val channel = Channel<String>(capacity = Channel.BUFFERED)

    val requests: Flow<String> = channel.receiveAsFlow()

    fun offer(agentId: String?): Boolean {
        val normalized = normalizeAgentId(agentId) ?: return false
        return channel.trySend(normalized).isSuccess
    }
}

internal fun normalizeAgentId(agentId: String?): String? {
    val normalized = agentId?.trim().orEmpty()
    return normalized.takeIf {
        it.isNotEmpty() &&
            it.length <= MAX_AGENT_ID_LENGTH &&
            it.none(Char::isISOControl)
    }
}

private const val MAX_AGENT_ID_LENGTH = 256
