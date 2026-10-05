package xtr.keymapper.dpad;

import android.os.Handler;
import android.os.SystemClock;
import xtr.keymapper.keymap.element.Dpad;
import xtr.keymapper.server.IInputInterface;
import static xtr.keymapper.server.InputService.*;

/** Existing virtual joystick, with ordered transitions and repeat-safe held-key state. */
public class DpadHandler {
    private final Dpad config;
    private final int pointerId, delayMillis;
    private final Handler handler;
    private final boolean[] held = new boolean[4];
    private final String[] codes;
    private IInputInterface input;
    private boolean pointerDown, stopped;
    private long nextEventAt;

    public DpadHandler(Dpad config, int pointerId, Handler handler, int delayMillis) {
        this.config = config; this.pointerId = pointerId; this.handler = handler;
        this.delayMillis = delayMillis;
        codes = new String[]{config.keycodes.Up, config.keycodes.Down, config.keycodes.Left, config.keycodes.Right};
    }

    public void setInterface(IInputInterface input) { this.input = input; }

    public synchronized void handleEvent(String code, int action) {
        if (stopped || (action != DOWN && action != UP)) return;
        int index = -1;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(code)) index = i;
        if (index < 0 || held[index] == (action == DOWN)) return;
        held[index] = action == DOWN;
        boolean any = held[0] || held[1] || held[2] || held[3];
        if (!pointerDown && any) {
            pointerDown = true;
            enqueue(config.xOfCenter, config.yOfCenter, DOWN);
        }
        if (!any) {
            pointerDown = false;
            enqueue(config.xOfCenter, config.yOfCenter, UP);
            return;
        }
        int dx = (held[3] ? 1 : 0) - (held[2] ? 1 : 0);
        int dy = (held[1] ? 1 : 0) - (held[0] ? 1 : 0);
        float radius = config.radius;
        if (dx != 0 && dy != 0) radius /= (float) Math.sqrt(2);
        enqueue(config.xOfCenter + dx * radius, config.yOfCenter + dy * radius, MOVE);
    }

    private void enqueue(float x, float y, int action) {
        nextEventAt = Math.max(nextEventAt + 1, SystemClock.uptimeMillis() + delayMillis);
        handler.postAtTime(() -> {
            synchronized (DpadHandler.this) {
                if (!stopped) input.injectEvent(x, y, action, pointerId);
            }
        }, this, nextEventAt);
    }

    public synchronized boolean hasHeldDirection() {
        return !stopped && (held[0] || held[1] || held[2] || held[3]);
    }

    public synchronized void stop() {
        stopped = true;
        handler.removeCallbacksAndMessages(this);
        // InputService releases active pointers after every event producer has stopped.
    }
}
