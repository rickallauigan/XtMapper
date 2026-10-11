package xtr.keymapper.editor

/** Presentation only; Linux identities remain the generic runtime's source of truth. */
data class ControllerControl(val code: String, val label: String, val x: Float, val y: Float, val reserved: Boolean = false)
object ControllerPresentation {
    val controls = listOf(
        ControllerControl("BTN_TL2", "LT", .14f, .08f), ControllerControl("BTN_TL", "LB", .14f, .21f),
        ControllerControl("BTN_TR2", "RT", .86f, .08f), ControllerControl("BTN_TR", "RB", .86f, .21f),
        ControllerControl("BTN_GAMEPAD", "A", .86f, .61f), ControllerControl("BTN_EAST", "B", .93f, .48f),
        ControllerControl("BTN_WEST", "X", .79f, .48f), ControllerControl("BTN_NORTH", "Y", .86f, .35f),
        ControllerControl("DPAD_UP", "↑", .25f, .64f), ControllerControl("DPAD_DOWN", "↓", .25f, .88f),
        ControllerControl("DPAD_LEFT", "←", .18f, .76f), ControllerControl("DPAD_RIGHT", "→", .32f, .76f),
        ControllerControl("NATIVE_LEFT", "Left stick", .18f, .42f, true),
        ControllerControl("BTN_THUMBL", "L3", .34f, .42f), ControllerControl("BTN_THUMBR", "R3", .65f, .76f),
        ControllerControl("RIGHT_STICK", "Right stick", .82f, .76f),
        ControllerControl("BTN_SELECT", "View", .38f, .19f), ControllerControl("BTN_START", "Menu / Chat", .62f, .19f, true),
        ControllerControl("BTN_MODE", "Home", .5f, .16f, true)
    )
    val rearNotice = "Rear buttons copy firmware-assigned inputs. Verified S10e setup: R4 copies R3; L4 copies L3. Stick clicks share those mappings. Verify your mode before changing them."
}
