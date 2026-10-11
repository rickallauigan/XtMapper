package xtr.keymapper

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ProfileDetailsDialog(details: SavedProfileDetails, appName: String, selected: Boolean,
    onDismiss: () -> Unit, onSelect: () -> Unit, onEdit: () -> Unit, icon: android.graphics.drawable.Drawable? = null, onVisualEdit: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ProfileDetailsContent(details, appName, selected, onDismiss, onSelect, onEdit, icon, onVisualEdit)
    }
}

@Composable
internal fun ProfileDetailsContent(details: SavedProfileDetails, appName: String, selected: Boolean,
    onDismiss: () -> Unit, onSelect: () -> Unit, onEdit: () -> Unit,
    icon: android.graphics.drawable.Drawable? = null, onVisualEdit: (() -> Unit)? = null) {
        Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(.9f).padding(12.dp),
            shape = MaterialTheme.shapes.extraLarge, tonalElevation = 8.dp) {
            Column(Modifier.padding(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (icon != null) Image(remember(details.id, icon) { icon.toBitmap().asImageBitmap() },
                        contentDescription = appName, modifier = Modifier.size(48.dp))
                    else Icon(Icons.Default.Gamepad, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close mapping details") }
                }
                LazyColumn(Modifier.weight(1f).testTag("details-list"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text(details.id, style = MaterialTheme.typography.headlineSmall)
                        Text(appName, style = MaterialTheme.typography.titleMedium)
                        Text(details.packageName, style = MaterialTheme.typography.bodySmall)
                        Text("${if (selected) "Selected" else "Not selected"} • ${if (details.enabled) "Enabled" else "Disabled"} • ${details.summary.count} mappings",
                            color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Text("Profile plane ${details.dimensions}", style = MaterialTheme.typography.bodySmall)
                        if (details.devices.isEmpty()) Text("No device-group preference assigned", style = MaterialTheme.typography.bodyMedium)
                        details.devices.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        details.error?.let { Text("Cannot display mappings: $it", color = MaterialTheme.colorScheme.error) }
                        if (details.error == null && details.summary.rows.isEmpty()) Text("No mappings yet. Use Edit Keymap to add controls.")
                    }
                    details.summary.rows.groupBy { it.group }.forEach { (group, rows) ->
                        item(key = "group:$group") { Text(group, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                        items(rows, key = { it.id }) { row -> MappingDetailsRow(row) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                if (onVisualEdit != null) OutlinedButton(onClick = onVisualEdit, modifier = Modifier.fillMaxWidth(), enabled = details.error == null) { Text("Visual Controller Edit") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), enabled = details.error == null) { Text("Edit Keymap") }
                    Button(onClick = onSelect, modifier = Modifier.weight(1f), enabled = !selected && details.error == null) { Text(if (selected) "Selected" else "Select Profile") }
                }
            }
        }
}

@Composable
private fun MappingDetailsRow(row: MappingRow) {
    var expanded by remember(row.id) { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(row.input, style = MaterialTheme.typography.titleMedium)
            Text(row.action, style = MaterialTheme.typography.bodyMedium)
            Text(if (expanded) row.parameters else "Show saved parameters", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
