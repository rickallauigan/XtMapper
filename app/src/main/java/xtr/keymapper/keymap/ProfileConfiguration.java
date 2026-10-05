package xtr.keymapper.keymap;

import java.util.LinkedHashSet;
import java.util.Set;

/** Validates external text completely before invoking the existing profile parser. No storage I/O. */
public final class ProfileConfiguration {
    private ProfileConfiguration() {}

    public static KeymapProfile parse(String text) {
        return KeymapProfiles.getProfile(validate(text));
    }

    public static Set<String> validate(String text) {
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Configuration is blank. Paste a profile first.");
        Set<String> lines = new LinkedHashSet<>();
        Set<String> singletons = new LinkedHashSet<>();
        int dpads = 0;
        Set<String> aimTriggers = new LinkedHashSet<>();
        String[] input = text.split("\\r?\\n", -1);
        for (int lineNumber = 0; lineNumber < input.length; lineNumber++) {
            String line = input[lineNumber].trim();
            if (line.isEmpty()) continue;
            String[] data = line.split("\\s+");
            String tag = data[0];
            try {
                switch (tag) {
                    case "DPAD": case "DPAD_UDLR":
                        count(data, 12); floats(data, 1, 5); positiveInt(data[6]); positiveInt(data[7]);
                        if (++dpads > KeymapProfile.MAX_DPADS) fail("At most three D-pads are supported");
                        break;
                    case "MOUSE_AIM":
                        if (data.length != 8 && data.length != 11) fail("MOUSE_AIM needs 7 or 10 values");
                        floats(data, 1, 2); Integer.parseInt(data[3]); floats(data, 4, 7);
                        if (data.length == 11) { floats(data, 8, 9); Integer.parseInt(data[10]); }
                        break;
                    case "AIM_KEY":
                        count(data, 7); floats(data, 2, 6);
                        if (!(data[1].matches("KEY_[A-Z0-9_]+") || data[1].equals("BTN_RIGHT") || data[1].equals("BTN_MOUSE")))
                            fail("AIM_KEY trigger must be KEY_* or BTN_RIGHT/BTN_MOUSE");
                        if (!aimTriggers.add(data[1])) fail("Duplicate AIM_KEY trigger");
                        if (aimTriggers.size() > 8) fail("At most eight aim triggers are supported");
                        for (int i = 4; i <= 6; i++) if (Float.parseFloat(data[i]) <= 0) fail("Aim range and sensitivities must be positive");
                        break;
                    case "MOUSE_LEFT": case "MOUSE_RIGHT":
                        count(data, 3); floats(data, 1, 2); break;
                    case "SWIPE_KEY":
                        count(data, 7); floats(data, 2, 3); floats(data, 5, 6); break;
                    case "CAMERA":
                        if (data.length != 7 && data.length != 8) fail("Expected 6 or 7 CAMERA values");
                        floats(data, 1, 4); Integer.parseInt(data[5]);
                        if (data.length == 8 && !data[7].equals("0") && !data[7].equals("1")) fail("CAMERA auto-active must be 0 or 1");
                        break;
                    case "MOUSE_WALK":
                        count(data, 4); floats(data, 1, 3); break;
                    case "MACRO":
                        count(data, 3); break;
                    case "APPLICATION":
                        count(data, 2); break;
                    case "SCREENSIZE":
                        count(data, 3); positiveInt(data[1]); positiveInt(data[2]); break;
                    case "ENABLED":
                        count(data, 1); break;
                    default:
                        if (!tag.startsWith("KEY_") || tag.length() == 4) fail("Unsupported line: " + tag);
                        count(data, 4); floats(data, 1, 3);
                }
                switch (tag) {
                    case "MOUSE_AIM": case "MOUSE_LEFT": case "MOUSE_RIGHT": case "CAMERA": case "MOUSE_WALK":
                    case "APPLICATION": case "SCREENSIZE": case "ENABLED":
                        if (!singletons.add(tag)) fail("Duplicate " + tag + " line");
                }
                lines.add(String.join(" ", data));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Line " + (lineNumber + 1) + " (" + tag + "): " + error.getMessage(), error);
            }
        }
        for (String line : lines) {
            String[] data = line.split("\\s+");
            if (data[0].startsWith("KEY_") && aimTriggers.contains(data[0])) fail("AIM_KEY conflicts with fixed key " + data[0]);
            if (data[0].equals("DPAD") || data[0].equals("DPAD_UDLR"))
                for (int i = 8; i < 12; i++) if (aimTriggers.contains(data[i])) fail("AIM_KEY conflicts with D-pad key " + data[i]);
            if (data[0].equals("SWIPE_KEY"))
                for (int i : new int[]{1, 4}) if ((aimTriggers.contains(data[i]) || aimTriggers.contains("KEY_" + data[i]))) fail("AIM_KEY conflicts with swipe key " + data[i]);
            if (data[0].equals("CAMERA") && aimTriggers.contains(data[6])) fail("AIM_KEY conflicts with camera trigger " + data[6]);
            if (data[0].equals("MACRO") && (aimTriggers.contains(data[2]) || aimTriggers.contains("KEY_" + data[2]))) fail("AIM_KEY conflicts with macro trigger " + data[2]);
            if (data[0].equals("MOUSE_RIGHT") && aimTriggers.contains("BTN_RIGHT")) fail("MOUSE_RIGHT conflicts with AIM_KEY BTN_RIGHT");
            if (data[0].equals("MOUSE_LEFT") && aimTriggers.contains("BTN_MOUSE")) fail("MOUSE_LEFT conflicts with AIM_KEY BTN_MOUSE");
        }
        return lines;
    }

    private static void count(String[] data, int count) {
        if (data.length != count) fail("Expected " + (count - 1) + " values, got " + (data.length - 1));
    }

    private static void floats(String[] data, int start, int end) {
        for (int i = start; i <= end; i++) {
            if (!Float.isFinite(Float.parseFloat(data[i]))) fail("Coordinates and numeric values must be finite");
        }
    }

    private static void positiveInt(String value) {
        if (Integer.parseInt(value) <= 0) fail("Dimensions must be positive integers");
    }

    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
