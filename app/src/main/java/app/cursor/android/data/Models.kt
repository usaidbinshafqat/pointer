package app.cursor.android.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val AUTO_MODEL_ID = "auto"

fun autoModel(): CursorModel = CursorModel(
    id = AUTO_MODEL_ID,
    displayName = "auto",
    description = "picks the model for this turn",
)

fun String.isAutoModelId(): Boolean =
    isBlank() ||
        equals(AUTO_MODEL_ID, ignoreCase = true) ||
        equals("default", ignoreCase = true)

@Serializable
data class PromptImage(
    val data: String? = null,
    val mimeType: String? = null,
    val url: String? = null,
)

@Serializable
data class PromptBody(
    val text: String,
    val images: List<PromptImage>? = null,
)

@Serializable
data class ModelParamValue(
    val id: String,
    val value: String,
)

@Serializable
data class ModelSelection(
    val id: String,
    val params: List<ModelParamValue>? = null,
)

@Serializable
data class EnvTarget(
    val type: String,
    val name: String? = null,
)

@Serializable
data class RepoConfig(
    val url: String,
    val startingRef: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class RenameAgentRequest(
    val name: String,
)

@Serializable
data class CreateAgentRequest(
    val prompt: PromptBody,
    val model: ModelSelection? = null,
    val name: String? = null,
    val env: EnvTarget? = null,
    val repos: List<RepoConfig>? = null,
    val autoCreatePR: Boolean? = null,
    val workOnCurrentBranch: Boolean? = null,
)

sealed interface CreateAgentPlan {
    data class Ready(val request: CreateAgentRequest) : CreateAgentPlan
    data class Invalid(val message: String) : CreateAgentPlan
}

/**
 * Shape proven against POST /v1/agents:
 * machine + worker display name (or id) + GitHub repos[0] → 201.
 * Omitting repos on a personal worker → 400 workspace-binding error.
 */
fun planCreateAgent(
    envType: String,
    worker: PrivateWorker?,
    machineFallback: String,
    repoUrl: String,
    startingRef: String,
    fallbackRepoUrl: String,
    prompt: PromptBody,
    model: ModelSelection? = null,
): CreateAgentPlan {
    val type = envType.ifBlank { "machine" }
    val machineRef = worker?.routingName()?.ifBlank { null }
        ?: worker?.resolvedId()?.ifBlank { null }
        ?: machineFallback.trim().ifBlank { null }
    val repo = repoUrl.ifBlank { worker?.repoUrl.orEmpty().ifBlank { fallbackRepoUrl } }
        .asGithubRepoUrl()
    if (type != "cloud" && repo.isBlank()) {
        return CreateAgentPlan.Invalid(
            "Pick a GitHub repo. A self-hosted machine cannot start without one.",
        )
    }
    if (type == "machine" && machineRef.isNullOrBlank()) {
        return CreateAgentPlan.Invalid("Pick a machine.")
    }
    return CreateAgentPlan.Ready(
        CreateAgentRequest(
            prompt = prompt,
            model = model,
            env = when (type) {
                "machine" -> EnvTarget(type = "machine", name = machineRef)
                "cloud" -> EnvTarget(type = "cloud")
                "pool" -> EnvTarget(type = "pool", name = machineRef ?: "default")
                else -> EnvTarget(type = type, name = machineRef)
            },
            repos = repo.takeIf { it.isNotBlank() }?.let {
                listOf(RepoConfig(url = it, startingRef = startingRef.ifBlank { "main" }))
            },
            workOnCurrentBranch = if (type == "machine") true else null,
        ),
    )
}

fun preferredRepoUrl(
    repositories: List<GitHubRepository>,
    defaultUrl: String = "",
    recentRepoUrls: List<String> = emptyList(),
): String {
    defaultUrl.asGithubRepoUrl().takeIf { it.isNotBlank() }?.let { return it }
    val recent = recentRepoUrls.map { it.asGithubRepoUrl() }.filter { it.isNotBlank() }
    for (url in recent) {
        repositories.firstOrNull { it.url.asGithubRepoUrl().equals(url, ignoreCase = true) }
            ?.let { return it.url.asGithubRepoUrl() }
        return url
    }
    return repositories.firstOrNull()?.url.orEmpty().asGithubRepoUrl()
}

@Serializable
data class CreateRunRequest(
    val prompt: PromptBody,
    val model: ModelSelection? = null,
)

@Serializable
data class AgentSummary(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val url: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRunId: String? = null,
    val env: AgentEnv? = null,
    val repos: List<RepoConfig>? = null,
)

@Serializable
data class AgentListResponse(
    val items: List<AgentSummary> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class AgentEnv(
    val type: String? = null,
    val name: String? = null,
    val id: String? = null,
    val workerId: String? = null,
) {
    fun refs(): List<String> = listOfNotNull(
        name?.trim()?.ifBlank { null },
        workerId?.trim()?.ifBlank { null },
        id?.trim()?.ifBlank { null },
    )
}

@Serializable
data class AgentDetail(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val url: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val latestRunId: String? = null,
    val env: AgentEnv? = null,
    val repos: List<RepoConfig>? = null,
) {
    fun toSummary(): AgentSummary = AgentSummary(
        id = id,
        name = name,
        status = status,
        url = url,
        createdAt = createdAt,
        updatedAt = updatedAt,
        latestRunId = latestRunId,
        env = env,
        repos = repos,
    )
}

@Serializable
data class GitBranch(
    val repoUrl: String? = null,
    val branch: String? = null,
    val prUrl: String? = null,
)

@Serializable
data class RunGit(
    val branches: List<GitBranch> = emptyList(),
)

/**
 * REST Get A Run / List Runs (`V1Run`) do not document a model field — only id,
 * agentId, status, timestamps, durationMs, result, error, and git. Optional
 * [model] / [modelId] are parsed if a backend version includes them; otherwise
 * they stay null and the composer falls back to Auto.
 */
@Serializable
data class RunSummary(
    val id: String,
    val agentId: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val durationMs: Long? = null,
    val result: String? = null,
    val git: RunGit? = null,
    val model: ModelSelection? = null,
    val modelId: String? = null,
) {
    fun resolvedModelId(): String? =
        model?.id?.takeIf { it.isNotBlank() } ?: modelId?.takeIf { it.isNotBlank() }
}

@Serializable
data class RunListResponse(
    val items: List<RunSummary> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class CreateAgentResponse(
    val agent: AgentDetail,
    val run: RunSummary,
)

@Serializable
data class CreateRunResponse(
    val run: RunSummary? = null,
    val id: String? = null,
    val agentId: String? = null,
    val status: String? = null,
) {
    fun resolvedRun(): RunSummary {
        run?.let { return it }
        val runId = id ?: error("Create run response missing run id")
        return RunSummary(id = runId, agentId = agentId, status = status)
    }
}

@Serializable
data class ConversationMessage(
    val id: String? = null,
    val type: String? = null,
    val text: String? = null,
    val model: ModelSelection? = null,
    val modelId: String? = null,
    val images: List<PromptImage>? = null,
) {
    fun resolvedModelId(): String? =
        model?.id?.takeIf { it.isNotBlank() } ?: modelId?.takeIf { it.isNotBlank() }
}

@Serializable
data class ConversationResponse(
    val id: String? = null,
    val messages: List<ConversationMessage> = emptyList(),
)

@Serializable
data class ModelParamOption(
    val value: String,
    val displayName: String? = null,
)

@Serializable
data class ModelParameter(
    val id: String,
    val displayName: String? = null,
    val values: List<ModelParamOption> = emptyList(),
)

@Serializable
data class ModelVariant(
    val params: List<ModelParamValue> = emptyList(),
    val displayName: String? = null,
    val description: String? = null,
    val isDefault: Boolean? = null,
)

@Serializable
data class CursorModel(
    val id: String,
    val displayName: String? = null,
    val description: String? = null,
    val aliases: List<String> = emptyList(),
    val parameters: List<ModelParameter> = emptyList(),
    val variants: List<ModelVariant> = emptyList(),
) {
    fun label(): String = displayName?.ifBlank { null } ?: if (isAuto()) "Auto" else id

    fun isAuto(): Boolean = id.isAutoModelId()

    fun supportsThinking(): Boolean {
        if (isAuto()) return false
        return adjustableParams().any { param ->
            val id = param.id.lowercase()
            id == "thinking" ||
                id == "reasoning" ||
                id == "effort" ||
                id == "reasoning_effort" ||
                id == "fast"
        } || variants.any { variant ->
            variant.params.any { it.id.equals("fast", ignoreCase = true) }
        }
    }

    fun supportsFast(): Boolean {
        if (isAuto()) return false
        return adjustableParams().any { it.id.equals("fast", ignoreCase = true) } ||
            variants.any { variant ->
                variant.params.any { it.id.equals("fast", ignoreCase = true) }
            }
    }

    fun adjustableParams(): List<ModelParameter> = parameters.filter { it.values.isNotEmpty() }

    fun defaultSelection(): ModelSelection {
        val preferred = variants.firstOrNull { it.isDefault == true } ?: variants.firstOrNull()
        return ModelSelection(
            id = id,
            params = preferred?.params?.takeIf { it.isNotEmpty() },
        )
    }

    fun selectionWith(params: List<ModelParamValue>?): ModelSelection {
        if (isAuto()) return ModelSelection(id = AUTO_MODEL_ID, params = null)
        val next = params?.takeIf { it.isNotEmpty() } ?: defaultSelection().params
        return ModelSelection(id = id, params = next)
    }

    /** Cursor Auto is omitting `model`, not sending id `default`. */
    fun toApiSelection(params: List<ModelParamValue>? = null): ModelSelection? {
        if (isAuto()) return null
        return selectionWith(params)
    }
}

fun List<CursorModel>.withAutoFirst(): List<CursorModel> {
    val existing = firstOrNull { it.isAuto() }
        ?: firstOrNull { it.displayName.equals("Auto", ignoreCase = true) }
    val rest = filterNot { model ->
        model.id == existing?.id || model.isAuto()
    }.distinctModels()
    return listOf(existing ?: autoModel()) + rest
}

fun List<CursorModel>.distinctModels(): List<CursorModel> {
    val byId = linkedMapOf<String, CursorModel>()
    for (model in this) {
        if (model.isAuto()) continue
        val idKey = model.id.trim().lowercase()
        val current = byId[idKey]
        byId[idKey] = if (current == null) model else current.mergeWith(model)
    }
    val byName = linkedMapOf<String, CursorModel>()
    for (model in byId.values) {
        val key = model.canonicalKey()
        val current = byName[key]
        byName[key] = if (current == null) model else current.mergeWith(model)
    }
    return byName.values.toList()
}

private val MODEL_MODE_SUFFIX =
    Regex("[:/_\\-\\s]+(fast|thinking|high|low|max|min|default)$")

fun CursorModel.canonicalKey(): String {
    val raw = displayName?.trim()?.lowercase().orEmpty().ifBlank { id.lowercase() }
    return raw.replace(MODEL_MODE_SUFFIX, "").trim().ifBlank { id.lowercase() }
}

fun CursorModel.cleanedDisplayName(): String? {
    val name = displayName?.trim()?.ifBlank { null } ?: return null
    return name.replace(MODEL_MODE_SUFFIX, "").trim().ifBlank { name }
}

fun CursorModel.inferredModeVariant(): ModelVariant? {
    val source = (displayName ?: id).trim().lowercase()
    val mode = MODEL_MODE_SUFFIX.find(source)?.groupValues?.getOrNull(1) ?: return null
    if (mode == "default") return null
    return ModelVariant(
        displayName = mode,
        params = listOf(
            ModelParamValue(
                id = "fast",
                value = if (mode == "fast") "true" else "false",
            ),
        ),
    )
}

fun CursorModel.mergeWith(other: CursorModel): CursorModel {
    val keep = if (id.length <= other.id.length) this else other
    val extra = if (keep.id == id) other else this
    val names = listOfNotNull(keep.cleanedDisplayName(), extra.cleanedDisplayName())
    val display = names.minByOrNull { it.length } ?: keep.displayName
    val inferred = listOfNotNull(keep.inferredModeVariant(), extra.inferredModeVariant())
    var mergedVariants = (keep.variants + extra.variants + inferred).distinctBy { variant ->
        variant.displayName.orEmpty() + "|" +
            variant.params.joinToString { "${it.id}=${it.value}" }
    }
    if (mergedVariants.size == 1 && keep.variants.isEmpty() && extra.variants.isEmpty()) {
        mergedVariants = listOf(
            ModelVariant(displayName = "default", isDefault = true),
            mergedVariants.first(),
        )
    }
    return keep.copy(
        displayName = display,
        description = keep.description?.ifBlank { null } ?: extra.description,
        aliases = (keep.aliases + extra.aliases + extra.id).distinct(),
        parameters = (keep.parameters + extra.parameters).distinctBy { it.id.lowercase() },
        variants = mergedVariants,
    )
}

data class ModelModeOption(
    val label: String,
    val params: List<ModelParamValue>,
)

fun CursorModel.modeOptions(): List<ModelModeOption> {
    if (isAuto()) return emptyList()
    if (variants.isNotEmpty()) {
        return variants.map { variant ->
            ModelModeOption(
                label = variant.displayName?.ifBlank { null }
                    ?: modeLabel(variant.params),
                params = variant.params,
            )
        }.distinctBy { it.label.lowercase() }
    }
    return adjustableParams().flatMap { param ->
        param.values.map { option ->
            ModelModeOption(
                label = modeOptionLabel(param.id, option.value, option.displayName),
                params = listOf(ModelParamValue(param.id, option.value)),
            )
        }
    }.distinctBy { it.label.lowercase() }
}

fun CursorModel.hasModeMenu(): Boolean = !isAuto()

fun List<ModelParamValue>.upsert(id: String, value: String): List<ModelParamValue> =
    filterNot { it.id.equals(id, ignoreCase = true) } + ModelParamValue(id, value)

fun List<ModelParamValue>.valueOf(id: String): String? =
    firstOrNull { it.id.equals(id, ignoreCase = true) }?.value

fun CursorModel.effectiveParameters(): List<ModelParameter> {
    if (isAuto()) return emptyList()
    val advertised = adjustableParams()
    if (advertised.isNotEmpty()) return advertised
    val fromVariants = linkedMapOf<String, MutableList<ModelParamOption>>()
    for (variant in variants) {
        for (param in variant.params) {
            val options = fromVariants.getOrPut(param.id.lowercase()) { mutableListOf() }
            if (options.none { it.value.equals(param.value, ignoreCase = true) }) {
                options += ModelParamOption(param.value, param.value)
            }
        }
    }
    if (fromVariants.isNotEmpty()) {
        return fromVariants.map { (id, values) ->
            ModelParameter(id = id, displayName = id.replace('_', ' '), values = values)
        }
    }
    return listOf(
        ModelParameter(
            id = "fast",
            displayName = "Fast",
            values = listOf(
                ModelParamOption("false", "Off"),
                ModelParamOption("true", "On"),
            ),
        ),
        ModelParameter(
            id = "reasoning_effort",
            displayName = "Effort",
            values = listOf(
                ModelParamOption("low", "Low"),
                ModelParamOption("medium", "Medium"),
                ModelParamOption("high", "High"),
            ),
        ),
    )
}

data class FastControl(
    val id: String,
    val onValue: String,
    val offValue: String,
)

data class EffortControl(
    val id: String,
    val values: List<ModelParamOption>,
)

fun CursorModel.fastControl(): FastControl? {
    val param = effectiveParameters().firstOrNull { it.id.equals("fast", ignoreCase = true) }
        ?: return null
    val on = param.values.firstOrNull {
        it.value.equals("true", ignoreCase = true) || it.value.equals("fast", ignoreCase = true)
    }?.value ?: param.values.firstOrNull()?.value ?: "true"
    val off = param.values.firstOrNull { !it.value.equals(on, ignoreCase = true) }?.value ?: "false"
    return FastControl(id = param.id, onValue = on, offValue = off)
}

fun CursorModel.effortControl(): EffortControl? {
    val param = effectiveParameters().firstOrNull { parameter ->
        val id = parameter.id.lowercase()
        id == "effort" ||
            id == "reasoning_effort" ||
            id == "reasoning" ||
            id == "thinking" ||
            id.contains("effort")
    } ?: return null
    if (param.values.isEmpty()) return null
    return EffortControl(id = param.id, values = param.values)
}

fun CursorModel.paramsWithDefaults(current: List<ModelParamValue>): List<ModelParamValue> {
    if (isAuto()) return emptyList()
    var next = current.ifEmpty { defaultSelection().params.orEmpty() }
    fastControl()?.let { fast ->
        if (next.valueOf(fast.id) == null) next = next.upsert(fast.id, fast.offValue)
    }
    effortControl()?.let { effort ->
        if (next.valueOf(effort.id) == null) {
            val medium = effort.values.firstOrNull {
                it.value.equals("medium", ignoreCase = true)
            } ?: effort.values.first()
            next = next.upsert(effort.id, medium.value)
        }
    }
    return next
}

fun modeLabel(params: List<ModelParamValue>): String {
    if (params.isEmpty()) return "default"
    return params.joinToString(" · ") { modeOptionLabel(it.id, it.value, null) }
}

fun modeOptionLabel(paramId: String, value: String, displayName: String?): String {
    displayName?.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
    if (paramId.equals("fast", ignoreCase = true)) {
        return if (value.equals("true", ignoreCase = true)) "fast" else "thinking"
    }
    return value.replace('_', ' ').lowercase()
}

@Serializable
data class ModelsResponse(
    val items: List<CursorModel> = emptyList(),
)

@Serializable
data class AccountInfo(
    val apiKeyName: String? = null,
    val createdAt: String? = null,
    val userId: Long? = null,
    val userEmail: String? = null,
    val userFirstName: String? = null,
    val userLastName: String? = null,
) {
    fun displayName(): String {
        val combined = listOfNotNull(
            userFirstName?.trim()?.ifBlank { null },
            userLastName?.trim()?.ifBlank { null },
        ).joinToString(" ").trim()
        return combined.ifBlank { userEmail ?: apiKeyName ?: "account" }
    }
}

@Serializable
data class PrivateWorker(
    val workerId: String = "",
    val isInUse: Boolean = false,
    val repoOwner: String? = null,
    val repoName: String? = null,
    val repoUrl: String? = null,
    val workspaceRootPath: String? = null,
    val workspacePaths: List<String> = emptyList(),
    val connectedAtMs: Long? = null,
    val userId: Long? = null,
    val teamId: Long? = null,
    val serviceAccountId: String? = null,
    val activeBcId: String? = null,
    val name: String? = null,
    val id: String? = null,
    @SerialName("displayName") val apiDisplayName: String? = null,
    val machineName: String? = null,
    val hostname: String? = null,
) {
    fun resolvedId(): String = workerId.ifBlank { id.orEmpty() }

    fun displayName(): String =
        (
            sequenceOf(name, apiDisplayName, machineName, hostname)
                .mapNotNull { it?.trim()?.ifBlank { null } }
                .firstOrNull { !it.isOpaqueId() }
                ?: workspaceRootPath?.substringAfterLast('/')?.ifBlank { null }
                ?: resolvedId().takeIf { it.isNotBlank() && !it.isOpaqueId() }
                ?: "This computer"
            ).humanMachineName()

    fun subtitle(): String = buildString {
        if (isInUse) append("Busy") else append("Idle")
        primaryWorkspacePath()?.let { append(" · $it") }
        repoUrl?.takeIf { it.isNotBlank() }?.let { append(" · ${it.substringAfterLast('/')}") }
    }

    fun primaryWorkspacePath(): String? =
        workspaceDirectories().firstOrNull()

    fun workspaceDirectories(): List<String> =
        (listOfNotNull(workspaceRootPath) + workspacePaths)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Public create prefers the worker `--name` over the opaque worker id. */
    fun routingName(): String =
        sequenceOf(name, apiDisplayName, machineName, hostname)
            .mapNotNull { it?.trim()?.ifBlank { null } }
            .firstOrNull { !it.isOpaqueId() }
            ?: resolvedId()
}

@Serializable
data class WorkersResponse(
    val workers: List<PrivateWorker> = emptyList(),
    val totalCount: Int? = null,
    val nextPageToken: String? = null,
)

@Serializable
data class GitHubRepository(
    val url: String,
    val owner: String? = null,
    val name: String? = null,
) {
    fun label(): String = name?.ifBlank { null }
        ?: url.removePrefix("https://github.com/").removePrefix("http://github.com/")
}

@Serializable
data class RepositoriesResponse(
    val items: List<GitHubRepository> = emptyList(),
)

enum class AttachmentKind { IMAGE, FILE }

@Serializable
data class DraftAttachment(
    val id: String,
    val mimeType: String,
    val base64Data: String = "",
    val displayName: String? = null,
    val kind: AttachmentKind = AttachmentKind.IMAGE,
    val textPreview: String? = null,
    val sizeBytes: Long? = null,
    /** App-private encoded image blob used instead of putting large base64 strings in DataStore. */
    val backingFilePath: String? = null,
) {
    val isImage: Boolean get() = kind == AttachmentKind.IMAGE
}

@Serializable
data class ChatFolder(
    val id: String,
    val name: String,
)

fun defaultFolders(): List<ChatFolder> = listOf(
    ChatFolder("inbox", "inbox"),
    ChatFolder("later", "later"),
    ChatFolder("archive", "archive"),
)

@Serializable
data class ActiveRun(
    val agentId: String,
    val runId: String,
    val title: String = "",
)

@Serializable
data class LocalAppState(
    val folders: List<ChatFolder> = defaultFolders(),
    val agentFolderIds: Map<String, String> = emptyMap(),
    val lastModelByAgent: Map<String, String> = emptyMap(),
    val lastGlobalModel: String = AUTO_MODEL_ID,
    val queuedPrompts: Map<String, List<QueuedPrompt>> = emptyMap(),
    val activeRuns: List<ActiveRun> = emptyList(),
    val chatTitles: Map<String, String> = emptyMap(),
    val chatIcons: Map<String, String> = emptyMap(),
    val notifiedDecisions: Set<String> = emptySet(),
    val lastReadAtByAgent: Map<String, String> = emptyMap(),
    val pinnedAgentIds: List<String> = emptyList(),
    /** Local names for machines the public API only exposes as opaque worker ids. */
    val machineAliases: Map<String, String> = emptyMap(),
    /** Unsent composer text + attachments, keyed by agent id. Survives leaving a chat. */
    val composerDrafts: Map<String, ComposerDraft> = emptyMap(),
)

@Serializable
data class ComposerDraft(
    val text: String = "",
    val attachments: List<DraftAttachment> = emptyList(),
) {
    val isEmpty: Boolean get() = text.isBlank() && attachments.isEmpty()
}

enum class InboxFilter { ALL, WORKING, DONE }

enum class InboxSectionKind { PINNED, DONE, WORKING, WORKSPACE }

enum class InboxCategory { WORKING, DONE, WORKSPACE }

data class InboxListSection(
    val id: String,
    val title: String,
    val kind: InboxSectionKind,
    val agents: List<AgentSummary>,
)

private val WORKING_STATUSES = setOf(
    "CREATING", "RUNNING", "WORKING", "ACTIVE", "IN_PROGRESS",
)

private val ATTENTION_STATUSES = setOf(
    "ERROR", "FAILED",
    "BLOCKED", "PAUSED", "TIMED_OUT", "TIMEOUT", "NEEDS_INPUT",
    "WAITING_FOR_USER", "AWAITING_USER", "NEEDS_ATTENTION",
)

fun AgentSummary.normalizedStatus(): String = status.orEmpty().uppercase()

fun AgentSummary.isWorkingStatus(): Boolean = normalizedStatus() in WORKING_STATUSES

fun AgentSummary.needsAttention(): Boolean {
    val status = normalizedStatus()
    if (status in ATTENTION_STATUSES) return true
    if (status.isBlank()) return false
    return status.contains("FAIL") ||
        status.contains("ERROR") ||
        status.contains("BLOCK") ||
        status.contains("TIMEOUT") ||
        status.contains("TIMED_OUT") ||
        status.contains("WAITING_FOR_USER") ||
        status.contains("AWAITING_USER") ||
        status.contains("NEEDS_INPUT")
}

fun AgentSummary.hasReviewPr(latestRun: RunSummary?): Boolean {
    if (repos?.any { !it.prUrl.isNullOrBlank() } == true) return true
    return latestRun?.git?.branches.orEmpty().any { !it.prUrl.isNullOrBlank() }
}

fun AgentSummary.isDoneWorking(): Boolean =
    !isWorkingStatus() && normalizedStatus().isNotBlank()

fun AgentSummary.activityInstant(latestRun: RunSummary? = null): java.time.Instant? {
    val times = listOfNotNull(updatedAt, latestRun?.updatedAt).mapNotNull { raw ->
        runCatching { java.time.Instant.parse(raw) }.getOrNull()
    }
    return times.maxOrNull()
}

fun AgentSummary.isUnread(
    lastReadAt: String?,
    latestRun: RunSummary? = null,
): Boolean {
    val activity = activityInstant(latestRun)
    val read = lastReadAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
    if (activity == null) return read == null
    if (read == null) return true
    return activity > read
}

/**
 * Exclusive inbox placement: busy work, unread finished work, then the rest.
 */
fun AgentSummary.inboxCategory(
    latestRun: RunSummary? = null,
    locallyWorking: Boolean = false,
    lastReadAt: String? = null,
): InboxCategory = when {
    locallyWorking || isWorkingStatus() -> InboxCategory.WORKING
    isDoneWorking() && isUnread(lastReadAt, latestRun) -> InboxCategory.DONE
    else -> InboxCategory.WORKSPACE
}

fun AgentSummary.repoShortName(): String? {
    val raw = repoLabel()?.substringAfterLast('/')?.substringBefore(".git")?.trim().orEmpty()
    return raw.takeIf { it.isNotBlank() }
}

fun AgentSummary.workspaceHeading(
    workers: List<PrivateWorker>,
    aliases: Map<String, String> = emptyMap(),
): String {
    machineLabel(workers, aliases)?.takeIf { it.isNotBlank() && !it.equals("cloud", true) }?.let { return it }
    if (env?.type.equals("machine", ignoreCase = true)) return REMOTE_CONTROL_LABEL
    repoShortName()?.let { return it }
    return "cloud"
}

fun AgentSummary.headline(
    workers: List<PrivateWorker>,
    titles: Map<String, String> = emptyMap(),
): String {
    titles[id]?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    name?.trim()?.takeIf { it.isNotBlank() && !it.isOpaqueId() }?.let { return it }
    machineLabel(workers)?.let { return it }
    repoShortName()?.let { return it }
    return "agent"
}

fun AgentSummary.matchesFilter(
    filter: InboxFilter,
    latestRun: RunSummary? = null,
    locallyWorking: Boolean = false,
    lastReadAt: String? = null,
): Boolean {
    val category = inboxCategory(latestRun, locallyWorking, lastReadAt)
    return when (filter) {
        InboxFilter.ALL -> true
        InboxFilter.WORKING -> category == InboxCategory.WORKING
        InboxFilter.DONE -> category == InboxCategory.DONE
    }
}

fun groupInboxSections(
    agents: List<AgentSummary>,
    workers: List<PrivateWorker>,
    latestRuns: Map<String, RunSummary> = emptyMap(),
    locallyWorkingIds: Set<String> = emptySet(),
    lastReadAtByAgent: Map<String, String> = emptyMap(),
    pinnedAgentIds: List<String> = emptyList(),
    machineAliases: Map<String, String> = emptyMap(),
): List<InboxListSection> {
    val remaining = agents.toMutableList()
    val sections = mutableListOf<InboxListSection>()

    fun take(predicate: (AgentSummary) -> Boolean): List<AgentSummary> {
        val matched = remaining.filter(predicate)
        val ids = matched.map { it.id }.toSet()
        remaining.removeAll { it.id in ids }
        return matched
    }

    fun categoryOf(agent: AgentSummary) = agent.inboxCategory(
        latestRuns[agent.id],
        agent.id in locallyWorkingIds,
        lastReadAtByAgent[agent.id],
    )

    val pinned = pinnedAgentIds.mapNotNull { id -> remaining.find { it.id == id } }
    if (pinned.isNotEmpty()) {
        val pinnedIds = pinned.map { it.id }.toSet()
        remaining.removeAll { it.id in pinnedIds }
        sections += InboxListSection(
            id = "pinned",
            title = "pinned",
            kind = InboxSectionKind.PINNED,
            agents = pinned,
        )
    }

    val done = take { categoryOf(it) == InboxCategory.DONE }
    if (done.isNotEmpty()) {
        sections += InboxListSection(
            id = "done",
            title = "done",
            kind = InboxSectionKind.DONE,
            agents = done,
        )
    }

    val working = take { categoryOf(it) == InboxCategory.WORKING }
    if (working.isNotEmpty()) {
        sections += InboxListSection(
            id = "working",
            title = "busy",
            kind = InboxSectionKind.WORKING,
            agents = working,
        )
    }

    remaining.groupBy { it.workspaceHeading(workers, machineAliases) }
        .entries
        .sortedBy { (heading, _) -> heading.lowercase() }
        .forEach { (heading, group) ->
            sections += InboxListSection(
                id = "workspace-$heading",
                title = heading,
                kind = InboxSectionKind.WORKSPACE,
                agents = group,
            )
        }

    return sections
}

const val REMOTE_CONTROL_LABEL = "remote control"

fun AgentSummary.machineLabel(
    workers: List<PrivateWorker>,
    aliases: Map<String, String> = emptyMap(),
): String? = resolveMachineLabel(env, id, workers, aliases)

fun AgentDetail.machineLabel(
    workers: List<PrivateWorker>,
    aliases: Map<String, String> = emptyMap(),
): String? = resolveMachineLabel(env, id, workers, aliases)

/** A machine an agent can target: a CLI `agent worker` or a desktop with remote control on. */
data class KnownMachine(
    val id: String,
    val label: String,
    val remoteControl: Boolean,
    val worker: PrivateWorker? = null,
    val agentCount: Int = 0,
) {
    val shortId: String get() = id.removePrefix("cursor-agent-worker-").take(10)
}

/**
 * Everything the account can run on, from two sources the public API exposes:
 * `/v0/private-workers` (CLI workers) and the `env.name` on existing machine agents,
 * which is the only trace of a remote-control desktop.
 */
fun knownMachines(
    workers: List<PrivateWorker>,
    agents: List<AgentSummary>,
    aliases: Map<String, String> = emptyMap(),
): List<KnownMachine> {
    val out = mutableListOf<KnownMachine>()
    workers.forEach { worker ->
        val id = worker.resolvedId().ifBlank { worker.displayName() }
        out += KnownMachine(
            id = id,
            label = aliases[id]?.takeIf { it.isNotBlank() } ?: worker.displayName(),
            remoteControl = false,
            worker = worker,
            agentCount = agents.count { workers.findMatching(it.env, it.id) == worker },
        )
    }
    agents
        .filter { it.env?.type.equals("machine", ignoreCase = true) }
        .mapNotNull { agent -> agent.env?.refs()?.firstOrNull()?.let { it to agent } }
        .filter { (ref, agent) -> workers.findMatching(agent.env, agent.id) == null && ref.isNotBlank() }
        .groupBy { (ref, _) -> ref }
        .forEach { (ref, group) ->
            if (out.none { it.id == ref }) {
                val alias = aliases[ref]?.takeIf { it.isNotBlank() }
                out += KnownMachine(
                    id = ref,
                    label = alias ?: if (ref.isOpaqueId()) REMOTE_CONTROL_LABEL else ref.humanMachineName(),
                    remoteControl = true,
                    agentCount = group.size,
                )
            }
        }
    return out
}

fun AgentDetail.headline(
    workers: List<PrivateWorker>,
    titles: Map<String, String> = emptyMap(),
): String {
    titles[id]?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    name?.trim()?.takeIf { it.isNotBlank() && !it.isOpaqueId() }?.let { return it }
    machineLabel(workers)?.let { return it }
    return "agent"
}

fun resolveMachineLabel(
    env: AgentEnv?,
    agentId: String?,
    workers: List<PrivateWorker>,
    aliases: Map<String, String> = emptyMap(),
): String? {
    env?.refs()?.firstNotNullOfOrNull { ref -> aliases[ref]?.takeIf { it.isNotBlank() } }
        ?.let { return it }
    val match = workers.findMatching(env, agentId)
    val fromWorker = match?.displayName()?.takeIf { it.isNotBlank() && !it.isOpaqueId() }
    if (fromWorker != null) return fromWorker
    val fromEnv = env?.refs()?.firstOrNull { !it.isOpaqueId() && !it.equals("machine", true) }
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val type = env?.type?.trim().orEmpty()
    return when {
        type.equals("cloud", true) -> "Cloud"
        type.equals("pool", true) -> "Pool"
        type.isNotBlank() && !type.equals("machine", true) -> type
        else -> null
    }
}

fun List<PrivateWorker>.findMatching(env: AgentEnv?, agentId: String?): PrivateWorker? {
    if (!agentId.isNullOrBlank()) {
        firstOrNull { it.activeBcId.equals(agentId, ignoreCase = true) }?.let { return it }
    }
    val refs = env?.refs().orEmpty()
    if (refs.isEmpty()) return null
    return firstOrNull { worker -> refs.any { worker.matchesRef(it) } }
}

fun PrivateWorker.matchesRef(raw: String): Boolean {
    val ref = raw.trim()
    if (ref.isBlank()) return false
    val keys = listOfNotNull(
        resolvedId().ifBlank { null },
        name,
        apiDisplayName,
        machineName,
        hostname,
        displayName(),
    )
    if (keys.any { it.equals(ref, ignoreCase = true) }) return true
    if (keys.any { it.hostKey() == ref.hostKey() }) return true
    val id = resolvedId()
    if (id.isNotBlank() && (
            id.startsWith(ref, ignoreCase = true) ||
                ref.startsWith(id.take(8), ignoreCase = true)
            )
    ) {
        return true
    }
    val path = workspaceRootPath.orEmpty()
    return path.equals(ref, ignoreCase = true) || path.endsWith("/$ref", ignoreCase = true)
}

fun String.isOpaqueId(): Boolean {
    val value = trim()
    if (value.isEmpty()) return true
    if (value.startsWith("bc-", ignoreCase = true)) return true
    if (value.startsWith("cursor-agent-worker-", ignoreCase = true)) return true
    if (value.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
        return true
    }
    return value.length == 8 && value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
}

/** `agent worker` names machines like "~ @ Alex's MacBook Pro"; keep only the machine part. */
fun String.humanMachineName(): String =
    if (contains(" @ ")) substringAfterLast(" @ ").trim().ifBlank { this } else this

/** Drop `/Users/<name>/` or `/home/<name>/` so chat diffs never show a local account path. */
fun String.withoutHomeDirectory(): String {
    val match = Regex("^/(?:Users|home)/[^/]+(?:/|$)").find(this) ?: return this
    return substring(match.range.last + 1).ifBlank { "~" }
}

private fun String.hostKey(): String =
    lowercase().removeSuffix(".local").substringBefore('.')

fun AgentSummary.repoLabel(): String? =
    repos?.firstOrNull()?.url
        ?.removePrefix("https://github.com/")
        ?.removePrefix("http://github.com/")

@Serializable
data class ChangedFile(
    val path: String,
    val tool: String,
    val linesAdded: Int? = null,
    val linesRemoved: Int? = null,
) {
    val fileName: String
        get() = path.substringAfterLast('/').ifBlank { path }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    val shortPath: String
        get() {
            val stripped = path.withoutHomeDirectory()
            return if (stripped.length > 64) "…${stripped.takeLast(63)}" else stripped
        }
}

@Serializable
data class AgentTodo(
    val id: String,
    val content: String,
    val status: String,
) {
    val isCompleted: Boolean
        get() = status.contains("COMPLETED", ignoreCase = true)
    val isInProgress: Boolean
        get() = status.contains("IN_PROGRESS", ignoreCase = true) ||
            status.contains("INPROGRESS", ignoreCase = true)
    val isCancelled: Boolean
        get() = status.contains("CANCELLED", ignoreCase = true) ||
            status.contains("CANCELED", ignoreCase = true)
}

data class RunExtras(
    val runId: String,
    val userText: String? = null,
    val todos: List<AgentTodo> = emptyList(),
    val files: List<ChangedFile> = emptyList(),
    val resultText: String? = null,
) {
    fun toInboxChangeStats(): InboxChangeStats {
        val added = files.mapNotNull { it.linesAdded }
        val removed = files.mapNotNull { it.linesRemoved }
        return InboxChangeStats(
            fileCount = files.size,
            linesAdded = added.takeIf { it.isNotEmpty() }?.sum(),
            linesRemoved = removed.takeIf { it.isNotEmpty() }?.sum(),
        )
    }
}

@Serializable
data class InboxChangeStats(
    val fileCount: Int,
    val linesAdded: Int? = null,
    val linesRemoved: Int? = null,
)

data class StreamFrame(
    val id: String?,
    val event: StreamEvent,
)

sealed interface StreamEvent {
    data class Status(val runId: String?, val status: String?) : StreamEvent
    data class Assistant(val text: String) : StreamEvent
    data class Thinking(val text: String) : StreamEvent
    data class ToolCall(
        val callId: String?,
        val name: String?,
        val status: String?,
        val path: String? = null,
        val todos: List<AgentTodo> = emptyList(),
        val linesAdded: Int? = null,
        val linesRemoved: Int? = null,
    ) : StreamEvent
    data class UserMessage(val text: String) : StreamEvent
    data class Result(val status: String?, val text: String?, val git: RunGit? = null) : StreamEvent
    data class Error(val code: String?, val message: String?) : StreamEvent
    data object Done : StreamEvent
    data object Heartbeat : StreamEvent
    data class Unknown(val event: String, val data: String) : StreamEvent
}

@Serializable
data class ChatLine(
    val id: String,
    val kind: Kind,
    val text: String,
    val thinking: String = "",
    val runId: String? = null,
    val todos: List<AgentTodo> = emptyList(),
    val changedFiles: List<ChangedFile> = emptyList(),
    val extrasResolved: Boolean = true,
    val attachments: List<DraftAttachment> = emptyList(),
    val queued: Boolean = false,
) {
    enum class Kind { USER, ASSISTANT, TOOL, STATUS, ERROR }

    fun forCache(): ChatLine = copy(
        attachments = emptyList(),
        thinking = thinking.take(12_000),
        queued = false,
    )
}

private fun ChatLine.isResolvedContent(): Boolean =
    kind == ChatLine.Kind.USER ||
        (kind == ChatLine.Kind.ASSISTANT && text.isNotBlank())

/**
 * Drops error rows that later conversation activity has already superseded.
 *
 * Assistant placeholders are inserted before a send/stream failure, then filled in
 * if the turn recovers — so an error can sit after the successful reply in the list.
 */
fun List<ChatLine>.withoutResolvedErrors(): List<ChatLine> =
    filterIndexed { index, line ->
        line.kind != ChatLine.Kind.ERROR || !errorIsResolved(index)
    }

private fun List<ChatLine>.errorIsResolved(errorIndex: Int): Boolean {
    val line = getOrNull(errorIndex) ?: return false
    if (drop(errorIndex + 1).any { it.isResolvedContent() }) return true
    val lastUserBefore = (errorIndex - 1 downTo 0).firstOrNull { this[it].kind == ChatLine.Kind.USER }
    if (lastUserBefore != null) {
        val turnHasAssistantReply = subList(lastUserBefore + 1, size).any {
            it.kind == ChatLine.Kind.ASSISTANT && it.text.isNotBlank()
        }
        if (turnHasAssistantReply) return true
    }
    return line.text.isTransientNetworkErrorText() && any { it.isResolvedContent() }
}

fun resolvedAgentError(
    error: String?,
    lines: List<ChatLine>,
    streaming: Boolean = false,
): String? {
    if (error.isNullOrBlank() || streaming) return null
    if (error.isTransientNetworkErrorText() && lines.any { it.isResolvedContent() }) return null
    val lastError = lines.indexOfLast { it.kind == ChatLine.Kind.ERROR }
    if (lastError >= 0 && lines.errorIsResolved(lastError)) return null
    val lastResolved = lines.indexOfLast { it.isResolvedContent() }
    if (lastResolved >= 0 && lastResolved > lastError) return null
    return error
}

@Serializable
data class QueuedPrompt(
    val id: String,
    val displayText: String,
    val apiText: String,
    val attachments: List<DraftAttachment> = emptyList(),
    val modelId: String = AUTO_MODEL_ID,
    val modelParams: List<ModelParamValue> = emptyList(),
)

enum class SendMode { QUEUE, NOW }

fun Throwable.isTransientNetworkFailure(): Boolean {
    if (this is java.net.UnknownHostException ||
        this is java.net.ConnectException ||
        this is java.net.SocketTimeoutException ||
        this is java.net.NoRouteToHostException ||
        this is java.net.SocketException ||
        this is java.io.InterruptedIOException
    ) {
        return true
    }
    val text = generateSequence(this) { it.cause }
        .map { it.message.orEmpty() }
        .joinToString(" ")
        .lowercase()
    return text.contains("failed to connect") ||
        text.contains("unable to resolve host") ||
        text.contains("unknown host") ||
        text.contains("timed out") ||
        text.contains("timeout") ||
        text.contains("connection reset") ||
        text.contains("connection refused") ||
        text.contains("network is unreachable") ||
        text.contains("software caused connection abort") ||
        text.contains("broken pipe") ||
        text.contains("ssl handshake") ||
        text.contains("unexpected end of stream")
}

fun CharSequence.isTransientNetworkErrorText(): Boolean {
    val text = toString().lowercase()
    return text.contains("failed to connect") ||
        text.contains("couldn't reach") ||
        text.contains("couldn’t reach") ||
        text.contains("unable to resolve") ||
        text.contains("unknown host") ||
        text.contains("timeout") ||
        text.contains("timed out") ||
        text.contains("connection reset") ||
        text.contains("connection refused") ||
        text.contains("network is unreachable")
}

/** Leftover connect / reachability copy from an earlier retry that later succeeded. */
fun CharSequence.isStaleConnectErrorText(): Boolean = isTransientNetworkErrorText()

fun Exception.isTransientStreamLoss(): Boolean {
    if (isTransientNetworkFailure()) return true
    val text = buildString {
        append(message.orEmpty())
        if (this@isTransientStreamLoss is ApiException) {
            append(' ')
            append(body)
            append(' ')
            append(statusCode)
        }
    }.lowercase()
    if (this is ApiException && statusCode in setOf(408, 410, 429, 502, 503, 504)) return true
    return text.contains("no longer available") ||
        text.contains("stream_expired") ||
        text.contains("stream expired") ||
        text.contains("last-event-id")
}

fun StreamEvent.Error.isTransientStreamLoss(): Boolean {
    val text = "${code.orEmpty()} ${message.orEmpty()}".lowercase()
    return text.isTransientNetworkErrorText() ||
        text.contains("no longer available") ||
        text.contains("stream_expired") ||
        text.contains("stream expired") ||
        text.contains("expired")
}

fun String?.isTerminalRun(): Boolean {
    val status = orEmpty().uppercase()
    return status in setOf(
        "FINISHED",
        "ERROR",
        "FAILED",
        "CANCELLED",
        "CANCELED",
        "EXPIRED",
    )
}

fun String?.isActiveRun(): Boolean =
    equals("RUNNING", ignoreCase = true) || equals("CREATING", ignoreCase = true)

fun String.asGithubRepoUrl(): String {
    val value = trim()
    if (value.isEmpty()) return value
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("github.com/", ignoreCase = true)) return "https://$value"
    return value
}

/** SSE can keep the socket open after the run finishes; stop reading on these events. */
fun StreamEvent.endsSse(): Boolean = when (this) {
    StreamEvent.Done -> true
    is StreamEvent.Result -> true
    is StreamEvent.Status -> status.isTerminalRun()
    is StreamEvent.Error -> true
    else -> false
}

/** Merge a stream token that may be a delta or a cumulative snapshot. */
fun mergeStreamText(current: String, incoming: String): String =
    mergeStreamText(current, incoming, priorAssistantTexts = emptyList())

fun mergeStreamText(
    current: String,
    incoming: String,
    priorAssistantTexts: List<String>,
): String {
    if (incoming.isEmpty()) return current
    if (isReplayOfPriorTurn(incoming, priorAssistantTexts)) {
        return if (isReplayOfPriorTurn(current, priorAssistantTexts)) "" else current
    }
    if (current.isEmpty()) return incoming
    if (incoming == current) return current
    if (incoming.startsWith(current)) return incoming
    if (current.startsWith(incoming)) return current
    if (current.endsWith(incoming)) return current
    if (isReplayOfPriorTurn(current, priorAssistantTexts)) return incoming
    // A new assistant ping after a finished sentence is a replacement, not a delta.
    if (looksLikeNewAssistantMessage(current, incoming)) return incoming
    // Long non-overlapping chunks are snapshots of a different message, not deltas.
    if (incoming.length > 24 && !incoming.startsWith(current.take(24)) && !current.endsWith(incoming.take(12))) {
        return incoming
    }
    return current + incoming
}

fun looksLikeNewAssistantMessage(current: String, incoming: String): Boolean {
    if (current.isBlank() || incoming.isBlank()) return false
    if (incoming.startsWith(current) || current.startsWith(incoming)) return false
    val last = current.trimEnd().lastOrNull() ?: return false
    val ended = last == '.' || last == '!' || last == '?' || last == '…'
    val start = incoming.trimStart().firstOrNull() ?: return false
    return ended && (start.isUpperCase() || start == '#')
}

fun isReplayOfPriorTurn(incoming: String, priorAssistantTexts: List<String>): Boolean {
    val next = incoming.trim()
    if (next.isBlank() || priorAssistantTexts.isEmpty()) return false
    return priorAssistantTexts.any { looksLikePreviousTurn(next, it) }
}

/** History may still be the previous turn; never clobber a live reply with a different message. */
fun shouldAdoptHistoryText(
    current: String,
    incoming: String,
    previousTurnText: String? = null,
    priorAssistantTexts: List<String> = emptyList(),
): Boolean {
    if (incoming.isBlank()) return false
    if (incoming == current) return false
    val priors = buildList {
        previousTurnText?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(priorAssistantTexts.map { it.trim() }.filter { it.isNotBlank() })
    }.distinct()
    if (isReplayOfPriorTurn(incoming, priors)) return false
    // A brand-new bubble must wait for SSE; conversation history is still the last turn.
    if (current.isBlank()) return false
    return incoming.startsWith(current)
}

fun looksLikePreviousTurn(incoming: String, previousTurnText: String): Boolean {
    val prior = previousTurnText.trim()
    val next = incoming.trim()
    if (prior.isBlank() || next.isBlank()) return false
    if (next == prior) return true
    if (prior.startsWith(next) || next.startsWith(prior)) return true
    return false
}

/** True when another client added a user turn that this device has not rendered yet. */
fun conversationHasRemoteAdvance(local: List<ChatLine>, remote: List<ChatLine>): Boolean {
    if (remote.isEmpty()) return false
    val localUsers = local
        .filter { it.kind == ChatLine.Kind.USER && !it.queued }
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
    val remoteUsers = remote
        .filter { it.kind == ChatLine.Kind.USER }
        .map { it.text.trim() }
        .filter { it.isNotBlank() }
    if (remoteUsers.size > localUsers.size) return true
    val lastRemote = remoteUsers.lastOrNull() ?: return false
    return lastRemote !in localUsers
}

/** Keep the local user+live-assistant tail so stale history cannot sit after a new prompt. */
fun mergeKeepingLocalTail(history: List<ChatLine>, local: List<ChatLine>): List<ChatLine> {
    val lastUser = local.lastOrNull { it.kind == ChatLine.Kind.USER && !it.queued }
        ?: return history.ifEmpty { local }
    val start = local.indexOfLast { it.id == lastUser.id }.coerceAtLeast(0)
    val tail = local.drop(start)
    val histIndex = history.indexOfLast {
        it.kind == ChatLine.Kind.USER && userTurnTextsMatch(it.text, lastUser.text)
    }
    return if (histIndex < 0) {
        history + tail
    } else {
        history.take(histIndex) + tail
    }
}

/**
 * Conversation history stores every assistant ping in a turn. Other clients show the
 * last one; keep only that so Android matches iOS / the web transcript.
 */
fun List<ConversationMessage>.toChatLines(): List<ChatLine> {
    if (isEmpty()) return emptyList()

    val out = mutableListOf<ChatLine>()
    var assistantText: String? = null
    var assistantId: String? = null

    fun flushAssistant() {
        val text = assistantText?.trim().orEmpty()
        if (text.isNotBlank()) {
            out += ChatLine(
                id = (assistantId ?: "hist-assistant-${out.size}").sanitizeId(),
                kind = ChatLine.Kind.ASSISTANT,
                text = text,
                extrasResolved = true,
            )
        }
        assistantText = null
        assistantId = null
    }

    for (message in this) {
        val text = message.text?.trim().orEmpty()
        val images = message.toDraftAttachments()
        if (text.isEmpty() && images.isEmpty()) continue
        when (message.type) {
            "user_message" -> {
                flushAssistant()
                out += ChatLine(
                    id = (message.id ?: "hist-user-${out.size}").sanitizeId(),
                    kind = ChatLine.Kind.USER,
                    text = text,
                    attachments = images,
                )
            }
            "assistant_message" -> {
                assistantText = text
                assistantId = message.id ?: assistantId ?: "hist-assistant-${out.size}"
            }
            else -> {
                flushAssistant()
                out += ChatLine(
                    id = (message.id ?: "hist-other-${out.size}").sanitizeId(),
                    kind = ChatLine.Kind.STATUS,
                    text = text,
                )
            }
        }
    }
    flushAssistant()

    return out.mapIndexed { index, line ->
        line.copy(id = "${index}-${line.id}".sanitizeId())
    }
}

private fun ConversationMessage.toDraftAttachments(): List<DraftAttachment> =
    images.orEmpty().mapIndexedNotNull { index, image ->
        val data = image.data?.trim().orEmpty()
        val url = image.url?.trim().orEmpty()
        if (data.isBlank() && url.isBlank()) return@mapIndexedNotNull null
        DraftAttachment(
            id = "${id ?: "hist"}-img-$index".sanitizeId(),
            mimeType = image.mimeType?.ifBlank { null } ?: "image/jpeg",
            base64Data = data,
            displayName = "photo",
            kind = AttachmentKind.IMAGE,
        )
    }

/** Copy in-memory photo bytes onto history user turns (the conversation API is text-only). */
fun List<ChatLine>.preservingUserAttachmentsFrom(previous: List<ChatLine>): List<ChatLine> {
    val priorUsers = previous.filter {
        it.kind == ChatLine.Kind.USER && !it.queued && it.attachments.isNotEmpty()
    }
    if (priorUsers.isEmpty()) return this
    val histUsers = filter { it.kind == ChatLine.Kind.USER }
    var histUserOrdinal = -1
    return map { line ->
        if (line.kind != ChatLine.Kind.USER) return@map line
        histUserOrdinal++
        if (line.attachments.isNotEmpty()) return@map line
        val byText = priorUsers.lastOrNull { prior ->
            userTurnTextsMatch(prior.text, line.text)
        }
        if (byText != null) return@map line.copy(attachments = byText.attachments)
        if (histUserOrdinal == histUsers.lastIndex) {
            val lastPrior = priorUsers.last()
            return@map line.copy(attachments = lastPrior.attachments)
        }
        line
    }
}

fun resolveAssistantText(
    live: String,
    history: String,
    priorAssistantTexts: List<String>,
    preferHistoryWhenDistinct: Boolean,
): String {
    if (history.isBlank()) return live
    if (isReplayOfPriorTurn(history, priorAssistantTexts)) return live
    if (live.isBlank()) return if (preferHistoryWhenDistinct) history else live
    if (live == history) return live
    if (shouldAdoptHistoryText(live, history, priorAssistantTexts = priorAssistantTexts)) {
        return history
    }
    if (history.startsWith(live)) return history
    if (live.startsWith(history)) return live
    if (containsBuriedSnippet(live, history) ||
        priorAssistantTexts.any { containsBuriedSnippet(live, it) }
    ) {
        return history
    }
    return if (preferHistoryWhenDistinct) history else live
}

fun containsBuriedSnippet(live: String, part: String): Boolean {
    val snippet = part.trim()
    if (snippet.length < 24) return false
    val needle = snippet.take(48)
    val idx = live.indexOf(needle)
    return idx > 8
}

fun userTurnTextsMatch(local: String, remote: String): Boolean {
    val a = local.trim()
    val b = remote.trim()
    if (a == b) return true
    if (a.isBlank() || b.isBlank()) return false
    return a.isSyntheticAttachmentCaption() && b.isSyntheticAttachmentCaption()
}

fun String.isSyntheticAttachmentCaption(): Boolean {
    val value = trim().lowercase()
    return value == "please review the attached files." ||
        value.matches(Regex("sent \\d+ attachment(?:s|\\(s\\))?"))
}

/** Overlay the conversation's latest assistant reply onto the local tail. */
fun adoptLatestAssistantFromHistory(
    local: List<ChatLine>,
    history: List<ChatLine>,
    preferHistoryWhenDistinct: Boolean,
): List<ChatLine> {
    if (local.isEmpty() || history.isEmpty()) return local.ifEmpty { history }
    val lastUser = local.lastOrNull { it.kind == ChatLine.Kind.USER && !it.queued }
        ?: return local
    val userIdx = local.indexOfLast { it.id == lastUser.id }
    val assistantIdx = local.indexOfLast { it.kind == ChatLine.Kind.ASSISTANT && !it.queued }
    if (assistantIdx < 0 || userIdx > assistantIdx) return local
    val histUserIdx = history.indexOfLast {
        it.kind == ChatLine.Kind.USER && userTurnTextsMatch(it.text, lastUser.text)
    }
    if (histUserIdx < 0) return local
    val histAssistant = history.drop(histUserIdx).lastOrNull { it.kind == ChatLine.Kind.ASSISTANT }
        ?: return local
    val live = local[assistantIdx]
    val priors = local
        .filter { it.kind == ChatLine.Kind.ASSISTANT && it.id != live.id && it.text.isNotBlank() }
        .map { it.text }
    val next = resolveAssistantText(
        live = live.text,
        history = histAssistant.text,
        priorAssistantTexts = priors,
        preferHistoryWhenDistinct = preferHistoryWhenDistinct,
    )
    if (next == live.text) return local
    return local.toMutableList().also { lines ->
        lines[assistantIdx] = live.copy(
            text = next,
            thinking = live.thinking.ifBlank { histAssistant.thinking },
            todos = if (live.todos.isNotEmpty()) live.todos else histAssistant.todos,
            changedFiles = if (live.changedFiles.isNotEmpty()) {
                live.changedFiles
            } else {
                histAssistant.changedFiles
            },
            extrasResolved = true,
        )
    }
}

fun mergeConversation(
    history: List<ChatLine>,
    local: List<ChatLine>,
    streaming: Boolean,
): List<ChatLine> {
    if (history.isEmpty()) return local
    if (local.isEmpty()) return history
    val historyWithAttachments = history.preservingUserAttachmentsFrom(local)
    val merged = mergeKeepingLocalTail(historyWithAttachments, local)
    return adoptLatestAssistantFromHistory(
        local = merged,
        history = historyWithAttachments,
        preferHistoryWhenDistinct = !streaming,
    ).withoutResolvedErrors()
}

fun String?.isFailedRun(): Boolean {
    val status = orEmpty().uppercase()
    return status in setOf("ERROR", "FAILED", "EXPIRED")
}
