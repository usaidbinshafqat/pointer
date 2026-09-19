package app.cursor.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.ui.theme.PointerMono
import com.mikepenz.markdown.annotator.annotatorSettings
import com.mikepenz.markdown.annotator.buildMarkdownAnnotatedString
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownColors
import com.mikepenz.markdown.model.MarkdownTypography
import kotlinx.coroutines.delay
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.flavours.gfm.GFMElementTypes.HEADER
import org.intellij.markdown.flavours.gfm.GFMElementTypes.ROW
import org.intellij.markdown.flavours.gfm.GFMTokenTypes.CELL

@Composable
fun AssistantMarkdown(
    content: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    var displayed by remember { mutableStateOf(content) }
    val latest by rememberUpdatedState(content)

    LaunchedEffect(isStreaming, content) {
        if (!isStreaming) {
            displayed = latest
            return@LaunchedEffect
        }
        while (true) {
            if (displayed != latest) displayed = latest
            delay(80)
        }
    }

    val typography = MaterialTheme.typography
    PointerMarkdown(
        content = displayed.ifEmpty { " " },
        modifier = modifier,
        colors = markdownColor(
            text = MaterialTheme.colorScheme.onSurface,
            codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            inlineCodeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            dividerColor = MaterialTheme.colorScheme.outline,
            tableBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
        markdownTypography = markdownTypography(
            h1 = typography.headlineMedium,
            h2 = typography.titleLarge,
            h3 = typography.titleMedium,
            h4 = typography.titleSmall,
            h5 = typography.titleSmall,
            h6 = typography.titleSmall,
            text = typography.bodyLarge,
            code = typography.bodyMedium.copy(fontFamily = PointerMono),
            inlineCode = typography.bodyMedium.copy(fontFamily = PointerMono),
            quote = typography.bodyLarge,
            paragraph = typography.bodyLarge,
            ordered = typography.bodyLarge,
            bullet = typography.bodyLarge,
            list = typography.bodyLarge,
            table = typography.bodyLarge,
        ),
    )
}

@Composable
internal fun PointerMarkdown(
    content: String,
    colors: MarkdownColors,
    markdownTypography: MarkdownTypography,
    modifier: Modifier = Modifier,
) {
    Markdown(
        content = content,
        colors = colors,
        typography = markdownTypography,
        modifier = modifier.fillMaxWidth(),
        components = markdownComponents(
            table = {
                ScrollableMarkdownTable(
                    content = it.content,
                    node = it.node,
                    style = it.typography.table,
                )
            },
        ),
    )
}

/**
 * mikepenz tables use maxLines=1 + Ellipsis inside fixed-width cells, which is
 * what clips chat tables. Size each column to its content, never ellipsize,
 * and scroll horizontally when the table is wider than the bubble.
 */
@Composable
private fun ScrollableMarkdownTable(
    content: String,
    node: ASTNode,
    style: TextStyle,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val settings = annotatorSettings()
    val headerStyle = style.copy(fontWeight = FontWeight.Bold)
    val cellPadding = 12.dp
    val minCol = 72.dp
    val bg = LocalMarkdownColors.current.tableBackground
    val outline = MaterialTheme.colorScheme.outlineVariant

    val grid = remember(content, node) { markdownTableCells(node) }
    if (grid.isEmpty() || grid.first().isEmpty()) return

    val columnCount = grid.maxOf { it.size }
    val annotatedGrid = grid.mapIndexed { rowIndex, row ->
        val rowStyle = if (rowIndex == 0) headerStyle else style
        List(columnCount) { col ->
            val cell = row.getOrNull(col) ?: return@List AnnotatedString("")
            content.buildMarkdownAnnotatedString(cell, rowStyle, settings)
        }
    }

    val columnWidths = List(columnCount) { col ->
        var widest = minCol
        annotatedGrid.forEachIndexed { rowIndex, row ->
            val rowStyle = if (rowIndex == 0) headerStyle else style
            val measured = measurer.measure(
                text = row[col],
                style = rowStyle,
                overflow = TextOverflow.Visible,
                softWrap = false,
                maxLines = 1,
            )
            val textDp = with(density) { measured.size.width.toDp() }
            if (textDp > widest) widest = textDp
        }
        widest + cellPadding * 2
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val viewport = maxWidth
        val natural = columnWidths.reduce { acc, width -> acc + width }
        val bounded = viewport < 10_000.dp
        val widths = if (bounded && natural < viewport) {
            val extra = (viewport - natural) / columnCount
            columnWidths.map { it + extra }
        } else {
            columnWidths
        }
        val total = widths.reduce { acc, width -> acc + width }
        val drawWidth = if (bounded) maxOf(total, viewport) else total
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .horizontalScroll(scroll, enabled = bounded && total > viewport)
                .width(drawWidth)
                .background(bg, RoundedCornerShape(12.dp)),
        ) {
            annotatedGrid.forEachIndexed { rowIndex, row ->
                if (rowIndex > 0) {
                    HorizontalDivider(color = outline)
                }
                Row(
                    modifier = Modifier.height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEachIndexed { col, text ->
                        Text(
                            text = text,
                            style = if (rowIndex == 0) headerStyle else style,
                            modifier = Modifier
                                .width(widths[col])
                                .padding(horizontal = cellPadding, vertical = 10.dp),
                            softWrap = false,
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

internal fun markdownTableCells(node: ASTNode): List<List<ASTNode>> {
    val header = node.findChildOfType(HEADER)?.children?.filter { it.type == CELL }.orEmpty()
    val body = node.children.filter { it.type == ROW }.map { row ->
        row.children.filter { it.type == CELL }
    }
    return listOf(header) + body
}
