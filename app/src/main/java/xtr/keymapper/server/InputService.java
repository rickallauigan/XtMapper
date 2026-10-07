package xtr.keymapper.server;

import static xtr.keymapper.InputEventCodes.BTN_MIDDLE;
import static xtr.keymapper.InputEventCodes.BTN_MOUSE;
import static xtr.keymapper.InputEventCodes.BTN_RIGHT;
import static xtr.keymapper.InputEventCodes.REL_WHEEL;
import static xtr.keymapper.InputEventCodes.REL_X;
import static xtr.keymapper.InputEventCodes.REL_Y;

import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.server.event.KeyEventHandler;
import xtr.keymapper.server.event.MouseEventHandler;

public class InputService implements IInputInterface {
    private final MouseEventHandler mouseEventHandler;
    private final KeyEventHandler keyEventHandler;
    private volatile xtr.keymapper.controller.ControllerDeviceMonitor controllers;
    private KeymapConfig keymapConfig;
    private KeymapProfile keymapProfile;
    private final Input input;
    private final java.util.Map<Integer, float[]> activeTouches = new java.util.HashMap<>();
    private volatile boolean acceptingTouches = true;
    private final int screenWidth, screenHeight;
    public static final int UP = 0, DOWN = 1, MOVE = 2;
    private final IRemoteServiceCallback mCallback;
    volatile boolean stopEvents = false;
    private final boolean isWaylandClient;
    private final int touchpadInputMode;
    private final View cursorView;
    private final int currentPointerMode;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean supportsAbsEvents = false;

    public InputService(KeymapProfile profile,
                        KeymapConfig keymapConfig,
                        IRemoteServiceCallback mCallback,
                        int screenWidth,
                        int screenHeight,
                        View cursorView,
                        boolean isWaylandClient,
                        int displayId) throws RemoteException {
        this.screenWidth = screenWidth; this.screenHeight = screenHeight;
        input = new Input(displayId);
        profile.scale(screenWidth, screenHeight);
        this.keymapProfile = profile;
        this.keymapConfig = keymapConfig;
        this.mCallback = mCallback;
        this.isWaylandClient = isWaylandClient;
        this.cursorView = cursorView;
        this.currentPointerMode = keymapConfig.pointerMode;
        if (currentPointerMode != KeymapConfig.POINTER_OVERLAY) {
            initMouseCursor(screenWidth, screenHeight);
            // Reduce visibility of system pointer
            cursorSetX(0);
            cursorSetY(0);
        }
        if (currentPointerMode != KeymapConfig.POINTER_SYSTEM) showCursor();

        // Disable touchpad features for wayland client
        this.touchpadInputMode = isWaylandClient ? KeymapConfig.TOUCHPAD_DISABLED : keymapConfig.touchpadInputMode;

        if (touchpadInputMode == KeymapConfig.TOUCHPAD_DIRECT)
            startTouchpadDirect();
        else if (touchpadInputMode == KeymapConfig.TOUCHPAD_RELATIVE)
            startTouchpadRelative();

        mouseEventHandler = new MouseEventHandler(this);
        mouseEventHandler.init(screenWidth, screenHeight);

        keyEventHandler = new KeyEventHandler(this);
        keyEventHandler.init();
        mouseEventHandler.activateDefaultMode();
        startControllers();
    }

    public synchronized void injectEvent(float x, float y, int action, int pointerId) {
        if (!acceptingTouches || !Float.isFinite(x) || !Float.isFinite(y)) return;
        if (action == DOWN) {
            if (activeTouches.containsKey(pointerId)) return;
            if (activeTouches.size() >= 10) return;
            activeTouches.put(pointerId, new float[]{x, y});
        } else {
            if (!activeTouches.containsKey(pointerId)) return;
            if (action == UP) activeTouches.remove(pointerId);
            else activeTouches.put(pointerId, new float[]{x, y});
        }
        switch (action) {
            case UP:
                input.injectTouch(MotionEvent.ACTION_UP, pointerId, 0.0f, x, y);
                break;
            case DOWN:
                input.injectTouch(MotionEvent.ACTION_DOWN, pointerId, 1.0f, x, y);
                break;
            case MOVE:
                input.injectTouch(MotionEvent.ACTION_MOVE, pointerId, 0.0f, x, y);
                break;
        }
    }

    @Override
    public void injectHoverEvent(float x, float y, int pointerId) {
        if(input.noPointersDown() && currentPointerMode == KeymapConfig.POINTER_OVERLAY)
            input.injectTouch(MotionEvent.ACTION_HOVER_MOVE, pointerId, 1.0f, x, y);
    }

    @Override
    public void injectRightClickEvent(float x, float y, int pointerId, boolean pressed) {
        input.injectMouseClick(pointerId, x, y, pressed, MotionEvent.BUTTON_SECONDARY);
    }

    @Override
    public void injectMiddleClickEvent(float x, float y, int pointerId, boolean pressed) {
        input.injectMouseClick(pointerId, x, y, pressed, MotionEvent.BUTTON_TERTIARY);
    }

    public void injectScroll(float x, float y, float value) {
        input.onScrollEvent(x, y, value);
    }

    @Override
    public void pauseResumeKeymap() {
        stopEvents = !stopEvents;
        if (stopEvents) stop();
        else {
            acceptingTouches = true;
            mouseEventHandler.init(screenWidth, screenHeight);
            keyEventHandler.init();
            mouseEventHandler.activateDefaultMode();
            startControllers();
        }
        if (!isWaylandClient) {
            setMouseLock(!stopEvents);
        }
    }

    public KeymapConfig getKeymapConfig() {
        return keymapConfig;
    }

    public KeyEventHandler getKeyEventHandler() {
        return keyEventHandler;
    }

