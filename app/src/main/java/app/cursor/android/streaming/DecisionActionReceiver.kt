package app.cursor.android.streaming

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.cursor.android.data.ActiveRun
import app.cursor.android.data.CursorApiClient
import app.cursor.android.data.LocalStore
import app.cursor.android.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DecisionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val agentId = intent.getStringExtra(StreamingNotifications.EXTRA_AGENT_ID).orEmpty()
        val question = intent.getStringExtra(StreamingNotifications.EXTRA_QUESTION).orEmpty()
        val choice = decisionChoice(intent.action)
        if (agentId.isBlank() || choice == null) return
        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            var submitted = false
            try {
                StreamingNotifications.notifyDecisionSending(app, choice == DecisionChoice.APPROVE, agentId)
                val settings = SettingsRepository(app).settings.first()
                check(settings.apiKey.isNotBlank()) {
                    "Pointer's API key is missing. Open settings and add it."
                }
                val api = CursorApiClient { settings.apiKey }
                val run = api.createRun(agentId, decisionPrompt(choice, question))
                submitted = true
                check(run.id.isNotBlank()) { "Cursor didn't return a run id." }

                val store = LocalStore(app)
                val title = store.snapshot().chatTitles[agentId].orEmpty().ifBlank { "Chat" }
                store.upsertActiveRun(
                    ActiveRun(
                        agentId = agentId,
                        runId = run.id,
                        title = title,
                    ),
                )
                ActiveRunPollWorker.enqueueMonitor(app, agentId, run.id)
                runCatching { StreamingForegroundService.start(app, "$title is working…") }
                StreamingNotifications.notifyDecisionSubmitted(
                    app,
                    choice == DecisionChoice.APPROVE,
                    agentId,
                )
            } catch (error: Exception) {
                val detail = if (submitted) {
                    "The decision was sent, but Pointer couldn't track the response. Open the chat to check it."
                } else {
                    decisionFailureMessage(error)
                }
                StreamingNotifications.notifyDecisionFailed(app, detail, agentId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE = "app.pointer.android.action.APPROVE"
        const val ACTION_DECLINE = "app.pointer.android.action.DECLINE"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
