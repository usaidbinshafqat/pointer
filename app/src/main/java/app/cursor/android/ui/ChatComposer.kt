package app.cursor.android.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.data.DraftAttachment

private val ComposerActionSize = 48.dp
private val ComposerIconSize = 24.dp
private val ComposerShape = RoundedCornerShape(28.dp)

@Composable
fun ChatComposer(
    draft: String,
    isStreaming: Boolean,
    modelLabel: String,
    attachments: List<DraftAttachment>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPickModel: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "follow up",
    expanded: Boolean = false,
    compact: Boolean = false,
    onToggleHeight: () -> Unit = {},
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val haptic = rememberPointerHaptics()
    var attachMenu by remember { mutableStateOf(false) }
    val canSend = draft.isNotBlank() || attachments.isNotEmpty()
    val showStop = isStreaming && !canSend
    val scheme = MaterialTheme.colorScheme
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        color = scheme.onSurface,
    )
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val fieldMax = when {
        expanded -> (screenHeight * 0.42f).coerceIn(220.dp, 380.dp)
        compact -> 40.dp
        else -> 140.dp
    }
    val fieldMin = if (compact) 24.dp else 32.dp
    val fieldHeight = if (expanded) {
        Modifier.height(fieldMax)
    } else {
        Modifier.heightIn(min = fieldMin, max = fieldMax)
    }
    val maxLines = when {
        expanded -> Int.MAX_VALUE
        compact -> 2
        else -> 6
    }
    val surfacePadding = if (compact) {
        Modifier.padding(start = 4.dp, top = 10.dp, end = 8.dp, bottom = 4.dp)
    } else {
        Modifier.padding(start = 4.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)
    }

    Column(
        modifier = modifier
            .widthIn(max = 840.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = ComposerShape,
            color = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Column(
                modifier = surfacePadding,
                verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
            ) {
                if (attachments.isNotEmpty()) {
                    AttachmentPreviewRow(
                        attachments = attachments,
                        onRemove = onRemoveAttachment,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(fieldHeight)
                        .padding(horizontal = 16.dp)
                        .onFocusChanged { onFocusChanged(it.isFocused) },
                    textStyle = bodyStyle,
                    cursorBrush = SolidColor(scheme.primary),
                    maxLines = maxLines,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.TopStart) {
                            if (draft.isEmpty()) {
                                Text(
                                    text = when {
                                        isStreaming && canSend -> "queue follow up"
                                        isStreaming -> "working…"
                                        else -> placeholder
                                    },
                                    style = bodyStyle.copy(color = scheme.onSurfaceVariant),
                                )
                            }
                            inner()
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box {
                        IconButton(
                            onClick = {
                                haptic(PointerHaptic.Open)
                                attachMenu = true
                            },
                            modifier = Modifier.size(ComposerActionSize),
                        ) {
                            Icon(
                                Icons.Outlined.Add,
                                contentDescription = "attach",
                                modifier = Modifier.size(ComposerIconSize),
                            )
                        }
                        DropdownMenu(
                            expanded = attachMenu,
                            onDismissRequest = { attachMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("photo") },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                onClick = {
                                    attachMenu = false
                                    onPickImage()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("file") },
                                leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                                onClick = {
                                    attachMenu = false
                                    onPickFile()
                                },
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            haptic(PointerHaptic.Open)
                            onPickModel()
                        },
                        modifier = Modifier.widthIn(max = 180.dp),
                    ) {
                        Text(
                            modelLabel.lowercase(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "choose model",
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            haptic(PointerHaptic.Toggle)
                            onToggleHeight()
                        },
                        modifier = Modifier.size(ComposerActionSize),
                    ) {
                        Icon(
                            if (expanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                            contentDescription = if (expanded) "shrink input" else "expand input",
                            modifier = Modifier.size(ComposerIconSize),
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            haptic(if (showStop) PointerHaptic.Warning else PointerHaptic.Click)
                            if (showStop) onStop() else onSend()
                        },
                        enabled = showStop || canSend,
                        modifier = Modifier.size(ComposerActionSize),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = scheme.onSurface,
                            contentColor = scheme.surface,
                            disabledContainerColor = scheme.onSurface.copy(alpha = 0.18f),
                            disabledContentColor = scheme.surface.copy(alpha = 0.54f),
                        ),
                    ) {
                        Icon(
                            if (showStop) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send,
                            contentDescription = if (showStop) "stop" else "send",
                            modifier = Modifier.size(ComposerIconSize),
                        )
                    }
                }
            }
        }
    }
}
