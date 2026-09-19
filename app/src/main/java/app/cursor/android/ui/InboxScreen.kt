package app.cursor.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cursor.android.CursorAppViewModel
import app.cursor.android.data.AgentSummary
import app.cursor.android.data.ChatFolder
import app.cursor.android.data.ChatIconCatalog
import app.cursor.android.data.InboxCategory
import app.cursor.android.data.InboxChangeStats
import app.cursor.android.data.InboxFilter
import app.cursor.android.data.InboxSectionKind
import app.cursor.android.data.PrivateWorker
import app.cursor.android.data.REMOTE_CONTROL_LABEL
import app.cursor.android.data.RunSummary
import app.cursor.android.data.groupInboxSections
import app.cursor.android.data.headline
import app.cursor.android.data.inboxCategory
import app.cursor.android.data.isUnread
import app.cursor.android.data.machineLabel
import app.cursor.android.data.needsAttention
import app.cursor.android.data.repoLabel
import kotlinx.coroutines.delay

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun InboxScreen(
    viewModel: CursorAppViewModel,
    onOpenAgent: (String) -> Unit,
    onNewAgent: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.inbox.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onInboxResumed()
    }
    var searching by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var folderDraft by remember { mutableStateOf("") }
    var overflowMenu by remember { mutableStateOf(false) }
    var menuAgentId by remember { mutableStateOf<String?>(null) }
    var pickingFolder by remember { mutableStateOf(false) }
    var renameAgentId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    var iconAgentId by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showCustomize by remember { mutableStateOf(false) }
    var showNewAgent by remember { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedAgentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var removalTargets by remember { mutableStateOf<Set<String>?>(null) }
    var collapsedIds by rememberSaveable { mutableStateOf("") }
    val collapsed = remember(collapsedIds) { collapsedIds.split('|').filter { it.isNotBlank() }.toSet() }

    val sections = remember(
        state.filteredAgents,
        state.workers,
        state.latestRuns,
        state.locallyWorkingIds,
        state.lastReadAtByAgent,
        state.pinnedAgentIds,
        state.machineAliases,
    ) {
        groupInboxSections(
            state.filteredAgents,
            state.workers,
            state.latestRuns,
            state.locallyWorkingIds,
            state.lastReadAtByAgent,
            state.pinnedAgentIds,
            state.machineAliases,
        )
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { message -> snackbar.showSnackbar(message) }
    }
    val pullState = rememberPullToRefreshState()

    val haptic = rememberPointerHaptics()
    val searchFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    )
    LaunchedEffect(searching, selectionMode) {
        if (!searching || selectionMode) keyboard?.hide()
    }
    LaunchedEffect(state.agents) {
        val known = state.agents.mapTo(mutableSetOf()) { it.id }
        selectedAgentIds = selectedAgentIds.filterTo(linkedSetOf()) { it in known }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AnimatedContent(
                targetState = searching to selectionMode,
                transitionSpec = {
                    (
                        fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                                initialOffsetY = { -it / 5 },
                            )
                        ) togetherWith (
                        fadeOut(tween(140, easing = FastOutSlowInEasing)) +
                            slideOutVertically(
                                animationSpec = tween(180, easing = FastOutSlowInEasing),
                                targetOffsetY = { -it / 5 },
                            )
                        )
                },
                label = "inbox search bar",
            ) { (open, selecting) ->
                if (selecting) {
                    val visibleIds = state.filteredAgents.mapTo(linkedSetOf()) { it.id }
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Close)
                                    selectionMode = false
                                    selectedAgentIds = emptySet()
                                },
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "finish selecting")
                            }
                        },
                        title = {
                            Text(
                                if (selectedAgentIds.isEmpty()) "select chats"
                                else "${selectedAgentIds.size} selected",
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Click)
                                    selectedAgentIds = if (
                                        visibleIds.isNotEmpty() && visibleIds.all { it in selectedAgentIds }
                                    ) {
                                        selectedAgentIds - visibleIds
                                    } else {
                                        selectedAgentIds + visibleIds
                                    }
                                },
                                enabled = visibleIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Outlined.SelectAll, contentDescription = "select all visible chats")
                            }
                            IconButton(
                                onClick = { removalTargets = selectedAgentIds },
                                enabled = selectedAgentIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Outlined.Archive, contentDescription = "archive selected chats")
                            }
                            IconButton(
                                onClick = { removalTargets = selectedAgentIds },
                                enabled = selectedAgentIds.isNotEmpty(),
                            ) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "delete selected chats")
                            }
                        },
                        colors = barColors,
                    )
                } else if (open) {
                    LaunchedEffect(Unit) {
                        delay(240)
                        runCatching { searchFocus.requestFocus() }
                        keyboard?.show()
                    }
                    TopAppBar(
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Close)
                                    searching = false
                                    viewModel.setInboxQuery("")
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "close search",
                                )
                            }
                        },
                        title = {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = viewModel::setInboxQuery,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(searchFocus),
                                placeholder = { Text("search") },
                                singleLine = true,
                                shape = CircleShape,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            )
                        },
                        actions = {
                            if (state.query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        haptic(PointerHaptic.Click)
                                        viewModel.setInboxQuery("")
                                    },
                                ) {
                                    Icon(Icons.Outlined.Close, contentDescription = "clear search")
                                }
                            }
                        },
                        colors = barColors,
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text("pointer", style = MaterialTheme.typography.titleLarge)
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    haptic(PointerHaptic.Open)
                                    searching = true
                                },
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = "search")
                            }
                            Box {
                                IconButton(
                                    onClick = {
                                        haptic(PointerHaptic.Open)
                                        overflowMenu = true
                                    },
                                ) {
                                    Icon(Icons.Outlined.MoreVert, contentDescription = "more")
                                }
                                DropdownMenu(
                                    expanded = overflowMenu,
                                    onDismissRequest = { overflowMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("select chats") },
                                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                                        onClick = {
                                            haptic(PointerHaptic.Click)
                                            overflowMenu = false
                                            selectionMode = true
                                            selectedAgentIds = emptySet()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                        onClick = {
                                            haptic(PointerHaptic.Click)
                                            overflowMenu = false
                                            showSettings = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("filter") },
                                        leadingIcon = { Icon(Icons.Outlined.FilterAlt, null) },
                                        onClick = {
                                            haptic(PointerHaptic.Click)
                                            overflowMenu = false
                                            showCustomize = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("new folder") },
                                        leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                        onClick = {
                                            haptic(PointerHaptic.Click)
                                            overflowMenu = false
                                            showNewFolder = true
                                        },
                                    )
                                }
                            }
                        },
                        colors = barColors,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (!selectionMode) Box {
                HorizontalFloatingToolbar(
                    expanded = true,
                    floatingActionButton = {
                        FloatingToolbarDefaults.StandardFloatingActionButton(
                            onClick = {
                                haptic(PointerHaptic.Click)
                                showNewAgent = true
                            },
                        ) {
                            Icon(Icons.Outlined.AddComment, contentDescription = "new chat")
                        }
                    },
                ) {
                    InboxFilterToggle(
                        selected = state.filter == InboxFilter.ALL,
                        icon = Icons.Outlined.Inbox,
                        label = "inbox",
                        onClick = {
                            haptic(PointerHaptic.Tab)
                            viewModel.setInboxFilter(InboxFilter.ALL)
                        },
                    )
                    InboxFilterToggle(
                        selected = state.filter == InboxFilter.WORKING,
                        icon = Icons.Outlined.HourglassEmpty,
                        label = "busy",
                        onClick = {
                            haptic(PointerHaptic.Tab)
                            viewModel.setInboxFilter(InboxFilter.WORKING)
                        },
                    )
                    InboxFilterToggle(
                        selected = state.filter == InboxFilter.DONE,
                        icon = Icons.Outlined.CheckCircle,
                        label = "done",
                        showDot = state.hasUnreadDone,
                        onClick = {
                            haptic(PointerHaptic.Tab)
                            viewModel.setInboxFilter(InboxFilter.DONE)
                        },
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        when {
            state.settings.apiKey.isBlank() && state.agents.isEmpty() -> {
                EmptySetup(
                    modifier = Modifier.padding(padding),
                    onSettings = { showSettings = true },
                )
            }
            state.loading && state.agents.isEmpty() -> {
                PointerBrandMark(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refreshInbox() },
                    state = pullState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = state.refreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 112.dp),
                    ) {
                        state.error?.takeIf { state.agents.isEmpty() }?.let { error ->
                            item(key = "error") {
                                Text(
                                    error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }

                        if (state.folderFilter != null) {
                            item(key = "folder-hint") {
                                val name = state.folders.firstOrNull { it.id == state.folderFilter }?.name
                                Text(
                                    name?.lowercase() ?: "folder",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }

                        if (state.filteredAgents.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (state.agents.isEmpty()) {
                                        "no agents yet. start one and point it at a connected machine."
                                    } else {
                                        "nothing in this view."
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }

                        sections.forEach { section ->
                            val showHeader = section.title.isNotBlank() && state.filter == InboxFilter.ALL
                            val collapsible = showHeader && section.kind != InboxSectionKind.PINNED
                            val isCollapsed = collapsible && section.id in collapsed
                            if (showHeader) {
                                item(key = "header-${section.id}") {
                                    SectionHeaderRow(
                                        title = section.title,
                                        count = section.agents.size,
                                        collapsible = collapsible,
                                        collapsed = isCollapsed,
                                        onToggle = {
                                            haptic(PointerHaptic.Tab)
                                            val next = if (isCollapsed) collapsed - section.id else collapsed + section.id
                                            collapsedIds = next.joinToString("|")
                                        },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            if (isCollapsed) return@forEach
                            itemsIndexed(section.agents, key = { _, agent -> agent.id }) { index, agent ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 3.dp)
                                        .animateItem(),
                                ) {
                                    AgentRow(
                                        agent = agent,
                                        index = index,
                                        count = section.agents.size,
                                        folder = state.folders.firstOrNull {
                                            it.id == state.agentFolderIds[agent.id]
                                        },
                                        workers = state.workers,
                                        titles = state.chatTitles,
                                        iconId = ChatIconCatalog.iconIdFor(
                                            agent.headline(state.workers, state.chatTitles),
                                            state.chatIcons[agent.id],
                                        ),
                                        changeStats = state.changeStats[agent.id],
                                        latestRun = state.latestRuns[agent.id],
                                        locallyWorking = agent.id in state.locallyWorkingIds,
                                        lastReadAt = state.lastReadAtByAgent[agent.id],
                                        unread = agent.isUnread(
                                            state.lastReadAtByAgent[agent.id],
                                            state.latestRuns[agent.id],
                                        ),
                                        pinned = agent.id in state.pinnedAgentIds,
                                        machineAliases = state.machineAliases,
                                        showDiff = state.settings.showDiff,
                                        showRuntime = state.settings.showRuntime,
                                        showUpdated = state.settings.showUpdated,
                                        selectionMode = selectionMode,
                                        selected = agent.id in selectedAgentIds,
                                        onClick = {
                                            haptic(PointerHaptic.Click)
                                            if (selectionMode) {
                                                selectedAgentIds = if (agent.id in selectedAgentIds) {
                                                    selectedAgentIds - agent.id
                                                } else {
                                                    selectedAgentIds + agent.id
                                                }
                                            } else {
                                                onOpenAgent(agent.id)
                                            }
                                        },
                                        onLongClick = {
                                            haptic(PointerHaptic.Open)
                                            selectionMode = true
                                            selectedAgentIds = selectedAgentIds + agent.id
                                            menuAgentId = null
                                            pickingFolder = false
                                        },
                                        onMore = {
                                            haptic(PointerHaptic.Open)
                                            pickingFolder = false
                                            menuAgentId = agent.id
                                        },
                                    )
                                    DropdownMenu(
                                        expanded = menuAgentId == agent.id,
                                        onDismissRequest = {
                                            menuAgentId = null
                                            pickingFolder = false
                                        },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("archive or delete") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.Archive, null)
                                            },
                                            onClick = {
                                                removalTargets = setOf(agent.id)
                                                menuAgentId = null
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("rename") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.DriveFileRenameOutline, null)
                                            },
                                            onClick = {
                                                renameAgentId = agent.id
                                                renameDraft = agent.headline(
                                                    state.workers,
                                                    state.chatTitles,
                                                )
                                                menuAgentId = null
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("change icon") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.EmojiEmotions, null)
                                            },
                                            onClick = {
                                                iconAgentId = agent.id
                                                menuAgentId = null
                                            },
                                        )
                                        val unread = agent.isUnread(
                                            state.lastReadAtByAgent[agent.id],
                                            state.latestRuns[agent.id],
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (unread) "mark as read" else "mark as unread") },
                                            leadingIcon = {
                                                Icon(
                                                    if (unread) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
                                                    null,
                                                )
                                            },
                                            onClick = {
                                                viewModel.setChatRead(agent.id, read = unread)
                                                menuAgentId = null
                                            },
                                        )
                                        val pinned = agent.id in state.pinnedAgentIds
                                        DropdownMenuItem(
                                            text = { Text(if (pinned) "unpin" else "pin to top") },
                                            leadingIcon = {
                                                Icon(Icons.Outlined.PushPin, null)
                                            },
                                            onClick = {
                                                viewModel.setPinned(agent.id, pinned = !pinned)
                                                menuAgentId = null
                                            },
                                        )
                                        Box {
                                            DropdownMenuItem(
                                                text = { Text("move to") },
                                                leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                                                trailingIcon = {
                                                    Icon(
                                                        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                                        contentDescription = null,
                                                    )
                                                },
                                                onClick = {
                                                    haptic(PointerHaptic.Open)
                                                    pickingFolder = true
                                                },
                                            )
                                            DropdownMenu(
                                                expanded = pickingFolder && menuAgentId == agent.id,
                                                onDismissRequest = { pickingFolder = false },
                                            ) {
                                                state.folders.forEach { folder ->
                                                    val current = state.agentFolderIds[agent.id] == folder.id
                                                    DropdownMenuItem(
                                                        text = { Text(folder.name.lowercase()) },
                                                        leadingIcon = {
                                                            Icon(
                                                                Icons.Outlined.Folder,
                                                                contentDescription = null,
                                                            )
                                                        },
                                                        trailingIcon = if (current) {
                                                            {
                                                                Icon(
                                                                    Icons.Outlined.Check,
                                                                    contentDescription = "current folder",
                                                                )
                                                            }
                                                        } else {
                                                            null
                                                        },
                                                        onClick = {
                                                            haptic(PointerHaptic.Click)
                                                            viewModel.assignAgentFolder(agent.id, folder.id)
                                                            menuAgentId = null
                                                            pickingFolder = false
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(key = "fab-spacer") { Spacer(Modifier.height(96.dp)) }
                    }
                }
            }
        }
    }

    val pendingRemoval = removalTargets
    if (!pendingRemoval.isNullOrEmpty()) {
        val count = pendingRemoval.size
        AlertDialog(
            onDismissRequest = { removalTargets = null },
            icon = { Icon(Icons.Outlined.Archive, contentDescription = null) },
            title = {
                Text(
                    if (count == 1) "archive or delete this chat?"
                    else "archive or delete $count chats?",
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Archive hides the selected ${if (count == 1) "chat" else "chats"} and can be reversed later through Cursor.")
                    Text(
                        "Permanent delete removes the conversation transcript and artifacts from Cursor. This cannot be undone.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (pendingRemoval.any { it in state.locallyWorkingIds }) {
                        Text("At least one selected chat is still working. Archiving or deleting it may stop that work.")
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            viewModel.archiveChats(pendingRemoval)
                            removalTargets = null
                            selectionMode = false
                            selectedAgentIds = emptySet()
                        },
                    ) {
                        Icon(Icons.Outlined.Archive, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (count == 1) "archive" else "archive $count")
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteChatsPermanently(pendingRemoval)
                            removalTargets = null
                            selectionMode = false
                            selectedAgentIds = emptySet()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            if (count == 1) "delete forever" else "delete $count forever",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { removalTargets = null }) { Text("cancel") }
            },
        )
    }

    val renamingId = renameAgentId
    if (renamingId != null) {
        AlertDialog(
            onDismissRequest = { renameAgentId = null },
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
                        viewModel.renameChat(renamingId, renameDraft)
                        renameAgentId = null
                    },
                    enabled = renameDraft.isNotBlank(),
                ) { Text("save") }
            },
            dismissButton = {
                TextButton(onClick = { renameAgentId = null }) { Text("cancel") }
            },
        )
    }

    state.renameNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::clearRenameNotice,
            title = { Text("name saved on this phone") },
            text = { Text(notice) },
            confirmButton = {
                TextButton(onClick = viewModel::clearRenameNotice) { Text("ok") }
            },
        )
    }

    val iconTargetId = iconAgentId
    if (iconTargetId != null) {
        val title = state.filteredAgents.firstOrNull { it.id == iconTargetId }
            ?.headline(state.workers, state.chatTitles)
            ?: state.agents.firstOrNull { it.id == iconTargetId }
                ?.headline(state.workers, state.chatTitles)
            .orEmpty()
        ChatIconPickerDialog(
            selectedId = ChatIconCatalog.iconIdFor(title, state.chatIcons[iconTargetId]),
            onSelect = { id ->
                viewModel.setChatIcon(iconTargetId, id)
                iconAgentId = null
            },
            onDismiss = { iconAgentId = null },
        )
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { showSettings = false },
                asSheet = true,
            )
        }
    }

    if (showCustomize) {
        CustomizeSheet(
            folders = state.folders,
            folderFilter = state.folderFilter,
            settings = state.settings,
            onFolder = viewModel::setFolderFilter,
            onNewFolder = {
                showCustomize = false
                showNewFolder = true
            },
            onToggle = { diff, runtime, updated ->
                viewModel.updateAppearance(showDiff = diff, showRuntime = runtime, showUpdated = updated)
            },
            onDismiss = { showCustomize = false },
        )
    }

    if (showNewAgent) {
        NewAgentSheet(
            viewModel = viewModel,
            onDismiss = { showNewAgent = false },
            onMoreOptions = {
                showNewAgent = false
                onNewAgent()
            },
            onCreated = { id ->
                showNewAgent = false
                onOpenAgent(id)
            },
        )
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("new folder") },
            text = {
                OutlinedTextField(
                    value = folderDraft,
                    onValueChange = { folderDraft = it },
                    label = { Text("name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addFolder(folderDraft)
                        folderDraft = ""
                        showNewFolder = false
                    },
                    enabled = folderDraft.isNotBlank(),
                ) { Text("create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolder = false }) { Text("cancel") }
            },
        )
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    count: Int,
    collapsible: Boolean,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "section chevron")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = collapsible, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (collapsed) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (collapsible) {
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = if (collapsed) "expand $title" else "collapse $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun EmptySetup(modifier: Modifier, onSettings: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("welcome to pointer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "an unofficial cloud agents client. add your own api key in settings to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSettings) {
            Text("open settings")
        }
    }
}

