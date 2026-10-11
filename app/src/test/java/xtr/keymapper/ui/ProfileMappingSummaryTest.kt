package xtr.keymapper

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
class ProfileMappingSummaryTest {
    private val combat = """
        APPLICATION com.mobile.legends
        ENABLED
        SCREENSIZE 2280 1080
        BTN_TL2 1981.08 946.58 0 150
        STICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 0.15 0
        STICK_AIM BTN_TR 1650 965 180 1 1 0.15 0
        STICK_CAMERA 2140 690 1.6 1.6 0.15
    """.trimIndent()
    private fun summary(text: String) = ProfileMappingSummary.from(ProfileConfiguration.parse(text))
    @Test fun gameSirCountsActualBindingsAndNeverInventsGameActionLabels() {
        val s = summary(combat)
        assertEquals(4, s.count)
        val lt = s.rows.first { it.input == "LT" }
        assertEquals("Hold / repeat", lt.action); assertTrue(lt.parameters.contains("150 ms"))
        assertFalse(s.rows.any { it.action.contains("Skill") || it.action.contains("Spell") })
        assertTrue(s.rows.first { it.input == "A" }.parameters.contains("right stick"))
    }
    @Test fun kbmCompoundActionsAndAdvancedMappingsHaveDocumentedCounts() {
        val s = summary("""
            APPLICATION com.mobile.legends
            SCREENSIZE 2280 1080
            KEY_F 100 200 0
            DPAD 10 20 40 50 60 80 80 KEY_W KEY_S KEY_A KEY_D
            MOUSE_LEFT 100 200
            MOUSE_RIGHT 300 400
            MOUSE_WALK 40 50 60
            MOUSE_AIM 100 200 1 300 400 500 600
            SWIPE_KEY KEY_G 100 200 KEY_H 300 400
            MACRO saved KEY_J
            CAMERA 100 200 1 1 0 KEY_ALT
        """.trimIndent())
        assertEquals(9, s.count)
        assertTrue(s.rows.any { it.action == "Movement pad (4 directions)" })
        assertTrue(s.rows.any { it.action == "Macro" }); assertTrue(s.rows.any { it.action == "Swipe" })
    }
    @Test fun chordsAndDiscreteDpadDirectionsAreSeparateActions() {
        val s = summary("CHORD BTN_SELECT BTN_TR 100 200 0\nDPAD_LEFT 20 30 0\nKEY_UNKNOWN 40 50 0")
        assertEquals(3, s.count); assertTrue(s.rows.any { it.input == "SELECT + RB" })
        assertTrue(s.rows.any { it.input == "UNKNOWN" })
    }
    @Test fun countAndOrderingDoNotDependOnSavedSetOrderingOrMetadata() {
        assertEquals(summary(combat), summary(combat.lines().reversed().joinToString("\n")))
        assertEquals(0, summary("APPLICATION a.b\nENABLED\nSCREENSIZE 100 200").count)
    }
    @Test fun duplicateLegacyRowsHaveDistinctStableKeys() {
        val s = summary("KEY_F 10 20 0\nKEY_F 30 40 0")
        assertEquals(2, s.rows.map { it.id }.distinct().size)
    }
    @Test fun readingExactProfileIsSideEffectFreeAndRefreshesOrHandlesDeletion() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = KeymapProfiles(context).sharedPref; prefs.edit().clear().commit()
        prefs.edit().putStringSet("G8", combat.lines().toSet()).putStringSet("KBM", setOf("KEY_F 10 20 0", "APPLICATION com.mobile.legends")).commit()
        val store = DeviceMappingStore(context); val group = store.createGroup("GameSir G8 Setup", listOf(DeviceMember("absent", "G8")))
        store.setBinding(group.id, "com.mobile.legends", "G8")
        val before = prefs.all.toMap(); val bindings = store.listBindings()
        val g8 = ProfileDetailsReader.read(context, "G8")!!; val kbm = ProfileDetailsReader.read(context, "KBM")!!
        assertEquals(4, g8.summary.count); assertEquals(1, kbm.summary.count)
        assertTrue(g8.enabled); assertFalse(kbm.enabled); assertTrue(g8.devices.single().contains("disconnected"))
        assertEquals(before, prefs.all); assertEquals(bindings, store.listBindings())
        prefs.edit().putStringSet("KBM", setOf("KEY_F 30 40 0")).commit()
        assertTrue(ProfileDetailsReader.read(context, "KBM")!!.summary.rows.single().parameters.contains("30.0"))
        prefs.edit().remove("G8").commit(); assertNull(ProfileDetailsReader.read(context, "G8"))
    }
    @Test fun invalidProfilesReportErrorWithoutWritingOrCrashing() {
        val context = RuntimeEnvironment.getApplication(); val prefs = KeymapProfiles(context).sharedPref
        prefs.edit().putStringSet("broken", setOf("STICK_AIM BTN_TR bad")).commit()
        val before = prefs.all.toMap(); assertNotNull(ProfileDetailsReader.read(context, "broken")!!.error)
        assertEquals(before, prefs.all)
    }
}
