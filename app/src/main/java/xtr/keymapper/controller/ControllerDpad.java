package xtr.keymapper.controller;

import java.util.function.BiConsumer;

/** Hat sign changes release the old direction before pressing the new one. */
public final class ControllerDpad {
    private int x, y;
    private final BiConsumer<String, Integer> sink;
    public ControllerDpad(BiConsumer<String, Integer> sink) { this.sink = sink; }
    public void axis(boolean horizontal, int raw) {
        int next = Integer.compare(raw, 0), previous = horizontal ? x : y;
        if (next == previous) return;
        if (previous != 0) sink.accept(code(horizontal, previous), 0);
        if (next != 0) sink.accept(code(horizontal, next), 1);
        if (horizontal) x = next; else y = next;
    }
    private static String code(boolean horizontal, int value) {
        return horizontal ? (value < 0 ? "DPAD_LEFT" : "DPAD_RIGHT") : (value < 0 ? "DPAD_UP" : "DPAD_DOWN");
    }
    public void reset() { axis(true, 0); axis(false, 0); }
}
