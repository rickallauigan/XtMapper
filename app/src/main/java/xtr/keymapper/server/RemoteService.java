package xtr.keymapper.server;

import android.app.ApplicationErrorReport;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import xtr.keymapper.ActivityObserver;
import xtr.keymapper.BuildConfig;
import xtr.keymapper.IRemoteService;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.OnKeyEventListener;
import xtr.keymapper.R;
import xtr.keymapper.Utils;
import xtr.keymapper.activity.MainActivity;
import xtr.keymapper.databinding.CursorBinding;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.server.event.KeyEventHandler;

public class RemoteService extends IRemoteService.Stub {
    private String currentDevice = "";
    private final RuntimeRequestGate<InputService> runtimeRequests = new RuntimeRequestGate<>();
    private OnKeyEventListener mOnKeyEventListener;
    boolean isWaylandClient = false;
    private ActivityObserverService activityObserverService;
    String nativeLibraryDir = System.getProperty("java.library.path");
    private View cursorView;
    private int TYPE_SECURE_SYSTEM_OVERLAY;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private WindowManager mWindowManager;
    protected Context context;
    public static final String TAG = "xtmapper-server";
    boolean startedFromShell = false;

    public RemoteService(Context context) {
        loadLibraries();
        this.context = context;
        init();
    }

    private WindowManager getWindowManager(int displayId) {
        WindowManager windowManager;
        try {
            windowManager = prepareCursorOverlayWindow(displayId);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
            windowManager = context.getSystemService(WindowManager.class);
        }

        return windowManager;
    }


    public void init() {
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        try {
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            nativeLibraryDir = ai.nativeLibraryDir;
            new Thread(this::start_getevent, "XtMapper input reader").start();
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, e.getMessage(), e);
            throw new RuntimeException(e);
        }

        Looper.getMainLooper().getThread().setUncaughtExceptionHandler((t, e) -> {
            try {
                ApplicationErrorReport.CrashInfo crashInfo = new ApplicationErrorReport.CrashInfo(e);

                new ProcessBuilder("am", "start", "-a", "android.intent.action.MAIN", "-n",
                        new ComponentName(context, MainActivity.class).flattenToString(),
                        "--es", "data",
                        crashInfo.exceptionMessage + "\n" +
                                crashInfo.exceptionClassName + "\n" +
                                crashInfo.stackTrace + "\n" +
                                crashInfo.throwClassName + "\n" +
                                crashInfo.throwFileName + "\n" +
                                crashInfo.throwLineNumber + "\n" +
                                crashInfo.throwMethodName).start();

            } catch (Exception ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
            System.exit(1);
        });
    }

    private void addCursorView() {
        if (cursorView == null) return;

        if(cursorView.isAttachedToWindow()) {
            cursorView.setVisibility(View.VISIBLE);
        } else {
            WindowManager.LayoutParams params = Utils.getPointerLayoutParams(TYPE_SECURE_SYSTEM_OVERLAY);
            try {
                mWindowManager.addView(cursorView, params);
            } catch (IllegalStateException e) { // A14 QPR3 issue https://gist.github.com/RikkaW/be3fe4178903702c54ec73b2fc1187fe
                cursorView = null;
                Log.e(TAG, e.getMessage(), e);
            }
        }
    }


    public WindowManager prepareCursorOverlayWindow(int displayId) throws NoSuchMethodException, NoSuchFieldException, IllegalAccessException, InvocationTargetException {
        final WindowManager windowManager;
        TYPE_SECURE_SYSTEM_OVERLAY = WindowManager.LayoutParams.class.getField("TYPE_SECURE_SYSTEM_OVERLAY").getInt(null);

        Display display = context.getSystemService(DisplayManager.class).getDisplay(displayId);
        this.context = this.context.createDisplayContext(display);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            this.context = this.context.createWindowContext(display, TYPE_SECURE_SYSTEM_OVERLAY, null);
        }
        /*
        * We obtained an instance of WindowManager configured to show windows on the given display.
        * Now call getSystemService(Class) on the returned window context.
        */
        windowManager = this.context.getSystemService(WindowManager.class);

        LayoutInflater layoutInflater = this.context.getSystemService(LayoutInflater.class);
        this.context.setTheme(R.style.Theme_XtMapper);
        cursorView = CursorBinding.inflate(layoutInflater).getRoot();


