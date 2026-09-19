package app.cursor.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

class CursorApiClient(
    private val apiKeyProvider: () -> String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private fun authHeader(): String {
        val key = apiKeyProvider().trim()
        require(key.isNotEmpty()) { "Add an API key in Settings." }
        return Credentials.basic(key, "")
    }

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder()
            .url("$BASE_URL$path")
            .header("Authorization", authHeader())
            .header("Accept", "application/json")

    suspend fun listAgents(limit: Int = 30): AgentListResponse = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/agents?limit=$limit&includeArchived=false").get().build()
        execute(request, AgentListResponse.serializer())
    }

    suspend fun listModels(): List<CursorModel> = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/models").get().build()
        execute(request, ModelsResponse.serializer()).items.withAutoFirst()
    }

    suspend fun getMe(): AccountInfo = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/me").get().build()
        execute(request, AccountInfo.serializer())
    }

    suspend fun listWorkers(): List<PrivateWorker> = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v0/private-workers?status=all&scope=all&limit=50")
            .get()
            .build()
        execute(request, WorkersResponse.serializer()).workers
    }

    suspend fun listRepositories(): List<GitHubRepository> = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/repositories").get().build()
        execute(request, RepositoriesResponse.serializer()).items
    }

    suspend fun getAgent(agentId: String): AgentDetail = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/agents/$agentId").get().build()
        execute(request, AgentDetail.serializer())
    }

    suspend fun getConversation(agentId: String): ConversationResponse =
        withContext(Dispatchers.IO) {
            val request = requestBuilder("/v0/agents/$agentId/conversation").get().build()
            val body = executeBody(request)
            val parsed = json.decodeFromString(ConversationResponse.serializer(), body)
            val root = json.parseToJsonElement(body) as? JsonObject ?: return@withContext parsed
            val messagesJson = root["messages"]?.jsonArray ?: return@withContext parsed
            parsed.copy(
                messages = parsed.messages.mapIndexed { index, message ->
                    val obj = messagesJson.getOrNull(index) as? JsonObject ?: return@mapIndexed message
                    message.withModelFromJson(obj)
                },
            )
        }

    suspend fun listRuns(agentId: String, limit: Int = 20): RunListResponse =
        withContext(Dispatchers.IO) {
            val request = requestBuilder("/v1/agents/$agentId/runs?limit=$limit").get().build()
            val body = executeBody(request)
            val parsed = json.decodeFromString(RunListResponse.serializer(), body)
            val root = json.parseToJsonElement(body) as? JsonObject ?: return@withContext parsed
            val itemsJson = root["items"]?.jsonArray ?: return@withContext parsed
            parsed.copy(
                items = parsed.items.mapIndexed { index, run ->
                    val obj = itemsJson.getOrNull(index) as? JsonObject ?: return@mapIndexed run
                    run.withModelFromJson(obj)
                },
            )
        }

    suspend fun getRun(agentId: String, runId: String): RunSummary =
        withContext(Dispatchers.IO) {
            val request = requestBuilder("/v1/agents/$agentId/runs/$runId").get().build()
            val body = executeBody(request)
            val parsed = json.decodeFromString(RunSummary.serializer(), body)
            // Overlay undocumented keys: published Get A Run / V1Run omit `model`.
            val obj = json.parseToJsonElement(body) as? JsonObject ?: return@withContext parsed
            parsed.withModelFromJson(obj)
        }

    suspend fun createAgent(body: CreateAgentRequest): CreateAgentResponse =
        withContext(Dispatchers.IO) {
            val payload = json.encodeToString(CreateAgentRequest.serializer(), body)
            val request = requestBuilder("/v1/agents")
                .post(payload.toRequestBody(mediaType))
                .build()
            execute(request, CreateAgentResponse.serializer())
        }

    suspend fun renameAgent(agentId: String, name: String): AgentDetail? =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim().take(100)
            val payload = json.encodeToString(
                RenameAgentRequest.serializer(),
                RenameAgentRequest(trimmed),
            )
            val body = payload.toRequestBody(mediaType)
            val attempts = listOf(
                requestBuilder("/v1/agents/$agentId").patch(body).build(),
                requestBuilder("/v1/agents/$agentId/rename").post(body).build(),
                requestBuilder("/v1/agents/$agentId").put(body).build(),
            )
            var lastError: Exception? = null
            for (request in attempts) {
                try {
                    val raw = executeBody(request)
                    parseRenamedAgent(raw, trimmed)?.let { return@withContext it }
                    return@withContext getAgent(agentId).copy(name = trimmed)
                } catch (e: ApiException) {
                    if (e.statusCode in setOf(400, 404, 405, 415, 422, 501)) {
                        lastError = e
                        continue
                    }
                    throw e
                }
            }
            throw lastError ?: ApiException(404, "Rename is not supported")
        }

    private fun parseRenamedAgent(raw: String, fallbackName: String): AgentDetail? {
        if (raw.isBlank()) return null
        runCatching { json.decodeFromString(AgentDetail.serializer(), raw) }
            .getOrNull()
            ?.let { return it.copy(name = it.name ?: fallbackName) }
        val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return null
        val nested = obj["agent"] ?: return null
        return runCatching {
            json.decodeFromJsonElement(AgentDetail.serializer(), nested)
        }.getOrNull()?.let { it.copy(name = it.name ?: fallbackName) }
    }

    suspend fun createRun(
        agentId: String,
        text: String,
        model: ModelSelection? = null,
        images: List<PromptImage>? = null,
    ): RunSummary = withContext(Dispatchers.IO) {
        val body = CreateRunRequest(
            prompt = PromptBody(
                text = text,
                images = images?.takeIf { it.isNotEmpty() },
            ),
            model = model,
        )
        val payload = json.encodeToString(CreateRunRequest.serializer(), body)
        val request = requestBuilder("/v1/agents/$agentId/runs")
            .post(payload.toRequestBody(mediaType))
            .build()
        execute(request, CreateRunResponse.serializer()).resolvedRun()
    }

    suspend fun cancelRun(agentId: String, runId: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/agents/$agentId/runs/$runId/cancel")
            .post("{}".toRequestBody(mediaType))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 409) {
                throw ApiException(response.code, response.body?.string().orEmpty())
            }
        }
    }

    suspend fun archiveAgent(agentId: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/agents/$agentId/archive")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        executeBody(request)
    }

    suspend fun deleteAgent(agentId: String) = withContext(Dispatchers.IO) {
        val request = requestBuilder("/v1/agents/$agentId")
            .delete()
            .build()
        executeBody(request)
    }

    suspend fun collectRunExtras(agentId: String, maxRuns: Int = 8): List<RunExtras> =
        withContext(Dispatchers.IO) {
            val runs = listRuns(agentId, limit = maxRuns).items
            coroutineScope {
                runs.map { run ->
                    async {
                        runCatching { extractRunExtras(agentId, run.id) }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
        }

    /** Latest run only — used for inbox file-change badges. */
    suspend fun fetchLatestRunChangeStats(agentId: String): InboxChangeStats =
        withContext(Dispatchers.IO) {
            val run = listRuns(agentId, limit = 1).items.firstOrNull()
                ?: return@withContext InboxChangeStats(fileCount = 0)
            extractRunExtras(agentId, run.id).toInboxChangeStats()
        }

    suspend fun extractRunExtras(agentId: String, runId: String): RunExtras =
        withContext(Dispatchers.IO) { readRunExtras(agentId, runId) }

    private fun readRunExtras(agentId: String, runId: String): RunExtras {
        val request = requestBuilder("/v1/agents/$agentId/runs/$runId/stream")
            .header("Accept", "text/event-stream")
            .get()
            .build()
        var userText: String? = null
        var resultText: String? = null
        var todos: List<AgentTodo> = emptyList()
        val files = linkedMapOf<String, ChangedFile>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return RunExtras(runId = runId)
            val source = response.body?.byteStream() ?: return RunExtras(runId = runId)
            BufferedReader(source.reader(Charsets.UTF_8)).use { reader ->
                var eventName = "message"
                val dataLines = mutableListOf<String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.isEmpty() -> {
                            if (dataLines.isNotEmpty()) {
                                val data = dataLines.joinToString("\n")
                                dataLines.clear()
                                val name = eventName
                                eventName = "message"
                                val event = parseEvent(name, data)
                                when (event) {
                                    is StreamEvent.UserMessage ->
                                        if (userText.isNullOrBlank()) userText = event.text
                                    is StreamEvent.Result ->
                                        resultText = event.text ?: resultText
                                    is StreamEvent.ToolCall -> {
                                        if (event.todos.isNotEmpty()) todos = event.todos
                                        val path = event.path
                                        val tool = event.name.orEmpty()
                                        if (!path.isNullOrBlank() && tool.isFileMutatingTool()) {
                                            files[path] = ChangedFile(
                                                path = path,
                                                tool = tool,
                                                linesAdded = event.linesAdded,
                                                linesRemoved = event.linesRemoved,
                                            )
                                        }
                                    }
                                    else -> Unit
                                }
                                if (event.endsSse()) return@use
                            } else {
                                eventName = "message"
                            }
                        }
                        line.startsWith(":") -> Unit
                        line.startsWith("event:") -> eventName = line.substringAfter("event:").trim()
                        line.startsWith("data:") -> dataLines += line.substringAfter("data:").trimStart()
                        line.startsWith("id:") -> Unit
                    }
                }
            }
        }
        return RunExtras(
            runId = runId,
            userText = userText,
            todos = todos,
            files = files.values.toList().sortedBy { it.fileName.lowercase() },
            resultText = resultText,
        )
    }

    @Deprecated("Use collectRunExtras")
    suspend fun collectChangedFiles(agentId: String, maxRuns: Int = 4): List<ChangedFile> =
        collectRunExtras(agentId, maxRuns).flatMap { it.files }.distinctBy { it.path }

    fun streamRun(
        agentId: String,
        runId: String,
        lastEventId: String? = null,
    ): Flow<StreamFrame> = callbackFlow {
        val builder = requestBuilder("/v1/agents/$agentId/runs/$runId/stream")
            .header("Accept", "text/event-stream")
        if (!lastEventId.isNullOrBlank()) {
            builder.header("Last-Event-ID", lastEventId)
        }
        val request = builder.get().build()
        val call = client.newCall(request)
        val producer = this

        launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw ApiException(response.code, response.body?.string().orEmpty())
                    }
                    val source = response.body?.byteStream()
                        ?: throw IOException("Empty SSE body")
                    BufferedReader(source.reader(Charsets.UTF_8)).use { reader ->
                        var eventName = "message"
                        var eventId: String? = null
                        val dataLines = mutableListOf<String>()

                        while (isActive && !call.isCanceled()) {
                            val line = try {
                                reader.readLine()
                            } catch (e: IOException) {
                                if (call.isCanceled() || !isActive) break
                                throw e
                            } ?: break
                            when {
                                line.isEmpty() -> {
                                    if (dataLines.isNotEmpty()) {
                                        val data = dataLines.joinToString("\n")
                                        dataLines.clear()
                                        val name = eventName
                                        val id = eventId
                                        eventName = "message"
                                        eventId = null
                                        val event = parseEvent(name, data)
                                        producer.send(StreamFrame(id, event))
                                        if (event.endsSse()) {
                                            producer.close()
                                            call.cancel()
                                            return@launch
                                        }
                                    } else {
                                        eventName = "message"
                                        eventId = null
                                    }
                                }
                                line.startsWith(":") -> Unit
                                line.startsWith("event:") ->
                                    eventName = line.substringAfter("event:").trim()
                                line.startsWith("data:") ->
                                    dataLines += line.substringAfter("data:").trimStart()
                                line.startsWith("id:") ->
                                    eventId = line.substringAfter("id:").trim()
                            }
                        }
                        if (dataLines.isNotEmpty() && isActive && !call.isCanceled()) {
                            val event = parseEvent(eventName, dataLines.joinToString("\n"))
                            producer.send(StreamFrame(eventId, event))
                        }
                    }
                }
                producer.close()
            } catch (e: kotlinx.coroutines.CancellationException) {
                producer.close()
            } catch (e: Exception) {
                if (call.isCanceled()) producer.close() else producer.close(e)
            }
        }

        awaitClose { call.cancel() }
    }

    private fun parseEvent(event: String, data: String): StreamEvent {
        if (data.isBlank() || data == "{}") {
            return when (event) {
                "done" -> StreamEvent.Done
                "heartbeat" -> StreamEvent.Heartbeat
                else -> StreamEvent.Unknown(event, data)
            }
        }
        return try {
            val obj = json.parseToJsonElement(data).jsonObject
            when (event) {
                "status" -> StreamEvent.Status(
                    runId = obj.string("runId"),
                    status = obj.string("status"),
                )
                "assistant" -> StreamEvent.Assistant(obj.string("text").orEmpty())
                "thinking" -> StreamEvent.Thinking(obj.string("text").orEmpty())
                "tool_call" -> {
                    val args = obj["args"]?.jsonObject
                    val result = obj["result"]?.jsonObject
                    val success = result?.get("success")?.jsonObject
                    StreamEvent.ToolCall(
                        callId = obj.string("callId")?.sanitizeId(),
                        name = obj.string("name"),
                        status = obj.string("status"),
                        path = args.extractPath() ?: success?.string("path"),
                        todos = args.extractTodos(),
                        linesAdded = success.int("linesAdded"),
                        linesRemoved = success.int("linesRemoved"),
                    )
                }
                "interaction_update" -> {
                    when (obj.string("type")) {
                        "user-message-appended" -> {
                            val text = obj["userMessage"]
                                ?.jsonObject
                                ?.string("text")
                                .orEmpty()
                            if (text.isNotBlank()) StreamEvent.UserMessage(text)
                            else StreamEvent.Unknown(event, data)
                        }
                        else -> StreamEvent.Unknown(event, data)
                    }
                }
                "result" -> {
                    val git = obj["git"]?.let {
                        runCatching { json.decodeFromJsonElement(RunGit.serializer(), it) }.getOrNull()
                    }
                    StreamEvent.Result(
                        status = obj.string("status"),
                        text = obj.string("text"),
                        git = git,
                    )
                }
                "error" -> StreamEvent.Error(
                    code = obj.string("code"),
                    message = obj.string("message"),
                )
                "done" -> StreamEvent.Done
                "heartbeat" -> StreamEvent.Heartbeat
                else -> StreamEvent.Unknown(event, data)
            }
        } catch (_: Exception) {
            StreamEvent.Unknown(event, data)
        }
    }

    private fun <T> execute(
        request: Request,
        deserializer: kotlinx.serialization.DeserializationStrategy<T>,
    ): T = json.decodeFromString(deserializer, executeBody(request))

    private fun executeBody(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, body)
            }
            return body
        }
    }

    private fun RunSummary.withModelFromJson(obj: JsonObject): RunSummary {
        if (!resolvedModelId().isNullOrBlank()) return this
        val selection = obj.extractModelSelection()
        val id = selection?.id ?: obj.string("modelId")?.takeIf { it.isNotBlank() }
        if (selection == null && id == null) return this
        return copy(model = model ?: selection, modelId = modelId ?: id)
    }

    private fun ConversationMessage.withModelFromJson(obj: JsonObject): ConversationMessage {
        if (!resolvedModelId().isNullOrBlank()) return this
        val selection = obj.extractModelSelection()
        val id = selection?.id ?: obj.string("modelId")?.takeIf { it.isNotBlank() }
        if (selection == null && id == null) return this
        return copy(model = model ?: selection, modelId = modelId ?: id)
    }

    private fun JsonObject.extractModelSelection(): ModelSelection? {
        val el = this["model"] ?: return null
        return when (el) {
            is JsonObject -> {
                val id = el.string("id")?.takeIf { it.isNotBlank() } ?: return null
                runCatching { json.decodeFromJsonElement(ModelSelection.serializer(), el) }
                    .getOrNull()
                    ?: ModelSelection(id = id)
            }
            is JsonPrimitive -> el.content.takeIf { it.isNotBlank() }?.let { ModelSelection(id = it) }
            else -> null
        }
    }

    companion object {
        const val BASE_URL = "https://api.cursor.com"
    }
}

