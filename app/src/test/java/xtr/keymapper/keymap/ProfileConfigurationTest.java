package xtr.keymapper.keymap;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileConfigurationTest {
    private static final String REPRESENTATIVE =
            "KEY_Q 120.5 240 35\n" +
            "DPAD 100 200 50 150 250 100 100 KEY_W KEY_S KEY_A KEY_D\n" +
            "DPAD_UDLR 300 200 50 350 250 100 100 KEY_UP KEY_DOWN KEY_LEFT KEY_RIGHT\n" +
            "MOUSE_AIM 960 320 1 170 220 300 180 1 1 0\n" +
            "MOUSE_RIGHT 50 60\nSWIPE_KEY KEY_1 10 20 KEY_2 30 40\n" +
            "CAMERA 100 200 1 1 1 KEY_ALT\nMOUSE_WALK 100 200 50\n" +
            "MACRO local-id KEY_R\nAPPLICATION example.game\nSCREENSIZE 1920 1080\nENABLED";

    @Test public void representativeProfileUsesExistingParser() {
        KeymapProfile profile = ProfileConfiguration.parse(REPRESENTATIVE);
        assertEquals("example.game", profile.packageName);
        assertFalse(profile.disabled);
        assertEquals(1920, profile.xRes);
        assertEquals(120.5f, profile.keys.get(0).x, 0f);
        assertEquals("KEY_Q", profile.keys.get(0).code);
        assertEquals(100f, profile.dpadArray[0].getX(), 0f);
        assertEquals("KEY_UP", profile.dpadArray[1].keycodes.Up);
        assertEquals(1, profile.swipeKeys.size());
        assertNotNull(profile.mouseAimConfig);
        assertNotNull(profile.rightClick);
        assertNotNull(profile.camera);
        assertEquals("KEY_ALT", profile.camera.triggerKeyCode);
        assertNotNull(profile.mouseWalk);
        assertEquals("KEY_R", profile.macroIdMap.get("local-id").triggerKey);
    }

    @Test public void cameraTriggerSurvivesSerializationRoundTrip() {
        KeymapProfile profile = ProfileConfiguration.parse("CAMERA 100 200 1 1 1 KEY_ALT");
        assertEquals("KEY_ALT", profile.camera.triggerKeyCode);

        String serialized = profile.camera.getData();
        assertEquals("KEY_ALT", serialized.split("\\s+")[6]);
        assertFalse(serialized.contains("KEY_KEY_ALT"));

        KeymapProfile reparsed = ProfileConfiguration.parse(serialized);
        assertEquals("KEY_ALT", reparsed.camera.triggerKeyCode);
    }

    @Test public void acceptsWhitespaceAndLegacyAim() {
        assertEquals(3, ProfileConfiguration.validate("  KEY_A\t10 20 35\r\n\r\n MOUSE_AIM 1 2 1 3 4 5 6\r\nSCREENSIZE 100 200 ").size());
    }

    @Test public void malformedCoordinateHasLineNumber() { rejects("KEY_A nope 20 35", "Line 1"); }
    @Test public void rejectsMalformedScreenSize() {
        rejects("SCREENSIZE 1920", "SCREENSIZE");
        rejects("SCREENSIZE 1920 bad", "SCREENSIZE");
        rejects("SCREENSIZE 0 1080", "positive");
    }
    @Test public void rejectsBlank() { rejects(" \n\t", "blank"); }
    @Test public void rejectsTruncatedElements() {
        rejects("DPAD 100 200", "Expected");
        rejects("CAMERA 1 2 3", "Expected");
        rejects("KEY_Q 1 2", "Expected");
    }
    @Test public void rejectsUnknownAndNonFiniteValues() {
        rejects("UNKNOWN 1 2", "Unsupported");
        rejects("KEY_A NaN 2 35", "finite");
        rejects("KEY_A Infinity 2 35", "finite");
    }
    @Test public void rejectsAmbiguousMetadataAndExcessDpads() {
        rejects("SCREENSIZE 100 200\nSCREENSIZE 300 400", "Duplicate");
        String dpad = "DPAD 100 200 50 150 250 100 100 KEY_W KEY_S KEY_A KEY_D\n";
        rejects(dpad.repeat(4), "three");
    }
    @Test public void invalidLaterLineDoesNotReturnPartialProfile() {
        rejects(REPRESENTATIVE + "\nKEY_B broken 10 35", "Line 13");
    }
    @Test public void usesExistingScaling() {
        KeymapProfile profile = ProfileConfiguration.parse(REPRESENTATIVE);
        profile.scale(960, 540);
        assertEquals(60.25f, profile.keys.get(0).x, 0f);
        assertEquals(50f, profile.dpadArray[0].getX(), 0f);
        assertEquals(960, profile.xRes);
    }
    @Test public void newAimAndMouseLinesRoundTripAndScale() {
        KeymapProfile profile = ProfileConfiguration.parse("AIM_KEY KEY_Q 500 600 180 2 3\n"
                + "AIM_KEY BTN_RIGHT 700 800 180 1 1\nMOUSE_LEFT 900 950\n"
                + "CAMERA 1000 400 1 2 1 KEY_GRAVE 1\nSCREENSIZE 2280 1080\nENABLED");
        assertEquals(2, profile.aimKeys.size());
        KeymapProfile imported = ProfileConfiguration.parse(profile.aimKeys.get(0).getData()
                + "\n" + profile.camera.getData() + "\nMOUSE_LEFT " + profile.leftClick.x + " " + profile.leftClick.y);
        assertEquals("KEY_Q", imported.aimKeys.get(0).code);
        assertEquals(180f, imported.aimKeys.get(0).radius, 0f);
        assertTrue(imported.camera.autoActive);
        assertEquals(900f, imported.leftClick.x, 0f);
        profile.scale(1140, 540);
        assertEquals(250f, profile.aimKeys.get(0).x, 0f);
        assertEquals(90f, profile.aimKeys.get(0).radius, 0f);
        assertEquals(1f, profile.aimKeys.get(0).xSensitivity, 0f);
        assertEquals(500f, profile.camera.x, 0f);
        assertEquals(450f, profile.leftClick.x, 0f);
    }

    @Test public void rejectsMalformedAimAndAmbiguousButtons() {
        rejects("AIM_KEY KEY_Q 1 2 3", "Expected");
        rejects("AIM_KEY UNKNOWN 1 2 30 1 1", "trigger");
        rejects("AIM_KEY KEY_Q NaN 2 30 1 1", "finite");
        rejects("AIM_KEY KEY_Q 1 2 0 1 1", "positive");
        rejects("AIM_KEY KEY_Q 1 2 30 -1 1", "positive");
        rejects("AIM_KEY KEY_Q 1 2 30 1 1\nAIM_KEY KEY_Q 3 4 30 1 1", "Duplicate");
        rejects("AIM_KEY KEY_Q 1 2 30 1 1\nKEY_Q 3 4 0", "conflicts");
        rejects("AIM_KEY BTN_RIGHT 1 2 30 1 1\nMOUSE_RIGHT 3 4", "conflicts");
        rejects("MOUSE_LEFT 1 2\nMOUSE_LEFT 3 4", "Duplicate");
        rejects("CAMERA 1 2 1 1 1 KEY_GRAVE 2", "0 or 1");
    }

    @Test public void aimTriggersCannotShadowMovementSwipeCameraOrMacro() {
        String aim = "AIM_KEY KEY_Q 100 200 50 1 1\n";
        rejects(aim + "DPAD 0 0 50 50 50 100 100 KEY_Q KEY_S KEY_A KEY_D", "conflicts");
        rejects(aim + "SWIPE_KEY Q 1 2 E 3 4", "conflicts");
        rejects(aim + "SWIPE_KEY KEY_Q 1 2 KEY_E 3 4", "conflicts");
        rejects(aim + "CAMERA 1 2 1 1 1 KEY_Q", "conflicts");
        rejects(aim + "MACRO test Q", "conflicts");
    }

    @Test public void legacyCameraDoesNotActivateAutomaticallyAndDpadRuntimeScales() {
        KeymapProfile profile = ProfileConfiguration.parse(REPRESENTATIVE);
        assertFalse(profile.camera.autoActive);
        profile.scale(960, 540);
        assertEquals(75f, profile.dpadArray[0].xOfCenter, 0f);
        assertEquals(125f, profile.dpadArray[0].yOfCenter, 0f);
        assertEquals(25f, profile.dpadArray[0].radius, 0f);
    }

    private static void rejects(String text, String message) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ProfileConfiguration.parse(text));
        assertTrue(error.getMessage(), error.getMessage().contains(message));
    }
}
