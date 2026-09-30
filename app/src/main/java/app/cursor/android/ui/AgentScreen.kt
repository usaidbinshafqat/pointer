package app.cursor.android.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cursor.android.CursorAppViewModel
import app.cursor.android.data.AttachmentEncoder
import app.cursor.android.data.AttachmentKind
import app.cursor.android.data.ChatIconCatalog
import app.cursor.android.data.ChatLine
import app.cursor.android.data.REMOTE_CONTROL_LABEL
import app.cursor.android.data.SendMode
import app.cursor.android.data.headline
import app.cursor.android.data.isAutoModelId
import app.cursor.android.data.isSyntheticAttachmentCaption
import app.cursor.android.data.machineLabel
import app.cursor.android.data.resolvedAgentError
import app.cursor.android.data.withoutResolvedErrors
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AgentScreen(
    agentId: String,
    viewModel: CursorAppViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.agent.collectAsStateWithLifecycle()
    val modelsState by viewModel.models.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val listState = remember(agentId) { LazyListState() }
    var composerBarHeightPx by remember(agentId) { mutableIntStateOf(0) }
    var composerExpanded by remember(agentId) { mutableStateOf(false) }
    var composerHeldOpen by remember(agentId) { mutableStateOf(false) }
    val composerCompact by remember {
        derivedStateOf {
            !composerExpanded && !composerHeldOpen && listState.firstVisibleItemIndex > 0
        }
    }
    val releaseComposerHold = remember(agentId) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    composerHeldOpen = false
                }
                return Offset.Zero
            }
        }
    }
    var showModels by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf("") }

    val haptic = rememberPointerHaptics()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { message -> snackbar.showSnackbar(message) }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(state.streaming) {
        if (state.streaming && Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val modelLabel = modelsState.models
        .firstOrNull { it.id == state.turnModelId }
        ?.label()
        ?: if (state.turnModelId.isBlank() || state.turnModelId.isAutoModelId()) "auto" else state.turnModelId

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5),
    ) { uris ->
        uris.forEach { viewModel.addAgentAttachment(context, it, AttachmentKind.IMAGE) }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        uris.forEach { viewModel.addAgentAttachment(context, it, AttachmentKind.FILE) }
    }

    var wasStreaming by remember(agentId) { mutableStateOf(false) }
    val newestTick = state.lines.lastOrNull()?.let { line ->
        "${line.id}:${line.text.length}:${line.thinking.orEmpty().length}:${line.todos.size}:${line.changedFiles.size}"
    } ?: "empty"

    LaunchedEffect(agentId) {
        viewModel.openAgent(agentId)
        if (modelsState.models.isEmpty()) viewModel.refreshModels()
        wasStreaming = false
        viewModel.startWatchingOpenAgent(agentId)
        try {
            awaitCancellation()
        } finally {
            viewModel.leaveAgent(agentId)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAgentScreenResumed(agentId)
    }

    LaunchedEffect(state.streaming) {
        if (wasStreaming && !state.streaming) haptic(PointerHaptic.Success)
        if (!wasStreaming && state.streaming) haptic(PointerHaptic.Tick)
        wasStreaming = state.streaming
    }

    LaunchedEffect(agentId, newestTick, state.streaming, state.historyLoaded, state.workingNote) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.queue.size) {
        if (state.queue.isEmpty()) showQueue = false
    }

    val machineLabel = state.agent?.machineLabel(inbox.workers, inbox.machineAliases)
        ?: if (state.agent?.env?.type.equals("machine", ignoreCase = true)) REMOTE_CONTROL_LABEL else null
    val syncing = state.refreshing || state.loading
    val updatedLabel = formatTimeAgo(state.conversationSyncedAt)
    val layoutDirection = LocalLayoutDirection.current
    val composerBarHeight = with(LocalDensity.current) {
        if (composerBarHeightPx > 0) composerBarHeightPx.toDp() else 96.dp
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.agent?.headline(inbox.workers, inbox.chatTitles) ?: "agent",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                buildString {
                                    append(state.agent?.status?.lowercase()?.replace('_', ' ') ?: "…")
                                    machineLabel?.let { append(" · $it") }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            VerticalDivider(
                                modifier = Modifier.height(12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Text(
                                when {
                                    syncing -> "updating"
                                    updatedLabel.isNotBlank() -> "updated $updatedLabel"
                                    else -> "not synced"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (syncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic(PointerHaptic.Click)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic(PointerHaptic.Click)
                        viewModel.reloadHistory()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "reload history")
                    }
                    IconButton(onClick = {
                        haptic(PointerHaptic.Open)
                        showMenu = true
                    }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "more")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("rename") },
                            leadingIcon = {
                                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                renameDraft = state.agent?.headline(inbox.workers, inbox.chatTitles)
                                    ?: ""
                                showRename = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("change icon") },
                            leadingIcon = {
                                Icon(Icons.Outlined.EmojiEmotions, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                showIconPicker = true
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
            AnimatedVisibility(
                visible = syncing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            }
        },
        snackbarHost = {
            SnackbarHost(
                snackbar,
                modifier = Modifier.padding(bottom = composerBarHeight),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
        when {
            state.loading && state.lines.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppSpinner()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "loading conversation…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val visibleLines = state.lines.withoutResolvedErrors()
                val visibleError = resolvedAgentError(state.error, state.lines, state.streaming)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(releaseComposerHold),
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 12.dp,
                        end = 16.dp,
                        bottom = composerBarHeight,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "bottom-spacer") { Spacer(Modifier.height(8.dp)) }
                    item(key = "streaming-banner") {
                        AnimatedVisibility(
                            visible = state.streaming,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.animateItem(),
                            ) {
                                AppSpinner(
                                    modifier = Modifier.size(14.dp),
                                    compact = true,
                                )
                                Text(
                                    state.workingNote ?: "working…",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    items(
                        items = visibleLines.asReversed(),
                        key = { line -> line.id },
                    ) { line ->
                        if (line.kind == ChatLine.Kind.TOOL) return@items
                        Box(modifier = Modifier.animateItem()) {
                        val lastUserIndex = visibleLines.indexOfLast { it.kind == ChatLine.Kind.USER }
                        val lineIndex = visibleLines.indexOfFirst { it.id == line.id }
                        val streamingAssistant =
                            line.kind == ChatLine.Kind.ASSISTANT &&
                                state.streaming &&
                                lineIndex > lastUserIndex
                        if (line.text.isNotBlank() ||
                            line.kind == ChatLine.Kind.ASSISTANT ||
                            line.attachments.isNotEmpty()
                        ) {
                            when (line.kind) {
                                ChatLine.Kind.ASSISTANT -> AssistantTurn(
                                    line = line,
                                    isStreaming = streamingAssistant,
                                )
                                else -> SimpleBubble(
                                    line = line,
                                    onRemoveQueued = if (line.queued) {
                                        { viewModel.removeQueued(line.id) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        }
                    }
                    visibleError?.let { error ->
                        item(key = "error-banner") {
                            Text(error, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                        ),
                    )
                    .onSizeChanged { composerBarHeightPx = it.height },
            ) {
                AnimatedVisibility(
                    visible = state.queue.isNotEmpty(),
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 16.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        QueueChip(
                            count = state.queue.size,
                            selected = showQueue,
                            onClick = {
                                haptic(PointerHaptic.Open)
                                showQueue = !showQueue
                            },
                        )
                    }
                }
                ChatComposer(
                    draft = state.draft,
                    isStreaming = state.streaming,
                    modelLabel = modelLabel,
                    attachments = state.attachments,
                    expanded = composerExpanded,
                    compact = composerCompact,
                    onDraftChange = viewModel::updateDraft,
                    onSend = {
                        if (state.streaming) {
                            viewModel.submitFollowUp(SendMode.QUEUE)
                        } else {
                            viewModel.submitFollowUp(SendMode.NOW)
                        }
                    },
                    onStop = {
                        haptic(PointerHaptic.Warning)
                        viewModel.cancelActiveRun()
                    },
                    onPickModel = {
                        if (modelsState.models.isEmpty()) viewModel.refreshModels()
                        showModels = true
                    },
                    onPickImage = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onPickFile = { filePicker.launch(AttachmentEncoder.PickerFileMimes) },
                    onRemoveAttachment = viewModel::removeAgentAttachment,
                    onToggleHeight = {
                        if (composerExpanded) {
                            composerExpanded = false
                            composerHeldOpen = false
                        } else {
                            composerExpanded = true
                        }
                    },
                    onFocusChanged = { focused ->
                        if (focused) composerHeldOpen = true
                    },
                )
            }
        }
    }

    if (showQueue) {
        QueueSheet(
            items = state.queue,
            onRemove = viewModel::removeQueued,
            onSendNow = viewModel::sendQueuedNow,
            onDismiss = { showQueue = false },
        )
    }

    if (showModels) {
        ModelPickerSheet(
            models = modelsState.models,
            selectedModelId = state.turnModelId,
            selectedParams = state.turnModelParams,
            loading = modelsState.loading,
            onSelect = { model, params ->
                viewModel.selectTurnModel(model, params)
            },
            onDismiss = { showModels = false },
        )
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("rename chat") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it.take(100) },
                    label = { Text("name") },
                    supportingText = {
                        Text("this name stays on this phone. other clients may still show the original title.")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.renameChat(agentId, renameDraft)
                        showRename = false
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("save") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("cancel") }
            },
        )
    }

    if (showIconPicker) {
        val title = state.agent?.headline(inbox.workers, inbox.chatTitles).orEmpty()
        ChatIconPickerDialog(
            selectedId = ChatIconCatalog.iconIdFor(title, inbox.chatIcons[agentId]),
            onSelect = { id ->
                viewModel.setChatIcon(agentId, id)
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false },
        )
    }

    inbox.renameNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::clearRenameNotice,
            title = { Text("name saved on this phone") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRenameNotice) { Text("ok") }
            },
        )
    }
}

@Composable
private fun AssistantTurn(line: ChatLine, isStreaming: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TurnTodosCard(
            todos = line.todos,
            loading = false,
        )

        ThinkingCard(
            thinking = line.thinking,
            isStreaming = isStreaming,
        )

        if (line.text.isNotBlank() || isStreaming) {
            CopyMessageMenu(text = line.text) { modifier ->
                AssistantMarkdown(
                    content = line.text.ifBlank { "…" },
                    isStreaming = isStreaming,
                    modifier = modifier.fillMaxWidth(),
                )
            }
        }

        TurnFilesCard(
            files = line.changedFiles,
            loading = false,
        )
    }
}

@Composable
private fun SimpleBubble(
    line: ChatLine,
    onRemoveQueued: (() -> Unit)? = null,
) {
    val bg = when (line.kind) {
        ChatLine.Kind.USER -> MaterialTheme.colorScheme.surfaceContainerHighest
        ChatLine.Kind.TOOL -> MaterialTheme.colorScheme.surfaceVariant
        ChatLine.Kind.STATUS -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ChatLine.Kind.ERROR -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when (line.kind) {
        ChatLine.Kind.USER -> MaterialTheme.colorScheme.onSurface
        ChatLine.Kind.ERROR -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (line.kind == ChatLine.Kind.USER) {
            Alignment.End
        } else {
            Alignment.Start
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (line.kind == ChatLine.Kind.ERROR) {
            Text(
                "error",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp),
            )
        }
        if (line.queued) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "queued",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (onRemoveQueued != null) {
                    IconButton(onClick = onRemoveQueued, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Remove from queue",
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        if (line.attachments.isNotEmpty()) {
            AttachmentPreviewRow(
                attachments = line.attachments,
                onRemove = {},
                compact = true,
            )
        }
        val caption = line.text.trim()
        val hideCaption = caption.isEmpty() || caption.isSyntheticAttachmentCaption()
        if (!hideCaption || line.attachments.isEmpty()) {
            val copyText = caption.takeIf {
                line.kind == ChatLine.Kind.USER || line.kind == ChatLine.Kind.ASSISTANT
            }.orEmpty()
            CopyMessageMenu(text = copyText) { modifier ->
                Surface(
                    modifier = modifier.widthIn(max = 560.dp),
                    shape = MaterialTheme.shapes.large,
                    color = bg,
                    contentColor = fg,
                ) {
                    Text(
                        text = caption.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyMessageMenu(
    text: String,
    content: @Composable (Modifier) -> Unit,
) {
    val copyable = text.isNotBlank()
    if (!copyable) {
        content(Modifier)
        return
    }
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = rememberPointerHaptics()
    Box {
        content(
            Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic(PointerHaptic.Open)
                    menuOpen = true
                },
            ),
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("copy") },
                leadingIcon = {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                },
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
                    haptic(PointerHaptic.Click)
                    menuOpen = false
                },
            )
        }
    }
}
