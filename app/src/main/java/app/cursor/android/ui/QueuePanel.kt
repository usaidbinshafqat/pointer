package app.cursor.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.data.QueuedPrompt

@Composable
fun QueueChip(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.height(32.dp),
        leadingIcon = {
            Icon(
                Icons.Outlined.Schedule,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        label = {
            Text(if (count == 1) "queued 1" else "queued $count")
        },
    )
}

/**
 * iOS "Queued" sheet parity: each row can be sent now (↑) or dropped (trash).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    items: List<QueuedPrompt>,
    onRemove: (String) -> Unit,
    onSendNow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberPointerHaptics()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "queued",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("done") }
            }
            if (items.isEmpty()) {
                Text(
                    "nothing queued",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items.forEach { item ->
                ListItem(
                    headlineContent = {
                        Text(item.displayText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = if (item.attachments.isNotEmpty()) {
                        {
                            Text(
                                "${item.attachments.size} attachment${if (item.attachments.size == 1) "" else "s"}",
                            )
                        }
                    } else {
                        null
                    },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Click)
                                    onSendNow(item.id)
                                },
                            ) {
                                Icon(Icons.Outlined.ArrowUpward, contentDescription = "send now")
                            }
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Warning)
                                    onRemove(item.id)
                                },
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "remove from queue")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
