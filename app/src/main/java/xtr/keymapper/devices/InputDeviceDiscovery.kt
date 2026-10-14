package xtr.keymapper.devices

import android.view.InputDevice
import android.os.Build

object InputDeviceDiscovery {
    // isExternal became public in API 29. Older systems expose no public equivalent;
    // use the hardware vendor/product identity and exclude virtual devices below.
    private fun isExternal(device: InputDevice): Boolean =
        if (Build.VERSION.SDK_INT >= 29) device.isExternal
        else device.vendorId != 0 || device.productId != 0

    // Retain every external interface, including NONE consumer-control interfaces, for HID grouping.
    fun connected(): List<HardwareDevice> = InputDevice.getDeviceIds().toList().mapNotNull { id ->
        InputDevice.getDevice(id)?.takeIf { isExternal(it) && !it.isVirtual }?.let {
            HardwareDevice(it.name, DeviceClassification.classify(it.sources, it.keyboardType), true, it.id,
                it.descriptor, it.vendorId, it.productId, it.sources, it.keyboardType)
        }
    }
}
