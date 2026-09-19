package app.cursor.android.data

import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.Base64
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentPersistenceTest {
    @Test
    fun `persistence prepares composer and queued attachments without changing prompt metadata`() {
        val image = attachment("image", AttachmentKind.IMAGE, base64Data = "large-image-payload")
        val file = attachment("file", AttachmentKind.FILE, base64Data = "unneeded-file-payload")
        val state = LocalAppState(
            chatTitles = mapOf("agent" to "Important chat"),
            composerDrafts = mapOf(
                "agent" to ComposerDraft(text = "draft", attachments = listOf(image, file)),
            ),
            queuedPrompts = mapOf(
                "agent" to listOf(
                    QueuedPrompt(
                        id = "queued",
                        displayText = "display",
                        apiText = "api",
                        attachments = listOf(file, image),
                        modelId = "model",
                        modelParams = listOf(ModelParamValue("fast", "true")),
                    ),
                ),
            ),
        )
        val persistedIds = mutableListOf<String>()

        val persisted = state.withPersistableAttachments { candidate ->
            persistedIds += candidate.id
            candidate.copy(
                base64Data = "",
                backingFilePath = candidate.id.takeIf { candidate.isImage }?.let { "/blobs/$it.img" },
            )
        }

        assertEquals(listOf("image", "file", "file", "image"), persistedIds)
        assertEquals("draft", persisted.composerDrafts.getValue("agent").text)
        assertEquals("", persisted.composerDrafts.getValue("agent").attachments[0].base64Data)
        assertEquals("/blobs/image.img", persisted.composerDrafts.getValue("agent").attachments[0].backingFilePath)
        assertNull(persisted.composerDrafts.getValue("agent").attachments[1].backingFilePath)
        val queued = persisted.queuedPrompts.getValue("agent").single()
        assertEquals("display", queued.displayText)
        assertEquals("api", queued.apiText)
        assertEquals("model", queued.modelId)
        assertEquals("true", queued.modelParams.single().value)
        assertEquals("Important chat", persisted.chatTitles.getValue("agent"))
    }

    @Test
    fun `blob path survives local state serialization without an inline image payload`() {
        val path = "/private/no-backup/attachment_blobs/photo.img"
        val state = LocalAppState(
            composerDrafts = mapOf(
                "agent" to ComposerDraft(
                    attachments = listOf(
                        attachment("photo", AttachmentKind.IMAGE, backingFilePath = path),
                    ),
                ),
            ),
        )
        val json = Json { encodeDefaults = true }

        val encoded = json.encodeToString(state)
        val decoded = json.decodeFromString<LocalAppState>(encoded)
        val restored = decoded.composerDrafts.getValue("agent").attachments.single()

        assertFalse(encoded.contains("large-image-payload"))
        assertEquals("", restored.base64Data)
        assertEquals(path, restored.backingFilePath)
        assertTrue(restored.isImage)
    }

    @Test
    fun `file backed image hydrates into the API prompt`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 126, 127, -1)
        val path = Files.createTempFile("pointer-image", ".blob")
        try {
            Files.write(path, bytes)
            val image = attachment(
                id = "photo",
                kind = AttachmentKind.IMAGE,
                mimeType = "image/png",
                backingFilePath = path.toString(),
            )

            val prompt = image.toPromptImage()

            assertEquals(Base64.getEncoder().encodeToString(bytes), prompt?.data)
            assertEquals("image/png", prompt?.mimeType)
            assertArrayEquals(bytes, image.imageBytes())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `inline image remains usable while old drafts migrate to blob storage`() {
        val image = attachment(
            id = "legacy-photo",
            kind = AttachmentKind.IMAGE,
            mimeType = "image/jpeg",
            base64Data = "AQIDBA==",
            backingFilePath = "/missing/blob.img",
        )

        assertEquals("AQIDBA==", image.toPromptImage()?.data)
        assertEquals("image/jpeg", image.toPromptImage()?.mimeType)
    }

    @Test
    fun `missing empty and oversized blobs are not uploaded`() {
        val empty = Files.createTempFile("pointer-empty", ".blob")
        val oversized = Files.createTempFile("pointer-large", ".blob")
        try {
            RandomAccessFile(oversized.toFile(), "rw").use {
                it.setLength(AttachmentEncoder.MaxImageBytes.toLong() + 1)
            }
            val images = listOf(
                attachment("missing", AttachmentKind.IMAGE, backingFilePath = "/definitely/missing.img"),
                attachment("empty", AttachmentKind.IMAGE, backingFilePath = empty.toString()),
                attachment("large", AttachmentKind.IMAGE, backingFilePath = oversized.toString()),
            )

            assertTrue(images.toPromptImages().isEmpty())
            assertTrue(images.all { it.imageBytes() == null })
        } finally {
            Files.deleteIfExists(empty)
            Files.deleteIfExists(oversized)
        }
    }

    @Test
    fun `API prompt accepts only images and enforces the image count limit`() {
        val file = attachment("notes", AttachmentKind.FILE, base64Data = "file-data")
        val images = (1..7).map { index ->
            attachment("image-$index", AttachmentKind.IMAGE, base64Data = "encoded-$index")
        }

        val prompts = (listOf(file) + images).toPromptImages()

        assertEquals(AttachmentEncoder.MaxImages, prompts.size)
        assertEquals((1..5).map { "encoded-$it" }, prompts.map { it.data })
    }

    @Test
    fun `non image attachments never hydrate from a backing file`() {
        val bytes = byteArrayOf(9, 8, 7)
        val path = Files.createTempFile("pointer-file", ".txt")
        try {
            Files.write(path, bytes)
            val file = attachment(
                id = "notes",
                kind = AttachmentKind.FILE,
                backingFilePath = path.toString(),
            )

            assertNull(file.imageBytes())
            assertNull(file.toPromptImage())
        } finally {
            Files.deleteIfExists(path)
        }
    }

    private fun attachment(
        id: String,
        kind: AttachmentKind,
        mimeType: String = if (kind == AttachmentKind.IMAGE) "image/jpeg" else "text/plain",
        base64Data: String = "",
        backingFilePath: String? = null,
    ) = DraftAttachment(
        id = id,
        mimeType = mimeType,
        base64Data = base64Data,
        kind = kind,
        backingFilePath = backingFilePath,
    )
}
