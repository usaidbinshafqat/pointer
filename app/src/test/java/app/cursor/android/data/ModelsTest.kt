package app.cursor.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ModelsTest {
    @Test
    fun `failed and finished unread chats are done`() {
        val failed = agent(status = "FAILED", hasPr = true)
        val finished = agent(status = "FINISHED")

        assertEquals(InboxCategory.DONE, failed.inboxCategory())
        assertEquals(InboxCategory.DONE, finished.inboxCategory())
        assertTrue(failed.matchesFilter(InboxFilter.DONE))
        assertTrue(finished.matchesFilter(InboxFilter.DONE))
        assertFalse(failed.matchesFilter(InboxFilter.WORKING))
    }

    @Test
    fun `working takes precedence over done`() {
        val agent = agent(status = "RUNNING", hasPr = true)

        assertEquals(InboxCategory.WORKING, agent.inboxCategory())
        assertTrue(agent.matchesFilter(InboxFilter.WORKING))
        assertFalse(agent.matchesFilter(InboxFilter.DONE))
    }

    @Test
    fun `reading a finished chat removes it from done`() {
        val agent = agent(status = "FINISHED")
        val readAt = "2026-06-01T00:00:00Z"

        assertEquals(InboxCategory.WORKSPACE, agent.inboxCategory(lastReadAt = readAt))
        assertFalse(agent.matchesFilter(InboxFilter.DONE, lastReadAt = readAt))
        assertTrue(agent.matchesFilter(InboxFilter.ALL, lastReadAt = readAt))
    }

    @Test
    fun `waiting for the user is unread done`() {
        assertEquals(InboxCategory.DONE, agent(status = "WAITING_FOR_USER").inboxCategory())
        assertEquals(InboxCategory.DONE, agent(status = "WAITING").inboxCategory())
    }

    @Test
    fun `missing status stays in workspace`() {
        assertEquals(InboxCategory.WORKSPACE, agent(status = null).inboxCategory())
    }

    @Test
    fun `sections use the same exclusive categories as filters`() {
        val agents = listOf(
            agent("needs", "NEEDS_INPUT"),
            agent("working", "CREATING"),
            agent("finished", "FINISHED", hasPr = true),
            agent("read", "FINISHED"),
        )
        val sections = groupInboxSections(
            agents,
            workers = emptyList(),
            lastReadAtByAgent = mapOf("read" to "2026-06-01T00:00:00Z"),
        )

        assertEquals(
            listOf(
                InboxSectionKind.DONE,
                InboxSectionKind.WORKING,
                InboxSectionKind.WORKSPACE,
            ),
            sections.map { it.kind },
        )
        assertEquals(setOf("needs", "finished"), sections.first { it.kind == InboxSectionKind.DONE }.agents.map { it.id }.toSet())
        assertEquals(listOf("working"), sections.first { it.kind == InboxSectionKind.WORKING }.agents.map { it.id })
        assertEquals(listOf("read"), sections.first { it.kind == InboxSectionKind.WORKSPACE }.agents.map { it.id })
    }

    @Test
    fun `local in-progress overrides a finished inbox row`() {
        val agent = agent(status = "FINISHED")

        assertEquals(InboxCategory.DONE, agent.inboxCategory())
        assertEquals(InboxCategory.WORKING, agent.inboxCategory(locallyWorking = true))
        assertTrue(agent.matchesFilter(InboxFilter.WORKING, locallyWorking = true))
        val sections = groupInboxSections(
            listOf(agent),
            workers = emptyList(),
            locallyWorkingIds = setOf(agent.id),
        )
        assertEquals(listOf(InboxSectionKind.WORKING), sections.map { it.kind })
    }

    @Test
    fun `pinned chats sit in a section at the top`() {
        val agents = listOf(
            agent("later", "FINISHED"),
            agent("keep", "FINISHED"),
            agent("busy", "RUNNING"),
        )
        val sections = groupInboxSections(
            agents,
            workers = emptyList(),
            pinnedAgentIds = listOf("keep"),
        )
        assertEquals("pinned", sections.first().id)
        assertEquals(listOf("keep"), sections.first().agents.map { it.id })
        assertFalse(sections.drop(1).flatMap { it.agents }.any { it.id == "keep" })
    }

    @Test
    fun `models with the same name collapse to one row`() {
        val models = listOf(
            CursorModel(id = "claude-4", displayName = "Claude 4"),
            CursorModel(id = "claude-4", displayName = "Claude 4"),
            CursorModel(
                id = "claude-4-thinking",
                displayName = "Claude 4",
                variants = listOf(
                    app.cursor.android.data.ModelVariant(
                        displayName = "thinking",
                        params = listOf(ModelParamValue("fast", "false")),
                    ),
                ),
            ),
            autoModel(),
        )
        val unique = models.withAutoFirst()
        assertEquals(2, unique.size)
        assertTrue(unique.first().isAuto())
        assertEquals("claude-4", unique[1].id)
        assertTrue(unique[1].hasModeMenu() || unique[1].variants.isNotEmpty() || unique[1].aliases.contains("claude-4-thinking"))
    }

    @Test
    fun `fast and thinking names collapse onto one row`() {
        val models = listOf(
            CursorModel(id = "claude-4", displayName = "Claude 4"),
            CursorModel(id = "claude-4-fast", displayName = "Claude 4 Fast"),
            CursorModel(id = "claude-4-thinking", displayName = "Claude 4 Thinking"),
            autoModel(),
        )
        val unique = models.withAutoFirst()
        assertEquals(2, unique.size)
        assertEquals("claude-4", unique[1].id)
        assertEquals("Claude 4", unique[1].displayName)
        assertTrue(unique[1].hasModeMenu())
    }

    @Test
    fun `non-auto models expose fast and effort controls`() {
        val model = CursorModel(id = "grok-4.6", displayName = "Cursor Grok 4.6")
        assertTrue(model.hasModeMenu())
        assertEquals("fast", model.fastControl()?.id)
        assertEquals("reasoning_effort", model.effortControl()?.id)
        val params = model.paramsWithDefaults(emptyList())
        assertEquals("false", params.valueOf("fast"))
        assertEquals("medium", params.valueOf("reasoning_effort"))
        val faster = params.upsert("fast", "true")
        assertEquals("true", faster.valueOf("fast"))
        assertEquals("medium", faster.valueOf("reasoning_effort"))
    }

    @Test
    fun `auto has no extra model controls`() {
        assertFalse(autoModel().hasModeMenu())
        assertEquals(null, autoModel().fastControl())
        assertEquals(null, autoModel().effortControl())
    }

    @Test
    fun `only supported attachment types are accepted`() {
        assertTrue(AttachmentEncoder.isSupportedImage("image/jpeg", "pic.jpg"))
        assertTrue(AttachmentEncoder.isSupportedImage("image/png", "pic.png"))
        assertTrue(AttachmentEncoder.isSupportedImage("image/webp", "pic.webp"))
        assertFalse(AttachmentEncoder.isSupportedImage("image/heic", "pic.heic"))
        assertTrue(AttachmentEncoder.isSupportedFile("application/pdf", "spec.pdf"))
        assertTrue(AttachmentEncoder.isSupportedFile("text/plain", "notes.txt"))
        assertFalse(AttachmentEncoder.isSupportedFile("application/zip", "archive.zip"))
        assertFalse(AttachmentEncoder.isSupportedFile("video/mp4", "clip.mp4"))
        assertFalse(AttachmentEncoder.isSupportedFile("application/vnd.android.package-archive", "app.apk"))
    }

    @Test
    fun `large image decode dimensions preserve aspect ratio within the max edge`() {
        assertEquals(1600 to 1200, scaledDimensions(12_000, 9_000, 1600))
        assertEquals(900 to 1600, scaledDimensions(2250, 4000, 1600))
        assertEquals(800 to 600, scaledDimensions(800, 600, 1600))
    }

    @Test
    fun `bitmap fallback samples before allocating image pixels`() {
        assertEquals(8, calculateInSampleSize(12_000, 9_000, 1600))
        assertEquals(2, calculateInSampleSize(2000, 1200, 1600))
        assertEquals(1, calculateInSampleSize(1200, 1600, 1600))
    }

    @Test
    fun `file-backed image bytes remain available after inline data is stripped`() {
        val expected = byteArrayOf(1, 2, 3, 4)
        val path = Files.createTempFile("pointer-attachment", ".img")
        try {
            Files.write(path, expected)
            val attachment = DraftAttachment(
                id = "image",
                mimeType = "image/jpeg",
                backingFilePath = path.toString(),
            )
            assertArrayEquals(expected, attachment.imageBytes())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `self-hosted workers expose the primary workspace directory`() {
        val worker = PrivateWorker(
            workerId = "w1",
            workspaceRootPath = "/Users/alex",
            workspacePaths = listOf("/Users/alex/pointer", "/Users/alex"),
        )
        assertEquals(
            listOf("/Users/alex", "/Users/alex/pointer"),
            worker.workspaceDirectories(),
        )
        assertEquals("/Users/alex", worker.primaryWorkspacePath())
        assertEquals("~ @ Alex's Mac mini", worker.copy(name = "~ @ Alex's Mac mini").routingName())
        assertEquals("https://github.com/example/pointer", "github.com/example/pointer".asGithubRepoUrl())
    }

    @Test
    fun `home directories are stripped from file paths`() {
        assertEquals("pointer/app/Main.kt", "/Users/alex/pointer/app/Main.kt".withoutHomeDirectory())
        assertEquals("pointer/app/Main.kt", "/home/alex/pointer/app/Main.kt".withoutHomeDirectory())
        assertEquals("~", "/Users/alex".withoutHomeDirectory())
        assertEquals("src/Main.kt", "src/Main.kt".withoutHomeDirectory())
        assertEquals(
            "pointer/app/Main.kt",
            ChangedFile(path = "/Users/alex/pointer/app/Main.kt", tool = "edit").shortPath,
        )
    }

    @Test
    fun `auto id is auto not default`() {
        assertEquals("auto", AUTO_MODEL_ID)
        assertTrue("auto".isAutoModelId())
        assertTrue("default".isAutoModelId())
        assertEquals(null, autoModel().toApiSelection())
    }

    @Test
    fun `chat icons guess from title keywords`() {
        assertEquals(20, ChatIconCatalog.all.size)
        assertEquals("code", ChatIconCatalog.guess("Refactor Kotlin helpers"))
        assertEquals("bug", ChatIconCatalog.guess("Fix crash in login"))
        assertEquals("phone", ChatIconCatalog.guess("Android toolbar polish"))
        assertEquals("git", ChatIconCatalog.guess("Open a merge PR"))
        assertEquals("chat", ChatIconCatalog.guess(""))
        assertEquals("rocket", ChatIconCatalog.iconIdFor("Untitled", "rocket"))
        assertEquals("chat", ChatIconCatalog.iconIdFor("Untitled", "missing"))
    }

    @Test
    fun `mergeStreamText treats snapshots and deltas`() {
        assertEquals("Hello world", mergeStreamText("Hello", "Hello world"))
        assertEquals("Hello world", mergeStreamText("Hello world", " world"))
        assertEquals("Hello world", mergeStreamText("Hello world", "Hello"))
        assertEquals("Hello world", mergeStreamText("Hello", " world"))
    }

    @Test
    fun `shouldAdoptHistoryText ignores a previous turn`() {
        assertFalse(shouldAdoptHistoryText("New reply so far", "Old finished answer that is longer"))
        assertTrue(shouldAdoptHistoryText("Hello", "Hello world"))
        assertFalse(shouldAdoptHistoryText("", "Anything"))
        assertFalse(shouldAdoptHistoryText("Hello world", "Hello"))
        assertFalse(
            shouldAdoptHistoryText(
                current = "",
                incoming = "Old finished answer",
                previousTurnText = "Old finished answer",
            ),
        )
        assertFalse(
            shouldAdoptHistoryText(
                current = "",
                incoming = "Brand new tokens",
                previousTurnText = "Old finished answer",
            ),
        )
        assertFalse(
            shouldAdoptHistoryText(
                current = "",
                incoming = "I'll retry the connection",
                priorAssistantTexts = listOf("I'll retry the connection, then verify the result."),
            ),
        )
        assertTrue(looksLikePreviousTurn("Old finished answer", "Old finished answer"))
        assertFalse(looksLikePreviousTurn("New reply", "Old finished answer"))
    }

    @Test
    fun `mergeStreamText does not mash two old replies`() {
        val first = "I'll retry the connection, then verify every expected result."
        val second = "The test device is connected. I'll update the remaining resources."
        assertEquals("", mergeStreamText("", first, listOf(first, second)))
        assertEquals(second, mergeStreamText(first, second, listOf(first)))
        assertEquals("Hello world", mergeStreamText("Hello", "Hello world"))
    }

    @Test
    fun `mergeStreamText replaces a new assistant ping after a finished sentence`() {
        val first = "Taking send-now out of the input bar so Send never sits there alone."
        val second = "Moving the queue chip above the composer on the right."
        assertEquals(second, mergeStreamText(first, second))
    }

    @Test
    fun `toChatLines keeps only the last assistant ping in a turn`() {
        val messages = listOf(
            ConversationMessage(id = "u1", type = "user_message", text = "fix the chip"),
            ConversationMessage(id = "a1", type = "assistant_message", text = "I'll move the chip."),
            ConversationMessage(
                id = "a2",
                type = "assistant_message",
                text = "The queue chip now sits right above the input.",
            ),
        )
        val lines = messages.toChatLines()
        assertEquals(listOf(ChatLine.Kind.USER, ChatLine.Kind.ASSISTANT), lines.map { it.kind })
        assertEquals("fix the chip", lines.first().text)
        assertEquals("The queue chip now sits right above the input.", lines.last().text)
    }

    @Test
    fun `completed merge prefers conversation last message over a mashed stream`() {
        val user = "the chip for the queue is not that great"
        val finalReply = "The queue chip now sits right above the input, on the right."
        val mashed = "check how iOS places the queue chip.Moving the queue chip above the composer."
        val history = listOf(
            ChatLine(id = "u1", kind = ChatLine.Kind.USER, text = user),
            ChatLine(id = "a1", kind = ChatLine.Kind.ASSISTANT, text = finalReply),
        )
        val local = listOf(
            ChatLine(id = "u1", kind = ChatLine.Kind.USER, text = user),
            ChatLine(id = "live", kind = ChatLine.Kind.ASSISTANT, text = mashed),
        )
        val merged = mergeConversation(history, local, streaming = false)
        assertEquals(finalReply, merged.last { it.kind == ChatLine.Kind.ASSISTANT }.text)
    }

    @Test
    fun `mergeKeepingLocalTail keeps the new prompt in front of a blank live reply`() {
        val history = listOf(
            ChatLine(id = "u1", kind = ChatLine.Kind.USER, text = "first"),
            ChatLine(id = "a1", kind = ChatLine.Kind.ASSISTANT, text = "old reply"),
        )
        val local = history + listOf(
            ChatLine(id = "u2", kind = ChatLine.Kind.USER, text = "what shipped?"),
            ChatLine(id = "a2", kind = ChatLine.Kind.ASSISTANT, text = ""),
        )
        val merged = mergeKeepingLocalTail(history, local)
        assertEquals("what shipped?", merged.last { it.kind == ChatLine.Kind.USER }.text)
        assertEquals("", merged.last { it.kind == ChatLine.Kind.ASSISTANT }.text)
        assertEquals("old reply", merged.first { it.kind == ChatLine.Kind.ASSISTANT }.text)
        assertEquals(1, merged.count { it.kind == ChatLine.Kind.ASSISTANT && it.text == "old reply" })
    }

    @Test
    fun `conversationHasRemoteAdvance detects another device's prompt`() {
        val local = listOf(
            ChatLine(id = "u1", kind = ChatLine.Kind.USER, text = "first"),
            ChatLine(id = "a1", kind = ChatLine.Kind.ASSISTANT, text = "reply"),
        )
        val remote = local + ChatLine(id = "u2", kind = ChatLine.Kind.USER, text = "from iphone")
        assertTrue(conversationHasRemoteAdvance(local, remote))
        assertFalse(conversationHasRemoteAdvance(local, local))
        assertFalse(conversationHasRemoteAdvance(remote, local))
    }

    @Test
    fun `merge keeps local photos when conversation history is text only`() {
        val photo = DraftAttachment(
            id = "img1",
            mimeType = "image/jpeg",
            base64Data = "abc",
            displayName = "shot.jpg",
        )
        val local = listOf(
            ChatLine(
                id = "u1",
                kind = ChatLine.Kind.USER,
                text = "Sent 1 attachment(s)",
                attachments = listOf(photo),
            ),
            ChatLine(id = "a1", kind = ChatLine.Kind.ASSISTANT, text = "looking at the photo"),
        )
        val history = listOf(
            ChatLine(
                id = "hist-u",
                kind = ChatLine.Kind.USER,
                text = "Please review the attached files.",
            ),
            ChatLine(id = "hist-a", kind = ChatLine.Kind.ASSISTANT, text = "looking at the photo"),
        )
        val merged = mergeConversation(history, local, streaming = false)
        val user = merged.last { it.kind == ChatLine.Kind.USER }
        assertEquals(1, user.attachments.size)
        assertEquals("img1", user.attachments.single().id)
        assertEquals(1, merged.count { it.kind == ChatLine.Kind.USER })
    }

    @Test
    fun `failed is a terminal run status`() {
        assertTrue("FAILED".isTerminalRun())
        assertTrue("FINISHED".isTerminalRun())
        assertFalse("RUNNING".isTerminalRun())
        assertTrue(StreamEvent.Done.endsSse())
        assertTrue(StreamEvent.Result(status = "FINISHED", text = "ok").endsSse())
        assertTrue(StreamEvent.Status(runId = "r", status = "FINISHED").endsSse())
        assertFalse(StreamEvent.Assistant("hi").endsSse())
    }

    @Test
    fun `machine create requires a github repo`() {
        val worker = PrivateWorker(
            workerId = "20c20b5c-0584-561e-be84-c6ebef076a02",
            name = "~ @ Alex's Mac mini",
            workspaceRootPath = "/Users/alex",
        )
        val invalid = planCreateAgent(
            envType = "machine",
            worker = worker,
            machineFallback = worker.workerId,
            repoUrl = "",
            startingRef = "main",
            fallbackRepoUrl = "",
            prompt = PromptBody("hello"),
        )
        assertTrue(invalid is CreateAgentPlan.Invalid)

        val ready = planCreateAgent(
            envType = "machine",
            worker = worker,
            machineFallback = worker.workerId,
            repoUrl = "github.com/example/pointer",
            startingRef = "main",
            fallbackRepoUrl = "",
            prompt = PromptBody("hello"),
        )
        assertTrue(ready is CreateAgentPlan.Ready)
        val request = (ready as CreateAgentPlan.Ready).request
        assertEquals("machine", request.env?.type)
        assertEquals("~ @ Alex's Mac mini", request.env?.name)
        assertEquals("https://github.com/example/pointer", request.repos?.single()?.url)
        assertEquals("main", request.repos?.single()?.startingRef)
        assertEquals(true, request.workOnCurrentBranch)
        assertEquals(null, request.model)

        val json = kotlinx.serialization.json.Json { explicitNulls = false }
        val encoded = json.encodeToString(CreateAgentRequest.serializer(), request)
        assertTrue(encoded.contains("\"workOnCurrentBranch\":true"))
        assertTrue(encoded.contains("~ @ Alex's Mac mini"))
        assertFalse(encoded.contains("\"model\""))
    }

    @Test
    fun `preferred repo uses recent github url`() {
        val repos = listOf(
            GitHubRepository(url = "https://github.com/example/notes"),
            GitHubRepository(url = "https://github.com/example/pointer"),
        )
        assertEquals(
            "https://github.com/example/pointer",
            preferredRepoUrl(repos, recentRepoUrls = listOf("github.com/example/pointer")),
        )
    }

    @Test
    fun `resolved errors hide after a later user message`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("e1", ChatLine.Kind.ERROR, "HTTP 409"),
            ChatLine("u2", ChatLine.Kind.USER, "try again"),
        )
        assertEquals(listOf("u1", "u2"), lines.withoutResolvedErrors().map { it.id })
        assertEquals(null, resolvedAgentError("HTTP 409", lines))
    }

    @Test
    fun `latest error stays until something newer lands`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("e1", ChatLine.Kind.ERROR, "HTTP 409"),
        )
        assertEquals(listOf("u1", "e1"), lines.withoutResolvedErrors().map { it.id })
        assertEquals("HTTP 409", resolvedAgentError("HTTP 409", lines))
    }

    @Test
    fun `stale connect errors hide once the chat already has messages`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("e1", ChatLine.Kind.ERROR, "Failed to connect to api.cursor.com/1.2.3.4:443"),
        )
        assertEquals(listOf("u1"), lines.withoutResolvedErrors().map { it.id })
        assertEquals(null, resolvedAgentError(lines.last().text, lines))
    }

    @Test
    fun `connect failures are treated as transient stream loss`() {
        val failed = java.net.ConnectException("Failed to connect to api.cursor.com/1.2.3.4:443")
        assertTrue(failed.isTransientNetworkFailure())
        assertTrue(failed.isTransientStreamLoss())
        assertTrue("Failed to connect to api.cursor.com".isStaleConnectErrorText())
        assertTrue("couldn't reach cursor. check the network and try again".isStaleConnectErrorText())
        assertFalse("HTTP 409".isStaleConnectErrorText())
    }

    @Test
    fun `friendly network errors hide once the chat already has messages`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("e1", ChatLine.Kind.ERROR, "couldn't reach cursor. check the network and try again"),
        )
        assertEquals(listOf("u1"), lines.withoutResolvedErrors().map { it.id })
        assertEquals(null, resolvedAgentError(lines.last().text, lines))
    }

    @Test
    fun `errors hide when the same turn later gets an assistant reply`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("a1", ChatLine.Kind.ASSISTANT, "done"),
            ChatLine("e1", ChatLine.Kind.ERROR, "HTTP 409"),
        )
        assertEquals(listOf("u1", "a1"), lines.withoutResolvedErrors().map { it.id })
        assertEquals(null, resolvedAgentError("HTTP 409", lines))
        assertEquals(null, resolvedAgentError("HTTP 409", lines, streaming = true))
    }

    @Test
    fun `error banner hides while a later turn is streaming`() {
        val lines = listOf(
            ChatLine("u1", ChatLine.Kind.USER, "first"),
            ChatLine("e1", ChatLine.Kind.ERROR, "HTTP 409"),
        )
        assertEquals(null, resolvedAgentError("HTTP 409", lines, streaming = true))
        assertEquals("HTTP 409", resolvedAgentError("HTTP 409", lines, streaming = false))
    }

    private fun agent(
        id: String = "agent",
        status: String?,
        hasPr: Boolean = false,
    ) = AgentSummary(
        id = id,
        status = status,
        updatedAt = "2026-01-01T00:00:00Z",
        repos = if (hasPr) {
            listOf(RepoConfig(url = "https://github.com/example/repo", prUrl = "https://github.com/example/repo/pull/1"))
        } else {
            null
        },
    )
}