class ApiException(val statusCode: Int, rawBody: String) :
    IOException("HTTP $statusCode: ${sanitizeApiError(rawBody)}") {
    val body: String = sanitizeApiError(rawBody)
}

private fun sanitizeApiError(body: String): String =
    body.take(400)
        .replace(Regex("(?i)(authorization|api[-_ ]?key|bearer)\\s*[:=]\\s*\\S+"), "$1: [redacted]")

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.content

private fun JsonObject?.int(key: String): Int? =
    this?.get(key)?.jsonPrimitive?.content?.toIntOrNull()

private fun JsonObject?.extractPath(): String? {
    if (this == null) return null
    val keys = listOf("path", "file_path", "filePath", "target_file", "targetFile")
    for (key in keys) {
        val value = string(key)?.trim()
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun JsonObject?.extractTodos(): List<AgentTodo> {
    if (this == null) return emptyList()
    val arr = this["todos"] ?: return emptyList()
    return runCatching {
        arr.jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val content = o.string("content")?.trim().orEmpty()
            if (content.isEmpty()) return@mapNotNull null
            AgentTodo(
                id = o.string("id") ?: content.hashCode().toString(),
                content = content,
                status = o.string("status") ?: "TODO_STATUS_PENDING",
            )
        }
    }.getOrDefault(emptyList())
}

fun String.isFileMutatingTool(): Boolean {
    val n = lowercase()
    return n == "edit_file" ||
        n == "write" ||
        n == "delete_file" ||
        n == "search_replace" ||
        n == "apply_patch" ||
        (n.contains("edit") && n.contains("file"))
}

fun String.sanitizeId(): String =
    replace('\n', '-').replace('\r', '-').trim()
