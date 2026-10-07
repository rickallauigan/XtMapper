package xtr.keymapper.controller;

import xtr.keymapper.keymap.element.Camera;
import xtr.keymapper.mouse.HeldAimHandler.TouchSink;

/** Profile-driven stick velocity camera drag; skill aiming always has priority. */
public final class ControllerCameraHandler {
    public static final int POINTER_ID = 58;
    public static final float PIXELS_PER_SECOND = 600;
    private final Camera config;
    private final TouchSink sink;
    private final float width, height;
    private float x, y, vx, vy;
    private boolean down, suspended;
    public ControllerCameraHandler(Camera config, TouchSink sink, float width, float height) {
        this.config = config; this.sink = sink; this.width = width; this.height = height;
    }
    public void axes(float rawX, float rawY) {
        float[] vector = ControllerAimHandler.vector(rawX, rawY, config.deadZone, 1, 1, false);
        vx = vector[0]; vy = vector[1];
        if (vx == 0 && vy == 0) release();
    }
    public void suspend(boolean active) {
        suspended = active;
        if (active) release();
    }
    /** Caller supplies elapsed time; cap stalls to prevent a large jump after scheduling delays. */
    public void tick(float seconds) {
        if (suspended || (vx == 0 && vy == 0)) return;
        if (!down) {
            x = config.x; y = config.y; down = true;
            sink.inject(x, y, 1, POINTER_ID);
        }
        float dt = Math.max(0, Math.min(.05f, seconds));
        float nextX = Math.max(0, Math.min(width - 1, x + vx * PIXELS_PER_SECOND * config.xSensitivity * dt));
        float nextY = Math.max(0, Math.min(height - 1, y + vy * PIXELS_PER_SECOND * config.ySensitivity * dt));
        if (x != nextX || y != nextY) {
            x = nextX; y = nextY; sink.inject(x, y, 2, POINTER_ID);
        }
    }
    private void release() {
        if (down) sink.inject(x, y, 0, POINTER_ID);
        down = false;
    }
    public void reset() { release(); vx = vy = 0; suspended = false; }
}
