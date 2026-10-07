package xtr.keymapper.mouse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import xtr.keymapper.keymap.element.AimKey;

/** State only: mouse events remain owned by MouseEventHandler. Latest held trigger owns motion. */
public final class HeldAimHandler {
    public interface TouchSink { void inject(float x, float y, int action, int pointerId); }
    private static final int UP = 0, DOWN = 1, MOVE = 2;
    public static final int FIRST_POINTER_ID = 42; // after keyboard, mouse and three D-pads
    private final List<AimKey> configs;
    private final TouchSink sink;
    private final Map<String, State> held = new LinkedHashMap<>();
    private final float width, height;

    private static final class State {
        final AimKey config;
        final int pointerId;
        float dx, dy, x, y;
        State(AimKey config, int pointerId) {
            this.config = config; this.pointerId = pointerId; x = config.x; y = config.y;
        }
    }

    public HeldAimHandler(List<AimKey> configs, TouchSink sink, float width, float height) {
        this.configs = configs; this.sink = sink; this.width = width; this.height = height;
    }

    public boolean trigger(String code, int action) {
        for (int i = 0; i < configs.size(); i++) {
            AimKey config = configs.get(i);
            if (config.stick || !config.code.equals(code)) continue;
            if (action == DOWN && !held.containsKey(code)) {
                // Aliases for the same HUD control share one touch, so E + RMB cannot double-press it.
                State state = held.values().stream().filter(s -> s.config.x == config.x && s.config.y == config.y)
                        .findFirst().orElse(null);
                if (state == null) {
                    state = new State(config, FIRST_POINTER_ID + i);
                    sink.inject(state.x, state.y, DOWN, state.pointerId);
                }
                held.put(code, state);
            } else if (action == UP) {
                State state = held.remove(code);
                if (state != null && !held.containsValue(state))
                    sink.inject(state.x, state.y, UP, state.pointerId);
            }
            return true;
        }
        return false;
    }

    public boolean isActive() { return !held.isEmpty(); }

    public void move(boolean horizontal, int value) {
        if (held.isEmpty()) return;
        State state = new ArrayList<>(held.values()).get(held.size() - 1);
        if (horizontal) state.dx += value * state.config.xSensitivity;
        else state.dy += value * state.config.ySensitivity;
        double distance = Math.hypot(state.dx, state.dy);
        if (distance > state.config.radius) {
            float scale = (float) (state.config.radius / distance);
            state.dx *= scale; state.dy *= scale;
        }
        state.x = Math.max(0, Math.min(width - 1, state.config.x + state.dx));
        state.y = Math.max(0, Math.min(height - 1, state.config.y + state.dy));
        sink.inject(state.x, state.y, MOVE, state.pointerId);
    }

    public void stop() {
        held.values().stream().distinct().forEach(s -> sink.inject(s.x, s.y, UP, s.pointerId));
        held.clear();
    }
}
