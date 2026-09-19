package app.cursor.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cursor.android.CursorAppViewModel
import app.cursor.android.data.AttachmentEncoder
import app.cursor.android.data.AttachmentKind
import app.cursor.android.data.isAutoModelId
import app.cursor.android.data.matchesRef
import kotlinx.coroutines.delay

/**
 * iOS parity for "Plan, ask, build…": a bottom sheet with repo + machine chips on top,
 * the prompt in the middle, and attach · model · send along the bottom. Advanced knobs
 * (pool, directory, starting ref, custom URLs) live behind "more options".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewAgentSheet(
    viewModel: CursorAppViewModel,
    onDismiss: () -> Unit,
    onMoreOptions: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.newAgent.collectAsStateWithLifecycle()
    val modelsState by viewModel.models.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val haptic = rememberPointerHaptics()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }

    var showModels by remember { mutableStateOf(false) }
    var repoMenu by remember { mutableStateOf(false) }
    var machineMenu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var customRepo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inbox.settings.apiKey) {
        if (inbox.settings.apiKey.isNotBlank()) {
            if (modelsState.models.isEmpty()) viewModel.refreshModels()
            viewModel.ensureRepositories()
            viewModel.refreshAccountAndWorkers()
        }
    }
    LaunchedEffect(Unit) {
        delay(180)
        runCatching { focus.requestFocus() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        uris.forEach { viewModel.addNewAgentAttachment(context, it, AttachmentKind.IMAGE) }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { viewModel.addNewAgentAttachment(context, it, AttachmentKind.FILE) }
    }

    val modelLabel = modelsState.models.firstOrNull { it.id == state.modelId }?.label()
        ?: if (state.modelId.isBlank() || state.modelId.isAutoModelId()) "auto" else state.modelId
    val machines = remember(inbox.workers, inbox.agents, inbox.machineAliases) { inbox.machines }
    val selectedMachine = machines.firstOrNull { m ->
        m.id.equals(state.machineName, ignoreCase = true) ||
            (m.worker?.matchesRef(state.machineName) == true)
    }
    val machineLabel = when {
        state.envType == "cloud" -> "cloud"
        selectedMachine != null -> selectedMachine.label
        state.machineName.isNotBlank() -> state.machineName
        machines.isNotEmpty() -> machines.first().label
        else -> "machine"
    }
    val repoLabel = state.repoUrl
        .takeIf { it.isNotBlank() }
        ?.substringAfterLast('/')
        ?.removeSuffix(".git")
        ?: if (state.envType == "cloud") "no repo" else "repo"
    val canSend = (state.prompt.isNotBlank() || state.attachments.isNotEmpty()) && !state.submitting
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 840.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    PickerChip(
                        label = repoLabel,
                        icon = Icons.Outlined.Link,
                        onClick = {
                            haptic(PointerHaptic.Open)
                            repoMenu = true
                        },
                    )
                    DropdownMenu(expanded = repoMenu, onDismissRequest = { repoMenu = false }) {
                        if (state.envType == "cloud") {
                            DropdownMenuItem(
                                text = { Text("no repository") },
                                onClick = {
                                    repoMenu = false
                                    viewModel.updateNewAgent { it.copy(repoUrl = "") }
                                },
                            )
                        }
                        inbox.repositories.forEach { repo ->
                            DropdownMenuItem(
                                text = { Text(repo.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    haptic(PointerHaptic.Click)
                                    repoMenu = false
                                    viewModel.updateNewAgent { it.copy(repoUrl = repo.url, error = null) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("custom url…") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = {
                                repoMenu = false
                                customRepo = state.repoUrl
                            },
                        )
                    }
                }
                Box {
                    PickerChip(
                        label = machineLabel,
                        icon = if (state.envType == "cloud") Icons.Outlined.Cloud else Icons.Outlined.Computer,
                        onClick = {
                            haptic(PointerHaptic.Open)
                            machineMenu = true
                        },
                    )
                    DropdownMenu(expanded = machineMenu, onDismissRequest = { machineMenu = false }) {
                        if (machines.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("no machines yet · turn on remote control or run `agent worker`") },
                                enabled = false,
                                onClick = {},
                            )
                        }
                        machines.forEach { machine ->
                            DropdownMenuItem(
                                text = { Text(machine.label) },
                                leadingIcon = {
                                    Icon(
                                        if (machine.remoteControl) Icons.Outlined.DesktopWindows else Icons.Outlined.Computer,
                                        null,
                                    )
                                },
                                trailingIcon = if (machine.remoteControl) {
                                    {
                                        Text(
                                            machine.shortId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = scheme.onSurfaceVariant,
                                        )
                                    }
                                } else {
                                    null
                                },
                                onClick = {
                                    haptic(PointerHaptic.Click)
                                    machineMenu = false
                                    viewModel.updateNewAgent {
                                        it.copy(
                                            envType = "machine",
                                            machineName = machine.id,
                                            repoUrl = machine.worker?.repoUrl?.takeIf { url -> url.isNotBlank() }
                                                ?: it.repoUrl,
                                            error = null,
                                        )
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("cloud") },
                            leadingIcon = { Icon(Icons.Outlined.Cloud, null) },
                            onClick = {
                                haptic(PointerHaptic.Click)
                                machineMenu = false
                                viewModel.updateNewAgent { it.copy(envType = "cloud", error = null) }
                            },
                        )
                    }
                }
            }

            BasicTextField(
                value = state.prompt,
                onValueChange = { value ->
                    viewModel.updateNewAgent { it.copy(prompt = value, error = null) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp, max = 220.dp)
                    .focusRequester(focus),
                textStyle = bodyStyle,
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                decorationBox = { inner ->
                    Box {
                        if (state.prompt.isEmpty()) {
                            Text(
                                "plan, ask, build…",
                                style = bodyStyle.copy(color = scheme.onSurfaceVariant),
                            )
                        }
                        inner()
                    }
                },
            )

            if (state.envType != "cloud" && state.repoUrl.isBlank()) {
                Text(
                    "a github repo is required to start on a machine. pick one above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            if (state.attachments.isNotEmpty()) {
                AttachmentPreviewRow(
                    attachments = state.attachments,
                    onRemove = viewModel::removeNewAgentAttachment,
                )
            }

            state.error?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }

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
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = "attach")
                    }
                    DropdownMenu(expanded = attachMenu, onDismissRequest = { attachMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("photo") },
                            leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            onClick = {
                                attachMenu = false
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("file") },
                            leadingIcon = { Icon(Icons.Outlined.AttachFile, null) },
                            onClick = {
                                attachMenu = false
                                filePicker.launch(AttachmentEncoder.PickerFileMimes)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("more options") },
                            leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                            onClick = {
                                attachMenu = false
                                onMoreOptions()
                            },
                        )
                    }
                }
                TextButton(
                    onClick = {
                        haptic(PointerHaptic.Open)
                        if (modelsState.models.isEmpty()) viewModel.refreshModels()
                        showModels = true
                    },
                    modifier = Modifier.widthIn(max = 200.dp),
                ) {
                    Text(modelLabel.lowercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "choose model",
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(18.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = {
                        haptic(PointerHaptic.Click)
                        viewModel.createAgent { id ->
                            onCreated(id)
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = scheme.onSurface,
                        contentColor = scheme.surface,
                        disabledContainerColor = scheme.onSurface.copy(alpha = 0.18f),
                        disabledContentColor = scheme.surface.copy(alpha = 0.54f),
                    ),
                ) {
                    if (state.submitting) {
                        AppSpinner(modifier = Modifier.size(22.dp), color = scheme.surface, compact = true)
                    } else {
                        Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "start agent")
                    }
                }
            }
        }
    }

    if (showModels) {
        ModelPickerSheet(
            models = modelsState.models,
            selectedModelId = state.modelId,
            selectedParams = state.modelParams,
            loading = modelsState.loading,
            onSelect = { model, params -> viewModel.selectNewAgentModel(model, params) },
            onDismiss = { showModels = false },
        )
    }

    customRepo?.let { draft ->
        var text by remember(draft) { mutableStateOf(draft) }
        AlertDialog(
            onDismissRequest = { customRepo = null },
            title = { Text("repository url") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("https://github.com/org/repo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateNewAgent { it.copy(repoUrl = text.trim(), error = null) }
                        customRepo = null
                    },
                    enabled = text.isNotBlank(),
                ) { Text("use") }
            },
            dismissButton = {
                TextButton(onClick = { customRepo = null }) { Text("cancel") }
            },
        )
    }
}

@Composable
private fun PickerChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
        },
        trailingIcon = {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        modifier = Modifier.widthIn(max = 200.dp),
    )
}