private fun changeSummary(stats: InboxChangeStats?): String? {
    if (stats == null) return null
    if (stats.fileCount <= 0) return "no changes"
    return buildString {
        stats.linesAdded?.let { append("+$it ") }
        stats.linesRemoved?.let { append("−$it ") }
        if (isNotEmpty()) append("· ")
        append("${stats.fileCount} file${if (stats.fileCount == 1) "" else "s"}")
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgentRow(
    agent: AgentSummary,
    index: Int,
    count: Int,
    folder: ChatFolder?,
    workers: List<PrivateWorker>,
    titles: Map<String, String> = emptyMap(),
    iconId: String,
    changeStats: InboxChangeStats? = null,
    latestRun: RunSummary? = null,
    locallyWorking: Boolean = false,
    lastReadAt: String? = null,
    unread: Boolean = false,
    pinned: Boolean = false,
    machineAliases: Map<String, String> = emptyMap(),
    showDiff: Boolean = true,
    showRuntime: Boolean = true,
    showUpdated: Boolean = true,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
) {
    val status = agent.status.orEmpty().uppercase()
    val machine = agent.machineLabel(workers, machineAliases)
        ?: if (agent.env?.type.equals("machine", ignoreCase = true)) REMOTE_CONTROL_LABEL else null
    val envType = agent.env?.type?.trim().orEmpty()
    val isCloud = envType.equals("cloud", ignoreCase = true) ||
        (envType.isBlank() && machine == null) ||
        machine.equals("cloud", ignoreCase = true)
    val repo = agent.repoLabel()?.substringAfterLast('/')?.removeSuffix(".git")
    val time = formatTimeAgo(agent.updatedAt ?: agent.createdAt)
    val category = agent.inboxCategory(latestRun, locallyWorking, lastReadAt)
    val statusWord = when {
        locallyWorking || category == InboxCategory.WORKING -> "working"
        agent.needsAttention() -> agent.status?.lowercase()?.replace('_', ' ')
        else -> null
    }
    val snippet = listOfNotNull(
        statusWord,
        if (showDiff) changeSummary(changeStats) else null,
        if (showRuntime && !isCloud) machine else null,
        repo,
        folder?.name?.lowercase()?.takeIf { it != "inbox" },
    ).joinToString(" · ")

    val titleColor = when {
        !locallyWorking && status in setOf("ERROR", "FAILED", "EXPIRED", "CANCELLED", "CANCELED", "BLOCKED") ->
            MaterialTheme.colorScheme.error
        locallyWorking || status in setOf("CREATING", "RUNNING", "WORKING", "ACTIVE", "IN_PROGRESS") ->
            MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        onLongClick = onLongClick,
        onLongClickLabel = "conversation actions",
        supportingContent = if (snippet.isNotBlank()) {
            {
                Text(snippet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            null
        },
        leadingContent = {
            if (selectionMode) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = if (selected) "selected" else "not selected",
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            } else {
                AgentStatusIcon(
                    category = category,
                    iconId = iconId,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!selectionMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (pinned) {
                            Icon(
                                Icons.Outlined.PushPin,
                                contentDescription = "pinned",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showUpdated && time.isNotBlank()) {
                            Text(
                                time,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = onMore,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "chat actions")
                        }
                    }
                    if (showRuntime) {
                        Icon(
                            if (isCloud) Icons.Outlined.Cloud else Icons.Outlined.Computer,
                            contentDescription = if (isCloud) "cloud" else "machine",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.segmentedColors(),
        modifier = Modifier.semantics {
            stateDescription = buildString {
                append(category.accessibilityLabel())
                if (unread) append(", unread")
                if (pinned) append(", pinned")
                if (selectionMode) append(if (selected) ", selected" else ", not selected")
            }
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                agent.headline(workers, titles),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (unread) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AgentStatusIcon(
    category: InboxCategory,
    iconId: String,
) {
    val colors = chatIconColors(category)
    Surface(
        shape = CircleShape,
        color = colors.first,
        contentColor = colors.second,
        modifier = Modifier.clearAndSetSemantics { },
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = category == InboxCategory.WORKING,
                label = "agent state",
            ) { working ->
                if (working) {
                    AppSpinner(
                        modifier = Modifier.size(28.dp),
                        color = colors.second,
                        trackColor = Color.Transparent,
                        compact = true,
                    )
                } else {
                    Icon(
                        imageVector = chatIconVector(iconId),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

private fun InboxCategory.accessibilityLabel(): String = when (this) {
    InboxCategory.DONE -> "done"
    InboxCategory.WORKING -> "busy"
    InboxCategory.WORKSPACE -> "conversation"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InboxFilterToggle(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    showDot: Boolean = false,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier = Modifier
            .heightIn(min = 40.dp)
            .widthIn(min = 88.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.height(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                if (showDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 6.dp, y = (-3).dp)
                            .size(7.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape),
                    )
                }
            }
        }
    }
}
