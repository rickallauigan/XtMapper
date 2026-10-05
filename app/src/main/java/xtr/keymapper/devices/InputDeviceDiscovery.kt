package xtr.keymapper.devices

import android.view.InputDevice

object InputDeviceDiscovery {
    // Retain every external interface, including NONE consumer-control interfaces, for HID grouping.
    fun connected(): List<HardwareDevice> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        InputDevice.getDevice(id)?.takeIf { it.isExternal && !it.isVirtual }?.let {
            HardwareDevice(it.name, DeviceClassification.classify(it.sources, it.keyboardType), true, it.id,
                it.descriptor, it.vendorId, it.productId, it.sources, it.keyboardType)
        }
    }
}
