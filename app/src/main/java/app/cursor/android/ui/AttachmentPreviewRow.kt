package app.cursor.android.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.data.DraftAttachment
import app.cursor.android.data.formatAttachmentSize
import app.cursor.android.data.imageBytes

@Composable
fun AttachmentPreviewRow(
    attachments: List<DraftAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (attachments.isEmpty()) return

    Row(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 0.dp else 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        attachments.forEach { attachment ->
            if (attachment.isImage) {
                val bitmap = remember(attachment.id, attachment.base64Data, attachment.backingFilePath) {
                    runCatching {
                        val bytes = attachment.imageBytes() ?: return@runCatching null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
                Box(modifier = Modifier.size(if (compact) 56.dp else 72.dp)) {
                    Surface(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.medium),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = attachment.displayName ?: "Attached image",
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Image,
                                    contentDescription = attachment.displayName ?: "Attached image",
                                    modifier = Modifier.size(if (compact) 22.dp else 28.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!compact) {
                        FilledTonalIconButton(
                            onClick = { onRemove(attachment.id) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            ),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove attachment",
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = attachment.displayName ?: "File",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val sizeLabel = attachment.sizeBytes?.let { formatAttachmentSize(it) }
                            val mimeLabel = attachment.mimeType.ifBlank { null }
                            val meta = listOfNotNull(sizeLabel, mimeLabel).joinToString(" · ")
                            if (meta.isNotEmpty()) {
                                Text(
                                    text = meta,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (!compact) {
                            FilledTonalIconButton(
                                onClick = { onRemove(attachment.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "Remove attachment",
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
