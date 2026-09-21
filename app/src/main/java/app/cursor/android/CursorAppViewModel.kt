package app.cursor.android

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cursor.android.data.AUTO_MODEL_ID
import app.cursor.android.data.ActiveRun
import app.cursor.android.data.AccountInfo
import app.cursor.android.data.AgentDetail
import app.cursor.android.data.AgentSummary
import app.cursor.android.data.AgentTodo
import app.cursor.android.data.ApiException
import app.cursor.android.data.ApiKeyCheck
import app.cursor.android.data.apiKeyProbeErrorMessage
import app.cursor.android.data.apiKeyVerifiedMessage
import app.cursor.android.data.AttachmentEncoder
import app.cursor.android.data.AttachmentKind
import app.cursor.android.data.CacheRepository
import app.cursor.android.data.CachedConversation
import app.cursor.android.data.ChangedFile
import app.cursor.android.data.ChatFolder
import app.cursor.android.data.ChatLine
import app.cursor.android.data.ComposerDraft
import app.cursor.android.data.CreateAgentPlan
import app.cursor.android.data.CursorApiClient
import app.cursor.android.data.CursorModel
import app.cursor.android.data.CursorSettings
import app.cursor.android.data.DraftAttachment
import app.cursor.android.data.GitHubRepository
import app.cursor.android.data.InboxChangeStats
import app.cursor.android.data.InboxCategory
import app.cursor.android.data.needsAttention
import app.cursor.android.data.InboxFilter
import app.cursor.android.data.KnownMachine
import app.cursor.android.data.knownMachines
import app.cursor.android.data.LocalStore
import app.cursor.android.data.headline
import app.cursor.android.data.inboxCategory
import app.cursor.android.data.PrivateWorker
import app.cursor.android.data.PromptBody
import app.cursor.android.data.QueuedPrompt
import app.cursor.android.data.RunExtras
import app.cursor.android.data.RunSummary
import app.cursor.android.data.SendMode
import app.cursor.android.data.SettingsRepository
import app.cursor.android.data.StreamEvent
import app.cursor.android.data.autoModel
import app.cursor.android.data.buildPromptText
import app.cursor.android.data.defaultFolders
import app.cursor.android.data.isActiveRun
import app.cursor.android.data.isAutoModelId
import app.cursor.android.data.isFileMutatingTool
import app.cursor.android.data.isTerminalRun
import app.cursor.android.data.isTransientNetworkFailure
import app.cursor.android.data.isTransientStreamLoss
import app.cursor.android.data.isReplayOfPriorTurn
import app.cursor.android.data.mergeConversation
import app.cursor.android.data.mergeStreamText
import app.cursor.android.data.resolveAssistantText
import app.cursor.android.data.toChatLines
import app.cursor.android.data.matchesFilter
import app.cursor.android.data.matchesRef
import app.cursor.android.data.planCreateAgent
import app.cursor.android.data.preferredRepoUrl
import app.cursor.android.data.sanitizeId
import app.cursor.android.data.toPromptImages
import app.cursor.android.data.withAutoFirst
import app.cursor.android.data.withoutResolvedErrors
import app.cursor.android.streaming.RunResumeCoordinator
import app.cursor.android.streaming.StreamingForegroundService
import app.cursor.android.streaming.StreamingNotifications
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val INBOX_CHANGE_STATS_LIMIT = 3
private const val CONVERSATION_EXTRAS_MAX_RUNS = 2

data class InboxUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val agents: List<AgentSummary> = emptyList(),
    val error: String? = null,
    val settings: CursorSettings = CursorSettings(),
    val account: AccountInfo? = null,
    val workers: List<PrivateWorker> = emptyList(),
    val repositories: List<GitHubRepository> = emptyList(),
    val folders: List<ChatFolder> = defaultFolders(),
    val agentFolderIds: Map<String, String> = emptyMap(),
    val filter: InboxFilter = InboxFilter.ALL,
    val folderFilter: String? = null,
    val query: String = "",
    val fromCache: Boolean = false,
    val lastModelByAgent: Map<String, String> = emptyMap(),
    val changeStats: Map<String, InboxChangeStats> = emptyMap(),
    val latestRuns: Map<String, RunSummary> = emptyMap(),
    val chatTitles: Map<String, String> = emptyMap(),
    val chatIcons: Map<String, String> = emptyMap(),
    val renameNotice: String? = null,
    val locallyWorkingIds: Set<String> = emptySet(),
    val lastReadAtByAgent: Map<String, String> = emptyMap(),
    val pinnedAgentIds: List<String> = emptyList(),
    val machineAliases: Map<String, String> = emptyMap(),
    val mutatingAgentIds: Set<String> = emptySet(),
) {
    val machines: List<KnownMachine>
        get() = knownMachines(workers, agents, machineAliases)

    val visibleAgents: List<AgentSummary>
        get() {
            val needle = query.trim()
            return agents.filter { agent ->
                (folderFilter == null || agentFolderIds[agent.id] == folderFilter) &&
                    (
                        needle.isEmpty() ||
                            agent.name.orEmpty().contains(needle, ignoreCase = true) ||
                            chatTitles[agent.id].orEmpty().contains(needle, ignoreCase = true) ||
                            agent.id.contains(needle, ignoreCase = true) ||
                            agent.status.orEmpty().contains(needle, ignoreCase = true) ||
                            agent.repos?.any { it.url.contains(needle, ignoreCase = true) } == true
                        )
            }
        }

    val filteredAgents: List<AgentSummary>
        get() = visibleAgents.filter { agent ->
            agent.matchesFilter(
                filter,
                latestRuns[agent.id],
                locallyWorking = agent.id in locallyWorkingIds,
                lastReadAt = lastReadAtByAgent[agent.id],
            )
        }

    val hasUnreadDone: Boolean
        get() = visibleAgents.any { agent ->
            agent.inboxCategory(
                latestRuns[agent.id],
                locallyWorking = agent.id in locallyWorkingIds,
                lastReadAt = lastReadAtByAgent[agent.id],
            ) == InboxCategory.DONE
        }
}

data class AgentUiState(
    val loading: Boolean = false,
    val agent: AgentDetail? = null,
    val lines: List<ChatLine> = emptyList(),
    val draft: String = "",
    val streaming: Boolean = false,
    val activeRunId: String? = null,
    val error: String? = null,
    val historyLoaded: Boolean = false,
    val extrasLoading: Boolean = false,
    val refreshing: Boolean = false,
    val conversationSyncedAt: String? = null,
    val turnModelId: String = AUTO_MODEL_ID,
    val turnModelParams: List<app.cursor.android.data.ModelParamValue> = emptyList(),
    val attachments: List<DraftAttachment> = emptyList(),
    val queue: List<QueuedPrompt> = emptyList(),
    val workingNote: String? = null,
)

data class NewAgentUiState(
    val prompt: String = "",
    val repoUrl: String = "",
    val startingRef: String = "main",
    val machineName: String = "",
    val envType: String = "machine",
    val modelId: String = AUTO_MODEL_ID,
    val modelParams: List<app.cursor.android.data.ModelParamValue> = emptyList(),
    val submitting: Boolean = false,
    val error: String? = null,
    val attachments: List<DraftAttachment> = emptyList(),
)

data class ModelsUiState(
    val loading: Boolean = false,
    val models: List<CursorModel> = emptyList(),
    val error: String? = null,
)

class CursorAppViewModel(app: Application) : AndroidViewModel(app) {
    private val settingsRepo = SettingsRepository(app)
    private val cacheRepo = CacheRepository(app)
    private val localStore = LocalStore(app)
    private var apiKeyCache = ""
    private val api = CursorApiClient { apiKeyCache }
    private val idSeq = AtomicLong(0)
    private var reposFetched = false

    private val _inbox = MutableStateFlow(InboxUiState())
    val inbox: StateFlow<InboxUiState> = _inbox.asStateFlow()

    private val _agent = MutableStateFlow(AgentUiState())
    val agent: StateFlow<AgentUiState> = _agent.asStateFlow()

    private val _newAgent = MutableStateFlow(NewAgentUiState())
    val newAgent: StateFlow<NewAgentUiState> = _newAgent.asStateFlow()

    private val _models = MutableStateFlow(ModelsUiState())
    val models: StateFlow<ModelsUiState> = _models.asStateFlow()

    /** One-shot, user-facing failures (network, API) that screens surface in a snackbar. */
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** One-shot confirmations, such as a verified API key. */
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private val _apiKeyCheck = MutableStateFlow<ApiKeyCheck>(ApiKeyCheck.Idle)
    val apiKeyCheck: StateFlow<ApiKeyCheck> = _apiKeyCheck.asStateFlow()

    private fun reportError(e: Exception, context: String, notify: Boolean = true) {
        if (e is CancellationException) return
        if (!notify) return
        if (e.isTransientNetworkFailure() && _inbox.value.agents.isNotEmpty()) return
        _errors.tryEmit("$context: ${e.userMessage()}")
    }

    fun setMachineAlias(machineId: String, alias: String) {
        viewModelScope.launch { localStore.setMachineAlias(machineId, alias) }
    }

    private val streamJobs = mutableMapOf<String, Job>()
    private val agentSnapshots = mutableMapOf<String, AgentUiState>()
    private val composerDrafts = ConcurrentHashMap<String, ComposerDraft>()
    private var openedAgentId: String? = null
    private var openJob: Job? = null
    private var openWatchJob: Job? = null
    private var openWatchFingerprint: String? = null
    private var inboxRefreshJob: Job? = null
    private var didResumeRuns = false
    private var lastNetworkApiKey: String? = null

