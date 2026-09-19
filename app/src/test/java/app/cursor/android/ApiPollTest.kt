package app.cursor.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPollTest {
    @Test
    fun `idle chats poll slowly`() {
        assertEquals(ApiPoll.IDLE_MS, ApiPoll.watchDelayMs(streaming = false, agentStatus = "FINISHED"))
        assertEquals(ApiPoll.IDLE_MS, ApiPoll.watchDelayMs(streaming = false, agentStatus = null))
    }

    @Test
    fun `active and streaming chats poll less often than the old two-second loop`() {
        assertEquals(ApiPoll.ACTIVE_MS, ApiPoll.watchDelayMs(streaming = false, agentStatus = "CREATING"))
        assertEquals(ApiPoll.ACTIVE_MS, ApiPoll.watchDelayMs(streaming = false, agentStatus = "RUNNING"))
        assertEquals(ApiPoll.STREAMING_MS, ApiPoll.watchDelayMs(streaming = true, agentStatus = "RUNNING"))
        assertTrue(ApiPoll.STREAMING_MS >= 8_000L)
        assertTrue(ApiPoll.IDLE_MS >= 10_000L)
    }

    @Test
    fun `live streams skip conversation refetch`() {
        assertFalse(
            ApiPoll.shouldFetchConversation(
                streaming = true,
                agentActive = true,
                fingerprintChanged = true,
            ),
        )
    }

    @Test
    fun `idle chats refetch only when the agent fingerprint changes`() {
        assertFalse(
            ApiPoll.shouldFetchConversation(
                streaming = false,
                agentActive = false,
                fingerprintChanged = false,
            ),
        )
        assertTrue(
            ApiPoll.shouldFetchConversation(
                streaming = false,
                agentActive = false,
                fingerprintChanged = true,
            ),
        )
        assertTrue(
            ApiPoll.shouldFetchConversation(
                streaming = false,
                agentActive = true,
                fingerprintChanged = false,
            ),
        )
    }
}
