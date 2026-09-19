package app.cursor.android.streaming

import app.cursor.android.data.ApiException
import java.io.IOException

internal enum class DecisionChoice {
    APPROVE,
    DECLINE,
}

internal fun decisionChoice(action: String?): DecisionChoice? = when (action) {
    DecisionActionReceiver.ACTION_APPROVE -> DecisionChoice.APPROVE
    DecisionActionReceiver.ACTION_DECLINE -> DecisionChoice.DECLINE
    else -> null
}

internal fun decisionPrompt(choice: DecisionChoice, question: String): String = buildString {
    append(
        when (choice) {
            DecisionChoice.APPROVE -> "Approved. Please proceed."
            DecisionChoice.DECLINE -> "Do not proceed. I decline."
        },
    )
    question.trim().takeIf { it.isNotBlank() }?.let {
        append("\n\n")
        append(it)
    }
}

internal fun decisionFailureMessage(error: Throwable): String {
    val api = error as? ApiException
    return when {
        api != null && api.statusCode in setOf(401, 403) ->
            "Cursor rejected the API key. Open Pointer settings and check it."
        api != null -> {
            val detail = api.body.trim().take(140)
            if (detail.isBlank()) "Cursor returned HTTP ${api.statusCode}. Try again."
            else "Cursor returned HTTP ${api.statusCode}: $detail"
        }
        error is IOException ->
            "Pointer couldn't reach Cursor. Check the network and try again."
        else -> error.message?.takeIf { it.isNotBlank() }?.take(160)
            ?: "Something went wrong. Open the chat and try again."
    }
}
