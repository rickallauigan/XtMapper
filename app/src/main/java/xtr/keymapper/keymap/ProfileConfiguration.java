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
                    case "MOUSE_RIGHT":
                        count(data, 3); floats(data, 1, 2); break;
                    case "SWIPE_KEY":
                        count(data, 7); floats(data, 2, 3); floats(data, 5, 6); break;
                    case "CAMERA":
                        count(data, 7); floats(data, 1, 4); Integer.parseInt(data[5]); break;
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
                    case "MOUSE_AIM": case "MOUSE_RIGHT": case "CAMERA": case "MOUSE_WALK":
                    case "APPLICATION": case "SCREENSIZE": case "ENABLED":
                        if (!singletons.add(tag)) fail("Duplicate " + tag + " line");
                }
                lines.add(String.join(" ", data));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Line " + (lineNumber + 1) + " (" + tag + "): " + error.getMessage(), error);
            }
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
