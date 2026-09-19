package app.cursor.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.localDataStore: DataStore<Preferences> by preferencesDataStore("cursor_local")

class LocalStore(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val key = stringPreferencesKey("local_state_json")

    val state: Flow<LocalAppState> = context.localDataStore.data.map { prefs ->
        val raw = prefs[key].orEmpty()
        if (raw.isBlank()) LocalAppState()
        else runCatching { json.decodeFromString<LocalAppState>(raw) }.getOrDefault(LocalAppState())
            .let { loaded ->
                if (loaded.folders.isEmpty()) loaded.copy(folders = defaultFolders()) else loaded
            }
    }

    suspend fun update(transform: (LocalAppState) -> LocalAppState) {
        var savedState: LocalAppState? = null
        context.localDataStore.edit { prefs ->
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<LocalAppState>(it) }.getOrNull()
            } ?: LocalAppState()
            val next = transform(current).withPersistableAttachments { attachment ->
                AttachmentEncoder.persistableCopy(context, attachment)
            }
            prefs[key] = json.encodeToString(next)
            savedState = next
        }
        savedState?.let { state ->
            AttachmentEncoder.pruneUnreferencedBlobs(
                context = context,
                referencedAttachments = buildList {
                    state.composerDrafts.values.forEach { addAll(it.attachments) }
                    state.queuedPrompts.values.flatten().forEach { addAll(it.attachments) }
                },
            )
        }
    }

    suspend fun addFolder(name: String): ChatFolder {
        val folder = ChatFolder(
            id = "folder-${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
        )
        update { state -> state.copy(folders = state.folders + folder) }
        return folder
    }

    suspend fun assignFolder(agentId: String, folderId: String) {
        update { state ->
            state.copy(agentFolderIds = state.agentFolderIds + (agentId to folderId))
        }
    }

    suspend fun snapshot(): LocalAppState = state.first()

    suspend fun upsertActiveRun(run: ActiveRun) {
        if (run.agentId.isBlank() || run.runId.isBlank()) return
        update { state ->
            val rest = state.activeRuns.filterNot {
                it.agentId == run.agentId && it.runId == run.runId
            }
            state.copy(activeRuns = rest + run)
        }
    }

    suspend fun removeActiveRun(agentId: String, runId: String) {
        update { state ->
            state.copy(
                activeRuns = state.activeRuns.filterNot {
                    it.agentId == agentId && it.runId == runId
                },
            )
        }
    }

    suspend fun rememberModel(agentId: String?, modelId: String?) {
        val resolved = modelId?.trim()?.takeIf { it.isNotBlank() }
        update { state ->
            val byAgent = when {
                agentId.isNullOrBlank() -> state.lastModelByAgent
                resolved == null -> state.lastModelByAgent - agentId
                else -> state.lastModelByAgent + (agentId to resolved)
            }
            state.copy(
                lastModelByAgent = byAgent,
                lastGlobalModel = resolved ?: state.lastGlobalModel,
            )
        }
    }

    suspend fun saveComposerDraft(agentId: String, draft: ComposerDraft?) {
        if (agentId.isBlank()) return
        update { state ->
            val next = if (draft == null || draft.isEmpty) {
                state.composerDrafts - agentId
            } else {
                state.composerDrafts + (agentId to draft)
            }
            state.copy(composerDrafts = next)
        }
    }

    suspend fun saveQueue(agentId: String, queue: List<QueuedPrompt>) {
        if (agentId.isBlank()) return
        update { state ->
            val next = if (queue.isEmpty()) {
                state.queuedPrompts - agentId
            } else {
                state.queuedPrompts + (agentId to queue)
            }
            state.copy(queuedPrompts = next)
        }
    }

    suspend fun renameChat(agentId: String, title: String) {
        if (agentId.isBlank()) return
        val trimmed = title.trim()
        update { state ->
            val next = if (trimmed.isEmpty()) {
                state.chatTitles - agentId
            } else {
                state.chatTitles + (agentId to trimmed.take(100))
            }
            state.copy(chatTitles = next)
        }
    }

    suspend fun markDecisionNotified(key: String) {
        if (key.isBlank()) return
        update { state ->
            state.copy(notifiedDecisions = state.notifiedDecisions + key)
        }
    }

    suspend fun markChatRead(agentId: String, at: String) {
        if (agentId.isBlank() || at.isBlank()) return
        update { state ->
            state.copy(lastReadAtByAgent = state.lastReadAtByAgent + (agentId to at))
        }
    }

    suspend fun markChatUnread(agentId: String) {
        if (agentId.isBlank()) return
        update { state ->
            state.copy(lastReadAtByAgent = state.lastReadAtByAgent - agentId)
        }
    }

    suspend fun setPinned(agentId: String, pinned: Boolean) {
        if (agentId.isBlank()) return
        update { state ->
            val next = state.pinnedAgentIds.toMutableList().apply {
                remove(agentId)
                if (pinned) add(0, agentId)
            }
            state.copy(pinnedAgentIds = next)
        }
    }

    suspend fun setChatIcon(agentId: String, iconId: String) {
        if (agentId.isBlank()) return
        val resolved = ChatIconCatalog.resolve(iconId).id
        update { state ->
            state.copy(chatIcons = state.chatIcons + (agentId to resolved))
        }
    }

    suspend fun setMachineAlias(machineId: String, alias: String) {
        if (machineId.isBlank()) return
        val trimmed = alias.trim().take(60)
        update { state ->
            val next = if (trimmed.isEmpty()) {
                state.machineAliases - machineId
            } else {
                state.machineAliases + (machineId to trimmed)
            }
            state.copy(machineAliases = next)
        }
    }

    suspend fun deleteAgents(agentIds: Set<String>) {
        if (agentIds.isEmpty()) return
        update { state -> state.withoutDeletedAgents(agentIds) }
    }

    suspend fun stopTrackingAgents(agentIds: Set<String>) {
        if (agentIds.isEmpty()) return
        update { state -> state.withoutTrackedAgentRuns(agentIds) }
    }
}

