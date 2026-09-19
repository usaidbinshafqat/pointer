package app.cursor.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.data.AgentTodo
import app.cursor.android.data.ChangedFile
import app.cursor.android.ui.theme.PointerMono

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TurnTodosCard(
    todos: List<AgentTodo>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading && todos.isEmpty()) return
    var expanded by rememberSaveable(todos.size, loading) { mutableStateOf(false) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(if (loading && todos.isEmpty()) "todos" else "todos (${todos.size})")
            },
            trailingContent = {
                when {
                    loading && todos.isEmpty() ->
                        AppSpinner(Modifier.size(24.dp), compact = true)
                    todos.isNotEmpty() -> Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "collapse todos" else "expand todos",
                    )
                }
            },
            modifier = Modifier.clickable(enabled = todos.isNotEmpty()) { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded && todos.isNotEmpty()) {
            Column {
                HorizontalDivider()
                todos.forEach { todo ->
                    ListItem(
                        headlineContent = {
                            Text(
                                todo.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (todo.isCompleted || todo.isCancelled) {
                                        TextDecoration.LineThrough
                                    } else {
                                        TextDecoration.None
                                    },
                                ),
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = when {
                                    todo.isCompleted -> Icons.Outlined.CheckCircle
                                    todo.isInProgress -> Icons.Outlined.Schedule
                                    else -> Icons.Outlined.RadioButtonUnchecked
                                },
                                contentDescription = todo.status,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TurnFilesCard(
    files: List<ChangedFile>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loading && files.isEmpty()) return
    var expanded by rememberSaveable(files.size, loading) { mutableStateOf(files.size in 1..8) }

    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("changes")
                    if (files.isNotEmpty()) {
                        Text(files.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            trailingContent = {
                when {
                    loading && files.isEmpty() ->
                        AppSpinner(Modifier.size(24.dp), compact = true)
                    files.isNotEmpty() -> Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "collapse changes" else "expand changes",
                    )
                }
            },
            modifier = Modifier.clickable(enabled = files.isNotEmpty()) { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded && files.isNotEmpty()) {
            Column {
                HorizontalDivider()
                files.forEach { file ->
                    ListItem(
                        headlineContent = {
                            Text(file.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(file.shortPath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            DiffDelta(added = file.linesAdded, removed = file.linesRemoved)
                        },
                        leadingContent = {
                            Icon(file.fileIcon(), contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}

private fun ChangedFile.fileIcon(): ImageVector {
    val ext = extension
    return when {
        ext in setOf("png", "jpg", "jpeg", "gif", "webp", "svg") -> Icons.Outlined.Image
        ext in setOf("kt", "kts", "java", "swift", "py", "ts", "tsx", "js", "go", "rs") ->
            Icons.Outlined.Code
        ext in setOf("json", "xml", "yml", "yaml", "toml", "gradle") -> Icons.Outlined.DataObject
        ext in setOf("md", "txt", "rst") -> Icons.Outlined.Description
        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }
}

/** GitHub-style +added / −removed pair; colours tuned for light and dark surfaces. */
@Composable
fun DiffDelta(added: Int?, removed: Int?) {
    if (added == null && removed == null) return
    val dark = isSystemInDarkTheme()
    val plus = if (dark) Color(0xFF3FB950) else Color(0xFF1A7F37)
    val minus = if (dark) Color(0xFFF85149) else Color(0xFFCF222E)
    val style = MaterialTheme.typography.labelLarge.copy(fontFamily = PointerMono)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        added?.let { Text("+$it", style = style, color = plus) }
        removed?.let { Text("−$it", style = style, color = minus) }
    }
}
