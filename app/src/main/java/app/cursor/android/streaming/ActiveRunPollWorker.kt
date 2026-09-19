package app.cursor.android.streaming

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class ActiveRunPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        RunResumeCoordinator.poll(applicationContext)
        val agentId = inputData.getString(KEY_AGENT_ID)
        val runId = inputData.getString(KEY_RUN_ID)
        if (!agentId.isNullOrBlank() && !runId.isNullOrBlank()) {
            val stillTracked = runCatching {
                app.cursor.android.data.LocalStore(applicationContext)
                    .snapshot()
                    .activeRuns
                    .any { it.agentId == agentId && it.runId == runId }
            }.getOrDefault(true)
            if (stillTracked) return Result.retry()
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        StreamingNotifications.ensureChannels(applicationContext)
        val notification = StreamingNotifications.ongoingNotification(
            applicationContext,
            "Checking agent runs…",
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                StreamingNotifications.POLL_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(StreamingNotifications.POLL_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val PERIODIC_NAME = "cursor-active-run-poll"
        private const val ONCE_NAME = "cursor-active-run-poll-once"
        private const val MONITOR_NAME_PREFIX = "cursor-active-run-monitor"
        private const val KEY_AGENT_ID = "agent-id"
        private const val KEY_RUN_ID = "run-id"

        fun enqueue(context: Context) {
            val wm = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ActiveRunPollWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build(),
            )
            wm.enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ActiveRunPollWorker>()
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }

        fun enqueueMonitor(context: Context, agentId: String, runId: String) {
            if (agentId.isBlank() || runId.isBlank()) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ActiveRunPollWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_AGENT_ID to agentId, KEY_RUN_ID to runId))
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.LINEAR,
                    15,
                    TimeUnit.SECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$MONITOR_NAME_PREFIX:$agentId:$runId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
