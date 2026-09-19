package app.cursor.android.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.Base64 as JavaBase64
import java.util.zip.Inflater
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min

object AttachmentEncoder {
    const val MaxImages = 5
    const val MaxImageBytes = 15 * 1024 * 1024
    const val MaxFileBytes = 10 * 1024 * 1024
    private const val MaxEdgePx = 1600
    private const val JpegQuality = 82
    private const val BlobDirectory = "attachment_blobs"
    private const val OrphanBlobRetentionMs = 7L * 24 * 60 * 60 * 1000
    const val TextPreviewLimit = 200_000
    private const val HexPreviewBytes = 32
    private const val PdfScanLimit = 2 * 1024 * 1024

    val PickerImageMime = "image/*"
    val PickerFileMimes = arrayOf(
        "application/pdf",
        "text/*",
        "application/json",
        "application/xml",
        "application/javascript",
        "text/javascript",
        "text/csv",
        "text/markdown",
        "text/html",
        "text/css",
        "application/x-yaml",
        "application/yaml",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    private val ImageMimes = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "image/webp",
    )
    private val FileMimes = setOf(
        "application/pdf",
        "application/json",
        "application/xml",
        "application/javascript",
        "text/javascript",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/html",
        "text/css",
        "text/x-python",
        "text/x-kotlin",
        "text/x-java-source",
        "application/x-yaml",
        "application/yaml",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )

    fun isSupportedImage(mime: String?, name: String?): Boolean {
        val lowerMime = mime?.lowercase(Locale.US).orEmpty()
        if (lowerMime in ImageMimes) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return ext in setOf("jpg", "jpeg", "png", "gif", "webp")
    }

    fun isSupportedFile(mime: String?, name: String?): Boolean {
        if (isSupportedImage(mime, name)) return false
        val lowerMime = mime?.lowercase(Locale.US).orEmpty()
        if (lowerMime.startsWith("text/")) return true
        if (lowerMime in FileMimes) return true
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return ext in TextExtensions || ext in setOf("pdf", "docx")
    }

