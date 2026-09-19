package app.cursor.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cursor.android.CursorAppViewModel
import app.cursor.android.data.AttachmentEncoder
import app.cursor.android.data.AttachmentKind
import app.cursor.android.data.isAutoModelId
import app.cursor.android.data.matchesRef

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NewAgentScreen(
    viewModel: CursorAppViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
) {
    val state by viewModel.newAgent.collectAsStateWithLifecycle()
    val modelsState by viewModel.models.collectAsStateWithLifecycle()
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    var showModels by remember { mutableStateOf(false) }
    var customRepo by remember { mutableStateOf(false) }
    var customMachine by remember { mutableStateOf(false) }
    var customRef by remember { mutableStateOf(false) }

    LaunchedEffect(inbox.settings.apiKey) {
        if (inbox.settings.apiKey.isNotBlank()) {
            if (modelsState.models.isEmpty()) viewModel.refreshModels()
            viewModel.ensureRepositories()
            viewModel.refreshAccountAndWorkers()
        }
    }

    val selectedLabel = modelsState.models
        .firstOrNull { it.id == state.modelId }
        ?.label()
        ?: if (state.modelId.isBlank() || state.modelId.isAutoModelId()) "auto" else state.modelId

    val envOptions = listOf(
        DropdownOption("machine", "this computer / my machines"),
        DropdownOption("cloud", "cloud vm"),
        DropdownOption("pool", "worker pool"),
    )
    val machineOptions = buildList {
        inbox.workers.forEach { worker ->
            add(
                DropdownOption(
                    value = worker.resolvedId().ifBlank { worker.displayName() },
                    label = worker.displayName(),
                    supporting = worker.subtitle(),
                ),
            )
        }
        inbox.settings.machineName.takeIf { it.isNotBlank() }?.let { name ->
            if (none { it.value == name }) {
                add(DropdownOption(name, name, "saved machine"))
            }
        }
        add(DropdownOption("__custom__", "custom name…"))
    }
    val repoOptions = buildList {
        if (state.envType == "cloud") {
            add(DropdownOption("", "no repository"))
        }
        inbox.repositories.forEach { repo ->
            add(DropdownOption(repo.url, repo.label(), repo.url))
        }
        inbox.settings.defaultRepoUrl.takeIf { it.isNotBlank() }?.let { url ->
            if (none { it.value == url }) add(DropdownOption(url, url, "default"))
        }
        add(DropdownOption("__custom__", "custom url…"))
    }
    val refOptions = listOf(
        DropdownOption("main", "main"),
        DropdownOption("master", "master"),
        DropdownOption("__custom__", "custom ref…"),
    )

    val context = LocalContext.current
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            TopAppBar(
                title = { Text("new agent") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelPickerButton(
                selectedLabel = selectedLabel,
                onClick = {
                    if (modelsState.models.isEmpty()) viewModel.refreshModels()
                    showModels = true
                },
            )

            OutlinedTextField(
                value = state.prompt,
                onValueChange = { value ->
                    viewModel.updateNewAgent { it.copy(prompt = value, error = null) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                label = { Text("task") },
                placeholder = { Text("fix the flaky login test and open a pr") },
            )

            AttachmentPreviewRow(
                attachments = state.attachments,
                onRemove = viewModel::removeNewAgentAttachment,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Text("  photo")
                }
                FilledTonalButton(
                    onClick = { filePicker.launch(AttachmentEncoder.PickerFileMimes) },
                ) {
                    Icon(Icons.Outlined.AttachFile, contentDescription = null)
                    Text("  file")
                }
            }

            SectionHeader("where it runs", "connected machines from your cursor account")
            RoundedDropdown(
                label = "environment",
                selectedValue = state.envType,
                options = envOptions,
                onSelect = { option ->
                    viewModel.updateNewAgent { it.copy(envType = option.value) }
                },
            )
            if (state.envType != "cloud") {
                RoundedDropdown(
                    label = if (state.envType == "pool") "pool" else "machine",
                    selectedValue = if (customMachine) "__custom__" else state.machineName,
                    options = machineOptions,
                    onSelect = { option ->
                        if (option.value == "__custom__") {
                            customMachine = true
                        } else {
                            customMachine = false
                            val worker = inbox.workers.firstOrNull { it.matchesRef(option.value) }
                            viewModel.updateNewAgent {
                                it.copy(
                                    machineName = option.value,
                                    repoUrl = worker?.repoUrl?.takeIf { url -> url.isNotBlank() }
                                        ?: it.repoUrl,
                                )
                            }
                        }
                    },
                    supportingText = if (inbox.workers.isEmpty()) {
                        "no workers visible yet. start `agent worker` on the mac"
                    } else {
                        null
                    },
                )
                if (customMachine) {
                    OutlinedTextField(
                        value = state.machineName,
                        onValueChange = { value ->
                            viewModel.updateNewAgent { it.copy(machineName = value) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("custom machine name") },
                        singleLine = true,
                    )
                }
                val selectedWorker = inbox.workers.firstOrNull { it.matchesRef(state.machineName) }
                val directoryOptions = (selectedWorker?.workspaceDirectories().orEmpty())
                    .ifEmpty { listOfNotNull(selectedWorker?.workspaceRootPath) }
                    .map { path ->
                        DropdownOption(
                            value = path,
                            label = path.substringAfterLast('/').ifBlank { path },
                            supporting = path,
                        )
                    }
                SectionHeader(
                    "workspace",
                    "local folder on that machine",
                )
                if (directoryOptions.isNotEmpty()) {
                    RoundedDropdown(
                        label = "directory",
                        selectedValue = selectedWorker?.primaryWorkspacePath()
                            ?: directoryOptions.first().value,
                        options = directoryOptions,
                        onSelect = { option ->
                            val worker = inbox.workers.firstOrNull { worker ->
                                worker.workspaceDirectories().contains(option.value) &&
                                    (state.machineName.isBlank() || worker.matchesRef(state.machineName))
                            } ?: selectedWorker
                            viewModel.updateNewAgent {
                                it.copy(
                                    machineName = worker?.resolvedId()?.ifBlank { state.machineName }
                                        ?: state.machineName,
                                    repoUrl = worker?.repoUrl.orEmpty(),
                                )
                            }
                        },
                    )
                } else {
                    Text(
                        "this machine hasn’t published a workspace path.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader(
                "git",
                if (state.envType == "cloud") {
                    "repos from your github connection."
                } else {
                    "a repo is required to start on a machine, even when the files already live in the directory above."
                },
            )
            RoundedDropdown(
                label = "repository",
                selectedValue = if (customRepo) "__custom__" else state.repoUrl,
                options = repoOptions,
                onSelect = { option ->
                    if (option.value == "__custom__") {
                        customRepo = true
                    } else {
                        customRepo = false
                        viewModel.updateNewAgent { it.copy(repoUrl = option.value) }
                    }
                },
            )
            if (customRepo) {
                OutlinedTextField(
                    value = state.repoUrl,
                    onValueChange = { value ->
                        viewModel.updateNewAgent { it.copy(repoUrl = value) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("custom repo url") },
                    singleLine = true,
                )
            }
            if (state.repoUrl.isNotBlank()) {
                RoundedDropdown(
                    label = "starting ref",
                    selectedValue = if (customRef) "__custom__" else state.startingRef,
                    options = refOptions,
                    onSelect = { option ->
                        if (option.value == "__custom__") {
                            customRef = true
                        } else {
                            customRef = false
                            viewModel.updateNewAgent { it.copy(startingRef = option.value) }
                        }
                    },
                )
                if (customRef) {
                    OutlinedTextField(
                        value = state.startingRef,
                        onValueChange = { value ->
                            viewModel.updateNewAgent { it.copy(startingRef = value) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("branch or sha") },
                        singleLine = true,
                    )
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.createAgent(onCreated) },
                enabled = !state.submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.submitting) {
                    AppSpinner(modifier = Modifier.height(18.dp), compact = true)
                } else {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("  start agent")
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
            onSelect = { model, params ->
                viewModel.selectNewAgentModel(model, params)
            },
            onDismiss = { showModels = false },
        )
    }
}
