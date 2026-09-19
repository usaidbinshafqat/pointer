package app.cursor.android.ui

import java.time.Duration
import java.time.Instant

fun formatTimeAgo(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val seconds = Duration.between(then, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 45 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        seconds < 86_400 * 7 -> "${seconds / 86_400}d ago"
        else -> "${seconds / (86_400 * 7)}w ago"
    }
}

fun formatUpdatedAt(iso: String?): String {
    val ago = formatTimeAgo(iso)
    return if (ago.isBlank()) "" else "last updated $ago"
}

fun formatConnectedAt(connectedAtMs: Long?): String {
    if (connectedAtMs == null || connectedAtMs <= 0L) return ""
    val iso = runCatching { Instant.ofEpochMilli(connectedAtMs).toString() }.getOrNull()
    return formatTimeAgo(iso)
}
