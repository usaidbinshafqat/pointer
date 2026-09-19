package app.cursor.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cursor.android.ui.theme.PointerMono
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun ThinkingCard(
    thinking: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentlyThinking = isStreaming && thinking.isNotBlank()
    if (thinking.isBlank() && !currentlyThinking) return

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = { if (thinking.isNotBlank()) expanded = !expanded },
            label = { Text(if (currentlyThinking) "thinking" else "thought") },
            leadingIcon = {
                if (currentlyThinking) {
                    AppSpinner(
                        modifier = Modifier.size(16.dp),
                        compact = true,
                    )
                } else {
                    Icon(
                        Icons.Outlined.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )
        AnimatedVisibility(visible = expanded && thinking.isNotBlank()) {
            val typography = MaterialTheme.typography
            Surface(
                onClick = { expanded = false },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                PointerMarkdown(
                    content = thinking.ifEmpty { " " },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    colors = markdownColor(
                        text = MaterialTheme.colorScheme.onSurfaceVariant,
                        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                        inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant,
                        dividerColor = MaterialTheme.colorScheme.outline,
                        tableBackground = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                    markdownTypography = markdownTypography(
                        h1 = typography.titleMedium,
                        h2 = typography.titleSmall,
                        h3 = typography.titleSmall,
                        h4 = typography.titleSmall,
                        h5 = typography.titleSmall,
                        h6 = typography.titleSmall,
                        text = typography.bodyMedium,
                        code = typography.bodyMedium.copy(fontFamily = PointerMono),
                        inlineCode = typography.bodyMedium.copy(fontFamily = PointerMono),
                        quote = typography.bodyMedium,
                        paragraph = typography.bodyMedium,
                        ordered = typography.bodyMedium,
                        bullet = typography.bodyMedium,
                        list = typography.bodyMedium,
                        table = typography.bodyMedium,
                    ),
                )
            }
        }
    }
}
