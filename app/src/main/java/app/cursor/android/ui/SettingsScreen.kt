package app.cursor.android.ui

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.cursor.android.CursorAppViewModel
import app.cursor.android.data.AccountInfo
import app.cursor.android.data.ApiKeyCheck
import app.cursor.android.data.FontWeightIds
import app.cursor.android.data.KnownMachine
import app.cursor.android.data.isAutoModelId

/**
 * Material You settings: an account header, then grouped list items. Most controls apply
 * immediately. The API key is the exception — it is tested against Cursor before it is stored.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    viewModel: CursorAppViewModel,
    onBack: () -> Unit,
    asSheet: Boolean = false,
) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val modelsState by viewModel.models.collectAsStateWithLifecycle()
    val apiKeyCheck by viewModel.apiKeyCheck.collectAsStateWithLifecycle()
    val settings = inbox.settings
    var showModels by remember { mutableStateOf(false) }
    var aliasTarget by remember { mutableStateOf<KnownMachine?>(null) }
    val haptic = rememberPointerHaptics()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.notices.collect { message -> snackbar.showSnackbar(message) }
    }
    var previousCheck by remember { mutableStateOf<ApiKeyCheck>(ApiKeyCheck.Idle) }
    LaunchedEffect(apiKeyCheck) {
        val previous = previousCheck
        previousCheck = apiKeyCheck
        if (previous is ApiKeyCheck.Checking && apiKeyCheck is ApiKeyCheck.Verified) {
            haptic(PointerHaptic.Success)
        } else if (previous is ApiKeyCheck.Checking && apiKeyCheck is ApiKeyCheck.Failed) {
            haptic(PointerHaptic.Error)
        }
    }
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    LaunchedEffect(settings.apiKey) {
        if (settings.apiKey.isNotBlank()) {
            if (modelsState.models.isEmpty()) viewModel.refreshModels()
            viewModel.refreshAccountAndWorkers()
            viewModel.ensureRepositories()
        }
    }

    val modelLabel = modelsState.models
        .firstOrNull { it.id == settings.modelId }
        ?.label()
        ?: if (settings.modelId.isAutoModelId()) "auto" else settings.modelId

    val form: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier = modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            inbox.account?.let { account ->
                AccountHeader(account = account)
                Spacer(Modifier.height(4.dp))
            }

            SectionHeader("appearance")
            SettingsGroup {
                ChoiceRow(
                    title = "theme",
                    value = settings.themeMode,
                    options = listOf("system" to "system", "light" to "light", "dark" to "dark"),
                    onSelect = { viewModel.updateAppearance(themeMode = it) },
                    supporting = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "colours from your wallpaper"
                    } else {
                        "default colour scheme"
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text("material you") },
                        supportingContent = {
                            Text(
                                if (settings.dynamicColor) {
                                    "colours from your wallpaper"
                                } else {
                                    "default colour scheme"
                                },
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                        trailingContent = {
                            Switch(
                                checked = settings.dynamicColor,
                                onCheckedChange = {
                                    haptic(PointerHaptic.Toggle)
                                    viewModel.updateAppearance(dynamicColor = it)
                                },
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                ChoiceRow(
                    title = "text size",
                    value = settings.textScale,
                    options = TextScaleIds.map { it to textScaleLabel(it) },
                    onSelect = { viewModel.updateAppearance(textScale = it) },
                )
                ChoiceRow(
                    title = "font weight",
                    value = settings.fontWeight,
                    options = FontWeightIds.map { it to fontWeightLabel(it) },
                    onSelect = { viewModel.updateAppearance(fontWeight = it) },
                    supporting = "fira sans, from thin to black",
                )
                ListItem(
                    headlineContent = { Text("haptics") },
                    supportingContent = { Text("clicks, tabs, and when an agent finishes") },
                    leadingContent = { Icon(Icons.Outlined.Vibration, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = settings.haptics,
                            onCheckedChange = {
                                haptic(PointerHaptic.Toggle)
                                viewModel.updateAppearance(haptics = it)
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            SectionHeader("account")
            SettingsGroup {
                ApiKeySection(
                    savedKey = settings.apiKey,
                    secureStorageAvailable = settings.secureStorageAvailable,
                    account = inbox.account,
                    status = apiKeyCheck,
                    onSave = viewModel::saveAndVerifyApiKey,
                    onReplace = viewModel::resetApiKeyCheck,
                )
                ListItem(
                    headlineContent = {
                        Text(modelLabel.lowercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    overlineContent = { Text("default model") },
                    supportingContent = { Text("every new message uses this unless you pick another model in that chat") },
                    leadingContent = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickableRow {
                        haptic(PointerHaptic.Open)
                        if (modelsState.models.isEmpty()) viewModel.refreshModels()
                        showModels = true
                    },
                )
                modelsState.error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            SectionHeader("machines", "remote control desktops and `agent worker` clis")
            SettingsGroup {
                val machines = inbox.machines
                if (machines.isEmpty()) {
                    Text(
                        "no machines yet. turn on remote control on a computer, or run `agent worker`.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                machines.forEach { machine ->
                    val worker = machine.worker
                    ListItem(
                        headlineContent = { Text(machine.label) },
                        supportingContent = {
                            Text(
                                if (worker != null) {
                                    listOfNotNull(
                                        worker.subtitle().lowercase(),
                                        formatConnectedAt(worker.connectedAtMs)
                                            .takeIf { it.isNotBlank() }
                                            ?.let { "connected $it" },
                                    ).joinToString("\n")
                                } else {
                                    "remote control · ${machine.shortId} · ${machine.agentCount} chat${if (machine.agentCount == 1) "" else "s"}"
                                },
                            )
                        },
                        leadingContent = {
                            Icon(
                                if (machine.remoteControl) Icons.Outlined.DesktopWindows else Icons.Outlined.Computer,
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { aliasTarget = machine }) {
                                Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "rename machine")
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                ChoiceRow(
                    title = "preferred environment",
                    value = settings.envType.ifBlank { "machine" },
                    options = listOf("machine" to "my machines", "cloud" to "cloud", "pool" to "pool"),
                    onSelect = { viewModel.updateConnection(envType = it) },
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CommitTextField(
                        value = settings.machineName,
                        onCommit = { viewModel.updateConnection(machineName = it) },
                        label = "fallback machine name",
                        placeholder = inbox.workers.firstOrNull()?.displayName() ?: "hostname",
                    )
                }
            }

            SectionHeader("git", "repos connected through the github app")
            SettingsGroup {
                if (inbox.repositories.isEmpty()) {
                    Text(
                        "no github repositories cached yet. they load once the api key is saved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                inbox.repositories.take(8).forEach { repo ->
                    ListItem(
                        headlineContent = { Text(repo.label()) },
                        supportingContent = {
                            Text(repo.url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { Icon(Icons.Outlined.Link, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    CommitTextField(
                        value = settings.defaultRepoUrl,
                        onCommit = { viewModel.updateConnection(defaultRepoUrl = it) },
                        label = "default repo url",
                        placeholder = "https://github.com/org/repo",
                    )
                }
            }

            if (version.isNotBlank()) {
                Text(
                    "pointer $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
                Text(
                    "independent project · not affiliated with anysphere\ncursor is a trademark of anysphere",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }

    if (asSheet) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp)) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "close")
                    }
                    Text(
                        "settings",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                }
                form(Modifier.fillMaxWidth())
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    } else {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            topBar = {
                TopAppBar(
                    title = { Text("settings") },
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
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            form(Modifier.padding(padding))
        }
    }

    aliasTarget?.let { machine ->
        var draft by remember(machine.id) { mutableStateOf(inbox.machineAliases[machine.id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { aliasTarget = null },
            title = { Text("name this machine") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(60) },
                    label = { Text("name") },
                    placeholder = { Text(machine.label) },
                    supportingText = { Text("only on this phone. the api reports it as ${machine.shortId}.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setMachineAlias(machine.id, draft)
                        aliasTarget = null
                    },
                ) { Text("save") }
            },
            dismissButton = {
                TextButton(onClick = { aliasTarget = null }) { Text("cancel") }
            },
        )
    }

    if (showModels) {
        ModelPickerSheet(
            models = modelsState.models,
            selectedModelId = settings.modelId,
            loading = modelsState.loading,
            onSelect = { model, _ ->
                viewModel.updateConnection(modelId = model.id)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }
}

@Composable
private fun AccountHeader(account: AccountInfo) {
    val name = account.displayName()
    val initials = name
        .split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Text(initials, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            account.userEmail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            account.apiKeyName?.let {
                Text(
                    "api key · $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ApiKeySection(
    savedKey: String,
    secureStorageAvailable: Boolean,
    account: AccountInfo?,
    status: ApiKeyCheck,
    onSave: (String) -> Unit,
    onReplace: () -> Unit,
) {
    val haptic = rememberPointerHaptics()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val verifiedLabel = (status as? ApiKeyCheck.Verified)?.accountLabel
        ?: account?.displayName()?.takeIf { savedKey.isNotBlank() }
    var replacing by remember { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (status is ApiKeyCheck.Verified) replacing = false
    }
    LaunchedEffect(savedKey) {
        if (savedKey.isBlank()) replacing = true
    }
    var draft by remember(savedKey) { mutableStateOf(savedKey) }
    var reveal by remember { mutableStateOf(false) }
    val checking = status is ApiKeyCheck.Checking
    val failed = status as? ApiKeyCheck.Failed
    val showEditor = replacing ||
        savedKey.isBlank() ||
        checking ||
        failed != null

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!secureStorageAvailable) {
            Text(
                "secure storage isn't available. restart pointer before adding an api key.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AnimatedContent(
            targetState = showEditor,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "api-key-form",
        ) { editor ->
            if (!editor) {
                SavedApiKeyCard(
                    accountLabel = verifiedLabel,
                    onReplace = {
                        haptic(PointerHaptic.Open)
                        onReplace()
                        replacing = true
                    },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = {
                            draft = it
                            if (status is ApiKeyCheck.Failed || status is ApiKeyCheck.Verified) {
                                onReplace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !checking && secureStorageAvailable,
                        isError = failed != null,
                        label = { Text("api key") },
                        placeholder = { Text("cloud agents key from your dashboard") },
                        supportingText = failed?.let { { Text(it.message) } },
                        singleLine = true,
                        visualTransformation = if (!reveal) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboard?.hide()
                                focusManager.clearFocus()
                                if (draft.isNotBlank() && !checking && secureStorageAvailable) {
                                    haptic(PointerHaptic.Click)
                                    onSave(draft)
                                }
                            },
                        ),
                        trailingIcon = {
                            IconButton(onClick = { reveal = !reveal }) {
                                Icon(
                                    if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (reveal) "hide" else "show",
                                )
                            }
                        },
                    )
                    Button(
                        onClick = {
                            haptic(PointerHaptic.Click)
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onSave(draft)
                        },
                        enabled = !checking && draft.isNotBlank() && secureStorageAvailable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text("  testing key…")
                        } else {
                            Text("save and test")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedApiKeyCard(
    accountLabel: String?,
    onReplace: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("api key saved", style = MaterialTheme.typography.titleSmall)
                Text(
                    accountLabel?.let { "connected as $it" } ?: "stored on this phone",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(
                onClick = onReplace,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("replace")
            }
        }
    }
}
