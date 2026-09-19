package app.cursor.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.cursor.android.data.ChatIconCatalog
import app.cursor.android.data.InboxCategory

fun chatIconVector(id: String): ImageVector = when (id) {
    "code" -> Icons.Outlined.Code
    "bug" -> Icons.Outlined.BugReport
    "design" -> Icons.Outlined.Palette
    "lock" -> Icons.Outlined.Lock
    "cloud" -> Icons.Outlined.Cloud
    "phone" -> Icons.Outlined.Smartphone
    "computer" -> Icons.Outlined.Computer
    "folder" -> Icons.Outlined.Folder
    "database" -> Icons.Outlined.Storage
    "test" -> Icons.Outlined.Science
    "settings" -> Icons.Outlined.Settings
    "image" -> Icons.Outlined.Image
    "translate" -> Icons.Outlined.Translate
    "speed" -> Icons.Outlined.Speed
    "git" -> Icons.Outlined.AccountTree
    "terminal" -> Icons.Outlined.Terminal
    "rocket" -> Icons.Outlined.RocketLaunch
    "docs" -> Icons.Outlined.Description
    "search" -> Icons.Outlined.Search
    else -> Icons.Outlined.Forum
}

@Composable
fun chatIconColors(category: InboxCategory): Pair<Color, Color> = when (category) {
    InboxCategory.WORKING ->
        MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
    InboxCategory.DONE ->
        MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
    InboxCategory.WORKSPACE ->
        MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
}

@Composable
fun ChatIconPickerDialog(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("chat icon") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp),
            ) {
                items(ChatIconCatalog.all, key = { it.id }) { icon ->
                    val selected = icon.id == selectedId
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            onClick = { onSelect(icon.id) },
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    chatIconVector(icon.id),
                                    contentDescription = icon.label,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("done") }
        },
    )
}
