package app.cursor.android.streaming

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.cursor.android.data.ActiveRun
import app.cursor.android.data.CursorApiClient
import app.cursor.android.data.LocalStore
import app.cursor.android.data.SettingsRepository
import app.cursor.android.data.isActiveRun
import app.cursor.android.data.isFailedRun
import app.cursor.android.data.isTerminalRun
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-level resume for runs that outlive the UI.
 *
 * [CursorApp] starts this on every process boot. It polls `getRun` for persisted
 * [ActiveRun]s: still CREATING/RUNNING keep the FGS alive; already-terminal runs
 * hydrate a preview and post a result notification. [app.cursor.android.CursorAppViewModel]
 * observes the same [LocalStore] list and calls `consumeStream` for live SSE.
 */
object RunResumeCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handled = Collections.synchronizedSet(mutableSetOf<String>())
    private val pollMutex = Mutex()

    @Volatile
    private var started = false

    fun start(app: Application) {
        if (started) return
        started = true
        StreamingNotifications.ensureChannels(app)
        ActiveRunPollWorker.enqueue(app)
        scope.launch { poll(app) }
    }

    suspend fun poll(context: Context) {
        pollMutex.withLock {
            val app = context.applicationContext
            val store = LocalStore(app)
            val settings = SettingsRepository(app).settings.first()
            if (settings.apiKey.isBlank()) return
            val api = CursorApiClient { settings.apiKey }
            val runs = store.snapshot().activeRuns
            for (run in runs) {
                val summary = runCatching { api.getRun(run.agentId, run.runId) }.getOrNull()
                    ?: continue
                when {
                    summary.status.isActiveRun() -> {
                        runCatching {
                            StreamingForegroundService.start(
                                app,
                                "${run.title.ifBlank { "Agent" }} is working…",
                            )
                        }
                    }
                    summary.status.isTerminalRun() -> {
                        val preview = previewText(api, run, summary.result, summary.status)
                        announceFinished(
                            context = app,
                            run = run,
                            preview = preview,
                            status = summary.status,
                            recovered = true,
                        )
                    }
                }
            }
        }
    }

    fun announceFinished(
        context: Context,
        run: ActiveRun,
        preview: String,
        status: String?,
        recovered: Boolean,
    ) {
        val key = "${run.agentId}:${run.runId}"
        val first = handled.add(key)
        scope.launch {
            LocalStore(context.applicationContext).removeActiveRun(run.agentId, run.runId)
        }
        if (!first) return
        val cancelled = status.equals("CANCELLED", ignoreCase = true) ||
            status.equals("CANCELED", ignoreCase = true)
        if (cancelled) return
        if (!canPostNotifications(context)) return
        val chatName = run.title.ifBlank { "Chat" }
        if (status.isFailedRun()) {
            StreamingNotifications.notifyResponseFailed(context, chatName, preview, run.agentId)
        } else {
            StreamingNotifications.notifyResponseComplete(context, chatName, preview, run.agentId)
        }
    }

    private suspend fun previewText(
        api: CursorApiClient,
        run: ActiveRun,
        result: String?,
        status: String?,
    ): String {
        result?.takeIf { it.isNotBlank() }?.let { return it }
        val messages = runCatching { api.getConversation(run.agentId).messages }
            .getOrDefault(emptyList())
        val lastAssistant = messages.lastOrNull { it.type == "assistant_message" }?.text
        if (!lastAssistant.isNullOrBlank()) return lastAssistant
        return when {
            status.isFailedRun() -> "Something went wrong"
            else -> "${run.title.ifBlank { "Agent" }} finished"
        }
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
