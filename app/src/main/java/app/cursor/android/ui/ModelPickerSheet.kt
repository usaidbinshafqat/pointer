package app.cursor.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.cursor.android.data.CursorModel
import app.cursor.android.data.ModelParamValue
import app.cursor.android.data.effortControl
import app.cursor.android.data.fastControl
import app.cursor.android.data.paramsWithDefaults
import app.cursor.android.data.upsert
import app.cursor.android.data.valueOf

@Composable
fun ModelPickerButton(
    selectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        ListItem(
            headlineContent = { Text(selectedLabel) },
            overlineContent = { Text("model") },
            trailingContent = {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    models: List<CursorModel>,
    selectedModelId: String,
    selectedParams: List<ModelParamValue> = emptyList(),
    loading: Boolean,
    onSelect: (CursorModel, List<ModelParamValue>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var query by remember { mutableStateOf("") }
    var optionsModel by remember { mutableStateOf<CursorModel?>(null) }
    val filtered = remember(models, query) {
        val q = query.trim().lowercase()
        models.filter { model ->
            q.isEmpty() ||
                model.id.lowercase().contains(q) ||
                model.label().lowercase().contains(q) ||
                model.aliases.any { it.lowercase().contains(q) } ||
                model.description.orEmpty().lowercase().contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val editing = optionsModel
        if (editing != null) {
            ModelOptionsPane(
                model = editing,
                selected = editing.id == selectedModelId ||
                    (editing.isAuto() && selectedModelId.isBlank()),
                selectedParams = if (editing.id == selectedModelId) selectedParams else emptyList(),
                onBack = { optionsModel = null },
                onDone = { model, params ->
                    onSelect(model, params)
                    optionsModel = null
                },
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "model",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (loading) {
                    Text(
                        "loading from cursor…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("search") },
                    singleLine = true,
                    shape = CircleShape,
                )
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filtered, key = { it.id }) { model ->
                        val selected = model.id == selectedModelId ||
                            (model.isAuto() && selectedModelId.isBlank())
                        ModelRow(
                            model = model,
                            selected = selected,
                            selectedParams = if (selected) selectedParams else emptyList(),
                            onSelect = { picked, params ->
                                onSelect(picked, params)
                                onDismiss()
                            },
                            onOpenOptions = { optionsModel = model },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: CursorModel,
    selected: Boolean,
    selectedParams: List<ModelParamValue>,
    onSelect: (CursorModel, List<ModelParamValue>) -> Unit,
    onOpenOptions: () -> Unit,
) {
    val current = model.paramsWithDefaults(if (selected) selectedParams else emptyList())
    val haptic = rememberPointerHaptics()

    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic(PointerHaptic.Click)
                onSelect(model, current)
            }
            .semantics { this.selected = selected },
        headlineContent = {
            Text(model.label().lowercase(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            val detail = when {
                model.isAuto() -> "cursor auto"
                current.isNotEmpty() ->
                    current.joinToString(" · ") { it.value.lowercase() }
                else -> null
            }
            detail?.let {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) {
                    Icon(Icons.Outlined.Check, contentDescription = "selected")
                }
                IconButton(
                    onClick = {
                        haptic(PointerHaptic.Open)
                        onOpenOptions()
                    },
                ) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "model options")
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelOptionsPane(
    model: CursorModel,
    selected: Boolean,
    selectedParams: List<ModelParamValue>,
    onBack: () -> Unit,
    onDone: (CursorModel, List<ModelParamValue>) -> Unit,
) {
    val haptic = rememberPointerHaptics()
    var params by remember(model.id, selectedParams) {
        mutableStateOf(model.paramsWithDefaults(if (selected) selectedParams else emptyList()))
    }
    val fast = model.fastControl()
    val effort = model.effortControl()
    var effortOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptic(PointerHaptic.Close)
                    onBack()
                },
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "back")
            }
            Text(
                model.label().lowercase(),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    haptic(PointerHaptic.Click)
                    onDone(model, params)
                },
            ) {
                Text("done")
            }
        }

        if (model.isAuto() || (fast == null && effort == null)) {
            Text(
                "no extra options for this model",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            return
        }

        if (fast != null) {
            val on = params.valueOf(fast.id).equals(fast.onValue, ignoreCase = true)
            ListItem(
                headlineContent = { Text("fast") },
                trailingContent = {
                    Switch(
                        checked = on,
                        onCheckedChange = { enabled ->
                            haptic(PointerHaptic.Click)
                            params = params.upsert(
                                fast.id,
                                if (enabled) fast.onValue else fast.offValue,
                            )
                        },
                    )
                },
            )
        }

        if (effort != null) {
            val current = params.valueOf(effort.id)
                ?: effort.values.firstOrNull {
                    it.value.equals("medium", ignoreCase = true)
                }?.value
                ?: effort.values.first().value
            val label = effort.values.firstOrNull {
                it.value.equals(current, ignoreCase = true)
            }?.displayName ?: current
            ExposedDropdownMenuBox(
                expanded = effortOpen,
                onExpandedChange = { effortOpen = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                OutlinedTextField(
                    value = label.lowercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("effort") },
                    trailingIcon = {
                        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = effortOpen,
                    onDismissRequest = { effortOpen = false },
                ) {
                    effort.values.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text((option.displayName ?: option.value).lowercase())
                            },
                            onClick = {
                                haptic(PointerHaptic.Click)
                                params = params.upsert(effort.id, option.value)
                                effortOpen = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }
        }
    }
}
