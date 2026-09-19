package app.cursor.android.streaming

import app.cursor.android.data.ApiException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionActionsTest {
    @Test
    fun `only known receiver actions resolve to a decision`() {
        assertEquals(
            DecisionChoice.APPROVE,
            decisionChoice(DecisionActionReceiver.ACTION_APPROVE),
        )
        assertEquals(
            DecisionChoice.DECLINE,
            decisionChoice(DecisionActionReceiver.ACTION_DECLINE),
        )
        assertNull(decisionChoice("app.pointer.android.action.UNKNOWN"))
        assertNull(decisionChoice(null))
    }

    @Test
    fun `prompt preserves decision and trims the quoted question`() {
        assertEquals(
            "Approved. Please proceed.\n\nMay I deploy?",
            decisionPrompt(DecisionChoice.APPROVE, "  May I deploy?  "),
        )
        assertEquals(
            "Do not proceed. I decline.",
            decisionPrompt(DecisionChoice.DECLINE, "   "),
        )
    }

    @Test
    fun `network failure is actionable without leaking low level detail`() {
        assertEquals(
            "Pointer couldn't reach Cursor. Check the network and try again.",
            decisionFailureMessage(IOException("api.cursor.com: nodename nor servname provided")),
        )
    }

    @Test
    fun `api failures include a bounded status detail`() {
        val message = decisionFailureMessage(ApiException(409, "Agent is already running"))
        assertTrue(message.startsWith("Cursor returned HTTP 409:"))
        assertTrue(message.length < 200)
    }

    @Test
    fun `authentication failures direct the user to settings`() {
        assertEquals(
            "Cursor rejected the API key. Open Pointer settings and check it.",
            decisionFailureMessage(ApiException(401, "Unauthorized")),
        )
        assertEquals(
            "Cursor rejected the API key. Open Pointer settings and check it.",
            decisionFailureMessage(ApiException(403, "Forbidden")),
        )
    }

    @Test
    fun `blank API response uses a concise retry message`() {
        assertEquals(
            "Cursor returned HTTP 503. Try again.",
            decisionFailureMessage(ApiException(503, "   ")),
        )
    }

    @Test
    fun `unexpected failure detail is bounded for notification display`() {
        val message = decisionFailureMessage(IllegalStateException("x".repeat(500)))

        assertEquals(160, message.length)
        assertTrue(message.all { it == 'x' })
    }
}
