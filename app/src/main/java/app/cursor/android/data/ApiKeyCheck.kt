package app.cursor.android.data

import java.io.IOException

sealed interface ApiKeyCheck {
    data object Idle : ApiKeyCheck
    data object Checking : ApiKeyCheck
    data class Verified(val accountLabel: String) : ApiKeyCheck
    data class Failed(val message: String) : ApiKeyCheck
}

fun apiKeyProbeErrorMessage(error: Throwable): String {
    val api = error as? ApiException
    return when {
        api != null && api.statusCode in setOf(401, 403) ->
            "that key was rejected. check it and try again"
        error is IOException && api == null ->
            "couldn't reach cursor. check the network and try again"
        api != null -> {
            val detail = api.body.trim().take(160)
            if (detail.isBlank()) "couldn't verify that key (HTTP ${api.statusCode})"
            else "couldn't verify that key · $detail"
        }
        else -> error.message?.takeIf { it.isNotBlank() } ?: "couldn't verify that key"
    }
}

fun apiKeyVerifiedMessage(account: AccountInfo): String =
    "api key works · signed in as ${account.displayName()}"
