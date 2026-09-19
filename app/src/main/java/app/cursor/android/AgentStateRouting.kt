package app.cursor.android

/**
 * The selected route owns the visible state even while its agent detail is still loading.
 * Without that explicit ownership, an unrelated background stream can mistake a null detail
 * for an unclaimed screen and write into the conversation currently being opened.
 */
internal fun ownsVisibleAgent(
    requestedAgentId: String,
    openedAgentId: String?,
    loadedAgentId: String?,
): Boolean = when {
    !openedAgentId.isNullOrBlank() -> openedAgentId == requestedAgentId
    else -> loadedAgentId == requestedAgentId
}

internal fun <T> routedAgentState(
    requestedAgentId: String,
    openedAgentId: String?,
    loadedAgentId: String?,
    visibleState: T,
    snapshots: Map<String, T>,
    emptyState: () -> T,
): T = if (ownsVisibleAgent(requestedAgentId, openedAgentId, loadedAgentId)) {
    visibleState
} else {
    snapshots[requestedAgentId] ?: emptyState()
}
