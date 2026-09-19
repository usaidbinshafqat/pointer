package app.cursor.android.streaming

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

class StreamingForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val label = intent?.getStringExtra(EXTRA_LABEL) ?: "Working on your request…"
                ServiceCompat.startForeground(
                    this,
                    StreamingNotifications.ONGOING_NOTIFICATION_ID,
                    StreamingNotifications.ongoingNotification(this, label),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    } else {
                        0
                    },
                )
            }
        }
        return START_STICKY
    }

    companion object {
        private const val ACTION_START = "app.cursor.android.streaming.START"
        private const val ACTION_STOP = "app.cursor.android.streaming.STOP"
        private const val EXTRA_LABEL = "label"

        fun start(context: Context, label: String) {
            StreamingNotifications.ensureChannels(context)
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StreamingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
            StreamingNotifications.cancelOngoing(context)
        }
    }
}
