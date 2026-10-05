package xtr.keymapper.devices

import android.view.InputDevice
import java.util.Locale

// Android ids are diagnostic, transient information only.
data class HardwareDevice(
    val name: String,
    val type: DeviceType,
    val isConnected: Boolean = true,
    val runtimeId: Int = -1,
    val descriptor: String = "",
    val vendorId: Int = 0,
    val productId: Int = 0,
    val sources: Int = 0,
    val keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE
) {
    val persistentIdentity: String get() = descriptor.takeIf { it.isNotBlank() }
        ?: "metadata:$vendorId:$productId:${normalizedName(name)}"
}

enum class DeviceType { BLUETOOTH_CONTROLLER, USB_KEYBOARD, GAMEPAD, MOUSE, NONE }

fun normalizedName(name: String) = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

/** Raw interface evidence; source checks include Android's source class bits. */
object InterfaceEvidence {
    private fun has(sources: Int, source: Int) = sources and source == source
    fun keyboard(sources: Int) = has(sources, InputDevice.SOURCE_KEYBOARD)
    fun mouse(sources: Int) = has(sources, InputDevice.SOURCE_MOUSE) || has(sources, InputDevice.SOURCE_MOUSE_RELATIVE)
    fun controller(sources: Int) = has(sources, InputDevice.SOURCE_GAMEPAD) || has(sources, InputDevice.SOURCE_JOYSTICK)
    fun alphabeticKeyboard(device: HardwareDevice) = keyboard(device.sources) &&
        device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
    fun dedicatedMouse(device: HardwareDevice) = mouse(device.sources) &&
        !keyboard(device.sources) && !controller(device.sources) && !has(device.sources, InputDevice.SOURCE_DPAD)
    fun dedicatedController(device: HardwareDevice) = controller(device.sources) &&
        !keyboard(device.sources) && !mouse(device.sources)
}

object DeviceClassification {
    // Full source masks, including source class bits (Android InputDevice constants).
    private fun has(sources: Int, source: Int) = sources and source == source
    fun classify(sources: Int, keyboardType: Int): DeviceType = when {
        has(sources, InputDevice.SOURCE_MOUSE) || has(sources, InputDevice.SOURCE_MOUSE_RELATIVE) -> DeviceType.MOUSE
        has(sources, InputDevice.SOURCE_GAMEPAD) || has(sources, InputDevice.SOURCE_JOYSTICK) -> DeviceType.GAMEPAD
        has(sources, InputDevice.SOURCE_KEYBOARD) && keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC -> DeviceType.USB_KEYBOARD
        else -> DeviceType.NONE
    }
}

data class LogicalDevice(val interfaces: List<HardwareDevice>) {
    val name get() = interfaces.first().name
    val identities get() = interfaces.map { it.persistentIdentity }.toSet()
    val descriptors get() = interfaces.map { it.descriptor }.filter { it.isNotBlank() }.toSet()
    // Composite HID classification uses all raw interfaces, not their precomputed types.
    val type get() = when {
        interfaces.any { InterfaceEvidence.dedicatedMouse(it) } -> DeviceType.MOUSE
        interfaces.any { InterfaceEvidence.dedicatedController(it) } -> DeviceType.GAMEPAD
        interfaces.any { InterfaceEvidence.alphabeticKeyboard(it) } -> DeviceType.USB_KEYBOARD
        interfaces.any { InterfaceEvidence.controller(it.sources) } -> DeviceType.GAMEPAD
        interfaces.any { InterfaceEvidence.mouse(it.sources) } -> DeviceType.MOUSE
        else -> DeviceType.NONE
    }
}

// Filter only after grouping so secondary NONE interfaces retain their identities and descriptors.
fun gamingLogicalDevices(devices: List<HardwareDevice>): List<LogicalDevice> =
    logicalDevices(devices).filter { it.type != DeviceType.NONE }

fun logicalDevices(devices: List<HardwareDevice>): List<LogicalDevice> = devices
    .groupBy {
        if (it.vendorId != 0 && it.productId != 0)
            "hid:${normalizedName(it.name)}:${it.vendorId}:${it.productId}"
        else if (it.descriptor.isNotBlank()) "descriptor:${it.descriptor}"
        else "runtime:${it.runtimeId}"
    }.values.map { LogicalDevice(it) }

data class DeviceMember(val identity: String, val name: String, val vendorId: Int = 0, val productId: Int = 0)
data class DeviceGroup(val id: String, val name: String, val members: List<DeviceMember>, val createdAt: Long)
data class ProfileBinding(val groupId: String, val packageName: String, val profileName: String)

object DeviceGroupResolver {
    fun activeGroup(groups: List<DeviceGroup>, connected: Set<String>): DeviceGroup? = groups
        .filter { it.members.isNotEmpty() && it.members.all { member -> member.identity in connected } }
        .sortedWith(compareByDescending<DeviceGroup> { it.members.map { m -> m.identity }.toSet().size }
            .thenBy { it.createdAt }.thenBy { it.id }).firstOrNull()

    // Null explicitly means use the existing ProfileSelector path.
    fun preferredProfile(groups: List<DeviceGroup>, bindings: List<ProfileBinding>, connected: Set<String>,
                         packageName: String, profilePackages: Map<String, String>): String? {
        val group = activeGroup(groups, connected) ?: return null
        val binding = bindings.firstOrNull { it.groupId == group.id && it.packageName == packageName } ?: return null
        return binding.profileName.takeIf { profilePackages[it] == packageName }
    }
}
