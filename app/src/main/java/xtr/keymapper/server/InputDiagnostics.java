package xtr.keymapper.server;

import android.util.Log;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import xtr.keymapper.BuildConfig;

/** Explicit debug-only tracing, disabled by default. No profile/device mutation. */
public final class InputDiagnostics {
    private InputDiagnostics() {}
    private static final Method GET = propertyGetter();
    private static Method propertyGetter() {
        try { return Class.forName("android.os.SystemProperties").getMethod("getBoolean", String.class, boolean.class); }
        catch (ReflectiveOperationException ignored) { return null; }
    }
    private static volatile boolean tracing;
    private static volatile long nextCheck;
    private static boolean enabled() {
        if (!BuildConfig.DEBUG) return false;
        long now = android.os.SystemClock.uptimeMillis();
        if (now < nextCheck) return tracing;
        synchronized (InputDiagnostics.class) {
            if (now >= nextCheck) {
                tracing = readEnabled(); nextCheck = now + 1000;
            }
            return tracing;
        }
    }
    private static boolean readEnabled() {
        if (!BuildConfig.DEBUG) return false;
        if (Boolean.getBoolean("xtmapper.traceInput")) return true;
        try { return GET != null && (boolean)GET.invoke(null, "debug.xtmapper.input_trace", false); }
        catch (ReflectiveOperationException ignored) { return false; }
    }
    public static void controller(String line) {
        if (enabled()) Log.d("XtMapperInputTrace", "controller " + line.trim());
    }
    public static void touch(int logical, int action, int count) {
        if (enabled()) Log.d("XtMapperInputTrace", "logical=" + logical + " action=" + action + " active=" + count);
    }
    public static void motion(MotionEvent event) {
        if (!enabled()) return;
        StringBuilder text = new StringBuilder("motion action=").append(event.getActionMasked())
            .append(" index=").append(event.getActionIndex()).append(" down=").append(event.getDownTime())
            .append(" time=").append(event.getEventTime()).append(" pointers=");
        for (int i=0;i<event.getPointerCount();i++) text.append(event.getPointerId(i)).append(':').append(event.getPressure(i)).append(',');
        Log.d("XtMapperInputTrace", text.toString());
    }
}