internal fun LocalAppState.withoutTrackedAgentRuns(agentIds: Set<String>): LocalAppState {
    if (agentIds.isEmpty()) return this
    return copy(
        activeRuns = activeRuns.filterNot { it.agentId in agentIds },
        queuedPrompts = queuedPrompts - agentIds,
    )
}

internal fun LocalAppState.withoutDeletedAgents(agentIds: Set<String>): LocalAppState {
    if (agentIds.isEmpty()) return this
    return copy(
        agentFolderIds = agentFolderIds - agentIds,
        lastModelByAgent = lastModelByAgent - agentIds,
        queuedPrompts = queuedPrompts - agentIds,
        activeRuns = activeRuns.filterNot { it.agentId in agentIds },
        chatTitles = chatTitles - agentIds,
        chatIcons = chatIcons - agentIds,
        notifiedDecisions = notifiedDecisions.filterNotTo(mutableSetOf()) { key ->
            agentIds.any { id -> key.startsWith("$id:") }
        },
        lastReadAtByAgent = lastReadAtByAgent - agentIds,
        pinnedAgentIds = pinnedAgentIds.filterNot { it in agentIds },
        composerDrafts = composerDrafts - agentIds,
    )
}

internal fun LocalAppState.withPersistableAttachments(
    persistAttachment: (DraftAttachment) -> DraftAttachment,
): LocalAppState = copy(
    composerDrafts = composerDrafts.mapValues { (_, draft) ->
        draft.copy(attachments = draft.attachments.map(persistAttachment))
    },
    queuedPrompts = queuedPrompts.mapValues { (_, queue) ->
        queue.map { prompt ->
            prompt.copy(attachments = prompt.attachments.map(persistAttachment))
        }
    },
)
