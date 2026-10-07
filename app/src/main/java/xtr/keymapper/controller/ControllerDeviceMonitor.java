package xtr.keymapper.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import xtr.keymapper.server.IInputInterface;
import java.util.function.Supplier;

/** Uses the existing getevent stream; native probes supply capabilities/ranges and removal evidence. */
public final class ControllerDeviceMonitor {
    private static final class NativeProbe {
        static { System.loadLibrary("controller_device"); }
        static void ensureLoaded() {}
    }
    public static native long[] probe(String path);
    private final Supplier<Map<String, long[]>> discovery;
    public static native long[] identity(String path);
    public static final class NodeDiscovery implements Supplier<Map<String, long[]>> {
        private record Evidence(long[] identity, long[] capabilities) {}
        private final Map<String, Evidence> cached = new HashMap<>();
        private final Supplier<java.util.List<String>> paths;
        private final java.util.function.Function<String, long[]> identities, capabilities;
        public NodeDiscovery() {
            this(() -> {
                File[] nodes = new File("/dev/input").listFiles();
                java.util.List<String> paths = new java.util.ArrayList<>();
                if (nodes != null) for (File file : nodes)
                    if (file.getName().matches("event[0-9]+")) paths.add(file.getAbsolutePath());
                return paths;
            }, path -> { NativeProbe.ensureLoaded(); return identity(path); }, ControllerDeviceMonitor::probe);
        }
        public NodeDiscovery(Supplier<java.util.List<String>> paths,
                java.util.function.Function<String, long[]> identities,
                java.util.function.Function<String, long[]> capabilities) {
            this.paths = paths; this.identities = identities; this.capabilities = capabilities;
        }
        public void invalidate(String path) { cached.remove(path); }
        @Override public Map<String, long[]> get() {
            Map<String, long[]> found = new HashMap<>();
            Set<String> present = new HashSet<>();
            for (String path : paths.get()) {
                long[] id = identities.apply(path);
                if (id == null) continue;
                present.add(path);
                Evidence old = cached.get(path);
                if (old == null || old.identity[0] != id[0] || old.identity[1] != id[1]) {
                    old = new Evidence(id.clone(), capabilities.apply(path)); cached.put(path, old);
                }
                if (old.capabilities != null) found.put(path, old.capabilities);
            }
            cached.keySet().retainAll(present);
            return found;
        }
    }
    private final Map<String, Device> devices = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final IInputInterface input;
    private final float width, height;
    private boolean running;
    private String owner;
    private long lastFrame;
    private final Runnable frame = new Runnable() {
        @Override public void run() {
            synchronized (ControllerDeviceMonitor.this) {
                if (!running) return;
                long now = android.os.SystemClock.uptimeMillis();
                Device current = devices.get(owner);
                if (current != null && current.camera != null) current.camera.tick((now - lastFrame) / 1000f);
                lastFrame = now; handler.postDelayed(this, 16);
            }
        }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            synchronized (ControllerDeviceMonitor.this) {
                if (!running) return;
                scan(); handler.postDelayed(this, 250);
            }
        }
    };
    private final class Device {
        final long[] info;
        final ControllerAimHandler aim;
        final ControllerDpad dpad;
        final ControllerCameraHandler camera;
        final Set<String> pressed = new HashSet<>();
        int rx, ry;
        boolean dirty;
        Device(long[] info) {
            this.info = info; rx = (int)info[4]; ry = (int)info[7];
            aim = new ControllerAimHandler(input.getKeymapProfile().aimKeys, input::injectEvent, width, height);
            var config = input.getKeymapProfile().camera;
            camera = config != null && config.stick ? new ControllerCameraHandler(config, input::injectEvent, width, height) : null;
            dpad = new ControllerDpad(this::button);
            axes();
        }
        void axes() {
            float x = ControllerAimHandler.normalize(rx, (int)info[2], (int)info[3], center(info[2], info[3]));
            float y = ControllerAimHandler.normalize(ry, (int)info[5], (int)info[6], center(info[5], info[6]));
            aim.axes(x, y);
            if (camera != null) camera.axes(x, y);
            dirty = false;
        }
        void button(String code, int action) {
            if (action == 1 ? !pressed.add(code) : !pressed.remove(code)) return;
            boolean aimingTrigger = input.getKeymapProfile().aimKeys.stream().anyMatch(c -> c.stick && c.code.equals(code));
            if (aimingTrigger && action == 1 && camera != null) camera.suspend(true);
            if (aim.trigger(code, action)) {
                if (camera != null) camera.suspend(aim.isActive());
                return;
            }
            try { input.getKeyEventHandler().handleEvent(" EV_KEY " + code + (action == 1 ? " DOWN" : " UP")); }
            catch (android.os.RemoteException error) { Log.e("XtMapper", "Controller key callback failed", error); }
        }
        void reset() {
            if (camera != null) camera.reset();
            dpad.reset();
            for (String code : new HashSet<>(pressed)) button(code, 0);
            aim.reset(); pressed.clear();
            if (camera != null) camera.reset();
        }
    }
    private static int center(long min, long max) { return (int)(min + (max - min + 1) / 2); }
    public ControllerDeviceMonitor(IInputInterface input, float width, float height) {
        this(input, width, height, new NodeDiscovery());
    }
    public ControllerDeviceMonitor(IInputInterface input, float width, float height, Supplier<Map<String, long[]>> discovery) {
        this.input = input; this.width = width; this.height = height; this.discovery = discovery;
    }
    public synchronized void start() {
        running = true; scan(); handler.postDelayed(poll, 250);
        if (input.getKeymapProfile().camera != null && input.getKeymapProfile().camera.stick) {
            lastFrame = android.os.SystemClock.uptimeMillis(); handler.postDelayed(frame, 16);
        }
    }
    private void scan() {
        Set<String> present = new HashSet<>();
        for (Map.Entry<String, long[]> entry : discovery.get().entrySet()) {
            String path = entry.getKey(); long[] info = entry.getValue();
            present.add(path);
            Device old = devices.get(path);
            if (old != null && (old.info[0] != info[0] || old.info[1] != info[1])) {
                old.reset(); devices.remove(path); if (path.equals(owner)) owner = null;
            }
            if (!devices.containsKey(path)) {
                devices.put(path, new Device(info));
                Log.i("XtMapper", "Controller detected: " + path + " RX=" + info[2] + ".." + info[3] + " RY=" + info[5] + ".." + info[6]);
            }
        }
        for (String path : new HashSet<>(devices.keySet())) if (!present.contains(path)) {
            devices.remove(path).reset(); if (path.equals(owner)) owner = null;
            Log.i("XtMapper", "Controller disconnected: " + path);
        }
    }
    /** true consumes controller traffic from generic keyboard/mouse routing, never from Android. */
    public synchronized boolean event(String path, String line) {
        if (!running) return false;
        Device device = devices.get(path);
        if (device == null) {
            String[] pending = line.trim().split("\\s+");
            if (pending.length < 3 || !pending[0].equals("EV_KEY")
                    || !ControllerBindings.isController(ControllerBindings.canonical(pending[1]))) return false;
            // A first button can arrive before the periodic scan after reconnect.
            // Probe now; do not let generic keys create a touch that this device cannot later release.
            scan(); device = devices.get(path);
            if (device == null) return true;
        }
        if (owner == null) owner = path;
        if (!path.equals(owner)) return true; // One active physical controller owns this profile.
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 3) return true;
        String type = parts[0], code = ControllerBindings.canonical(parts[1]), value = parts[2];
        if (type.equals("EV_KEY") && ControllerBindings.isController(code)) {
            if (device.dirty) device.axes();
            if (value.equals("DOWN")) device.button(code, 1);
            else if (value.equals("UP")) device.button(code, 0);
        } else if (type.equals("EV_ABS")) {
            int raw;
            try { raw = (int)Long.parseLong(value, 16); } catch (NumberFormatException error) { return true; }
            switch (code) {
                case "ABS_RX": device.rx = raw; device.dirty = true; break;
                case "ABS_RY": device.ry = raw; device.dirty = true; break;
                case "ABS_HAT0X": device.dpad.axis(true, raw); break;
                case "ABS_HAT0Y": device.dpad.axis(false, raw); break;
                // ABS_X/Y remain native. ABS_Z/RZ do not duplicate digital trigger actions.
            }
        } else if (type.equals("EV_SYN")) {
            if (code.equals("SYN_DROPPED")) {
                device.reset(); devices.remove(path); owner = null;
                if (discovery instanceof NodeDiscovery nodes) nodes.invalidate(path);
            }
            else if (code.equals("SYN_REPORT") && device.dirty) device.axes();
        }
        return true;
    }
    public synchronized void stop() {
        running = false; handler.removeCallbacks(poll); handler.removeCallbacks(frame);
        for (Device device : devices.values()) device.reset();
        devices.clear(); owner = null;
    }
}
