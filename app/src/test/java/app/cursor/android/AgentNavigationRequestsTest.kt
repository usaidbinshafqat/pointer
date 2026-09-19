package app.cursor.android

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentNavigationRequestsTest {
    @Test
    fun `request offered before collection is buffered for cold start`() = runBlocking {
        val requests = AgentNavigationRequests()

        assertTrue(requests.offer("  agent-123  "))

        assertEquals("agent-123", requests.requests.first())
    }

    @Test
    fun `blank and unsafe agent ids are rejected`() {
        assertNull(normalizeAgentId(null))
        assertNull(normalizeAgentId("   "))
        assertNull(normalizeAgentId("agent\u0000id"))
        assertNull(normalizeAgentId("a".repeat(257)))
    }

    @Test
    fun `rejected request is not enqueued`() {
        val requests = AgentNavigationRequests()

        assertFalse(requests.offer("\n"))
    }

    @Test
    fun `active target agent is not added to the back stack again`() {
        assertFalse(shouldNavigateToAgent(AGENT_ROUTE, "agent-123", "agent-123"))
        assertTrue(shouldNavigateToAgent(AGENT_ROUTE, "agent-456", "agent-123"))
        assertTrue(shouldNavigateToAgent("inbox", null, "agent-123"))
    }

    @Test
    fun `multiple notification taps are delivered in order once navigation starts`() = runBlocking {
        val requests = AgentNavigationRequests()
        assertTrue(requests.offer("first"))
        assertTrue(requests.offer("second"))

        assertEquals(listOf("first", "second"), requests.requests.take(2).toList())
    }

    @Test
    fun `normalization accepts the documented maximum and preserves interior spaces`() {
        assertEquals("agent with spaces", normalizeAgentId("  agent with spaces  "))
        assertEquals("a".repeat(256), normalizeAgentId("a".repeat(256)))
        assertNull(normalizeAgentId("a".repeat(257)))
    }

    @Test
    fun `all ISO control characters are rejected even when embedded`() {
        assertNull(normalizeAgentId("agent\nid"))
        assertNull(normalizeAgentId("agent\u007fid"))
    }
}
