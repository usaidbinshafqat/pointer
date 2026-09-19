package app.cursor.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.cursor.android.data.ChatFolder
import app.cursor.android.data.CursorSettings

/**
 * iOS "Customize" parity: what the inbox groups by (folder) and which metadata each row shows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeSheet(
    folders: List<ChatFolder>,
    folderFilter: String?,
    settings: CursorSettings,
    onFolder: (String?) -> Unit,
    onNewFolder: () -> Unit,
    onToggle: (showDiff: Boolean?, showRuntime: Boolean?, showUpdated: Boolean?) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = rememberPointerHaptics()
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "customize",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            SectionHeader("folder", modifier = Modifier.padding(horizontal = 16.dp))
            val choices = listOf<Pair<String?, String>>(null to "all conversations") +
                folders.map { it.id to it.name.lowercase() }
            choices.forEach { (id, name) ->
                ListItem(
                    headlineContent = { Text(name) },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    trailingContent = {
                        RadioButton(selected = folderFilter == id, onClick = null)
                    },
                    colors = transparent,
                    modifier = Modifier.selectable(
                        selected = folderFilter == id,
                        onClick = {
                            haptic(PointerHaptic.Click)
                            onFolder(id)
                        },
                    ),
                )
            }
            ListItem(
                headlineContent = { Text("new folder") },
                leadingContent = { Icon(Icons.Outlined.CreateNewFolder, contentDescription = null) },
                colors = transparent,
                modifier = Modifier.selectable(selected = false, onClick = onNewFolder),
            )

            SectionHeader("show on each chat", modifier = Modifier.padding(horizontal = 16.dp))
            ToggleRow(
                title = "changes",
                icon = { Icon(Icons.Outlined.Difference, contentDescription = null) },
                checked = settings.showDiff,
                onChecked = { onToggle(it, null, null) },
            )
            ToggleRow(
                title = "machine",
                icon = { Icon(Icons.Outlined.Computer, contentDescription = null) },
                checked = settings.showRuntime,
                onChecked = { onToggle(null, it, null) },
            )
            ToggleRow(
                title = "last updated",
                icon = { Icon(Icons.Outlined.Schedule, contentDescription = null) },
                checked = settings.showUpdated,
                onChecked = { onToggle(null, null, it) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    icon: @Composable () -> Unit,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    val haptic = rememberPointerHaptics()
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic(PointerHaptic.Toggle)
                    onChecked(it)
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
