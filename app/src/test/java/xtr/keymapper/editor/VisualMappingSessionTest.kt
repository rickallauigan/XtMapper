package xtr.keymapper.editor

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import xtr.keymapper.keymap.*
import xtr.keymapper.devices.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualMappingSessionTest {
    private val baseline = setOf("APPLICATION com.mobile.legends", "ENABLED", "SCREENSIZE 2280 1080",
        "BTN_TL2 1981.08 946.58 0 150", "STICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 0.15 0",
        "STICK_AIM BTN_TR 1650 965 123 2 3 0.2 1", "STICK_CAMERA 2140 690 1.6 1.6 0.15",
        "BTN_SELECT 1860 536 0", "BTN_THUMBR 1551 846.5 0", "BTN_THUMBL 1660.5 646 0")
    private fun session() = VisualMappingSession("G8", baseline)
    private fun rejected(action: () -> Unit) { try { action(); fail("must reject") } catch (_: IllegalArgumentException) {} }
    @Test fun movesAimWithoutChangingModeFlagsOrOtherMappings() {
        val s = session(); val aim = s.profile.aimKeys.first { it.code == "BTN_TR" }
        aim.x = 100f; aim.y = 200f; aim.radius = 222f // unit coordinates, not a game calibration
        s.replace("BTN_TR", aim.data)
        assertEquals(baseline - "STICK_AIM BTN_TR 1650 965 123 2 3 0.2 1", s.lines - aim.data)
        val edited = s.profile.aimKeys.first { it.code == "BTN_TR" }
        assertTrue(edited.stick); assertTrue(edited.invertY); assertEquals(.2f, edited.deadZone, 0f)
        assertEquals(2f, edited.xSensitivity, 0f); assertEquals(3f, edited.ySensitivity, 0f)
    }
    @Test fun changingAimOrRepeatTypeRequiresExplicitConfirmation() {
        val s = session(); rejected { s.replace("BTN_TR", "BTN_TR 10 20 0") }
        assertFalse(s.dirty); s.replace("BTN_TR", "BTN_TR 10 20 0", true); assertTrue(s.dirty)
        rejected { s.replace("BTN_TL2", "BTN_TL2 10 20 0") }
    }
    @Test fun invalidChangesAreAtomicAndCannotCrossInputOwnership() {
        val s = session()
        for (bad in listOf("BTN_TL2 NaN 20 0 150", "BTN_TL2 10 20 0 59", "BTN_EAST 10 20 0")) rejected { s.replace("BTN_TL2", bad) }
        assertEquals(baseline, s.lines)
    }
    @Test fun nativeMovementChatAndHomeCannotBeOverwritten() {
        val s = session()
        for (code in listOf("NATIVE_LEFT", "BTN_START", "BTN_MODE")) rejected { s.replace(code, "$code 10 20 0") }
        assertEquals(baseline, s.lines)
    }
    @Test fun duplicateAndCompoundBindingsAreNeverSilentlyRemoved() {
        val dup = VisualMappingSession("dup", baseline + "BTN_TL2 1 2 0")
        rejected { dup.replace("BTN_TL2", "BTN_TL2 3 4 0 150") }
        val chord = VisualMappingSession("chord", setOf("SCREENSIZE 2280 1080", "CHORD BTN_SELECT BTN_TR 1 2 0"))
        rejected { chord.replace("BTN_SELECT", "BTN_SELECT 3 4 0") }
        assertFalse(chord.dirty); assertFalse(dup.dirty)
    }
    @Test fun newDpadAndRearAliasesRemainSingleCanonicalInputs() {
        val s = session(); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0")
        s.replace("BTN_THUMBR", "BTN_THUMBR 30 40 0")
        assertEquals(1, s.profile.keys.count { it.code == "BTN_THUMBR" })
        assertEquals(1, s.profile.keys.count { it.code == "DPAD_LEFT" })
        assertFalse(s.lines.any { it.startsWith("BTN_START ") || it.startsWith("ABS_X ") })
    }
    @Test fun undoResetAndCancelOnlyChangeDraft() {
        val s = session(); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0"); s.replace("BTN_SELECT", "BTN_SELECT 30 40 0")
        s.undo(); assertEquals(listOf("BTN_SELECT 1860 536 0"), s.bindings("BTN_SELECT"))
        s.resetBinding("DPAD_LEFT"); assertFalse(s.dirty)
        s.replace("BTN_SELECT", null); s.reset(); assertEquals(baseline, s.lines); assertEquals(baseline, s.original)
    }
    @Test fun saveWritesOnlyIntendedProfileAndRoundTripsWithoutBindingMutation() {
        val context = RuntimeEnvironment.getApplication(); val prefs = KeymapProfiles(context).sharedPref
        prefs.edit().clear().putStringSet("G8", baseline).putStringSet("KBM", setOf("KEY_F 1 2 0")).commit()
        val store = DeviceMappingStore(context); val group = store.createGroup("G8", listOf(DeviceMember("g8", "G8")))
        store.setBinding(group.id, "com.mobile.legends", "G8"); val bindings = store.listBindings()
        val s = session(); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0")
        assertEquals(baseline, prefs.getStringSet("G8", null)) // discard/no Save writes nothing
        s.save(context)
        assertEquals(setOf("KEY_F 1 2 0"), prefs.getStringSet("KBM", null)); assertEquals(bindings, store.listBindings())
        val loaded = VisualMappingSession("G8", prefs.getStringSet("G8", null)!!)
        assertEquals(s.lines, loaded.lines); assertEquals(s.profile.keys.size, loaded.profile.keys.size)
        assertEquals(s.lines, ProfileConfiguration.validate(s.lines.joinToString("\n")))
    }
    @Test fun deletedRenamedOrConcurrentlyChangedProfilesAreNotOverwritten() {
        val context = RuntimeEnvironment.getApplication(); val prefs = KeymapProfiles(context).sharedPref
        prefs.edit().putStringSet("G8", baseline).commit(); val s = session(); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0")
        val changed = baseline + "KEY_Q 1 2 0"; prefs.edit().putStringSet("G8", changed).commit()
        rejected { s.save(context) }; assertEquals(changed, prefs.getStringSet("G8", null))
        prefs.edit().remove("G8").commit(); rejected { s.save(context) }; assertFalse(prefs.contains("G8"))
    }
    @Test fun disabledProfileAndMissingDimensionsAreHandledExplicitly() {
        val s = VisualMappingSession("disabled", baseline - "ENABLED"); s.replace("DPAD_LEFT", "DPAD_LEFT 10 20 0")
        assertTrue(s.profile.disabled); rejected { VisualMappingSession("empty", setOf("APPLICATION app")) }
    }
    @Test fun planeIdentityScalingInsetsAndRotationRoundTripWithoutDoubleScaling() {
        for (plane in listOf(TargetPlane(2280f,1080f,profileWidth=2280f,profileHeight=1080f),
            TargetPlane(1140f,540f,12f,24f,2280f,1080f), TargetPlane(1080f,2280f,profileWidth=2280f,profileHeight=1080f))) {
            val v = plane.toViewport(1000f, 600f); val p = plane.toProfile(v.first,v.second)
            assertEquals(1000f,p.first,.001f); assertEquals(600f,p.second,.001f)
        }
        val p = TargetPlane(2280f,1080f,profileWidth=2280f,profileHeight=1080f)
        assertEquals(0f to 0f, p.toProfile(-1f,-1f)); assertEquals(2279f to 1079f,p.toProfile(9999f,9999f))
        rejected { p.toProfile(Float.NaN,0f) }
    }
    @Test fun controllerPresentationUsesDistinctCodesAndKeepsAnalogAndReservedInputsHonest() {
        val controls = ControllerPresentation.controls
        assertEquals(controls.size, controls.map { it.code }.distinct().size)
        assertTrue(controls.first { it.code == "BTN_START" }.reserved)
        assertTrue(controls.first { it.code == "NATIVE_LEFT" }.reserved)
        assertFalse(controls.any { it.code == "R4" || it.code == "L4" })
        assertTrue(ControllerPresentation.rearNotice.contains("copies R3"))
    }
    @Test fun legacyButtonAliasesEditOneBindingWithoutNormalizingUnrelatedLines() {
        val source = setOf("SCREENSIZE 2280 1080", "BTN_A 10 20 0 150", "KEY_F 30 40 0")
        val s = VisualMappingSession("legacy", source)
        assertEquals(listOf("BTN_A 10 20 0 150"),s.bindings("BTN_GAMEPAD"))
        s.replace("BTN_GAMEPAD","BTN_GAMEPAD 50 60 0 150")
        assertEquals(setOf("SCREENSIZE 2280 1080", "KEY_F 30 40 0", "BTN_GAMEPAD 50 60 0 150"),s.lines)
        assertEquals(source,s.original)
    }

}
