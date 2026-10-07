package xtr.keymapper.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xtr.keymapper.keymap.element.AimKey;
import xtr.keymapper.mouse.HeldAimHandler.TouchSink;

/** Absolute right-stick aiming. No mouse emulation and no left-stick touch conversion. */
public final class ControllerAimHandler {
    public static final int FIRST_POINTER_ID = 50;
    private final List<AimKey> configs;
    private final TouchSink sink;
    private final float width, height;
    private final Map<String, State> held = new LinkedHashMap<>();
    private float x, y;
    private static final class State {
        final AimKey config; final int pointer;
        float x, y;
        State(AimKey config, int pointer) { this.config = config; this.pointer = pointer; x = config.x; y = config.y; }
    }
    public ControllerAimHandler(List<AimKey> configs, TouchSink sink, float width, float height) {
        this.configs = configs; this.sink = sink; this.width = width; this.height = height;
    }
    public static float normalize(int value, int min, int max, int center) {
        if (min >= max || center <= min || center >= max) return 0;
        double extent = value < center ? (double) center - min : (double) max - center;
        return (float)Math.max(-1, Math.min(1, ((double)value - center) / extent));
    }
    public static float[] vector(float x, float y, float deadZone, float sx, float sy, boolean invertY) {
        double length = Math.hypot(x, y);
        if (length <= deadZone || length == 0) return new float[]{0, 0};
        double magnitude = (Math.min(1, length) - deadZone) / (1 - deadZone);
        float vx = (float)(x / length * magnitude * sx);
        float vy = (float)(y / length * magnitude * sy * (invertY ? -1 : 1));
        double scaled = Math.hypot(vx, vy);
        if (scaled > 1) { vx /= scaled; vy /= scaled; }
        return new float[]{vx, vy};
    }
    public synchronized boolean trigger(String code, int action) {
        for (int i = 0; i < configs.size(); i++) {
            AimKey config = configs.get(i);
            if (!config.stick || !config.code.equals(code)) continue;
            if (action == 1 && !held.containsKey(code)) {
                State state = new State(config, FIRST_POINTER_ID + i);
                held.put(code, state);
                sink.inject(state.x, state.y, 1, state.pointer);
                move(state); // Includes stick already displaced when the shoulder goes down.
            } else if (action == 0) {
                State state = held.remove(code);
                if (state != null) sink.inject(state.x, state.y, 0, state.pointer);
            }
            return true;
        }
        return false;
    }
    public synchronized void axes(float x, float y) {
        this.x = x; this.y = y;
        // Latest held action owns the stick. Other held pointers remain independently releasable.
        State latest = null;
        for (State state : held.values()) latest = state;
        if (latest != null) move(latest);
    }
    private void move(State state) {
        AimKey c = state.config;
        float[] v = vector(x, y, c.deadZone, c.xSensitivity, c.ySensitivity, c.invertY);
        float dx = v[0] * c.radius, dy = v[1] * c.radius;
        // Shrink the whole vector at an edge, preserving direction rather than clipping each axis.
        float fit = 1;
        if (dx > 0) fit = Math.min(fit, (width - 1 - c.x) / dx);
        else if (dx < 0) fit = Math.min(fit, -c.x / dx);
        if (dy > 0) fit = Math.min(fit, (height - 1 - c.y) / dy);
        else if (dy < 0) fit = Math.min(fit, -c.y / dy);
        fit = Math.max(0, fit);
        float nextX = c.x + dx * fit, nextY = c.y + dy * fit;
        if (nextX != state.x || nextY != state.y) {
            state.x = nextX; state.y = nextY;
            sink.inject(state.x, state.y, 2, state.pointer);
        }
    }
    public synchronized void reset() {
        for (State state : held.values()) sink.inject(state.x, state.y, 0, state.pointer);
        held.clear(); x = y = 0;
    }
}