    public MouseEventHandler getMouseEventHandler() {
        return mouseEventHandler;
    }

    @Override
    public KeymapProfile getKeymapProfile() {
        return keymapProfile;
    }

    public IRemoteServiceCallback getCallback() {
        return mCallback;
    }

    public void moveCursorX(int x) {
        if (cursorView != null) {
            mHandler.post(() -> cursorView.setX(x));
        } else {
            try {
                mCallback.setCursorX(x);
            } catch (RemoteException ignored) {
            }
        }
        if (currentPointerMode != KeymapConfig.POINTER_OVERLAY) {
            // To avoid conflict with touch input when moving virtual pointer
            if (input.noPointersDown()) cursorSetX(x);
        }
    }

    public void moveCursorY(int y) {
        if (cursorView != null) {
            mHandler.post(() -> cursorView.setY(y));
        } else {
            try {
                mCallback.setCursorY(y);
            } catch (RemoteException ignored) {
            }
        }
        if (currentPointerMode != KeymapConfig.POINTER_OVERLAY) {
            // To avoid conflict with touch input when moving virtual pointer
            if (input.noPointersDown()) cursorSetY(y);
        }
    }

    @Override
    public void hideCursor() {
        if (cursorView != null) {
            mHandler.post(() -> cursorView.setVisibility(View.GONE));
        } else {
            try {
                mCallback.disablePointer();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void showCursor() {
        if (cursorView != null) {
            mHandler.post(() -> cursorView.setVisibility(View.VISIBLE));
        } else {
            try {
                mCallback.enablePointer();
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public void reloadKeymap() {
        try {
            KeymapProfile nextProfile = mCallback.requestKeymapProfile();
            KeymapConfig nextConfig = mCallback.requestKeymapConfig();
            stop();
            nextProfile.scale(screenWidth, screenHeight);
            keymapProfile = nextProfile;
            keymapConfig = nextConfig;
            if (!stopEvents) {
                acceptingTouches = true;
                mouseEventHandler.init(screenWidth, screenHeight);
                keyEventHandler.init();
                mouseEventHandler.activateDefaultMode();
                startControllers();
            }
        } catch (Exception e) {
            stop();
            Log.e(RemoteService.TAG, e.getMessage(), e);
        }
    }

    public void stop() {
        if (controllers != null) { controllers.stop(); controllers = null; }
        keyEventHandler.stop();
        mouseEventHandler.stop();
        synchronized (this) {
            acceptingTouches = false;
            // Includes fixed keys, D-pad, swipe/macro pointers and pending mode transitions.
            activeTouches.forEach((id, point) -> input.injectTouch(MotionEvent.ACTION_UP, id, 0, point[0], point[1]));
            activeTouches.clear();
        }
    }

    public void stopTouchpad() {
        if (touchpadInputMode == KeymapConfig.TOUCHPAD_DIRECT)
            stopTouchpadDirect();
        else if (touchpadInputMode == KeymapConfig.TOUCHPAD_RELATIVE)
            stopTouchpadRelative();
    }

    private void startControllers() {
        if (isWaylandClient || !keymapProfile.hasControllerBindings()) return;
        controllers = new xtr.keymapper.controller.ControllerDeviceMonitor(this, screenWidth, screenHeight);
        controllers.start();
    }

    public boolean onControllerEvent(String path, String line) {
        xtr.keymapper.controller.ControllerDeviceMonitor current = controllers;
        return current != null && current.event(path, line);
    }

    public native int openDevice(String device);
    public native boolean isMouseDeviceCurrent(String device);
    public native void stopMouse();
    
    // mouse cursor created with uinput in mouse_cursor.cpp
    public native void cursorSetX(int x);
    public native void cursorSetY(int y);
    private native int initMouseCursor(int width, int height);
    public native void destroyUinputDev();

    public native void setMouseLock(boolean lock);

    // touchpad_direct.cpp
    private native void startTouchpadDirect();
    public native void stopTouchpadDirect();

    private native void startTouchpadRelative();
    public native void stopTouchpadRelative();

    /*
     * Called from native code to send mouse event to client
     */
    public void sendMouseEvent(int code, int value) {
        if (code == -1) {
            if (acceptingTouches && !stopEvents) mouseEventHandler.resetAfterMouseDisconnect();
        } else if (!stopEvents) mouseEventHandler.handleEvent(code, value);
    }

    public void onWaylandMouseEvent(String line) {
        String[] input_event = line.split("\\s+");
        int value = Integer.parseInt(input_event[3]);
        switch (input_event[2]) {
            case "ABS_X":
                supportsAbsEvents = true;
                if (!mouseEventHandler.mouseAimActive)
                    mouseEventHandler.evAbsX(value);
                break;
            case "ABS_Y":
                if (!mouseEventHandler.mouseAimActive)
                    mouseEventHandler.evAbsY(value);
                break;
            case "REL_WHEEL":
                mouseEventHandler.handleEvent(REL_WHEEL, value);
                break;
            case "BTN_LEFT":
                mouseEventHandler.handleEvent(BTN_MOUSE, value);
                break;
            case "BTN_RIGHT":
                mouseEventHandler.handleEvent(BTN_RIGHT, value);
                break;
            case "BTN_MIDDLE":
                mouseEventHandler.handleEvent(BTN_MIDDLE, value);
                break;
            case "REL_X":
                if (mouseEventHandler.mouseAimActive || !supportsAbsEvents)
                    mouseEventHandler.handleEvent(REL_X, value);
                break;
            case "REL_Y":
                if (mouseEventHandler.mouseAimActive || !supportsAbsEvents)
                    mouseEventHandler.handleEvent(REL_Y, value);
                break;
        }
    }

}