        Binder sWindowToken = new Binder();
        Method setDefaultTokenMethod = windowManager.getClass().getMethod("setDefaultToken", IBinder.class);
        setDefaultTokenMethod.invoke(windowManager, sWindowToken);
        return windowManager;
    }

    public static void loadLibraries() {
        System.loadLibrary("mouse_read");
        System.loadLibrary("mouse_cursor");
        System.loadLibrary("touchpad_direct");
        System.loadLibrary("touchpad_relative");
    }

    /**
     * Executes getevent command and processes the output or reads from stdin if wayland client
     */
    private void start_getevent() { try {
        final BufferedReader getevent;
        if (isWaylandClient) {
            getevent = new BufferedReader(new InputStreamReader(System.in));
        } else {
            getevent = Utils.geteventStream(nativeLibraryDir);
        }
        String line;
        while ((line = getevent.readLine()) != null) {
            String[] data = line.split(":"); // split a string like "/dev/input/event2: EV_REL REL_X ffffffff"
            // The reader only produces frames. Runtime selection and handling happen
            // on the same looper as initialization, teardown, pause, resume and reload.
            final String frame = line;
            RuntimeRequestGate.Frame<InputService> target = runtimeRequests.captureFrame();
            mHandler.post(() -> handleInputFrame(target, data, frame));
        }
    } catch (Exception e){
        Log.e(TAG, e.getMessage(), e);
    }}

    private void handleInputFrame(RuntimeRequestGate.Frame<InputService> target, String[] data, String line) {
        if (!runtimeRequests.accepts(target) || !addNewDevices(data)) return;
        InputService current = target.owner;
        if (current == null) return;
        try {
            if (isWaylandClient && data[0].contains("wl_pointer")) current.onWaylandMouseEvent(data[1]);
            KeyEventHandler keys = current.getKeyEventHandler();
            if (!current.stopEvents) {
                if (!current.onControllerEvent(data[0], data[1])) keys.handleEvent(data[1]);
            } else keys.handleKeyboardShortcutEvent(data[1]);
            if (mOnKeyEventListener != null) mOnKeyEventListener.onKeyEvent(line);
        } catch (RemoteException error) {
            Log.e(TAG, "Input callback unavailable", error);
        }
    }

    /**
     * @param data split output of getevent command
     * @return true if output is valid for processing
     */
    private boolean addNewDevices(String[] data) {
        String[] input_event;
        if (data.length != 2) return false;
        String evdev = data[0];

        input_event = data[1].split("\\s+");
        if (isWaylandClient) return true;
        if (input_event.length >= 3 && input_event[1].equals("EV_REL")
                && (input_event[2].equals("REL_X") || input_event[2].equals("REL_Y"))) {
            InputService current = runtimeRequests.currentOwner();
            if (current == null) currentDevice = evdev;
            else if (!currentDevice.equals(evdev) || !current.isMouseDeviceCurrent(evdev)) {
                if (current.openDevice(evdev) >= 0) currentDevice = evdev;
            }
        }
        return true;
    }

    private final DeathRecipient mStartServerDeathRecipient = this::stopServer;

    /**
     * Called by client to start the remote server.
     *
     * @param profile  The keymap profile
     * @param keymapConfig Some configurations
     * @param cb  The instance used to callback to remote service
     * @param screenHeight Screen resolution (vertical)
     * @param screenWidth  Screen resolution (horizontal)
     */
    @Override
    public void startServer(KeymapProfile profile, KeymapConfig keymapConfig, IRemoteServiceCallback cb, int screenWidth, int screenHeight, int displayId) throws RemoteException {
        long request = runtimeRequests.next();
        mHandler.post(() -> runtimeRequests.commit(request, () -> {
            runtimeRequests.discardOwned(runtimeRequests.takeOwned(request), this::stopRuntime);
            if (!runtimeRequests.isCurrent(request)) return;
            if (cb != null) try {
                cb.asBinder().linkToDeath(mStartServerDeathRecipient, 0);
            } catch (RemoteException deadClient) {
                return;
            }
            mWindowManager = getWindowManager(displayId);
            if (keymapConfig.pointerMode != KeymapConfig.POINTER_SYSTEM) addCursorView();
            else cursorView = null;

            final InputService candidate;
            try {
                candidate = new InputService(profile, keymapConfig, cb, screenWidth, screenHeight,
                    cursorView, isWaylandClient, displayId);
                if (!isWaylandClient) {
                    candidate.setMouseLock(true);
                    candidate.openDevice(currentDevice);
                }
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
            if (!runtimeRequests.publishOwned(request, candidate)) {
                runtimeRequests.discardOwned(candidate, this::stopRuntime);
                return;
            }
            if (!runtimeRequests.isCurrent(request)) {
                runtimeRequests.discardOwned(candidate, this::stopRuntime);
                return;
            }
            // Launch app/game
            if (!profile.packageName.equals(BuildConfig.APPLICATION_ID) && keymapConfig.disableAutoProfiling) {
                Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(profile.packageName);
                if (launchIntent != null && launchIntent.getComponent() != null) try {
                    new ProcessBuilder("am", "start", "-a", "android.intent.action.MAIN", "-n",
                            launchIntent.getComponent().flattenToString()).start();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }));
    }

    @Override
    public void destroy() {
        stopServer();
    }

    @Override
    public void stopServer() {
        long request = runtimeRequests.next();
        mHandler.post(() -> runtimeRequests.commit(request, () -> stopServer(request, true)));
    }

    private void stopServer(long request, boolean exitProcess) {
        runtimeRequests.discardOwned(runtimeRequests.takeOwned(request), this::stopRuntime);
        if (!startedFromShell && exitProcess && runtimeRequests.isCurrent(request)) System.exit(0);
    }

    private void stopRuntime(InputService stopping, boolean releaseSharedResources) {
        stopping.stopEvents = true;
        try { stopping.stop(); }
        finally { if (releaseSharedResources) releaseSharedRuntimeResources(stopping); }
    }

    private void releaseSharedRuntimeResources(InputService stopping) {
        // Stale Java state is always released. Shared cursor/native resources belong to
        // the successor when one exists and must not be touched by stale cleanup.
        try {
            if (stopping.getCallback() != null) stopping.getCallback().disablePointer();
            stopping.hideCursor();
        } catch (Exception error) {
            Log.w(TAG, "Cursor callback unavailable during stop", error);
        }
        if (!isWaylandClient) {
            try { stopping.stopMouse(); }
            finally {
                try { stopping.stopTouchpad(); }
                finally { stopping.destroyUinputDev(); }
            }
        }
        if (stopping.getCallback() != null) {
            try { stopping.getCallback().asBinder().unlinkToDeath(mStartServerDeathRecipient, 0); }
            catch (java.util.NoSuchElementException ignored) { /* client already died */ }
        }
    }
    private final DeathRecipient mKeyEventListenerDeathRecipient = () -> mOnKeyEventListener = null;

    @Override
    public void registerOnKeyEventListener(OnKeyEventListener l) throws RemoteException {
        l.asBinder().linkToDeath(mKeyEventListenerDeathRecipient, 0);
        mOnKeyEventListener = l;
    }

    @Override
    public void unregisterOnKeyEventListener(OnKeyEventListener l)  {
        if (l != null) l.asBinder().unlinkToDeath(mKeyEventListenerDeathRecipient, 0);
        mOnKeyEventListener = null;
    }

    @Override
    public void registerActivityObserver(ActivityObserver callback) {
        if (activityObserverService != null)
            activityObserverService.stop();
        activityObserverService = new ActivityObserverService(callback);
    }

    @Override
    public void unregisterActivityObserver(ActivityObserver callback) {
        if (activityObserverService != null)
            activityObserverService.stop();
        activityObserverService = null;
    }

    /**
     * Used to temporary stop the keymapping.
     */
    @Override
    public void pauseMouse(){
        long request = runtimeRequests.next();
        mHandler.post(() -> runtimeRequests.commit(request, () -> pauseRuntime(runtimeRequests.currentOwner())));
    }

    private void pauseRuntime(InputService current) {
        if (current != null && !current.stopEvents) current.pauseResumeKeymap();
    }

    @Override
    public void resumeMouse(){
        long request = runtimeRequests.next();
        mHandler.post(() -> runtimeRequests.commit(request, () -> {
            InputService current = runtimeRequests.currentOwner();
            if (current != null && current.stopEvents) current.pauseResumeKeymap();
        }));
    }

    /**
     * Used to refresh by requesting new keymap from the user app
     */
    @Override
    public void reloadKeymap() {
        // Reload belongs to the current lifecycle request; it must not cancel a
        // preceding resume posted by the editor's existing exit path.
        long request = runtimeRequests.currentRequest();
        mHandler.post(() -> runtimeRequests.commit(request, () -> {
            InputService current = runtimeRequests.currentOwner();
            if (current != null) current.reloadKeymap();
        }));
    }

    @Override
    public boolean isMappingPaused() {
        RuntimeRequestGate.Frame<InputService> frame = runtimeRequests.captureFrame();
        return (frame.owner == null || frame.owner.isPausedAndReleased()) && runtimeRequests.accepts(frame);
    }

    @Override
    public boolean isActive() {
        return runtimeRequests.currentOwner() != null;
    }

}