    fun encodeImage(context: Context, uri: Uri): DraftAttachment? {
        val resolver = context.contentResolver
        val name = displayName(context, uri) ?: "image"
        val mime = resolver.getType(uri) ?: guessMime(name) ?: "image/jpeg"
        val original = decodeBitmap(context, uri, MaxEdgePx) ?: return null
        return try {
            val scaled = scaleToMaxEdge(original, MaxEdgePx)
            val out = ByteArrayOutputStream()
            val usePng = mime.contains("png", ignoreCase = true)
            val ok = if (usePng) {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, JpegQuality, out)
            }
            if (scaled !== original) scaled.recycle()
            if (!ok || out.size() > MaxImageBytes) return null
            val encoded = out.toByteArray()
            val id = UUID.randomUUID().toString()
            val backingFilePath = storeImageBlob(context, id, encoded) ?: return null
            DraftAttachment(
                id = id,
                mimeType = if (usePng) "image/png" else "image/jpeg",
                // Keep the encoded bytes in one app-private blob. Preview and API upload load it
                // on demand, avoiding a second ~33%-larger base64 copy in live state.
                base64Data = "",
                displayName = name,
                kind = AttachmentKind.IMAGE,
                sizeBytes = encoded.size.toLong(),
                backingFilePath = backingFilePath,
            )
        } finally {
            original.recycle()
        }
    }

    fun encodeFile(context: Context, uri: Uri): DraftAttachment? {
        val resolver = context.contentResolver
        val meta = uriMeta(context, uri)
        val mime = resolver.getType(uri) ?: guessMime(meta.name) ?: "application/octet-stream"
        val name = meta.name ?: "file"

        if (isSupportedImage(mime, name) && !mime.contains("svg", ignoreCase = true)) {
            return encodeImage(context, uri)
        }
        if (!isSupportedFile(mime, name)) return null

        val read = resolver.openInputStream(uri)?.use { input -> readPrefix(input, MaxFileBytes) }
            ?: return null
        val sizeBytes = meta.size
            ?: if (read.truncated) null else read.bytes.size.toLong()
            ?: read.bytes.size.toLong()

        val bytes = read.bytes
        val extracted = extractPreview(name, mime, bytes, truncated = read.truncated, sizeBytes = sizeBytes)
        return fileAttachment(
            mime = mime,
            name = name,
            sizeBytes = sizeBytes ?: bytes.size.toLong(),
            preview = extracted,
        )
    }

    private fun fileAttachment(
        mime: String,
        name: String,
        sizeBytes: Long?,
        preview: String,
    ): DraftAttachment = DraftAttachment(
        id = UUID.randomUUID().toString(),
        mimeType = mime,
        // Non-image attachments are represented by their extracted text. Their raw bytes are
        // never sent to the API, so retaining another base64 copy only bloats memory and storage.
        base64Data = "",
        displayName = name,
        kind = AttachmentKind.FILE,
        textPreview = preview,
        sizeBytes = sizeBytes,
    )

    private fun extractPreview(
        name: String,
        mime: String,
        bytes: ByteArray,
        truncated: Boolean,
        sizeBytes: Long?,
    ): String {
        val lowerName = name.lowercase(Locale.US)
        val lowerMime = mime.lowercase(Locale.US)

        if (lowerMime.contains("pdf") || lowerName.endsWith(".pdf")) {
            extractPdfText(bytes)?.let { text ->
                return truncatedText(text, truncated, sizeBytes)
            }
        }

        if (
            lowerMime.contains("wordprocessingml") ||
            lowerMime.contains("officedocument.word") ||
            lowerName.endsWith(".docx")
        ) {
            extractDocxText(bytes)?.let { text ->
                return truncatedText(text, truncated, sizeBytes)
            }
        }

        val decoded = decodeText(bytes)
        if (decoded != null && (looksLikeText(mime, name) || isMostlyPrintable(decoded))) {
            return truncatedText(decoded, truncated, sizeBytes)
        }

        val hex = hexPreview(bytes)
        val extra = buildString {
            if (truncated) {
                append("Only the first ${formatAttachmentSize(bytes.size.toLong())} were inspected; ")
                append("the file is larger")
                sizeBytes?.let { append(" (${formatAttachmentSize(it)})") }
                append('.')
            }
        }.ifBlank { null }
        return workspaceNote(name, mime, sizeBytes, hex, extra)
    }

    private fun truncatedText(text: String, fileTruncated: Boolean, sizeBytes: Long?): String {
        val clipped = text.take(TextPreviewLimit)
        return buildString {
            append(clipped)
            if (text.length > TextPreviewLimit || fileTruncated) {
                append("\n\n[Truncated: showing first ${clipped.length} characters")
                sizeBytes?.let { append("; file size ${formatAttachmentSize(it)}") }
                append("]")
            }
        }
    }

    private fun workspaceNote(
        name: String,
        mime: String,
        sizeBytes: Long?,
        hex: String?,
        extra: String?,
    ): String = buildString {
        append("This is not an inline image. The API only accepts png/jpeg/gif/webp for images, ")
        append("so the raw bytes were not sent as an image.")
        hex?.let { append(" First bytes (hex): $it.") }
        extra?.let { append(' ').append(it) }
        append(" Ask to have \"$name\"")
        sizeBytes?.let { append(" (${formatAttachmentSize(it)}, $mime)") }
        append(" on the workspace if you need the full file.")
    }

    private fun displayName(context: Context, uri: Uri): String? = uriMeta(context, uri).name

    private fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val target = scaledDimensions(info.size.width, info.size.height, maxEdge)
                    decoder.setTargetSize(target.first, target.second)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                }
            }.getOrNull()?.let { return it }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun storeImageBlob(context: Context, id: String, bytes: ByteArray): String? = runCatching {
        val directory = File(context.noBackupFilesDir, BlobDirectory)
        if (!directory.exists() && !directory.mkdirs()) return@runCatching null
        val target = File(directory, "$id.img")
        val temporary = File(directory, "$id.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        target.absolutePath
    }.getOrNull()

    internal fun persistableCopy(context: Context, attachment: DraftAttachment): DraftAttachment {
        if (!attachment.isImage) return attachment.copy(base64Data = "", backingFilePath = null)
        val currentPath = attachment.backingFilePath
            ?.takeIf { path -> File(path).let { it.isFile && it.length() in 1..MaxImageBytes.toLong() } }
        val storedPath = currentPath ?: runCatching {
            val bytes = Base64.decode(attachment.base64Data, Base64.DEFAULT)
            if (bytes.isEmpty() || bytes.size > MaxImageBytes) null
            else storeImageBlob(context, attachment.id, bytes)
        }.getOrNull()
        // Keep inline data only as a last-resort fallback if app-private storage is unavailable;
        // normally persisted drafts and queues contain just the small blob path.
        return if (storedPath != null) {
            attachment.copy(base64Data = "", backingFilePath = storedPath)
        } else {
            attachment
        }
    }

    internal fun pruneUnreferencedBlobs(
        context: Context,
        referencedAttachments: Collection<DraftAttachment>,
    ) {
        val referencedPaths = referencedAttachments.mapNotNull { it.backingFilePath }.toSet()
        val staleBefore = System.currentTimeMillis() - OrphanBlobRetentionMs
        File(context.noBackupFilesDir, BlobDirectory).listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths && file.lastModified() < staleBefore) {
                runCatching { file.delete() }
            }
        }
    }

    private data class UriMeta(val name: String?, val size: Long?)

    private fun uriMeta(context: Context, uri: Uri): UriMeta {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use UriMeta(null, null)
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
            val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                cursor.getLong(sizeIdx).takeIf { it >= 0 }
            } else {
                null
            }
            UriMeta(name, size)
        } ?: UriMeta(null, null)
    }

    private data class ReadPrefix(val bytes: ByteArray, val truncated: Boolean)

    private fun readPrefix(input: java.io.InputStream, maxBytes: Int): ReadPrefix {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        var truncated = false
        while (true) {
            val remaining = maxBytes - total
            if (remaining <= 0) {
                truncated = input.read() >= 0
                break
            }
            val count = input.read(buffer, 0, min(buffer.size, remaining))
            if (count < 0) break
            total += count
            output.write(buffer, 0, count)
        }
        return ReadPrefix(output.toByteArray(), truncated)
    }

    internal fun looksLikeText(mime: String, name: String): Boolean {
        val mimeLower = mime.lowercase(Locale.US)
        if (mimeLower.startsWith("text/")) return true
        if (mimeLower.contains("json") || mimeLower.contains("xml") || mimeLower.contains("javascript")) {
            return true
        }
        if (mimeLower.contains("csv") || mimeLower.contains("yaml") || mimeLower.contains("markdown")) {
            return true
        }
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        return ext in TextExtensions
    }

    internal fun fenceLanguage(name: String, mime: String): String {
        val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.US)
        return when (ext) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js", "mjs", "cjs" -> "javascript"
            "ts" -> "typescript"
            "tsx" -> "tsx"
            "jsx" -> "jsx"
            "json" -> "json"
            "xml", "svg" -> "xml"
            "html", "htm" -> "html"
            "css", "scss" -> "css"
            "md", "markdown" -> "markdown"
            "csv", "tsv" -> "csv"
            "yml", "yaml" -> "yaml"
            "swift" -> "swift"
            "gradle" -> "gradle"
            "sh", "zsh", "bash" -> "bash"
            "sql" -> "sql"
            "rb" -> "ruby"
            "go" -> "go"
            "rs" -> "rust"
            "c" -> "c"
            "h", "hpp", "hh" -> "cpp"
            "cpp", "cc", "cxx" -> "cpp"
            "cs" -> "csharp"
            "toml" -> "toml"
            "ini", "properties" -> "ini"
            "php" -> "php"
            "dart" -> "dart"
            "lua" -> "lua"
            "r" -> "r"
            "pdf" -> "text"
            "docx" -> "text"
            else -> when {
                mime.contains("json", ignoreCase = true) -> "json"
                mime.contains("xml", ignoreCase = true) -> "xml"
                mime.contains("csv", ignoreCase = true) -> "csv"
                mime.contains("markdown", ignoreCase = true) -> "markdown"
                mime.startsWith("text/") -> ""
                else -> ""
            }
        }
    }

    private fun guessMime(name: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "md" -> "text/markdown"
            "txt" -> "text/plain"
            else -> null
        }
    }

    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xff
            val b1 = bytes[1].toInt() and 0xff
            if (b0 == 0xFF && b1 == 0xFE) {
                return runCatching { String(bytes, StandardCharsets.UTF_16LE) }.getOrNull()
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return runCatching { String(bytes, StandardCharsets.UTF_16BE) }.getOrNull()
            }
        }
        val probe = bytes.copyOf(min(bytes.size, 8192))
        if (probe.any { it == 0.toByte() }) return null
        return decodeUtf8(bytes)
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun isMostlyPrintable(text: String): Boolean {
        if (text.isEmpty()) return true
        val sample = text.take(4000)
        val ok = sample.count { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || ch.code in 32..126 || !ch.isISOControl()
        }
        return ok.toFloat() / sample.length >= 0.85f
    }

    private fun extractDocxText(bytes: ByteArray): String? = runCatching {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xml = zip.readBytes().decodeToString()
                    val text = xml
                        .replace(Regex("(?i)</w:p>"), "\n")
                        .replace(Regex("(?i)</w:tr>"), "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace(Regex("[ \\t]+"), " ")
                        .replace(Regex("\\n{3,}"), "\n\n")
                        .trim()
                    return@use text.takeIf { it.isNotBlank() }
                }
                entry = zip.nextEntry
            }
            null
        }
    }.getOrNull()

    private fun extractPdfText(bytes: ByteArray): String? {
        val window = if (bytes.size > PdfScanLimit) bytes.copyOf(PdfScanLimit) else bytes
        val chunks = ArrayList<String>()
        var searchFrom = 0
        val streamMarker = "stream".toByteArray()
        val endMarker = "endstream".toByteArray()
        while (chunks.sumOf { it.length } < TextPreviewLimit) {
            val start = indexOf(window, streamMarker, searchFrom) ?: break
            var contentStart = start + streamMarker.size
            if (contentStart < window.size && window[contentStart] == '\r'.code.toByte()) contentStart++
            if (contentStart < window.size && window[contentStart] == '\n'.code.toByte()) contentStart++
            val end = indexOf(window, endMarker, contentStart) ?: break
            if (end > contentStart) {
                val stream = window.copyOfRange(contentStart, end)
                extractPdfStrings(inflateOrRaw(stream), chunks)
            }
            searchFrom = end + endMarker.size
        }
        if (chunks.isEmpty()) {
            extractPdfStrings(window, chunks)
        }
        val text = chunks.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return text.takeIf { it.length >= 16 }
    }

    private fun inflateOrRaw(stream: ByteArray): ByteArray {
        inflate(stream, nowrap = false)?.takeIf { it.isNotEmpty() }?.let { return it }
        inflate(stream, nowrap = true)?.takeIf { it.isNotEmpty() }?.let { return it }
        return stream
    }

    private fun inflate(stream: ByteArray, nowrap: Boolean): ByteArray? = runCatching {
        val inflater = Inflater(nowrap)
        inflater.setInput(stream)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
            if (out.size() > TextPreviewLimit * 2) break
        }
        inflater.end()
        out.toByteArray()
    }.getOrNull()

    private fun extractPdfStrings(data: ByteArray, out: MutableList<String>) {
        var i = 0
        while (i < data.size && out.sumOf { it.length } < TextPreviewLimit) {
            if (data[i] == '('.code.toByte()) {
                val sb = StringBuilder()
                i++
                var depth = 1
                while (i < data.size && depth > 0) {
                    val c = data[i]
                    when {
                        c == '\\'.code.toByte() && i + 1 < data.size -> {
                            i++
                            when (val esc = data[i].toInt().toChar()) {
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000c')
                                '(', ')', '\\' -> sb.append(esc)
                                else -> {
                                    if (esc in '0'..'7') {
                                        var oct = esc - '0'
                                        var consumed = 1
                                        while (consumed < 3 && i + 1 < data.size) {
                                            val next = data[i + 1].toInt().toChar()
                                            if (next !in '0'..'7') break
                                            i++
                                            oct = oct * 8 + (next - '0')
                                            consumed++
                                        }
                                        sb.append(oct.toChar())
                                    }
                                }
                            }
                        }
                        c == '('.code.toByte() -> {
                            depth++
                            sb.append('(')
                        }
                        c == ')'.code.toByte() -> depth--
                        else -> sb.append((c.toInt() and 0xff).toChar())
                    }
                    i++
                }
                val s = sb.toString().trim()
                if (s.length >= 2 && s.any { it.isLetterOrDigit() }) out += s
            } else {
                i++
            }
        }
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int? {
        if (pattern.isEmpty() || from < 0) return null
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return null
    }

    private fun hexPreview(bytes: ByteArray): String =
        bytes.take(HexPreviewBytes).joinToString(" ") { b ->
            "%02x".format(b.toInt() and 0xff)
        }

    private fun scaleToMaxEdge(source: Bitmap, maxEdge: Int): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxEdge) return source
        val scale = maxEdge.toFloat() / longest.toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private val TextExtensions = setOf(
        "txt", "md", "markdown", "json", "xml", "csv", "tsv", "yml", "yaml",
        "kt", "kts", "java", "py", "swift", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "html", "htm", "css", "scss", "gradle", "properties", "toml", "ini",
        "sh", "bash", "zsh", "sql", "rb", "go", "rs", "c", "h", "cpp", "cc", "hpp",
        "cs", "log", "gitignore", "env", "r", "php", "lua", "pl", "dart", "vue",
        "svelte", "svg", "ktm", "proto", "graphql", "tf", "makefile",
    )
}

