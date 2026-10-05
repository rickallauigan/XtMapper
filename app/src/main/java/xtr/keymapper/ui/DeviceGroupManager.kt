package xtr.keymapper

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import xtr.keymapper.devices.*
import xtr.keymapper.keymap.KeymapProfiles

@Composable
internal fun DeviceGroupManager(devices: List<HardwareDevice>) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) {
        gamingLogicalDevices(devices).forEach { HardwareDeviceItem(it.interfaces.first().copy(type = it.type)) }
        return
    }
    val context = LocalContext.current
    val store = remember(context) { DeviceMappingStore(context) }
    var revision by remember { mutableIntStateOf(0) }
    val groups = remember(revision) { store.listGroups() }
    val bindings = remember(revision) { store.listBindings() }
    val logical = gamingLogicalDevices(devices.filter { it.isConnected })
    val connected = devices.filter { it.isConnected }.map { it.persistentIdentity }.toSet()
    val active = DeviceGroupResolver.activeGroup(groups, connected)
    var editing by remember { mutableStateOf<DeviceGroup?>(null) }
    var creating by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<LogicalDevice?>(null) }
    val profiles = KeymapProfiles(context)
    val games = profiles.getAllProfiles().values.map { it.packageName }.distinct().sorted()
    fun appLabel(pkg: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) { pkg }

    groups.forEach { group ->
        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(if (active?.id == group.id) "Active device group" else if (group.members.all { it.identity in connected }) "Connected" else "Disconnected")
                group.members.groupBy { Triple(normalizedName(it.name), it.vendorId, it.productId) }.values.forEach { members ->
                    Text("${members.first().name} · ${members.count { it.identity in connected }}/${members.size} interfaces connected")
                }
                bindings.filter { it.groupId == group.id }.forEach { binding ->
                    val valid = profiles.getAllProfilesForApp(binding.packageName).containsKey(binding.profileName)
                    Text("${appLabel(binding.packageName)}: ${if (valid) binding.profileName else "Missing mapping (${binding.profileName})"}")
                }
                TextButton(onClick = { editing = group }) { Text("Configure") }
            }
        }
    }
    TextButton(onClick = { creating = true }) { Text("Create Device Group") }
    // Gaming hardware details retain every interface, including devices already in a group.
    logical.forEach { device ->
        val grouped = groups.any { g -> g.members.any { it.identity in device.identities } }
        HardwareDeviceItem(device.interfaces.first().copy(type = device.type))
        TextButton(onClick = { details = device }) {
            Text("Details · ${device.interfaces.size} input interface${if (device.interfaces.size == 1) "" else "s"}" +
                (if (grouped) " · grouped" else " · ungrouped"))
        }
    }
    details?.let { device ->
        AlertDialog(onDismissRequest = { details = null }, title = { Text(device.name) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Type: ${device.type}\n${device.interfaces.size} HID interfaces")
                device.interfaces.forEach {
                    Text("${it.name}\nType: ${it.type}\nVendor/product: ${it.vendorId}/${it.productId}\nRuntime id: ${it.runtimeId}\nSources: 0x${it.sources.toString(16)}\nDescriptor: ${it.descriptor.ifBlank { "Unavailable (metadata fallback)" }}")
                }
            } }, confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } })
    }
    if (creating || editing != null) {
        val original = editing
        var name by remember(original?.id) { mutableStateOf(original?.name ?: "") }
        var members by remember(original?.id) { mutableStateOf(original?.members ?: emptyList()) }
        var selectedBindings by remember(original?.id) {
            mutableStateOf(bindings.filter { it.groupId == original?.id }.associate { it.packageName to it.profileName })
        }
        fun dismiss() { creating = false; editing = null }
        AlertDialog(onDismissRequest = { dismiss() }, title = { Text(if (original == null) "Create Device Group" else "Configure Device Group") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group name") }, singleLine = true)
                Text("Member devices", style = MaterialTheme.typography.titleSmall)
                if (logical.isEmpty()) Text("Connect a device to add members.")
                logical.forEach { device ->
                    Row {
                        Checkbox(checked = device.identities.all { id -> members.any { it.identity == id } }, onCheckedChange = { checked ->
                            members = members.filterNot { it.identity in device.identities } + if (checked) device.interfaces.map {
                                DeviceMember(it.persistentIdentity, it.name, it.vendorId, it.productId)
                            }.distinctBy { it.identity } else emptyList()
                        })
                        Text(device.name + if (device.interfaces.size > 1) " (${device.interfaces.size} input interfaces)" else "", Modifier.weight(1f))
                    }
                }
                members.filter { it.identity !in connected }.forEach { member ->
                    Row {
                        Checkbox(checked = true, onCheckedChange = { members = members.filterNot { it.identity == member.identity } })
                        Text("${member.name} (disconnected)", Modifier.weight(1f))
                    }
                }
                Text("Preferred mappings", style = MaterialTheme.typography.titleSmall)
                if (games.isEmpty()) Text("Create an application profile first.")
                // Include stale bindings so deleted profiles can be explicitly cleared.
                (games + selectedBindings.keys).distinct().forEach { pkg ->
                    val names = profiles.getAllProfilesForApp(pkg).keys.sorted()
                    var expanded by remember(pkg) { mutableStateOf(false) }
                    Text(appLabel(pkg))
                    val selected = selectedBindings[pkg]
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(if (selected == null) "No preferred mapping ▾" else if (selected in names) "$selected ▾" else "Missing mapping: $selected ▾")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("No preferred mapping") }, onClick = { selectedBindings = selectedBindings - pkg; expanded = false })
                            names.forEach { profile -> DropdownMenuItem(text = { Text(profile) }, onClick = {
                                selectedBindings = selectedBindings + (pkg to profile); expanded = false
                            }) }
                        }
                    }
                }
            } }, confirmButton = {
                TextButton(enabled = name.isNotBlank() && members.isNotEmpty(), onClick = {
                    val group = if (original == null) store.createGroup(name, members) else original.copy(name = name.trim(), members = members).also { store.updateGroup(it) }
                    (bindings.filter { it.groupId == group.id }.map { it.packageName } + selectedBindings.keys).distinct().forEach {
                        store.setBinding(group.id, it, selectedBindings[it])
                    }
                    revision++; dismiss()
                }) { Text("Save") }
            }, dismissButton = {
                Row {
                    if (original != null) TextButton(onClick = { store.deleteGroup(original.id); revision++; dismiss() }) { Text("Delete group") }
                    TextButton(onClick = { dismiss() }) { Text("Cancel") }
                }
            })
    }
}
