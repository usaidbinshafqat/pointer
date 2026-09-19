package app.cursor.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStateRoutingTest {
    @Test
    fun `opened route owns visible state before detail loads`() {
        assertTrue(
            ownsVisibleAgent(
                requestedAgentId = "opening-agent",
                openedAgentId = "opening-agent",
                loadedAgentId = null,
            ),
        )
        assertFalse(
            ownsVisibleAgent(
                requestedAgentId = "background-agent",
                openedAgentId = "opening-agent",
                loadedAgentId = null,
            ),
        )
    }

    @Test
    fun `missing background snapshot never falls back to visible state`() {
        val state = routedAgentState(
            requestedAgentId = "background-agent",
            openedAgentId = "visible-agent",
            loadedAgentId = "visible-agent",
            visibleState = "visible conversation",
            snapshots = emptyMap(),
            emptyState = { "empty conversation" },
        )

        assertEquals("empty conversation", state)
    }

    @Test
    fun `background agent reads only its keyed snapshot`() {
        val state = routedAgentState(
            requestedAgentId = "background-agent",
            openedAgentId = "visible-agent",
            loadedAgentId = "visible-agent",
            visibleState = "visible conversation",
            snapshots = mapOf("background-agent" to "background conversation"),
            emptyState = { "empty conversation" },
        )

        assertEquals("background conversation", state)
    }

    @Test
    fun `loaded detail identifies visible state when no route is selected`() {
        assertTrue(
            ownsVisibleAgent(
                requestedAgentId = "loaded-agent",
                openedAgentId = null,
                loadedAgentId = "loaded-agent",
            ),
        )
    }

    @Test
    fun `explicit route wins over stale detail while the new detail loads`() {
        assertFalse(
            ownsVisibleAgent(
                requestedAgentId = "stale-loaded-agent",
                openedAgentId = "new-route-agent",
                loadedAgentId = "stale-loaded-agent",
            ),
        )
        assertTrue(
            ownsVisibleAgent(
                requestedAgentId = "new-route-agent",
                openedAgentId = "new-route-agent",
                loadedAgentId = "stale-loaded-agent",
            ),
        )
    }

    @Test
    fun `visible state takes precedence over a stale snapshot for the selected agent`() {
        val state = routedAgentState(
            requestedAgentId = "selected-agent",
            openedAgentId = "selected-agent",
            loadedAgentId = "selected-agent",
            visibleState = "live visible conversation",
            snapshots = mapOf("selected-agent" to "stale snapshot"),
            emptyState = { "empty conversation" },
        )

        assertEquals("live visible conversation", state)
    }

    @Test
    fun `blank route falls back to the loaded detail owner`() {
        assertTrue(
            ownsVisibleAgent(
                requestedAgentId = "loaded-agent",
                openedAgentId = "   ",
                loadedAgentId = "loaded-agent",
            ),
        )
    }
}
