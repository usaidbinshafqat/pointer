package app.cursor.android.streaming

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.cursor.android.MainActivity
import app.cursor.android.R

object StreamingNotifications {
    const val ONGOING_CHANNEL_ID = "pointer_streaming"
    const val RESULT_CHANNEL_ID = "pointer_results"
    const val DECISION_CHANNEL_ID = "pointer_decisions"
    const val ONGOING_NOTIFICATION_ID = 2101
    const val RESULT_NOTIFICATION_ID = 2102
    const val POLL_NOTIFICATION_ID = 2103
    const val DECISION_NOTIFICATION_ID = 2200

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                ONGOING_CHANNEL_ID,
                "agent replies",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps Pointer updating while a run is in progress"
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                RESULT_CHANNEL_ID,
                "responses",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alerts as soon as a chat finishes"
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                DECISION_CHANNEL_ID,
                "decisions",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "When a chat needs you to approve or decline"
                enableVibration(true)
            },
        )
    }

    fun openAppIntent(context: Context, agentId: String? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!agentId.isNullOrBlank()) putExtra(EXTRA_AGENT_ID, agentId)
        }
        return PendingIntent.getActivity(
            context,
            agentId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun ongoingNotification(context: Context, title: String): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, ONGOING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("pointer")
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, ONGOING_CHANNEL_ID, "pointer is active"))
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun notifyResponseComplete(context: Context, chatName: String, body: String, agentId: String? = null) {
        ensureChannels(context)
        val preview = truncate(body.ifBlank { "$chatName finished" }, 250)
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("$chatName is done")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, RESULT_CHANNEL_ID, "open pointer to view this update"))
            .setContentIntent(openAppIntent(context, agentId))
            .build()
        post(context, resultId(agentId), notification)
    }

    fun notifyResponseFailed(context: Context, chatName: String, error: String, agentId: String? = null) {
        ensureChannels(context)
        val preview = truncate(error.ifBlank { "Something went wrong" }, 150)
        val notification = NotificationCompat.Builder(context, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("$chatName failed")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, RESULT_CHANNEL_ID, "open pointer to view this update"))
            .setContentIntent(openAppIntent(context, agentId))
            .build()
        post(context, resultId(agentId), notification)
    }

    fun notifyDecision(
        context: Context,
        chatName: String,
        question: String,
        agentId: String,
    ) {
        ensureChannels(context)
        val body = question.ifBlank { "This chat needs a decision from you." }
        val approve = decisionAction(context, agentId, question, approved = true)
        val decline = decisionAction(context, agentId, question, approved = false)
        val notification = NotificationCompat.Builder(context, DECISION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("$chatName needs a decision")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, DECISION_CHANNEL_ID, "open pointer to view this decision"))
            .setContentIntent(openAppIntent(context, agentId))
            .addAction(0, "approve", approve)
            .addAction(0, "don’t", decline)
            .build()
        post(context, decisionId(agentId), notification)
    }

    fun notifyDecisionSending(context: Context, approved: Boolean, agentId: String) {
        ensureChannels(context)
        val verb = if (approved) "approval" else "decline"
        val notification = NotificationCompat.Builder(context, DECISION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("Sending $verb…")
            .setContentText("Pointer is contacting Cursor")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openAppIntent(context, agentId))
            .build()
        post(context, decisionId(agentId), notification)
    }

    fun notifyDecisionSubmitted(context: Context, approved: Boolean, agentId: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, DECISION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle(if (approved) "Approval sent" else "Decline sent")
            .setContentText("Pointer is watching the response")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, DECISION_CHANNEL_ID, "decision sent"))
            .setContentIntent(openAppIntent(context, agentId))
            .build()
        post(context, decisionId(agentId), notification)
    }

    fun notifyDecisionFailed(context: Context, error: String, agentId: String) {
        ensureChannels(context)
        val preview = truncate(error.ifBlank { "Open the chat and try again." }, 180)
        val notification = NotificationCompat.Builder(context, DECISION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("Couldn't send decision")
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicNotification(context, DECISION_CHANNEL_ID, "open pointer to retry"))
            .setContentIntent(openAppIntent(context, agentId))
            .build()
        post(context, decisionId(agentId), notification)
    }

    fun cancelOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
    }

    private fun post(context: Context, id: Int, notification: Notification): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        }.getOrDefault(false)
    }

    private fun decisionAction(
        context: Context,
        agentId: String,
        question: String,
        approved: Boolean,
    ): PendingIntent {
        val intent = Intent(context, DecisionActionReceiver::class.java).apply {
            action = if (approved) DecisionActionReceiver.ACTION_APPROVE else DecisionActionReceiver.ACTION_DECLINE
            putExtra(EXTRA_AGENT_ID, agentId)
            putExtra(EXTRA_QUESTION, question)
        }
        val request = (agentId.hashCode() xor if (approved) 1 else 2)
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun resultId(agentId: String?): Int {
        if (agentId.isNullOrBlank()) return RESULT_NOTIFICATION_ID
        return RESULT_NOTIFICATION_ID + (agentId.hashCode() and 0xffff)
    }

    private fun decisionId(agentId: String): Int {
        return DECISION_NOTIFICATION_ID + (agentId.hashCode() and 0xffff)
    }

    private fun truncate(text: String, maxLength: Int): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxLength) return trimmed
        val prefix = trimmed.take(maxLength)
        val cut = prefix.lastIndexOf(' ').takeIf { it > maxLength / 2 } ?: maxLength
        return prefix.take(cut).trimEnd() + "…"
    }

    private fun publicNotification(context: Context, channelId: String, text: String): Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_pointer)
            .setContentTitle("pointer")
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .build()

    const val EXTRA_AGENT_ID = "agentId"
    const val EXTRA_QUESTION = "question"
}
