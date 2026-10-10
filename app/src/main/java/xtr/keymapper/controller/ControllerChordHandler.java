package xtr.keymapper.controller;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import xtr.keymapper.keymap.element.ControllerChord;
import xtr.keymapper.mouse.HeldAimHandler.TouchSink;

/** Ownership is decided only on trigger DOWN and persists until trigger UP. */
public final class ControllerChordHandler {
    public static final int FIRST_POINTER_ID = 59;
    private final List<ControllerChord> configs;
    private final TouchSink sink;
    private final int firstPointer;
    private final Set<String> held = new HashSet<>();
    private final Map<String, Integer> claimed = new HashMap<>();

    public ControllerChordHandler(List<ControllerChord> configs, TouchSink sink) {
        this(configs, sink, FIRST_POINTER_ID);
    }
    public ControllerChordHandler(List<ControllerChord> configs, TouchSink sink, int firstPointer) {
        this.configs = configs; this.sink = sink; this.firstPointer = firstPointer;
    }
    public boolean event(String code, int action) {
        if (action == 1) {
            if (!held.add(code)) return true;
            for (int i = 0; i < configs.size(); i++) {
                ControllerChord c = configs.get(i);
                if (c.trigger.equals(code) && held.contains(c.modifier)) {
                    claimed.put(code, i); touch(i, 1); return true;
                }
            }
        } else {
            if (!held.remove(code)) return true;
            Integer index = claimed.remove(code);
            if (index != null) { touch(index, 0); return true; }
        }
        return configs.stream().anyMatch(c -> c.modifier.equals(code));
    }
    private void touch(int index, int action) {
        ControllerChord c = configs.get(index);
        sink.inject(c.x + c.offset, c.y + c.offset, action, firstPointer + index);
    }
    public void reset() {
        for (int index : claimed.values()) touch(index, 0);
        claimed.clear(); held.clear();
    }
}
