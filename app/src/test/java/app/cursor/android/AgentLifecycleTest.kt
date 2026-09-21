package app.cursor.android

import app.cursor.android.data.ActiveRun
import app.cursor.android.data.AgentSummary
import app.cursor.android.data.ComposerDraft
import app.cursor.android.data.LocalAppState
import app.cursor.android.data.QueuedPrompt
import app.cursor.android.data.withChatsRead
import app.cursor.android.data.withoutDeletedAgents
import app.cursor.android.data.withoutTrackedAgentRuns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLifecycleTest {
    @Test
    fun `inbox removal drops remote rows and transient status for selected chats`() {
        val state = InboxUiState(
            agents = listOf(agent("one"), agent("two")),
            latestRuns = mapOf("one" to app.cursor.android.data.RunSummary(id = "run-one")),
            locallyWorkingIds = setOf("one", "two"),
            lastReadAtByAgent = mapOf("one" to "now", "two" to "later"),
            pinnedAgentIds = listOf("one", "two"),
            chatTitles = mapOf("one" to "keep for archive"),
        )

        val result = state.withoutAgents(setOf("one"))

        assertEquals(listOf("two"), result.agents.map { it.id })
        assertFalse("one" in result.latestRuns)
        assertEquals(setOf("two"), result.locallyWorkingIds)
        assertFalse("one" in result.lastReadAtByAgent)
        assertEquals(listOf("two"), result.pinnedAgentIds)
        assertEquals("keep for archive", result.chatTitles["one"])
    }

    @Test
    fun `permanent deletion clears every local agent scoped value`() {
        val prompt = QueuedPrompt(id = "queued", displayText = "queued", apiText = "queued")
        val state = LocalAppState(
            agentFolderIds = mapOf("one" to "archive", "two" to "inbox"),
            lastModelByAgent = mapOf("one" to "model"),
            queuedPrompts = mapOf("one" to listOf(prompt)),
            activeRuns = listOf(ActiveRun("one", "run-one"), ActiveRun("two", "run-two")),
            chatTitles = mapOf("one" to "title"),
            chatIcons = mapOf("one" to "code"),
            notifiedDecisions = setOf("one:run-one:WAITING", "two:run-two:DONE"),
            lastReadAtByAgent = mapOf("one" to "now"),
            pinnedAgentIds = listOf("one", "two"),
            composerDrafts = mapOf("one" to ComposerDraft(text = "draft")),
        )

        val result = state.withoutDeletedAgents(setOf("one"))

        assertFalse("one" in result.agentFolderIds)
        assertFalse("one" in result.lastModelByAgent)
        assertFalse("one" in result.queuedPrompts)
        assertEquals(listOf("two"), result.activeRuns.map { it.agentId })
        assertFalse("one" in result.chatTitles)
        assertFalse("one" in result.chatIcons)
        assertEquals(setOf("two:run-two:DONE"), result.notifiedDecisions)
        assertFalse("one" in result.lastReadAtByAgent)
        assertEquals(listOf("two"), result.pinnedAgentIds)
        assertFalse("one" in result.composerDrafts)
    }

    @Test
    fun `archive stops queued and active tracking but preserves reversible metadata`() {
        val prompt = QueuedPrompt(id = "queued", displayText = "queued", apiText = "queued")
        val state = LocalAppState(
            queuedPrompts = mapOf("one" to listOf(prompt)),
            activeRuns = listOf(ActiveRun("one", "run-one")),
            chatTitles = mapOf("one" to "local title"),
            composerDrafts = mapOf("one" to ComposerDraft(text = "draft")),
        )

        val result = state.withoutTrackedAgentRuns(setOf("one"))

        assertTrue(result.queuedPrompts.isEmpty())
        assertTrue(result.activeRuns.isEmpty())
        assertEquals("local title", result.chatTitles["one"])
        assertEquals("draft", result.composerDrafts["one"]?.text)
    }

    @Test
    fun `bulk read applies one timestamp and preserves other read state`() {
        val state = LocalAppState(
            lastReadAtByAgent = mapOf("existing" to "earlier"),
        )

        val result = state.withChatsRead(setOf("one", "two", ""), "now")

        assertEquals(
            mapOf("existing" to "earlier", "one" to "now", "two" to "now"),
            result.lastReadAtByAgent,
        )
    }

    @Test
    fun `empty lifecycle selection is a no op`() {
        val inbox = InboxUiState(agents = listOf(agent("one")))
        val local = LocalAppState(chatTitles = mapOf("one" to "title"))

        assertSame(inbox, inbox.withoutAgents(emptySet()))
        assertSame(local, local.withoutDeletedAgents(emptySet()))
        assertSame(local, local.withoutTrackedAgentRuns(emptySet()))
        assertSame(local, local.withChatsRead(emptySet(), "now"))
    }

    private fun agent(id: String) = AgentSummary(id = id)
}