internal fun scaledDimensions(width: Int, height: Int, maxEdge: Int): Pair<Int, Int> {
    require(maxEdge > 0)
    if (width <= 0 || height <= 0) return 1 to 1
    val longest = max(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toDouble() / longest.toDouble()
    return (width * scale).toInt().coerceAtLeast(1) to
        (height * scale).toInt().coerceAtLeast(1)
}

internal fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(maxEdge > 0)
    var sample = 1
    val longest = max(width, height).coerceAtLeast(1)
    while (longest / sample > maxEdge && sample <= Int.MAX_VALUE / 2) {
        sample *= 2
    }
    return sample
}

fun DraftAttachment.imageBytes(): ByteArray? {
    if (!isImage) return null
    if (base64Data.isNotBlank()) {
        runCatching { Base64.decode(base64Data, Base64.DEFAULT) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() && it.size <= AttachmentEncoder.MaxImageBytes }
            ?.let { return it }
    }
    val path = backingFilePath ?: return null
    return runCatching {
        val file = File(path)
        if (!file.isFile || file.length() !in 1..AttachmentEncoder.MaxImageBytes.toLong()) null
        else file.readBytes()
    }.getOrNull()
}

fun formatAttachmentSize(bytes: Long): String {
    val abs = kotlin.math.abs(bytes)
    return when {
        abs < 1024 -> "$bytes B"
        abs < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

fun List<DraftAttachment>.toPromptImages(): List<PromptImage> =
    filter { it.isImage }
        .take(AttachmentEncoder.MaxImages)
        .mapNotNull(DraftAttachment::toPromptImage)

internal fun DraftAttachment.toPromptImage(): PromptImage? {
    if (!isImage) return null
    val data = base64Data.takeIf { it.isNotBlank() }
        ?: imageBytes()?.let(JavaBase64.getEncoder()::encodeToString)
    return data?.takeIf { it.isNotBlank() }?.let {
        PromptImage(data = it, mimeType = mimeType)
    }
}

fun buildPromptText(draft: String, attachments: List<DraftAttachment>): String = buildString {
    append(draft.trim())
    attachments.filterNot { it.isImage }.forEach { file ->
        val name = file.displayName?.ifBlank { null } ?: "file"
        val mime = file.mimeType.ifBlank { "application/octet-stream" }
        val size = file.sizeBytes?.let { formatAttachmentSize(it) }
        append("\n\nAttached file: $name")
        append("\nMIME: $mime")
        size?.let { append("\nSize: $it") }
        val preview = file.textPreview?.takeIf { it.isNotBlank() }
        if (preview == null) {
            append("\nNo inline preview was extracted. Ask to have this file on the workspace if you need its contents.")
        } else if (looksLikeExtractedBody(preview)) {
            val lang = AttachmentEncoder.fenceLanguage(name, mime)
            append("\n\n```").append(lang).append('\n')
            append(preview)
            append("\n```")
        } else {
            append('\n').append(preview)
        }
    }
}

private fun looksLikeExtractedBody(preview: String): Boolean {
    if (preview.startsWith("This is not an inline image.")) return false
    if (preview.startsWith("The file could not be read")) return false
    return true
}
