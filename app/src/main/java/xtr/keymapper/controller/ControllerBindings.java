package xtr.keymapper.controller;

import java.util.Map;

/** Persistence uses Linux codes; labels never change the binding identity. */
public final class ControllerBindings {
    private ControllerBindings() {}
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("BTN_GAMEPAD", "A"), Map.entry("BTN_EAST", "B"),
        Map.entry("BTN_WEST", "X"), Map.entry("BTN_NORTH", "Y"),
        Map.entry("BTN_TL", "LB"), Map.entry("BTN_TR", "RB"),
        Map.entry("BTN_TL2", "LT"), Map.entry("BTN_TR2", "RT"),
        Map.entry("BTN_THUMBL", "L3"), Map.entry("BTN_THUMBR", "R3"),
        Map.entry("BTN_SELECT", "SELECT"), Map.entry("BTN_START", "START"),
        Map.entry("BTN_MODE", "MODE"), Map.entry("DPAD_UP", "D-UP"),
        Map.entry("DPAD_DOWN", "D-DOWN"), Map.entry("DPAD_LEFT", "D-LEFT"),
        Map.entry("DPAD_RIGHT", "D-RIGHT"));
    public static String canonical(String code) {
        if (code == null) return null;
        switch (code) {
            case "BTN_A": case "BTN_SOUTH": return "BTN_GAMEPAD";
            case "BTN_B": return "BTN_EAST";
            case "BTN_X": return "BTN_WEST";
            case "BTN_Y": return "BTN_NORTH";
            default: return code;
        }
    }
    public static boolean isController(String code) { return code != null && LABELS.containsKey(canonical(code)); }
    public static boolean isBinding(String code) {
        return code != null && ((code.matches("KEY_[A-Z0-9_]+") && !code.startsWith("KEY_BTN_")) || isController(code));
    }
    public static String label(String code) {
        return LABELS.getOrDefault(canonical(code), code.startsWith("KEY_") ? code.substring(4) : code);
    }
    public static String capture(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 4) return null;
        if (parts[1].equals("EV_KEY") && parts[3].equals("DOWN")) {
            String code = canonical(parts[2]); return isBinding(code) ? code : null;
        }
        if (parts[1].equals("EV_ABS") && (parts[2].equals("ABS_HAT0X") || parts[2].equals("ABS_HAT0Y"))) {
            try {
                int value = (int)Long.parseLong(parts[3], 16);
                if (value == 0) return null;
                return parts[2].equals("ABS_HAT0X") ? (value < 0 ? "DPAD_LEFT" : "DPAD_RIGHT")
                    : (value < 0 ? "DPAD_UP" : "DPAD_DOWN");
            } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
    public static String editorCode(String text) {
        return isController(text) || text.startsWith("KEY_") ? canonical(text) : "KEY_" + text;
    }
}
