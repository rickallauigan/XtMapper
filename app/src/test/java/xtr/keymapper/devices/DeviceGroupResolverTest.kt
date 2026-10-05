package xtr.keymapper.devices

import android.view.InputDevice
import org.junit.Assert.*
import org.junit.Test

class DeviceGroupResolverTest {
    private fun device(id: Int, descriptor: String = "keyboard", vendor: Int = 12, product: Int = 34) =
        HardwareDevice(" Keyboard  Name ", DeviceType.USB_KEYBOARD, runtimeId = id,
            descriptor = descriptor, vendorId = vendor, productId = product,
            sources = InputDevice.SOURCE_KEYBOARD, keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC)
    private fun group(id: String, vararg identities: String, createdAt: Long = 1) =
        DeviceGroup(id, id, identities.map { DeviceMember(it, it) }, createdAt)

    @Test fun identityIgnoresRuntimeId() {
        assertEquals(device(1).persistentIdentity, device(987).persistentIdentity)
        assertEquals("keyboard", device(1).persistentIdentity)
        assertEquals(device(1, "").persistentIdentity, device(987, "").persistentIdentity)
        assertEquals(device(1, "", vendor = 0, product = 0).persistentIdentity,
            device(987, "", vendor = 0, product = 0).persistentIdentity)
    }
    @Test fun mousePrecedesKeyboardAndGamepad() {
        val sources = InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD
        assertEquals(DeviceType.MOUSE, DeviceClassification.classify(
            InputDevice.SOURCE_MOUSE or sources, InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        assertEquals(DeviceType.MOUSE, DeviceClassification.classify(
            InputDevice.SOURCE_MOUSE_RELATIVE or sources, InputDevice.KEYBOARD_TYPE_ALPHABETIC))
    }
    @Test fun gamepadAndJoystickPrecedeKeyboard() {
        listOf(InputDevice.SOURCE_GAMEPAD, InputDevice.SOURCE_JOYSTICK).forEach { source ->
            assertEquals(DeviceType.GAMEPAD, DeviceClassification.classify(
                source or InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        }
    }
    @Test fun onlyAlphabeticKeyboardSourcesBecomeKeyboards() {
        assertEquals(DeviceType.USB_KEYBOARD, DeviceClassification.classify(
            InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC))
        listOf(InputDevice.KEYBOARD_TYPE_NONE, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC).forEach { type ->
            assertEquals(DeviceType.NONE, DeviceClassification.classify(InputDevice.SOURCE_KEYBOARD, type))
        }
        assertEquals(DeviceType.NONE, DeviceClassification.classify(
            InputDevice.SOURCE_TOUCHSCREEN, InputDevice.KEYBOARD_TYPE_ALPHABETIC))
    }
    private fun interfaceDevice(name: String, descriptor: String, vendor: Int, product: Int,
                                sources: Int, keyboardType: Int) =
        HardwareDevice(name, DeviceClassification.classify(sources, keyboardType),
            descriptor = descriptor, vendorId = vendor, productId = product,
            sources = sources, keyboardType = keyboardType)

    @Test fun realEvisionCompositeIsKeyboardInEitherOrder() {
        val keyboard = interfaceDevice("Evision RGB Keyboard", "evision-keyboard", 0x05ac, 0x0256,
            InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        val mixed = interfaceDevice(" evision  rgb keyboard ", "evision-mixed", 0x05ac, 0x0256,
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD or InputDevice.SOURCE_MOUSE or
                InputDevice.SOURCE_JOYSTICK, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        assertComposite(DeviceType.USB_KEYBOARD, keyboard, mixed)
    }

    @Test fun realLenovoCompositeIsMouseInEitherOrder() {
        val mouse = interfaceDevice("Lenovo Multi-function Mouse M300", "lenovo-mouse", 0x17ef, 0x6054,
            InputDevice.SOURCE_MOUSE, InputDevice.KEYBOARD_TYPE_NONE)
        val keyboard = interfaceDevice(" lenovo multi-function mouse m300 ", "lenovo-keyboard", 0x17ef, 0x6054,
            InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        assertComposite(DeviceType.MOUSE, mouse, keyboard)
    }

    private fun assertComposite(expected: DeviceType, first: HardwareDevice, second: HardwareDevice) {
        listOf(listOf(first, second), listOf(second, first)).forEach { interfaces ->
            val logical = logicalDevices(interfaces).single()
            assertEquals(expected, logical.type)
            assertEquals(interfaces, logical.interfaces)
            assertEquals(setOf(first.descriptor, second.descriptor), logical.descriptors)
            assertEquals(setOf(first.persistentIdentity, second.persistentIdentity), logical.identities)
            assertEquals(listOf(logical), gamingLogicalDevices(interfaces))
        }
    }

    @Test fun logicalClassificationUsesRawEvidenceForPureDevicesAndFallbacks() {
        listOf(
            Triple(InputDevice.SOURCE_KEYBOARD, InputDevice.KEYBOARD_TYPE_ALPHABETIC, DeviceType.USB_KEYBOARD),
            Triple(InputDevice.SOURCE_MOUSE, InputDevice.KEYBOARD_TYPE_NONE, DeviceType.MOUSE),
            Triple(InputDevice.SOURCE_MOUSE_RELATIVE, InputDevice.KEYBOARD_TYPE_NONE, DeviceType.MOUSE),
            Triple(InputDevice.SOURCE_GAMEPAD, InputDevice.KEYBOARD_TYPE_NONE, DeviceType.GAMEPAD),
            Triple(InputDevice.SOURCE_JOYSTICK, InputDevice.KEYBOARD_TYPE_NONE, DeviceType.GAMEPAD),
            Triple(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_JOYSTICK,
                InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC, DeviceType.GAMEPAD),
            Triple(InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE,
                InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC, DeviceType.MOUSE),
            Triple(InputDevice.SOURCE_TOUCHSCREEN, InputDevice.KEYBOARD_TYPE_NONE, DeviceType.NONE)
        ).forEach { (sources, keyboardType, expected) ->
            val device = interfaceDevice("neutral", "interface", 1, 2, sources, keyboardType)
                .copy(type = DeviceType.NONE)
            assertEquals(expected, logicalDevices(listOf(device)).single().type)
            assertEquals(if (expected == DeviceType.NONE) 0 else 1, gamingLogicalDevices(listOf(device)).size)
        }
    }

    @Test fun dedicatedControllerPrecedesAlphabeticKeyboard() {
        assertComposite(DeviceType.GAMEPAD, device(1), device(2, "controller").copy(
            sources = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD,
            keyboardType = InputDevice.KEYBOARD_TYPE_NONE))
    }
    @Test fun compositeKeyboardRetainsNoneInterfaceInEitherOrderAfterUiFiltering() {
        val keyboard = device(1, "alphabetic").copy(name = "Evision RGB Keyboard")
        val controls = device(2, "consumer").copy(name = keyboard.name, type = DeviceType.NONE, sources = 0, keyboardType = InputDevice.KEYBOARD_TYPE_NONE)
        val irrelevant = device(3, "helper", product = 99).copy(type = DeviceType.NONE, sources = 0, keyboardType = InputDevice.KEYBOARD_TYPE_NONE)
        listOf(listOf(keyboard, controls), listOf(controls, keyboard)).forEach { interfaces ->
            val all = interfaces + irrelevant
            assertEquals(2, logicalDevices(all).size)
            val logical = gamingLogicalDevices(all).single()
            assertEquals("Evision RGB Keyboard", logical.name)
            assertEquals(DeviceType.USB_KEYBOARD, logical.type)
            assertEquals(interfaces, logical.interfaces)
            assertEquals(setOf("alphabetic", "consumer"), logical.descriptors)
            assertEquals(setOf("alphabetic", "consumer"), logical.identities)
        }
    }
    @Test fun duplicateInterfacesRetainAllDescriptors() {
        val logical = logicalDevices(listOf(device(1, "a"), device(2, "b").copy(name = "keyboard name")))
        assertEquals(1, logical.size)
        assertEquals(setOf("a", "b"), logical.single().descriptors)
        assertEquals(2, logical.single().interfaces.size)
    }
    @Test fun differentMetadataAndDescriptorsDoNotMerge() {
        assertEquals(2, logicalDevices(listOf(device(1, "a"), device(2, "b", product = 99))).size)
        assertEquals(2, logicalDevices(listOf(device(1, "a", vendor = 0), device(2, "b", vendor = 0))).size)
        assertEquals(2, logicalDevices(listOf(device(1, "a", vendor = 0, product = 0),
            device(2, "b", vendor = 0, product = 0))).size)
    }
    @Test fun unknownInterfacesWithoutDescriptorsRemainSeparateByRuntimeId() {
        val first = device(1, "", vendor = 0, product = 0)
        val second = device(987, "", vendor = 0, product = 0).copy(name = "keyboard name")
        listOf(listOf(first, second), listOf(second, first)).forEach { interfaces ->
            val logical = logicalDevices(interfaces)
            assertEquals(2, logical.size)
            assertEquals(interfaces.map { listOf(it) }, logical.map { it.interfaces })
            assertTrue(logical.all { it.type == DeviceType.USB_KEYBOARD })
        }
    }
    @Test fun allMembersRequiredAndEmptyGroupsNeverMatch() {
        assertNull(DeviceGroupResolver.activeGroup(listOf(group("g", "a", "b")), setOf("a")))
        assertNull(DeviceGroupResolver.activeGroup(listOf(group("empty")), emptySet()))
        assertEquals("g", DeviceGroupResolver.activeGroup(listOf(group("g", "a", "b")), setOf("a", "b"))?.id)
    }
    @Test fun mostSpecificGroupWins() {
        assertEquals("large", DeviceGroupResolver.activeGroup(listOf(group("small", "a"), group("large", "a", "b")), setOf("a", "b"))?.id)
    }
    @Test fun tiesUseCreationThenIdRegardlessOfInputOrder() {
        val a = group("a", "a"); val b = group("b", "a")
        assertEquals(a, DeviceGroupResolver.activeGroup(listOf(b, a), setOf("a")))
        assertEquals(a, DeviceGroupResolver.activeGroup(listOf(a, b), setOf("a")))
        assertEquals(b, DeviceGroupResolver.activeGroup(listOf(a.copy(createdAt = 2), b), setOf("a")))
    }
    private fun resolve(profiles: Map<String, String>, groups: List<DeviceGroup> = listOf(group("g", "a")),
                        bindings: List<ProfileBinding> = listOf(ProfileBinding("g", "game", "general"))) =
        DeviceGroupResolver.preferredProfile(groups, bindings, setOf("a"), "game", profiles)
    @Test fun boundProfileResolves() { assertEquals("general", resolve(mapOf("general" to "game", "mage" to "game"))) }
    @Test fun missingAndRenamedProfileFallBack() {
        assertNull(resolve(emptyMap()))
        assertNull(resolve(mapOf("renamed" to "game")))
    }
    @Test fun packageMismatchFallsBack() { assertNull(resolve(mapOf("general" to "other"))) }
    @Test fun noGroupsOrBindingsPreservesSelectorPath() {
        assertNull(resolve(mapOf("general" to "game"), groups = emptyList()))
        assertNull(resolve(mapOf("general" to "game"), bindings = emptyList()))
    }
    @Test fun disconnectedGroupBindingFallsBack() {
        assertNull(resolve(mapOf("general" to "game"), groups = listOf(group("g", "a", "b"))))
    }
    @Test fun invalidSpecificBindingDoesNotSelectLessSpecificGroup() {
        assertNull(DeviceGroupResolver.preferredProfile(
            listOf(group("small", "a"), group("large", "a", "b")),
            listOf(ProfileBinding("small", "game", "general"), ProfileBinding("large", "game", "missing")),
            setOf("a", "b"), "game", mapOf("general" to "game")))
    }

}