    init {
        viewModelScope.launch {
            settingsRepo.migrateLegacyApiKey()
            settingsRepo.migrateDefaultFontWeight()
        }
        viewModelScope.launch { hydrateFromCache() }
        viewModelScope.launch {
            localStore.state.collect { local ->
                local.composerDrafts.forEach { (id, draft) ->
                    composerDrafts.putIfAbsent(id, draft)
                }
                _inbox.update {
                    it.copy(
                        folders = local.folders.ifEmpty { defaultFolders() },
                        agentFolderIds = local.agentFolderIds,
                        lastModelByAgent = local.lastModelByAgent,
                        chatTitles = local.chatTitles,
                        chatIcons = local.chatIcons,
                        lastReadAtByAgent = local.lastReadAtByAgent,
                        pinnedAgentIds = local.pinnedAgentIds,
                        machineAliases = local.machineAliases,
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepo.settings.collect { settings ->
                apiKeyCache = settings.apiKey
                val previousDefault = _inbox.value.settings.modelId
                _inbox.update { it.copy(settings = settings) }
                _newAgent.update { form ->
                    val envType = form.envType.ifBlank { settings.envType.ifBlank { "machine" } }
                    form.copy(
                        repoUrl = form.repoUrl.ifBlank {
                            preferredRepoUrl(
                                repositories = _inbox.value.repositories,
                                defaultUrl = settings.defaultRepoUrl,
                                recentRepoUrls = _inbox.value.agents.mapNotNull {
                                    it.repos?.firstOrNull()?.url
                                },
                            )
                        },
                        machineName = form.machineName.ifBlank {
                            settings.machineName.ifBlank {
                                _inbox.value.workers.firstOrNull()?.displayName().orEmpty()
                            }
                        },
                        envType = envType,
                        modelId = if (form.modelId == previousDefault) settings.modelId else form.modelId,
                    )
                }
                applyDefaultModel(settings.modelId, previousDefault)
                val keyChanged = lastNetworkApiKey != settings.apiKey
                lastNetworkApiKey = settings.apiKey
                if (settings.apiKey.isNotBlank() && keyChanged) {
                    refreshInbox()
                    refreshModels()
                    refreshAccountAndWorkers()
                    if (!didResumeRuns) {
                        didResumeRuns = true
                        resumePersistedRuns()
                    }
                }
            }
        }
    }

    private fun resumePersistedRuns() {
        viewModelScope.launch {
            val runs = runCatching { localStore.snapshot().activeRuns }.getOrDefault(emptyList())
            for (run in runs) {
                followPersistedRun(run)
            }
        }
    }

    private suspend fun followPersistedRun(run: ActiveRun) {
        if (streamJobs[run.agentId]?.isActive == true) return
        try {
            ensureApiKey()
            val summary = runCatching { api.getRun(run.agentId, run.runId) }.getOrNull()
            when {
                summary == null || summary.status.isActiveRun() -> {
                    consumeStream(run.agentId, run.runId, appendOnly = true)
                }
                summary.status.isTerminalRun() -> {
                    runCatching { hydrateConversation(run.agentId) }
                    val preview = summary.result?.takeIf { it.isNotBlank() }
                        ?: agentState(run.agentId).lines
                            .lastOrNull { it.kind == ChatLine.Kind.ASSISTANT }
                            ?.text
                            .orEmpty()
                    RunResumeCoordinator.announceFinished(
                        context = getApplication(),
                        run = run,
                        preview = preview.ifBlank { "${run.title.ifBlank { "Agent" }} finished" },
                        status = summary.status,
                        recovered = true,
                    )
                }
            }
        } catch (_: Exception) {
            // Keep the persisted row; WorkManager / next launch will retry.
        }
    }

    private suspend fun hydrateFromCache() {
        val cachedAgents = runCatching { cacheRepo.loadAgents() }.getOrDefault(emptyList())
        val cachedModels = runCatching { cacheRepo.loadModels() }.getOrDefault(emptyList())
        val cachedAccount = runCatching { cacheRepo.loadAccount() }.getOrNull()
        val cachedWorkers = runCatching { cacheRepo.loadWorkers() }.getOrDefault(emptyList())
        val cachedRepos = runCatching { cacheRepo.loadRepositories() }.getOrDefault(emptyList())
        val cachedChangeStats = runCatching { cacheRepo.loadInboxChangeStats() }
            .getOrDefault(emptyMap())
        if (cachedAgents.isNotEmpty() || cachedChangeStats.isNotEmpty()) {
            _inbox.update {
                it.copy(
                    agents = cachedAgents.ifEmpty { it.agents },
                    fromCache = cachedAgents.isNotEmpty(),
                    loading = false,
                    error = if (cachedAgents.isNotEmpty()) null else it.error,
                    changeStats = cachedChangeStats.ifEmpty { it.changeStats },
                )
            }
        }
        if (cachedModels.isNotEmpty()) {
            _models.update { it.copy(models = cachedModels.withAutoFirst()) }
        }
        if (cachedAccount != null || cachedWorkers.isNotEmpty() || cachedRepos.isNotEmpty()) {
            _inbox.update {
                it.copy(
                    account = cachedAccount ?: it.account,
                    workers = cachedWorkers.ifEmpty { it.workers },
                    repositories = cachedRepos.ifEmpty { it.repositories },
                )
            }
        }
    }

    fun refreshInbox(silent: Boolean = false) {
        if (silent && inboxRefreshJob?.isActive == true) return
        inboxRefreshJob = viewModelScope.launch {
            val hasRows = _inbox.value.agents.isNotEmpty()
            _inbox.update {
                it.copy(
                    loading = !hasRows && !silent,
                    refreshing = hasRows && !silent,
                    error = null,
                )
            }
            try {
                ensureApiKey()
                val response = api.listAgents()
                val knownIds = response.items.map { it.id }.toSet()
                _inbox.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        agents = response.items,
                        fromCache = false,
                        latestRuns = it.latestRuns.filterKeys { id -> id in knownIds },
                        changeStats = (
                            runCatching { cacheRepo.loadInboxChangeStats() }
                                .getOrDefault(emptyMap()) + it.changeStats
                            ).filterKeys { id -> id in knownIds },
                    )
                }
                runCatching { cacheRepo.saveAgents(response.items) }
                refreshLatestRuns(response.items)
                maybeNotifyDecisions(response.items)
            } catch (e: Exception) {
                reportError(e, "couldn’t refresh the inbox", notify = !silent)
                _inbox.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.agents.isEmpty()) e.userMessage() else null,
                    )
                }
            }
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            _models.update { it.copy(loading = true, error = null) }
            try {
                ensureApiKey()
                val items = api.listModels()
                _models.update { it.copy(loading = false, models = items.withAutoFirst()) }
                runCatching { cacheRepo.saveModels(items) }
            } catch (e: Exception) {
                _models.update {
                    it.copy(
                        loading = false,
                        error = if (it.models.isEmpty()) e.userMessage() else null,
                    )
                }
            }
        }
    }

    fun refreshAccountAndWorkers() {
        viewModelScope.launch {
            try {
                ensureApiKey()
                val account = runCatching { api.getMe() }.getOrNull()
                val workers = runCatching { api.listWorkers() }.getOrDefault(emptyList())
                _inbox.update {
                    it.copy(
                        account = account ?: it.account,
                        workers = workers.ifEmpty { it.workers },
                    )
                }
                account?.let { runCatching { cacheRepo.saveAccount(it) } }
                if (workers.isNotEmpty()) runCatching { cacheRepo.saveWorkers(workers) }
                _newAgent.update { form ->
                    if (form.machineName.isNotBlank()) form
                    else form.copy(
                        machineName = workers.firstOrNull()?.displayName()
                            ?: _inbox.value.settings.machineName,
                    )
                }
            } catch (_: Exception) {
                // Cached account/workers stay visible.
            }
        }
    }

    fun ensureRepositories() {
        if (reposFetched && _inbox.value.repositories.isNotEmpty()) return
        viewModelScope.launch {
            try {
                ensureApiKey()
                val items = api.listRepositories()
                reposFetched = true
                _inbox.update { it.copy(repositories = items) }
                runCatching { cacheRepo.saveRepositories(items) }
            } catch (_: Exception) {
                reposFetched = true
            }
        }
    }

    private fun defaultModelId(): String =
        _inbox.value.settings.modelId.ifBlank { AUTO_MODEL_ID }

    private fun applyDefaultModel(modelId: String, previousDefault: String) {
        val id = modelId.ifBlank { AUTO_MODEL_ID }
        if (id == previousDefault) return
        _newAgent.update { form ->
            if (form.modelId == previousDefault) form.copy(modelId = id) else form
        }
        val openId = visibleAgentId()
        val hasOverride = !openId.isNullOrBlank() &&
            !_inbox.value.lastModelByAgent[openId].isNullOrBlank()
        if (!hasOverride) {
            _agent.update {
                it.copy(turnModelId = id, turnModelParams = emptyList())
            }
        }
    }

    fun updateAppearance(
        themeMode: String? = null,
        dynamicColor: Boolean? = null,
        spinnerStyle: String? = null,
        textScale: String? = null,
        fontWeight: String? = null,
        haptics: Boolean? = null,
        showDiff: Boolean? = null,
        showRuntime: Boolean? = null,
        showUpdated: Boolean? = null,
    ) {
        val current = _inbox.value.settings
        val next = current.copy(
            themeMode = themeMode ?: current.themeMode,
            dynamicColor = dynamicColor ?: current.dynamicColor,
            spinnerStyle = spinnerStyle ?: current.spinnerStyle,
            textScale = textScale ?: current.textScale,
            fontWeight = fontWeight ?: current.fontWeight,
            haptics = haptics ?: current.haptics,
            showDiff = showDiff ?: current.showDiff,
            showRuntime = showRuntime ?: current.showRuntime,
            showUpdated = showUpdated ?: current.showUpdated,
        )
        _inbox.update { it.copy(settings = next) }
        viewModelScope.launch { settingsRepo.save(next) }
    }

    /**
     * Tests the key with GET /v1/me, then stores it only if Cursor accepts it.
     * A rejected key does not overwrite a working one.
     */
    fun saveAndVerifyApiKey(apiKey: String) {
        viewModelScope.launch {
            val key = apiKey.trim()
            if (!_inbox.value.settings.secureStorageAvailable) {
                val message = "secure storage isn't available. restart pointer and try again"
                _apiKeyCheck.value = ApiKeyCheck.Failed(message)
                _notices.tryEmit(message)
                return@launch
            }
            if (key.isBlank()) {
                val message = "paste an api key first"
                _apiKeyCheck.value = ApiKeyCheck.Failed(message)
                _notices.tryEmit(message)
                return@launch
            }
            _apiKeyCheck.value = ApiKeyCheck.Checking
            try {
                val account = CursorApiClient { key }.getMe()
                val next = _inbox.value.settings.copy(apiKey = key)
                settingsRepo.saveVerifiedApiKey(next)
                apiKeyCache = key
                _inbox.update { it.copy(settings = next, account = account, error = null) }
                _apiKeyCheck.value = ApiKeyCheck.Verified(account.displayName())
                _notices.tryEmit(apiKeyVerifiedMessage(account))
                reposFetched = false
                refreshModels()
                refreshAccountAndWorkers()
                refreshInbox()
                ensureRepositories()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = apiKeyProbeErrorMessage(e)
                _apiKeyCheck.value = ApiKeyCheck.Failed(message)
                _notices.tryEmit(message)
            }
        }
    }

    fun resetApiKeyCheck() {
        if (_apiKeyCheck.value !is ApiKeyCheck.Checking) {
            _apiKeyCheck.value = ApiKeyCheck.Idle
        }
    }

    /**
     * Connection-level settings apply as soon as a field commits. API keys go through
     * [saveAndVerifyApiKey] so a bad paste cannot overwrite a working key.
     */
    fun updateConnection(
        machineName: String? = null,
        defaultRepoUrl: String? = null,
        envType: String? = null,
        modelId: String? = null,
    ) {
        val current = _inbox.value.settings
        val next = current.copy(
            machineName = machineName?.trim() ?: current.machineName,
            defaultRepoUrl = defaultRepoUrl?.trim() ?: current.defaultRepoUrl,
            envType = envType?.ifBlank { current.envType } ?: current.envType,
            modelId = modelId?.ifBlank { AUTO_MODEL_ID } ?: current.modelId,
        )
        if (next == current) return
        val modelChanged = next.modelId != current.modelId
        _inbox.update { it.copy(settings = next) }
        viewModelScope.launch { settingsRepo.save(next) }
        if (modelChanged) applyDefaultModel(next.modelId, previousDefault = current.modelId)
    }

    fun selectTurnModel(model: CursorModel, params: List<app.cursor.android.data.ModelParamValue> = emptyList()) {
        _agent.update {
            it.copy(
                turnModelId = model.id,
                turnModelParams = if (model.isAuto()) emptyList() else params.ifEmpty {
                    model.defaultSelection().params.orEmpty()
                },
            )
        }
        val agentId = _agent.value.agent?.id
        viewModelScope.launch {
            if (agentId.isNullOrBlank()) return@launch
            if (model.id == defaultModelId()) {
                localStore.rememberModel(agentId, null)
            } else {
                localStore.rememberModel(agentId, model.id)
            }
        }
    }

    fun selectNewAgentModel(model: CursorModel, params: List<app.cursor.android.data.ModelParamValue> = emptyList()) {
        _newAgent.update {
            it.copy(
                modelId = model.id,
                modelParams = if (model.isAuto()) emptyList() else params.ifEmpty {
                    model.defaultSelection().params.orEmpty()
                },
            )
        }
    }

    fun setInboxFilter(filter: InboxFilter) {
        _inbox.update { it.copy(filter = filter) }
    }

    fun onInboxResumed() {
        publishOpenAgentToInbox()
        if (apiKeyCache.isNotBlank()) refreshInbox(silent = true)
    }

    private fun publishOpenAgentToInbox() {
        val chat = _agent.value
        val detail = chat.agent ?: return
        val summary = detail.toSummary().let { row ->
            if (chat.streaming) row.copy(status = "RUNNING") else row
        }
        _inbox.update { inbox ->
            val agents = inbox.agents.toMutableList()
            val index = agents.indexOfFirst { it.id == summary.id }
            if (index >= 0) {
                val current = agents[index]
                agents[index] = current.copy(
                    name = summary.name ?: current.name,
                    status = summary.status ?: current.status,
                    updatedAt = summary.updatedAt ?: current.updatedAt,
                    latestRunId = summary.latestRunId ?: current.latestRunId,
                    repos = summary.repos ?: current.repos,
                )
            } else {
                agents.add(0, summary)
            }
            inbox.copy(
                agents = agents,
                locallyWorkingIds = if (chat.streaming) {
                    inbox.locallyWorkingIds + summary.id
                } else {
                    inbox.locallyWorkingIds - summary.id
                },
            )
        }
    }

    private fun setLocallyWorking(agentId: String, working: Boolean, status: String? = null) {
        val now = java.time.Instant.now().toString()
        _inbox.update { state ->
            val ids = if (working) {
                state.locallyWorkingIds + agentId
            } else {
                state.locallyWorkingIds - agentId
            }
            state.copy(
                locallyWorkingIds = ids,
                agents = state.agents.map { agent ->
                    if (agent.id != agentId) {
                        agent
                    } else {
                        agent.copy(
                            status = when {
                                working -> "RUNNING"
                                !status.isNullOrBlank() -> status
                                else -> agent.status
                            },
                            updatedAt = now,
                        )
                    }
                },
            )
        }
        if (!working && isVisibleAgent(agentId)) {
            markChatRead(agentId)
        }
    }

    private fun markChatRead(agentId: String) {
        if (agentId.isBlank()) return
        val at = java.time.Instant.now().toString()
        _inbox.update { state ->
            state.copy(lastReadAtByAgent = state.lastReadAtByAgent + (agentId to at))
        }
        viewModelScope.launch { localStore.markChatRead(agentId, at) }
    }

    fun setChatRead(agentId: String, read: Boolean) {
        if (agentId.isBlank()) return
        if (read) {
            markChatRead(agentId)
            return
        }
        _inbox.update { state ->
            state.copy(lastReadAtByAgent = state.lastReadAtByAgent - agentId)
        }
        viewModelScope.launch { localStore.markChatUnread(agentId) }
    }

    fun markChatsRead(agentIds: Set<String>) {
        val validIds = agentIds.filterTo(linkedSetOf()) { it.isNotBlank() }
        if (validIds.isEmpty()) return
        val at = java.time.Instant.now().toString()
        _inbox.update { state ->
            state.copy(
                lastReadAtByAgent = state.lastReadAtByAgent +
                    validIds.associateWith { at },
            )
        }
        viewModelScope.launch { localStore.markChatsRead(validIds, at) }
    }

    fun setPinned(agentId: String, pinned: Boolean) {
        if (agentId.isBlank()) return
        _inbox.update { state ->
            val next = state.pinnedAgentIds.toMutableList().apply {
                remove(agentId)
                if (pinned) add(0, agentId)
            }
            state.copy(pinnedAgentIds = next)
        }
        viewModelScope.launch { localStore.setPinned(agentId, pinned) }
    }

    private suspend fun maybeNotifyDecisions(agents: List<AgentSummary>) {
        val inbox = _inbox.value
        val already = localStore.snapshot().notifiedDecisions
        for (agent in agents) {
            if (agent.inboxCategory(
                    inbox.latestRuns[agent.id],
                    locallyWorking = agent.id in inbox.locallyWorkingIds,
                    lastReadAt = inbox.lastReadAtByAgent[agent.id],
                ) != InboxCategory.DONE ||
                !agent.needsAttention()
            ) continue
            val key = "${agent.id}:${agent.latestRunId.orEmpty()}:${agent.status.orEmpty()}"
            if (key in already) continue
            val question = runCatching {
                api.getConversation(agent.id).messages
                    .lastOrNull { it.type == "assistant_message" }
                    ?.text
            }.getOrNull().orEmpty().ifBlank { "This chat is waiting for you." }
            val name = agent.headline(inbox.workers, inbox.chatTitles)
            StreamingNotifications.notifyDecision(
                context = getApplication(),
                chatName = name,
                question = question,
                agentId = agent.id,
            )
            localStore.markDecisionNotified(key)
        }
    }

    private suspend fun refreshLatestRuns(agents: List<AgentSummary>) {
        if (agents.isEmpty()) {
            _inbox.update { it.copy(latestRuns = emptyMap()) }
            return
        }
        coroutineScope {
            agents.mapIndexed { index, agent ->
                async {
                    val run = runCatching {
                        val runId = agent.latestRunId
                        if (!runId.isNullOrBlank()) api.getRun(agent.id, runId)
                        else api.listRuns(agent.id, limit = 1).items.firstOrNull()
                    }.getOrNull() ?: runCatching {
                        api.listRuns(agent.id, limit = 1).items.firstOrNull()
                    }.getOrNull()
                    if (run != null) {
                        _inbox.update { it.copy(latestRuns = it.latestRuns + (agent.id to run)) }
                    }
                    val alreadyCached = _inbox.value.changeStats[agent.id] != null
                    if (!alreadyCached && index < INBOX_CHANGE_STATS_LIMIT) {
                        val stats = when {
                            run != null -> runCatching {
                                api.extractRunExtras(agent.id, run.id).toInboxChangeStats()
                            }.getOrNull()
                            else -> runCatching {
                                api.fetchLatestRunChangeStats(agent.id)
                            }.getOrNull()
                        }
                        if (stats != null) {
                            _inbox.update {
                                it.copy(changeStats = it.changeStats + (agent.id to stats))
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        runCatching { cacheRepo.saveInboxChangeStats(_inbox.value.changeStats) }
        if (openWatchJob?.isActive == true) {
            visibleAgentId()?.let { syncRemoteActivity(it) }
        }
    }

    fun setFolderFilter(folderId: String?) {
        _inbox.update { it.copy(folderFilter = folderId) }
    }

    fun setInboxQuery(query: String) {
        _inbox.update { it.copy(query = query) }
    }

    fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { localStore.addFolder(trimmed) }
    }

    fun assignAgentFolder(agentId: String, folderId: String) {
        viewModelScope.launch { localStore.assignFolder(agentId, folderId) }
    }

    fun clearRenameNotice() {
        _inbox.update { it.copy(renameNotice = null) }
    }

    fun setChatIcon(agentId: String, iconId: String) {
        if (agentId.isBlank()) return
        viewModelScope.launch { localStore.setChatIcon(agentId, iconId) }
    }

    fun renameChat(agentId: String, title: String) {
        val trimmed = title.trim().take(100)
        if (agentId.isBlank() || trimmed.isBlank()) return
        viewModelScope.launch {
            localStore.renameChat(agentId, trimmed)
            applyChatTitle(agentId, trimmed)
            try {
                ensureApiKey()
                val updated = api.renameAgent(agentId, trimmed)
                val name = updated?.name?.ifBlank { null } ?: trimmed
                applyChatTitle(agentId, name)
                _inbox.update { it.copy(renameNotice = null) }
            } catch (_: Exception) {
                _inbox.update {
                    it.copy(
                        renameNotice = "Saved on this phone. Other clients may still show the original title.",
                    )
                }
            }
        }
    }

    fun archiveChats(agentIds: Set<String>) {
        applyAgentLifecycleAction(agentIds, AgentLifecycleAction.ARCHIVE)
    }

    fun deleteChatsPermanently(agentIds: Set<String>) {
        applyAgentLifecycleAction(agentIds, AgentLifecycleAction.DELETE)
    }

    private fun applyAgentLifecycleAction(
        requestedIds: Set<String>,
        action: AgentLifecycleAction,
    ) {
        val inbox = _inbox.value
        val knownIds = inbox.agents.mapTo(mutableSetOf()) { it.id }
        val targets = requestedIds.filterTo(linkedSetOf()) {
            it in knownIds && it !in inbox.mutatingAgentIds
        }
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _inbox.update { it.copy(mutatingAgentIds = it.mutatingAgentIds + targets) }
            val succeeded = linkedSetOf<String>()
            val failures = linkedMapOf<String, String>()
            try {
                ensureApiKey()
                for (agentId in targets) {
                    try {
                        when (action) {
                            AgentLifecycleAction.ARCHIVE -> api.archiveAgent(agentId)
                            AgentLifecycleAction.DELETE -> api.deleteAgent(agentId)
                        }
                        succeeded += agentId
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures[agentId] = e.userMessage()
                    }
                }

                if (succeeded.isNotEmpty()) {
                    succeeded.forEach { agentId ->
                        streamJobs.remove(agentId)?.cancel()
                        agentSnapshots.remove(agentId)
                        composerDrafts.remove(agentId)
                    }
                    if (visibleAgentId() in succeeded) {
                        openedAgentId = null
                        _agent.value = AgentUiState()
                    }
                    runCatching {
                        when (action) {
                            AgentLifecycleAction.ARCHIVE -> localStore.stopTrackingAgents(succeeded)
                            AgentLifecycleAction.DELETE -> {
                                localStore.deleteAgents(succeeded)
                                cacheRepo.deleteConversations(succeeded)
                            }
                        }
                    }
                    _inbox.update { state ->
                        state.withoutAgents(succeeded).copy(
                            mutatingAgentIds = state.mutatingAgentIds - targets,
                        )
                    }
                    runCatching { cacheRepo.saveAgents(_inbox.value.agents) }
                    syncForegroundService()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                targets.filterNot { it in succeeded }.forEach { failures[it] = e.userMessage() }
            } finally {
                _inbox.update { it.copy(mutatingAgentIds = it.mutatingAgentIds - targets) }
            }

            if (failures.isNotEmpty()) {
                val verb = if (action == AgentLifecycleAction.ARCHIVE) "archive" else "delete"
                val detail = failures.values.firstOrNull().orEmpty()
                _errors.tryEmit(
                    "couldn’t $verb ${failures.size} ${if (failures.size == 1) "chat" else "chats"}: $detail",
                )
            }
        }
    }

    private fun applyChatTitle(agentId: String, title: String) {
        _inbox.update { state ->
            state.copy(
                agents = state.agents.map { agent ->
                    if (agent.id == agentId) agent.copy(name = title) else agent
                },
            )
        }
        mutateAgent(agentId) { state ->
            val agent = state.agent ?: return@mutateAgent state
            state.copy(agent = agent.copy(name = title))
        }
        viewModelScope.launch {
            runCatching { cacheRepo.saveAgents(_inbox.value.agents) }
        }
    }

    fun updateNewAgent(transform: (NewAgentUiState) -> NewAgentUiState) {
        _newAgent.update(transform)
    }

    fun openAgent(agentId: String) {
        markChatRead(agentId)
        val previousId = visibleAgentId()
        rememberComposer(previousId ?: openedAgentId ?: agentId.takeIf { hasComposerInput() })
        if (previousId != null && previousId != agentId) {
            agentSnapshots[previousId] = _agent.value
            persistConversation(previousId, _agent.value.lines)
            persistComposerAsync(previousId)
        }
        openedAgentId = agentId
        val snapshot = agentSnapshots.remove(agentId)
        val liveStream = streamJobs[agentId]?.isActive == true ||
            snapshot?.streaming == true ||
            (previousId == agentId && _agent.value.streaming)
        if (previousId == agentId) {
            applyComposer(agentId)
            mutateAgent(agentId) { it.copy(refreshing = true) }
            openJob?.cancel()
            openJob = viewModelScope.launch {
                refreshOpenConversation(agentId, replaceLines = !liveStream)
            }
            return
        }
        if (snapshot != null && liveStream) {
            _agent.value = snapshot.copy(refreshing = true)
            applyComposer(agentId)
            openJob?.cancel()
            openJob = viewModelScope.launch {
                refreshOpenConversation(agentId, replaceLines = false)
            }
            return
        }

        openJob?.cancel()
        val lastStored = _inbox.value.lastModelByAgent[agentId]?.takeIf { it.isNotBlank() }
        val lastModel = lastStored ?: defaultModelId()
        val currentIfSame = _agent.value.takeIf { previousId == agentId }
        val seed = snapshot?.takeIf { it.lines.isNotEmpty() || it.historyLoaded }
            ?: currentIfSame?.takeIf { it.lines.isNotEmpty() || it.historyLoaded }
        val keepQueue = snapshot?.queue.orEmpty().ifEmpty { seed?.queue.orEmpty() }
        val composer = storedComposer(agentId, snapshot ?: currentIfSame)
        _agent.value = AgentUiState(
            loading = seed == null,
            refreshing = true,
            extrasLoading = false,
            agent = seed?.agent,
            lines = seed?.lines.orEmpty(),
            draft = composer.text,
            attachments = composer.attachments,
            historyLoaded = seed?.historyLoaded == true,
            conversationSyncedAt = seed?.conversationSyncedAt,
            turnModelId = seed?.turnModelId ?: lastModel,
            turnModelParams = seed?.turnModelParams.orEmpty(),
            queue = keepQueue,
            streaming = seed?.streaming == true,
            activeRunId = seed?.activeRunId,
            workingNote = seed?.workingNote,
        )
        openJob = viewModelScope.launch {
            try {
                ensureApiKey()
                val disk = runCatching { cacheRepo.loadConversation(agentId) }
                    .getOrDefault(CachedConversation())
                if (disk.lines.isNotEmpty() || disk.syncedAt != null) {
                    mutateAgent(agentId) {
                        it.copy(
                            loading = false,
                            refreshing = true,
                            lines = if (it.lines.isEmpty() && disk.lines.isNotEmpty()) {
                                disk.lines.withQueued(keepQueue)
                            } else {
                                it.lines
                            },
                            historyLoaded = it.historyLoaded || disk.lines.isNotEmpty(),
                            extrasLoading = false,
                            conversationSyncedAt = it.conversationSyncedAt ?: disk.syncedAt,
                        )
                    }
                }
                val persistedQueue = localStore.state.first().queuedPrompts[agentId].orEmpty()
                val queue = keepQueue.ifEmpty { persistedQueue }
                val previousLines = agentState(agentId).lines
                val detail = api.getAgent(agentId)
                val history = runCatching { api.getConversation(agentId).messages }
                    .getOrDefault(emptyList())
                val historyLines = history.toChatLines().preservingExtrasFrom(previousLines)
                val runs = runCatching { api.listRuns(agentId, limit = 5).items }
                    .getOrDefault(emptyList())
                val turnModelId = resolveTurnModel(lastStored)
                val streamingNow = streamJobs[agentId]?.isActive == true
                val merged = mergeConversation(
                    history = historyLines,
                    local = previousLines,
                    streaming = streamingNow,
                ).withQueued(queue).ifEmpty {
                    listOf(
                        ChatLine(
                            id = nextId("status"),
                            kind = ChatLine.Kind.STATUS,
                            text = "No prior messages yet — send the first follow-up.",
                        ),
                    )
                }
                mutateAgent(agentId) {
                    val kept = storedComposer(agentId, it)
                    it.copy(
                        loading = false,
                        refreshing = false,
                        agent = detail,
                        lines = merged,
                        historyLoaded = true,
                        extrasLoading = false,
                        turnModelId = turnModelId,
                        queue = queue,
                        draft = kept.text,
                        attachments = kept.attachments,
                        error = null,
                    )
                }
                persistConversation(agentId, agentState(agentId).lines)

                val latestRunId = detail.latestRunId
                if (!latestRunId.isNullOrBlank() && streamJobs[agentId]?.isActive != true) {
                    val active = runs.firstOrNull {
                        it.id == latestRunId && it.status.isActiveRun()
                    } ?: runs.firstOrNull { it.status.isActiveRun() }
                    if (active != null) {
                        consumeStream(agentId, active.id, appendOnly = true)
                    }
                }
            } catch (e: Exception) {
                val hasLines = agentState(agentId).lines.isNotEmpty()
                reportError(
                    e,
                    "couldn’t load the conversation",
                    notify = !hasLines || !e.isTransientNetworkFailure(),
                )
                mutateAgent(agentId) {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        extrasLoading = false,
                        error = if (it.lines.isEmpty()) e.userMessage() else null,
                    )
                }
            }
        }
    }

    private suspend fun refreshOpenConversation(agentId: String, replaceLines: Boolean) {
        try {
            ensureApiKey()
            if (agentState(agentId).conversationSyncedAt == null) {
                val disk = runCatching { cacheRepo.loadConversation(agentId) }
                    .getOrDefault(CachedConversation())
                if (disk.syncedAt != null) {
                    mutateAgent(agentId) {
                        it.copy(conversationSyncedAt = disk.syncedAt, refreshing = true)
                    }
                }
            }
            val detail = api.getAgent(agentId)
            val live = streamJobs[agentId]?.isActive == true || agentState(agentId).streaming
            if (!replaceLines || live) {
                mutateAgent(agentId) {
                    it.copy(
                        agent = detail,
                        loading = false,
                        refreshing = false,
                        error = null,
                    )
                }
                return
            }
            val previous = agentState(agentId).lines
            val history = runCatching { api.getConversation(agentId).messages.toChatLines() }
                .getOrDefault(emptyList())
                .preservingExtrasFrom(previous)
            mutateAgent(agentId) {
                it.copy(
                    agent = detail,
                    loading = false,
                    refreshing = false,
                    lines = mergeConversation(history, previous, streaming = false)
                        .ifEmpty { previous },
                    historyLoaded = true,
                    error = null,
                )
            }
            persistConversation(agentId, agentState(agentId).lines)
        } catch (e: Exception) {
            mutateAgent(agentId) {
                it.copy(
                    loading = false,
                    refreshing = false,
                    error = if (it.lines.isEmpty()) e.userMessage() else null,
                )
            }
        }
    }

    fun startWatchingOpenAgent(agentId: String) {
        if (agentId.isBlank()) return
        openWatchJob?.cancel()
        openWatchFingerprint = null
        openWatchJob = viewModelScope.launch {
            delay(1_200)
            agentState(agentId).agent?.let { openWatchFingerprint = agentFingerprint(it) }
            while (isActive) {
                if (isVisibleAgent(agentId)) {
                    syncRemoteActivity(agentId)
                }
                val state = agentState(agentId)
                val streaming = streamJobs[agentId]?.isActive == true || state.streaming
                delay(ApiPoll.watchDelayMs(streaming, state.agent?.status))
            }
        }
    }

    fun stopWatchingOpenAgent(agentId: String) {
        if (!isVisibleAgent(agentId)) return
        openWatchJob?.cancel()
        openWatchJob = null
    }

    fun leaveAgent(agentId: String) {
        rememberComposer(agentId)
        stopWatchingOpenAgent(agentId)
        persistComposerAsync(agentId)
    }

    fun onAgentScreenResumed(agentId: String) {
        if (agentId.isBlank()) return
        viewModelScope.launch { syncRemoteActivity(agentId) }
    }

    private fun agentFingerprint(detail: AgentDetail): String =
        "${detail.status.orEmpty()}|${detail.updatedAt.orEmpty()}|${detail.latestRunId.orEmpty()}"

    private suspend fun syncRemoteActivity(agentId: String) {
        if (agentId.isBlank()) return
        if (openJob?.isActive == true) return
        val visible = isVisibleAgent(agentId)
        if (!visible) return
        try {
            ensureApiKey()
            val liveStream = streamJobs[agentId]?.isActive == true
            val pendingLocalSend = agentState(agentId).streaming &&
                agentState(agentId).lines.lastOrNull {
                    it.kind == ChatLine.Kind.ASSISTANT
                }?.let { last -> last.text.isBlank() } == true

            if (liveStream) {
                val runs = runCatching { api.listRuns(agentId, limit = 5).items }
                    .getOrDefault(emptyList())
                val active = runs.firstOrNull { it.status.isActiveRun() }
                val alreadyThisRun = active != null &&
                    agentState(agentId).activeRunId == active.id
                if (active != null && !alreadyThisRun) {
                    attachRemoteRun(agentId, active.id)
                }
                return
            }

            val detail = runCatching { api.getAgent(agentId) }.getOrNull()
            if (detail != null) {
                mutateAgent(agentId) { it.copy(agent = detail, error = null) }
                publishRemoteStatus(detail)
            }
            val fingerprint = detail?.let { agentFingerprint(it) }
            val fingerprintChanged = fingerprint != openWatchFingerprint
            if (fingerprint != null) openWatchFingerprint = fingerprint
            val agentActive = detail?.status.isActiveRun() == true
            val fetchFull = ApiPoll.shouldFetchConversation(
                streaming = false,
                agentActive = agentActive,
                fingerprintChanged = fingerprintChanged || fingerprint == null,
            )
            if (!fetchFull) return

            val runs = runCatching { api.listRuns(agentId, limit = 5).items }
                .getOrDefault(emptyList())
            val active = runs.firstOrNull { it.status.isActiveRun() }

            val previous = agentState(agentId).lines
            val history = runCatching { api.getConversation(agentId).messages.toChatLines() }
                .getOrDefault(emptyList())
            val alreadyThisRun = active != null &&
                streamJobs[agentId]?.isActive == true &&
                agentState(agentId).activeRunId == active.id
            val liveTurn = streamJobs[agentId]?.isActive == true ||
                agentState(agentId).streaming ||
                pendingLocalSend

            if (history.isNotEmpty()) {
                val queue = agentState(agentId).queue
                val merged = mergeConversation(
                    history = history.preservingExtrasFrom(previous),
                    local = previous,
                    streaming = liveTurn,
                ).withQueued(queue)
                if (merged.map { it.kind to it.text } != previous.withQueued(queue).map { it.kind to it.text }) {
                    mutateAgent(agentId) {
                        it.copy(
                            lines = merged,
                            historyLoaded = true,
                            agent = detail ?: it.agent,
                        )
                    }
                    if (!liveTurn) persistConversation(agentId, agentState(agentId).lines)
                }
            }

            if (active != null && !alreadyThisRun) {
                attachRemoteRun(agentId, active.id)
            } else if (
                detail != null &&
                active == null &&
                streamJobs[agentId]?.isActive != true &&
                !pendingLocalSend &&
                !detail.status.isActiveRun()
            ) {
                if (agentState(agentId).streaming && agentState(agentId).activeRunId != null) {
                    mutateAgent(agentId) {
                        it.copy(streaming = false, workingNote = null, activeRunId = null)
                    }
                    setLocallyWorking(agentId, false, detail.status)
                }
            } else if (active == null && detail?.status.isActiveRun() == true && !pendingLocalSend) {
                setLocallyWorking(agentId, true)
                mutateAgent(agentId) {
                    it.copy(streaming = true, workingNote = it.workingNote ?: "working…")
                }
            }
        } catch (_: Exception) {
            // Keep the on-screen cache; the next poll retries.
        }
    }

    private fun attachRemoteRun(agentId: String, runId: String) {
        setLocallyWorking(agentId, true)
        mutateAgent(agentId) {
            it.copy(
                streaming = true,
                workingNote = it.workingNote ?: "working…",
                activeRunId = runId,
            )
        }
        consumeStream(agentId, runId, appendOnly = true)
    }

    private fun publishRemoteStatus(detail: AgentDetail) {
        val summary = detail.toSummary()
        _inbox.update { inbox ->
            inbox.copy(
                agents = inbox.agents.map { agent ->
                    if (agent.id != summary.id) {
                        agent
                    } else {
                        agent.copy(
                            status = summary.status ?: agent.status,
                            updatedAt = summary.updatedAt ?: agent.updatedAt,
                            latestRunId = summary.latestRunId ?: agent.latestRunId,
                            name = summary.name ?: agent.name,
                        )
                    }
                },
            )
        }
    }

    fun updateDraft(value: String) {
        _agent.update { it.copy(draft = value) }
        rememberComposer(composerAgentId())
    }

    fun addAgentAttachment(context: Context, uri: Uri, kind: AttachmentKind) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    takeReadPermission(context, uri)
                    encodeAttachment(context, uri, kind)
                }.getOrNull()
            }
            if (encoded == null) {
                _agent.update { it.copy(error = "couldn't attach that file") }
                return@launch
            }
            _agent.update { state ->
                val next = mergeAttachment(state.attachments, encoded)
                if (next === state.attachments) {
                    state.copy(error = "too many images (max ${AttachmentEncoder.MaxImages})")
                } else {
                    state.copy(attachments = next, error = null)
                }
            }
            rememberComposer(composerAgentId())
            persistComposerAsync(composerAgentId())
        }
    }

    fun removeAgentAttachment(id: String) {
        _agent.update { it.copy(attachments = it.attachments.filterNot { item -> item.id == id }) }
        rememberComposer(composerAgentId())
        persistComposerAsync(composerAgentId())
    }

    fun addNewAgentAttachment(context: Context, uri: Uri, kind: AttachmentKind) {
        viewModelScope.launch {
            val encoded = withContext(Dispatchers.IO) {
                runCatching {
                    takeReadPermission(context, uri)
                    encodeAttachment(context, uri, kind)
                }.getOrNull()
            }
            if (encoded == null) {
                _newAgent.update { it.copy(error = "couldn't attach that file") }
                return@launch
            }
            _newAgent.update { state ->
                val next = mergeAttachment(state.attachments, encoded)
                if (next === state.attachments) {
                    state.copy(error = "too many images (max ${AttachmentEncoder.MaxImages})")
                } else {
                    state.copy(attachments = next, error = null)
                }
            }
        }
    }

    fun removeNewAgentAttachment(id: String) {
        _newAgent.update { it.copy(attachments = it.attachments.filterNot { item -> item.id == id }) }
    }

    private fun encodeAttachment(context: Context, uri: Uri, kind: AttachmentKind): DraftAttachment? {
        return when (kind) {
            AttachmentKind.IMAGE -> AttachmentEncoder.encodeImage(context, uri)
            AttachmentKind.FILE -> AttachmentEncoder.encodeFile(context, uri)
        }
    }

    private fun takeReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun mergeAttachment(
        current: List<DraftAttachment>,
        incoming: DraftAttachment,
    ): List<DraftAttachment> {
        val images = current.filter { it.isImage }.toMutableList()
        val files = current.filterNot { it.isImage }.toMutableList()
        if (incoming.isImage) {
            if (images.size >= AttachmentEncoder.MaxImages) return current
            images += incoming
        } else {
            files += incoming
        }
        return images + files
    }

    fun sendFollowUp() {
        submitFollowUp(SendMode.QUEUE)
    }

    fun submitFollowUp(mode: SendMode) {
        val state = _agent.value
        val agentId = state.agent?.id ?: return
        if (state.draft.isBlank() && state.attachments.isEmpty()) return

        val outgoing = state.attachments
        val display = state.draft.trim().ifBlank { "Sent ${outgoing.size} attachment(s)" }
        val apiText = buildPromptText(state.draft, outgoing)
            .ifBlank { "Please review the attached files." }
        val modelId = state.turnModelId.ifBlank { AUTO_MODEL_ID }
        val modelParams = state.turnModelParams
        val lineId = nextId("user")
        val busy = state.streaming || streamJobs[agentId]?.isActive == true
        val pendingAssistantId = if (!busy || mode == SendMode.NOW) nextId("assistant") else null
        composerDrafts.remove(agentId)
        persistComposerAsync(agentId)
        mutateAgent(agentId) {
            val withUser = it.copy(
                draft = "",
                attachments = emptyList(),
                error = null,
                lines = (
                    it.lines + ChatLine(
                        id = lineId,
                        kind = ChatLine.Kind.USER,
                        text = display,
                        attachments = outgoing,
                        queued = state.streaming && mode == SendMode.QUEUE,
                    )
                    ).withoutResolvedErrors(),
            )
            if (pendingAssistantId == null) {
                withUser
            } else {
                withUser.copy(
                    streaming = true,
                    workingNote = "working…",
                    lines = withUser.lines + ChatLine(
                        id = pendingAssistantId,
                        kind = ChatLine.Kind.ASSISTANT,
                        text = "",
                        extrasResolved = false,
                    ),
                )
            }
        }

        if (!busy || mode == SendMode.NOW) {
            setLocallyWorking(agentId, true)
            mutateAgent(agentId) {
                it.copy(streaming = true, workingNote = "working…", error = null)
            }
            viewModelScope.launch {
                if (busy && mode == SendMode.NOW) {
                    runCatching { cancelRunInternal(agentId, drainQueue = false, silent = true) }
                    waitUntilIdle(agentId)
                }
                dispatchRun(agentId, apiText, outgoing, modelId, modelParams)
            }
            return
        }

        val queued = QueuedPrompt(
            id = lineId,
            displayText = display,
            apiText = apiText,
            attachments = outgoing,
            modelId = modelId,
            modelParams = modelParams,
        )
        mutateAgent(agentId) { it.copy(queue = it.queue + queued) }
        persistAgentQueue(agentId)
    }

    fun removeQueued(id: String) {
        val agentId = _agent.value.agent?.id ?: return
        mutateAgent(agentId) { state ->
            state.copy(
                queue = state.queue.filterNot { it.id == id },
                lines = state.lines.filterNot { it.id == id && it.queued },
            )
        }
        persistAgentQueue(agentId)
    }

    fun moveQueued(id: String, delta: Int) {
        val agentId = _agent.value.agent?.id ?: return
        mutateAgent(agentId) { state ->
            val from = state.queue.indexOfFirst { it.id == id }
            if (from < 0) return@mutateAgent state
            val to = (from + delta).coerceIn(0, state.queue.lastIndex)
            if (to == from) return@mutateAgent state
            val next = state.queue.toMutableList()
            val item = next.removeAt(from)
            next.add(to, item)
            state.copy(queue = next)
        }
        persistAgentQueue(agentId)
    }

    private fun dispatchRun(
        agentId: String,
        apiText: String,
        attachments: List<DraftAttachment>,
        modelId: String,
        modelParams: List<app.cursor.android.data.ModelParamValue> = emptyList(),
    ) {
        viewModelScope.launch {
            try {
                ensureApiKey()
                val model = resolveModel(modelId)
                val run = createRunRetrying(
                    agentId,
                    apiText,
                    model.toApiSelection(modelParams),
                    attachments,
                )
                consumeStream(agentId, run.id, appendOnly = true)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                mutateAgent(agentId) {
                    it.copy(
                        streaming = false,
                        error = e.userMessage(),
                        lines = it.lines + ChatLine(
                            id = nextId("error"),
                            kind = ChatLine.Kind.ERROR,
                            text = e.userMessage(),
                        ),
                    )
                }
                setLocallyWorking(agentId, false, "ERROR")
            }
        }
    }

    fun createAgent(onCreated: (String) -> Unit) {
        val form = _newAgent.value
        val prompt = buildPromptText(form.prompt, form.attachments)
        if (form.prompt.isBlank() && form.attachments.isEmpty()) {
            _newAgent.update { it.copy(error = "Write a task prompt first.") }
            return
        }

        viewModelScope.launch {
            _newAgent.update { it.copy(submitting = true, error = null) }
            try {
                val settings = settingsRepo.settings.first()
                apiKeyCache = settings.apiKey
                ensureApiKey()

                val envType = form.envType.ifBlank { settings.envType }.ifBlank { "cloud" }
                val machine = form.machineName.ifBlank {
                    settings.machineName.ifBlank {
                        _inbox.value.workers.firstOrNull()?.displayName().orEmpty()
                    }
                }
                val worker = _inbox.value.workers.firstOrNull { it.matchesRef(machine) }
                val model = resolveModel(form.modelId.ifBlank { AUTO_MODEL_ID })
                val recentRepos = _inbox.value.agents.mapNotNull { agent ->
                    agent.repos?.firstOrNull()?.url?.takeIf { it.isNotBlank() }
                }
                val plan = planCreateAgent(
                    envType = envType,
                    worker = worker,
                    machineFallback = machine,
                    repoUrl = form.repoUrl.ifBlank {
                        preferredRepoUrl(
                            repositories = _inbox.value.repositories,
                            defaultUrl = settings.defaultRepoUrl,
                            recentRepoUrls = recentRepos,
                        )
                    },
                    startingRef = form.startingRef,
                    fallbackRepoUrl = settings.defaultRepoUrl,
                    prompt = PromptBody(
                        text = prompt.ifBlank { "Please review the attached files." },
                        images = form.attachments.toPromptImages().takeIf { it.isNotEmpty() },
                    ),
                    model = model.toApiSelection(form.modelParams),
                )
                val request = when (plan) {
                    is CreateAgentPlan.Invalid -> {
                        _newAgent.update { it.copy(submitting = false, error = plan.message) }
                        return@launch
                    }
                    is CreateAgentPlan.Ready -> plan.request
                }

                val created = api.createAgent(request)
                if (model.id != defaultModelId()) {
                    localStore.rememberModel(created.agent.id, model.id)
                }
                _newAgent.update {
                    it.copy(submitting = false, prompt = "", attachments = emptyList(), error = null)
                }
                onCreated(created.agent.id)
                setLocallyWorking(created.agent.id, true)
                openAgent(created.agent.id)
                consumeStream(created.agent.id, created.run.id, appendOnly = true)
                refreshInbox()
            } catch (e: Exception) {
                val machineHint = if (form.envType.ifBlank { "machine" } == "machine") {
                    " · turn on remote control on that computer (or run `agent worker`), then try again or pick another machine"
                } else {
                    ""
                }
                _newAgent.update { it.copy(submitting = false, error = e.userMessage() + machineHint) }
            }
        }
    }

    fun cancelActiveRun() {
        val agentId = _agent.value.agent?.id ?: return
        viewModelScope.launch {
            cancelRunInternal(agentId, drainQueue = false)
        }
    }

    fun sendQueuedNow(id: String) {
        val agentId = _agent.value.agent?.id ?: return
        val item = _agent.value.queue.firstOrNull { it.id == id } ?: return
        val pendingAssistantId = nextId("assistant")
        mutateAgent(agentId) { state ->
            state.copy(
                queue = state.queue.filterNot { it.id == id },
                streaming = true,
                workingNote = "working…",
                error = null,
                lines = state.lines.map { line ->
                    if (line.id == id) line.copy(queued = false) else line
                } + ChatLine(
                    id = pendingAssistantId,
                    kind = ChatLine.Kind.ASSISTANT,
                    text = "",
                    extrasResolved = false,
                ),
            )
        }
        persistAgentQueue(agentId)
        setLocallyWorking(agentId, true)
        viewModelScope.launch {
            runCatching { cancelRunInternal(agentId, drainQueue = false, silent = true) }
            waitUntilIdle(agentId)
            dispatchRun(agentId, item.apiText, item.attachments, item.modelId, item.modelParams)
        }
    }

    private suspend fun cancelRunInternal(
        agentId: String,
        drainQueue: Boolean,
        silent: Boolean = false,
    ) {
        val runId = agentState(agentId).activeRunId
            ?: _inbox.value.latestRuns[agentId]?.takeIf { it.status.isActiveRun() }?.id
            ?: return
        try {
            api.cancelRun(agentId, runId)
            streamJobs[agentId]?.cancel()
            localStore.removeActiveRun(agentId, runId)
            mutateAgent(agentId) { state ->
                if (silent) {
                    state.copy(activeRunId = null)
                } else {
                    state.copy(
                        streaming = false,
                        activeRunId = null,
                        workingNote = null,
                        lines = state.lines + ChatLine(
                            id = nextId("status"),
                            kind = ChatLine.Kind.STATUS,
                            text = "Stopped the current run.",
                        ),
                    )
                }
            }
            if (!silent) setLocallyWorking(agentId, false, "CANCELLED")
            if (drainQueue) drainQueue(agentId)
        } catch (e: Exception) {
            reportError(e, "couldn’t reach the api")
            mutateAgent(agentId) { it.copy(error = e.userMessage()) }
        } finally {
            syncForegroundService()
        }
    }

    fun reloadHistory() {
        val agentId = _agent.value.agent?.id ?: return
        viewModelScope.launch {
            try {
                ensureApiKey()
                _agent.update { it.copy(refreshing = true, extrasLoading = false) }
                val previous = _agent.value.lines
                val history = api.getConversation(agentId).messages.toChatLines()
                    .preservingExtrasFrom(previous)
                val extras = runCatching {
                    api.collectRunExtras(agentId, maxRuns = CONVERSATION_EXTRAS_MAX_RUNS)
                }.getOrDefault(emptyList())
                val lines = mergeConversation(
                    history = history.withRunExtras(extras).preservingExtrasFrom(previous),
                    local = previous,
                    streaming = _agent.value.streaming,
                ).withQueued(_agent.value.queue)
                _agent.update {
                    it.copy(
                        lines = lines,
                        historyLoaded = true,
                        extrasLoading = false,
                        refreshing = false,
                        error = null,
                    )
                }
                persistConversation(agentId, lines)
            } catch (e: Exception) {
                _agent.update {
                    it.copy(extrasLoading = false, refreshing = false, error = e.userMessage())
                }
            }
        }
    }

    private fun resolveTurnModel(stored: String?): String =
        stored?.takeIf { it.isNotBlank() } ?: defaultModelId()

    private fun resolveModel(modelId: String): CursorModel {
        val id = modelId.ifBlank { AUTO_MODEL_ID }
        if (id.isAutoModelId()) return _models.value.models.firstOrNull { it.isAuto() } ?: autoModel()
        return _models.value.models.firstOrNull { it.id == id }
            ?: CursorModel(id = id, displayName = id)
    }

    private fun consumeStream(agentId: String, runId: String, appendOnly: Boolean) {
        streamJobs[agentId]?.cancel()
        streamJobs[agentId] = viewModelScope.launch {
            val thisJob = coroutineContext.job
            val linesNow = agentState(agentId).lines
            val lastUserIndex = linesNow.indexOfLast { it.kind == ChatLine.Kind.USER }
            val existingAssistant = linesNow.lastOrNull { line ->
                if (line.kind != ChatLine.Kind.ASSISTANT) return@lastOrNull false
                val index = linesNow.indexOf(line)
                if (index <= lastUserIndex) return@lastOrNull false
                line.text.isBlank()
            }
            val assistantId = existingAssistant?.id ?: nextId("assistant-$runId")
            val priorTexts = linesNow
                .filter { it.kind == ChatLine.Kind.ASSISTANT && it.id != assistantId && it.text.isNotBlank() }
                .map { it.text }
            setLocallyWorking(agentId, true)
            mutateAgent(agentId) { state ->
                val base = if (appendOnly) state.lines else emptyList()
                val hasAssistant = base.any { it.id == assistantId }
                val cleaned = base.filterNot {
                    it.kind == ChatLine.Kind.ASSISTANT &&
                        it.runId == runId &&
                        it.text.isBlank() &&
                        it.thinking.isBlank() &&
                        it.id != assistantId
                }
                state.copy(
                    streaming = true,
                    activeRunId = runId,
                    workingNote = "working…",
                    error = null,
                    lines = if (hasAssistant) {
                        cleaned.map { line ->
                            if (line.id == assistantId) {
                                line.copy(
                                    runId = runId,
                                    extrasResolved = false,
                                    text = "",
                                    thinking = line.thinking,
                                )
                            } else {
                                line
                            }
                        }
                    } else {
                        cleaned + ChatLine(
                            id = assistantId,
                            kind = ChatLine.Kind.ASSISTANT,
                            text = "",
                            thinking = "",
                            runId = runId,
                            extrasResolved = false,
                        )
                    },
                )
            }
            val persisted = ActiveRun(
                agentId = agentId,
                runId = runId,
                title = runTitle(agentId),
            )
            localStore.upsertActiveRun(persisted)
            syncForegroundService()
            var lastEventId: String? = null
            var completed = false
            var terminalStatus: String? = null
            try {
                while (isActive && !completed) {
                    try {
                        api.streamRun(agentId, runId, lastEventId).collect { frame ->
                            if (!frame.id.isNullOrBlank()) lastEventId = frame.id
                            when (val event = frame.event) {
                                is StreamEvent.Assistant ->
                                    appendAssistant(agentId, assistantId, event.text, priorTexts)
                                is StreamEvent.Thinking -> {
                                    if (event.text.isNotEmpty()) {
                                        updateAssistantMeta(agentId, assistantId) { line ->
                                            line.copy(thinking = mergeStreamText(line.thinking, event.text))
                                        }
                                    }
                                    mutateAgent(agentId) {
                                        it.copy(workingNote = "thinking…")
                                    }
                                }
                                is StreamEvent.UserMessage -> Unit
                                is StreamEvent.ToolCall -> {
                                    mutateAgent(agentId) { it.copy(workingNote = "working…") }
                                    upsertTool(agentId, event)
                                    if (event.todos.isNotEmpty()) {
                                        updateAssistantMeta(agentId, assistantId) {
                                            it.copy(todos = event.todos)
                                        }
                                    }
                                    val path = event.path
                                    val tool = event.name.orEmpty()
                                    if (!path.isNullOrBlank() && tool.isFileMutatingTool()) {
                                        updateAssistantMeta(agentId, assistantId) { line ->
                                            val existing = line.changedFiles.firstOrNull { it.path == path }
                                            val files = (
                                                line.changedFiles.filterNot { it.path == path } +
                                                    ChangedFile(
                                                        path = path,
                                                        tool = tool,
                                                        linesAdded = event.linesAdded ?: existing?.linesAdded,
                                                        linesRemoved = event.linesRemoved
                                                            ?: existing?.linesRemoved,
                                                    )
                                                )
                                                .sortedBy { it.fileName.lowercase() }
                                            line.copy(changedFiles = files)
                                        }
                                    }
                                }
                                is StreamEvent.Status -> {
                                    val status = event.status?.uppercase().orEmpty()
                                    mutateAgent(agentId) {
                                        it.copy(workingNote = workingLabel(status))
                                    }
                                    if (status.isTerminalRun()) {
                                        terminalStatus = status
                                        completed = true
                                    }
                                }
                                is StreamEvent.Result -> {
                                    event.text?.takeIf { it.isNotBlank() }?.let { finalText ->
                                        if (!isReplayOfPriorTurn(finalText, priorTexts)) {
                                            replaceAssistant(agentId, assistantId, finalText)
                                        }
                                    }
                                    updateAssistantMeta(agentId, assistantId) {
                                        it.copy(extrasResolved = true)
                                    }
                                    terminalStatus = event.status ?: "FINISHED"
                                    completed = true
                                }
                                is StreamEvent.Error -> {
                                    if (event.isTransientStreamLoss()) {
                                        mutateAgent(agentId) {
                                            it.copy(workingNote = "working…")
                                        }
                                    } else {
                                        mutateAgent(agentId) {
                                            it.copy(
                                                lines = it.lines + ChatLine(
                                                    id = nextId("error"),
                                                    kind = ChatLine.Kind.ERROR,
                                                    text = event.message ?: event.code ?: "Stream error",
                                                ),
                                            )
                                        }
                                        terminalStatus = "ERROR"
                                        completed = true
                                    }
                                }
                                StreamEvent.Done -> {
                                    terminalStatus = terminalStatus ?: "FINISHED"
                                    completed = true
                                }
                                StreamEvent.Heartbeat -> Unit
                                is StreamEvent.Unknown -> Unit
                            }
                            if (completed) {
                                throw CancellationException("sse-complete")
                            }
                        }
                    } catch (e: CancellationException) {
                        if (!completed) throw e
                    } catch (e: Exception) {
                        if (completed) {
                            break
                        }
                        if (e.isTransientStreamLoss()) {
                            mutateAgent(agentId) { it.copy(workingNote = "working…") }
                        } else {
                            mutateAgent(agentId) {
                                it.copy(
                                    lines = it.lines + ChatLine(
                                        id = nextId("error"),
                                        kind = ChatLine.Kind.ERROR,
                                        text = e.message ?: "Stream error",
                                    ),
                                )
                            }
                            terminalStatus = "ERROR"
                            completed = true
                            break
                        }
                    }
                    if (completed) break
                    val run = runCatching { api.getRun(agentId, runId) }.getOrNull()
                    fastForwardFromConversation(agentId, assistantId, runId)
                    if (run?.status.isTerminalRun()) {
                        run?.result?.takeIf { it.isNotBlank() }?.let { finalText ->
                            val current = agentState(agentId).lines
                                .firstOrNull { it.id == assistantId }
                                ?.text
                                .orEmpty()
                            val priors = agentState(agentId).lines
                                .filter {
                                    it.kind == ChatLine.Kind.ASSISTANT &&
                                        it.id != assistantId &&
                                        it.text.isNotBlank()
                                }
                                .map { it.text }
                            val next = resolveAssistantText(
                                live = current,
                                history = finalText,
                                priorAssistantTexts = priors,
                                preferHistoryWhenDistinct = true,
                            )
                            if (next.isNotBlank() && next != current) {
                                replaceAssistant(agentId, assistantId, next)
                            }
                        }
                        terminalStatus = run?.status
                        completed = true
                        break
                    }
                    mutateAgent(agentId) {
                        it.copy(streaming = true, workingNote = "working…", error = null)
                    }
                    delay(1_500)
                }
                updateAssistantMeta(agentId, assistantId) { it.copy(extrasResolved = true) }
                runCatching { hydrateConversation(agentId, streaming = !completed) }
            } finally {
                val stillCurrent = streamJobs[agentId] === thisJob
                if (stillCurrent) {
                    mutateAgent(agentId) {
                        it.copy(streaming = false, activeRunId = null, workingNote = null)
                    }
                    streamJobs.remove(agentId)
                    localStore.removeActiveRun(agentId, runId)
                    syncForegroundService()
                    val hadQueuedFollowUp = agentState(agentId).queue.isNotEmpty()
                    if (isActive && completed) {
                        val preview = agentState(agentId).lines
                            .lastOrNull { it.kind == ChatLine.Kind.ASSISTANT }
                            ?.text
                            .orEmpty()
                            .ifBlank {
                                agentState(agentId).lines
                                    .lastOrNull { it.kind == ChatLine.Kind.ERROR }
                                    ?.text
                                    .orEmpty()
                            }
                        RunResumeCoordinator.announceFinished(
                            context = getApplication(),
                            run = persisted,
                            preview = preview.ifBlank { "${persisted.title} finished" },
                            status = terminalStatus ?: "FINISHED",
                            recovered = false,
                        )
                    }
                    if (isActive) drainQueue(agentId)
                    if (!hadQueuedFollowUp) {
                        setLocallyWorking(agentId, false, terminalStatus ?: "FINISHED")
                    }
                }
            }
        }
    }

    private fun runTitle(agentId: String): String {
        val inbox = _inbox.value
        return inbox.agents.firstOrNull { it.id == agentId }
            ?.headline(inbox.workers, inbox.chatTitles)
            ?: agentState(agentId).agent?.headline(inbox.workers, inbox.chatTitles)
            ?: "Chat"
    }

    private suspend fun hydrateConversation(agentId: String, streaming: Boolean? = null) {
        val history = api.getConversation(agentId).messages.toChatLines()
        val extras = runCatching {
            api.collectRunExtras(agentId, maxRuns = CONVERSATION_EXTRAS_MAX_RUNS)
        }.getOrDefault(emptyList())
        val previous = agentState(agentId).lines
        val queuedLines = previous.filter { it.queued }
        if (history.isNotEmpty()) {
            val stillStreaming = streaming
                ?: (agentState(agentId).streaming || streamJobs[agentId]?.isActive == true)
            val lines = mergeConversation(
                history = history
                    .preservingExtrasFrom(previous)
                    .withRunExtras(extras),
                local = previous,
                streaming = stillStreaming,
            ) + queuedLines
            mutateAgent(agentId) {
                it.copy(
                    lines = lines,
                    historyLoaded = true,
                )
            }
            persistConversation(agentId, lines)
        }
    }

    private fun persistConversation(agentId: String, lines: List<ChatLine>) {
        if (agentId.isBlank() || lines.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val syncedAt = cacheRepo.saveConversation(agentId, lines)
                mutateAgent(agentId) { it.copy(conversationSyncedAt = syncedAt) }
            }
        }
    }

    private suspend fun fastForwardFromConversation(
        agentId: String,
        assistantId: String,
        runId: String,
    ) {
        val history = runCatching { api.getConversation(agentId).messages.toChatLines() }
            .getOrDefault(emptyList())
        val latest = history.lastOrNull { it.kind == ChatLine.Kind.ASSISTANT } ?: return
        val current = agentState(agentId).lines.firstOrNull { it.id == assistantId }
        val priors = agentState(agentId).lines
            .filter {
                it.kind == ChatLine.Kind.ASSISTANT &&
                    it.id != assistantId &&
                    it.text.isNotBlank()
            }
            .map { it.text }
        val next = resolveAssistantText(
            live = current?.text.orEmpty(),
            history = latest.text,
            priorAssistantTexts = priors,
            preferHistoryWhenDistinct = false,
        )
        if (next.isNotBlank() && next != current?.text.orEmpty()) {
            replaceAssistant(agentId, assistantId, next)
        }
    }

    private suspend fun createRunRetrying(
        agentId: String,
        text: String,
        model: app.cursor.android.data.ModelSelection?,
        attachments: List<DraftAttachment>,
    ) = run {
        var lastError: Exception? = null
        repeat(12) { attempt ->
            try {
                return@run api.createRun(
                    agentId = agentId,
                    text = text,
                    model = model,
                    images = attachments.toPromptImages(),
                )
            } catch (e: Exception) {
                lastError = e
                val busy = e is ApiException && e.statusCode == 409
                if (!busy) throw e
                delay(500L * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("Could not start run")
    }

    private suspend fun waitUntilIdle(agentId: String) {
        repeat(20) {
            val busy = runCatching {
                api.listRuns(agentId, limit = 5).items.any { it.status.isActiveRun() }
            }.getOrDefault(streamJobs[agentId]?.isActive == true)
            if (!busy) return
            delay(400)
        }
    }

    private fun drainQueue(agentId: String) {
        val next = agentState(agentId).queue.firstOrNull() ?: return
        if (streamJobs[agentId]?.isActive == true) return
        mutateAgent(agentId) { state ->
            state.copy(
                queue = state.queue.drop(1),
                lines = state.lines.map { line ->
                    if (line.id == next.id) line.copy(queued = false) else line
                },
            )
        }
        persistAgentQueue(agentId)
        setLocallyWorking(agentId, true)
        dispatchRun(agentId, next.apiText, next.attachments, next.modelId, next.modelParams)
    }

    private fun persistAgentQueue(agentId: String) {
        val queue = agentState(agentId).queue
        viewModelScope.launch { localStore.saveQueue(agentId, queue) }
    }

    private fun hasComposerInput(): Boolean {
        val state = _agent.value
        return state.draft.isNotBlank() || state.attachments.isNotEmpty()
    }

    private fun composerAgentId(): String? =
        visibleAgentId()

    private fun rememberComposer(agentId: String?) {
        if (agentId.isNullOrBlank()) return
        val state = _agent.value
        val belongsToThisChat = state.agent?.id.isNullOrBlank() || state.agent?.id == agentId
        if (!belongsToThisChat) return
        val draft = ComposerDraft(state.draft, state.attachments)
        if (draft.isEmpty) {
            composerDrafts.remove(agentId)
        } else {
            composerDrafts[agentId] = draft
        }
    }

    private fun storedComposer(agentId: String, fallback: AgentUiState?): ComposerDraft {
        composerDrafts[agentId]?.let { return it }
        if (fallback != null && (fallback.draft.isNotBlank() || fallback.attachments.isNotEmpty())) {
            return ComposerDraft(fallback.draft, fallback.attachments)
        }
        return ComposerDraft()
    }

    private fun applyComposer(agentId: String) {
        val saved = composerDrafts[agentId] ?: return
        mutateAgent(agentId) { it.copy(draft = saved.text, attachments = saved.attachments) }
    }

    private fun persistComposerAsync(agentId: String?) {
        if (agentId.isNullOrBlank()) return
        viewModelScope.launch {
            localStore.saveComposerDraft(agentId, composerDrafts[agentId])
        }
    }

    private fun List<ChatLine>.withQueued(queue: List<QueuedPrompt>): List<ChatLine> {
        if (queue.isEmpty()) return this
        val existing = map { it.id }.toSet()
        val extra = queue
            .filter { it.id !in existing }
            .map { item ->
                ChatLine(
                    id = item.id,
                    kind = ChatLine.Kind.USER,
                    text = item.displayText,
                    attachments = item.attachments,
                    queued = true,
                )
            }
        return this + extra
    }

    private fun agentState(agentId: String): AgentUiState {
        return routedAgentState(
            requestedAgentId = agentId,
            openedAgentId = openedAgentId,
            loadedAgentId = _agent.value.agent?.id,
            visibleState = _agent.value,
            snapshots = agentSnapshots,
            emptyState = { AgentUiState() },
        )
    }

    private fun mutateAgent(agentId: String, transform: (AgentUiState) -> AgentUiState) {
        if (isVisibleAgent(agentId)) {
            _agent.update(transform)
        } else {
            val current = agentSnapshots[agentId] ?: AgentUiState()
            agentSnapshots[agentId] = transform(current)
        }
    }

    private fun visibleAgentId(): String? =
        openedAgentId?.takeIf { it.isNotBlank() } ?: _agent.value.agent?.id

    private fun isVisibleAgent(agentId: String): Boolean = ownsVisibleAgent(
        requestedAgentId = agentId,
        openedAgentId = openedAgentId,
        loadedAgentId = _agent.value.agent?.id,
    )

    private fun syncForegroundService() {
        val context = getApplication<Application>()
        val active = streamJobs.values.any { it.isActive } ||
            _agent.value.streaming ||
            agentSnapshots.values.any { it.streaming }
        if (active) {
            val name = _agent.value.agent?.name ?: "Agent"
            StreamingForegroundService.start(context, "$name is working…")
        } else {
            StreamingForegroundService.stop(context)
        }
    }

    private fun workingLabel(status: String): String = when (status.uppercase()) {
        "CREATING" -> "starting…"
        "WORKING" -> "working…"
        else -> "working…"
    }

    private fun updateAssistantMeta(
        agentId: String,
        assistantId: String,
        transform: (ChatLine) -> ChatLine,
    ) {
        mutateAgent(agentId) { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { it.id == assistantId }
            if (index >= 0) lines[index] = transform(lines[index])
            state.copy(lines = lines)
        }
    }

    private fun upsertTool(agentId: String, event: StreamEvent.ToolCall) {
        if (event.name.equals("todo_write", ignoreCase = true)) return
        if (event.name.orEmpty().isFileMutatingTool()) return

        val callId = event.callId?.sanitizeId().orEmpty().ifBlank { nextId("tool") }
        val label = buildString {
            append(event.name ?: "tool")
            event.status?.let { append(" · $it") }
        }
        mutateAgent(agentId) { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { it.kind == ChatLine.Kind.TOOL && it.id == "tool-$callId" }
            val line = ChatLine(
                id = "tool-$callId",
                kind = ChatLine.Kind.TOOL,
                text = label,
            )
            if (index >= 0) lines[index] = line else lines += line
            state.copy(lines = lines)
        }
    }

    private fun appendAssistant(
        agentId: String,
        assistantId: String,
        delta: String,
        priorTexts: List<String> = emptyList(),
    ) {
        if (delta.isEmpty()) return
        mutateAgent(agentId) { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { it.id == assistantId }
            if (index >= 0) {
                val current = lines[index]
                val merged = mergeStreamText(current.text, delta, priorTexts)
                lines[index] = current.copy(text = merged)
            } else {
                if (isReplayOfPriorTurn(delta, priorTexts)) return@mutateAgent state
                lines += ChatLine(
                    id = assistantId,
                    kind = ChatLine.Kind.ASSISTANT,
                    text = delta,
                    runId = assistantId.substringAfter("assistant-", missingDelimiterValue = ""),
                )
            }
            state.copy(lines = lines)
        }
    }

    private fun replaceAssistant(agentId: String, assistantId: String, text: String) {
        mutateAgent(agentId) { state ->
            val lines = state.lines.toMutableList()
            val index = lines.indexOfLast { it.id == assistantId }
            if (index >= 0) {
                lines[index] = lines[index].copy(text = text)
            } else {
                lines += ChatLine(
                    id = assistantId,
                    kind = ChatLine.Kind.ASSISTANT,
                    text = text,
                )
            }
            state.copy(lines = lines)
        }
    }

    private fun nextId(prefix: String): String =
        "$prefix-${idSeq.incrementAndGet()}-${UUID.randomUUID().toString().take(8)}"

    private fun ensureApiKey() {
        require(apiKeyCache.isNotBlank()) {
            "Add an API key in Settings first."
        }
    }
}

internal enum class AgentLifecycleAction { ARCHIVE, DELETE }

internal fun InboxUiState.withoutAgents(agentIds: Set<String>): InboxUiState {
    if (agentIds.isEmpty()) return this
    return copy(
        agents = agents.filterNot { it.id in agentIds },
        latestRuns = latestRuns - agentIds,
        changeStats = changeStats - agentIds,
        locallyWorkingIds = locallyWorkingIds - agentIds,
        lastReadAtByAgent = lastReadAtByAgent - agentIds,
        pinnedAgentIds = pinnedAgentIds.filterNot { it in agentIds },
    )
}

private fun List<ChatLine>.withRunExtras(extras: List<RunExtras>): List<ChatLine> {
    if (isEmpty() || extras.isEmpty()) return this

    val remaining = extras.toMutableList()
    val result = mutableListOf<ChatLine>()
    var pendingUser: String? = null

    for (line in this) {
        when (line.kind) {
            ChatLine.Kind.USER -> {
                pendingUser = line.text
                result += line
            }
            ChatLine.Kind.ASSISTANT -> {
                val user = pendingUser?.trim().orEmpty()
                pendingUser = null
                val byText = if (user.isNotBlank()) {
                    val idx = remaining.indexOfFirst { extra ->
                        val prompt = extra.userText?.trim().orEmpty()
                        prompt.isNotBlank() && (
                            prompt == user ||
                                prompt.startsWith(user.take(100)) ||
                                user.startsWith(prompt.take(100))
                            )
                    }
                    if (idx >= 0) remaining.removeAt(idx) else null
                } else {
                    null
                }
                val matched = byText ?: remaining.removeLastOrNull()
                result += if (matched != null) {
                    line.copy(
                        runId = matched.runId,
                        todos = matched.todos,
                        changedFiles = matched.files,
                        extrasResolved = true,
                    )
                } else {
                    line.copy(extrasResolved = true)
                }
            }
            else -> result += line
        }
    }
    return result
}

private fun List<ChatLine>.preservingExtrasFrom(previous: List<ChatLine>): List<ChatLine> {
    if (isEmpty() || previous.isEmpty()) return this
    val priorAssistants = previous.filter {
        it.kind == ChatLine.Kind.ASSISTANT &&
            (it.todos.isNotEmpty() || it.changedFiles.isNotEmpty() || it.thinking.isNotBlank())
    }
    if (priorAssistants.isEmpty()) return this
    return map { line ->
        if (line.kind != ChatLine.Kind.ASSISTANT) return@map line
        if (line.todos.isNotEmpty() || line.changedFiles.isNotEmpty()) return@map line
        val match = priorAssistants.firstOrNull { prior ->
            prior.text.trim() == line.text.trim() ||
                (prior.text.isNotBlank() && line.text.startsWith(prior.text.take(120))) ||
                (line.text.isNotBlank() && prior.text.startsWith(line.text.take(120)))
        } ?: return@map line
        line.copy(
            runId = line.runId ?: match.runId,
            todos = match.todos,
            changedFiles = match.changedFiles,
            thinking = line.thinking.ifBlank { match.thinking },
            extrasResolved = true,
        )
    }
}

private fun Exception.userMessage(): String = when {
    this is ApiException -> {
        val detail = apiErrorMessage().ifBlank { body.ifBlank { message.orEmpty() } }.take(220)
        if (statusCode == 400) detail else "HTTP $statusCode · $detail"
    }
    isTransientNetworkFailure() -> "couldn't reach cursor. check the network and try again"
    else -> message ?: "something went wrong"
}

private fun ApiException.apiErrorMessage(): String {
    val quoted = Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").findAll(body)
        .map { it.groupValues[1] }
        .lastOrNull()
        ?.replace("\\n", " ")
        ?.removePrefix("Bad Request: ")
        ?.trim()
        .orEmpty()
    return quoted
}
