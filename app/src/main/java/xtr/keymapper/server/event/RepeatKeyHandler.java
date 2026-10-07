package xtr.keymapper.server.event;

import android.os.Handler;
import java.util.HashMap;
import java.util.Map;
import xtr.keymapper.keymap.element.Key;
import xtr.keymapper.mouse.HeldAimHandler.TouchSink;

/** Optional fixed-binding tap repetition. UP/reset cancels both phases and releases owned touches. */
public final class RepeatKeyHandler {
    private final Handler handler;
    private final TouchSink sink;
    private final Map<Integer, Pulse> held = new HashMap<>();
    private final class Pulse implements Runnable {
        final Key key; final int pointer;
        boolean down;
        Pulse(Key key, int pointer) { this.key = key; this.pointer = pointer; }
        @Override public void run() {
            synchronized (RepeatKeyHandler.this) {
                if (held.get(pointer) != this) return;
                down = !down;
                sink.inject(key.x + key.offset, key.y + key.offset, down ? 1 : 0, pointer);
                int pressMs = Math.min(40, key.repeatIntervalMs / 2);
                handler.postDelayed(this, down ? pressMs : key.repeatIntervalMs - pressMs);
            }
        }
        void cancel() {
            handler.removeCallbacks(this);
            if (down) sink.inject(key.x + key.offset, key.y + key.offset, 0, pointer);
            down = false;
        }
    }
    public RepeatKeyHandler(Handler handler, TouchSink sink) { this.handler = handler; this.sink = sink; }
    public synchronized void event(Key key, int pointer, int action) {
        if (action == 1 && !held.containsKey(pointer)) {
            Pulse pulse = new Pulse(key, pointer); held.put(pointer, pulse); pulse.run();
        } else if (action == 0) {
            Pulse pulse = held.remove(pointer); if (pulse != null) pulse.cancel();
        }
    }
    public synchronized void reset() {
        for (Pulse pulse : held.values()) pulse.cancel();
        held.clear();
    }
}
