package xtr.keymapper.server.event;

import static xtr.keymapper.InputEventCodes.BTN_EXTRA;
import static xtr.keymapper.InputEventCodes.BTN_MIDDLE;
import static xtr.keymapper.InputEventCodes.BTN_MOUSE;
import static xtr.keymapper.InputEventCodes.BTN_RIGHT;
import static xtr.keymapper.InputEventCodes.BTN_SIDE;
import static xtr.keymapper.InputEventCodes.REL_WHEEL;
import static xtr.keymapper.InputEventCodes.REL_X;
import static xtr.keymapper.InputEventCodes.REL_Y;
import static xtr.keymapper.server.InputService.MOVE;

import android.os.RemoteException;
import android.util.Log;

import java.util.Objects;

import xtr.keymapper.keymap.KeymapConfig;
import xtr.keymapper.mouse.MouseAimHandler;
import xtr.keymapper.mouse.HeldAimHandler;
import xtr.keymapper.mouse.MousePinchZoom;
import xtr.keymapper.mouse.MouseWalkHandler;
import xtr.keymapper.mouse.MouseWheelZoom;
import xtr.keymapper.keymap.KeymapProfile;
import xtr.keymapper.keymap.element.Key;
import xtr.keymapper.server.IInputInterface;
import xtr.keymapper.server.RemoteService;
import xtr.keymapper.server.pid.PointerId;

public class MouseEventHandler {
    float sensitivity;
    float scroll_speed_multiplier;
    private MousePinchZoom pinchZoom;
    private MouseWheelZoom scrollZoomHandler;
    public static final int pointerId = PointerId.pid1.id;
    private MouseAimHandler mouseAimHandler;
    private MouseAimHandler mouseCameraHandler;
    private Key rightClick;
    private Key leftClick;
    private HeldAimHandler heldAim;
    private boolean leftMappedDown, rightMappedDown;
    int x1 = 100, y1 = 100;
    int width; int height;
    private final IInputInterface mInput;
    boolean pointer_down;
    public boolean mouseAimActive = false;
    private boolean mouseWalkActive = false;
    private MouseAimHandler mouseAimOrCameraHandler;
    private MouseWalkHandler mouseWalkHandler;

    public boolean triggerMouseAim() {
        return triggerMouseAimOrCamera(mouseAimHandler);
    }


    public void triggerCamera() {
        triggerMouseAimOrCamera(mouseCameraHandler);
    }

    private boolean triggerMouseAimOrCamera(MouseAimHandler instance) {
        if (instance == null) return false;
        if (mouseAimOrCameraHandler != null && mouseAimOrCameraHandler != instance && mouseAimActive) {
            mouseAimOrCameraHandler.stop();
            mouseAimActive = false;
        }
        mouseAimOrCameraHandler = instance;
        if (instance != null) {
            mouseAimActive = !mouseAimActive;
            if (mouseAimActive) {
                if (mouseWalkActive) stopMouseWalk();
                instance.resetPointer();
                // Notifying user that shooting mode was activated
                try {
                    mInput.getCallback().alertMouseAimActivated();
                } catch (RemoteException e) {
                    Log.e(RemoteService.TAG, e.getMessage(), e);
                }
                mInput.hideCursor();
            } else {
                instance.stop();
                mInput.showCursor();
            }
            return true;
        }
        return false;
    }

    public MouseEventHandler(IInputInterface mInput) {
        this.mInput = mInput;
    }

    public void init(){
        init(width, height);
    }

    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        KeymapProfile profile = mInput.getKeymapProfile();
        if (profile.mouseAimConfig != null)
            mouseAimHandler = new MouseAimHandler(profile.mouseAimConfig);
        if (profile.camera != null)
            mouseCameraHandler = new MouseAimHandler(profile.camera);
        if (profile.mouseWalk != null)
            mouseWalkHandler = new MouseWalkHandler(profile.mouseWalk);

        this.rightClick = profile.rightClick;
        this.leftClick = profile.leftClick;
        heldAim = new HeldAimHandler(profile.aimKeys, mInput::injectEvent, width, height);

        if (mouseAimHandler != null) {
            mouseAimHandler.setInterface(mInput);
            mouseAimHandler.setDimensions(width, height);
        }
        if (mouseCameraHandler != null) {
            mouseCameraHandler.setInterface(mInput);
            mouseCameraHandler.setDimensions(width, height);
        }

        if (mouseWalkHandler != null) {
            mouseWalkHandler.setInterface(mInput);
            mouseWalkHandler.setDimensions(width, height);
        }

        KeymapConfig keymapConfig = mInput.getKeymapConfig();
        if (keymapConfig.ctrlMouseWheelZoom)
            scrollZoomHandler = new MouseWheelZoom(mInput);

        sensitivity = keymapConfig.mouseSensitivity;
        scroll_speed_multiplier = keymapConfig.scrollSpeed;
    }

    private void movePointerX() {
        if (mouseWalkActive) mouseWalkHandler.onCursorPosition(x1, y1);
        mInput.moveCursorX(x1);
    }

    private void movePointerY() {
        if (mouseWalkActive) mouseWalkHandler.onCursorPosition(x1, y1);
        mInput.moveCursorY(y1);
    }

    private void startMouseWalk() {
        // Stop mouse aim/camera prior
        if (mouseAimOrCameraHandler != null && mouseAimActive) {
            triggerMouseAimOrCamera(mouseAimOrCameraHandler);
        }
        // Makes sure that pointer is up before starting
        mouseWalkHandler.resetPointer();
        mouseWalkActive = true;
    }

    private void stopMouseWalk() {
        mouseWalkActive = false;
        mouseWalkHandler.stop();
    }

    public void activateDefaultMode() {
        if (mInput.getKeymapProfile().camera != null && mInput.getKeymapProfile().camera.autoActive)
            triggerCamera();
    }

    public synchronized void setMovementActive(boolean active) {
        if (mouseCameraHandler != null) mouseCameraHandler.setMovementActive(active);
    }

    public synchronized boolean handleAimTrigger(String code, int action) {
        boolean wasActive = heldAim != null && heldAim.isActive();
        boolean handled = heldAim != null && heldAim.trigger(code, action);
        if (!handled) return false;
        if (!wasActive && heldAim.isActive()) {
            if (mouseAimActive && mouseAimOrCameraHandler != null) mouseAimOrCameraHandler.stop();
            if (mouseWalkActive) stopMouseWalk();
            mInput.hideCursor();
        } else if (wasActive && !heldAim.isActive()) {
            if (mouseAimActive && mouseAimOrCameraHandler != null) mouseAimOrCameraHandler.resetPointer();
            else mInput.showCursor();
        }
        return true;
    }

    private boolean handleRightClick(int value) {
        if (rightClick != null) {
            if (value == 1 && !rightMappedDown) {
                rightMappedDown = true;
                mInput.injectEvent(rightClick.x, rightClick.y, 1, PointerId.pid3.id);
            } else if (value == 0 && rightMappedDown) {
                rightMappedDown = false;
                mInput.injectEvent(rightClick.x, rightClick.y, 0, PointerId.pid3.id);
            }
            return true;
        }
        boolean aimShortcut = mInput.getKeymapConfig().rightClickMouseAim
                || Objects.equals(mInput.getKeymapConfig().mouseAimShortcutKey, "KEY_RMB");
        if (mouseWalkHandler != null) {
            if (value == 1) {
                if (mouseWalkActive) stopMouseWalk(); else startMouseWalk();
            }
            return true;
        }
        if (aimShortcut && mouseAimHandler != null) {
            if (value == 1) triggerMouseAim();
            return true;
        }
        return false;
    }

    public synchronized void handleEvent(int code, int value) {
        if (code == BTN_RIGHT && handleAimTrigger("BTN_RIGHT", value)) return;
        if (code == BTN_MOUSE && handleAimTrigger("BTN_MOUSE", value)) return;
        if (heldAim != null && heldAim.isActive() && (code == REL_X || code == REL_Y)) {
            heldAim.move(code == REL_X, value);
            return;
        }
        if (code == BTN_MOUSE && leftClick != null) {
            if (value == 1 && !leftMappedDown) {
                leftMappedDown = true;
                mInput.injectEvent(leftClick.x, leftClick.y, 1, pointerId);
            } else if (value == 0 && leftMappedDown) {
                leftMappedDown = false;
                mInput.injectEvent(leftClick.x, leftClick.y, 0, pointerId);
            }
            return;
        }
        if (mouseAimOrCameraHandler != null && mouseAimActive
                && (heldAim == null || !heldAim.isActive())) {
            // CAMERA owns motion, while legacy MOUSE_AIM also owns its configured left click.
            if (code == REL_X || code == REL_Y || (code == BTN_MOUSE && mouseAimOrCameraHandler == mouseAimHandler)) {
                mouseAimOrCameraHandler.handleEvent(code, value, this::handleMouseEvent);
                return;
            }
        }
        handleMouseEvent(code, value);
    }

    private void handleMouseEvent(int code, int value) {
        KeymapConfig keymapConfig = mInput.getKeymapConfig();
        if (mInput.getKeyEventHandler().ctrlKeyPressed && pointer_down)
            if (keymapConfig.ctrlDragMouseGesture) {
                if (pinchZoom != null) pointer_down = pinchZoom.handleEvent(code, value);
                return;
            }
        switch (code) {
            case REL_X: {
                value = (int) (value*sensitivity);
                if (value == 0) break;
                x1 += value;
                if (x1 > width || x1 < 0) x1 -= value;
                if (pointer_down) mInput.injectEvent(x1, y1, MOVE, pointerId);
                else mInput.injectHoverEvent(x1, y1, pointerId);
                break;
            }
            case REL_Y: {
                value = (int) (value*sensitivity);
                if (value == 0) break;
                y1 += value;
                if (y1 > height || y1 < 0) y1 -= value;
                if (pointer_down) mInput.injectEvent(x1, y1, MOVE, pointerId);
                else mInput.injectHoverEvent(x1, y1, pointerId);
                break;
            }
            case BTN_MOUSE:
                pointer_down = value == 1;
                if (mInput.getKeyEventHandler().ctrlKeyPressed && keymapConfig.ctrlDragMouseGesture) {
                    pinchZoom = new MousePinchZoom(mInput, x1, y1);
                    pinchZoom.handleEvent(code, value);
                } else mInput.injectEvent(x1, y1, value, pointerId);
                break;

            case BTN_RIGHT:
                if (!handleRightClick(value)) {
                    mInput.injectRightClickEvent(x1, y1, pointerId, value == 1);
                }
                break;

            case BTN_EXTRA:
            case BTN_SIDE:
            case BTN_MIDDLE:
                if (value == 1 && Objects.equals(mInput.getKeymapConfig().mouseAimShortcutKey, "KEY_MMB"))
                    triggerMouseAim();
                else if (value == 1 && mInput.getKeymapProfile().camera != null
                        && Objects.equals(mInput.getKeymapProfile().camera.triggerKeyCode, "KEY_MMB"))
                    triggerCamera();
                else
                    mInput.injectMiddleClickEvent(x1, y1, pointerId, value == 1);
                break;

            case REL_WHEEL:
                if (mInput.getKeyEventHandler().ctrlKeyPressed && keymapConfig.ctrlMouseWheelZoom)
                    scrollZoomHandler.onScrollEvent(value, x1, y1);
                else
                    mInput.injectScroll(x1, y1, value * scroll_speed_multiplier);
                break;
        }
        if (code == REL_X) movePointerX();
        if (code == REL_Y) movePointerY();
    }

    public void evAbsY(int y) {
        this.y1 = y;
        if (pointer_down) mInput.injectEvent(x1, y1, MOVE, pointerId);
        else mInput.injectHoverEvent(x1, y1, pointerId);
        movePointerY();
    }

    public void evAbsX(int x) {
        this.x1 = x;
        if (pointer_down) mInput.injectEvent(x1, y1, MOVE, pointerId);
        else mInput.injectHoverEvent(x1, y1, pointerId);
        movePointerX();
    }

    public synchronized void resetAfterMouseDisconnect() {
        stop();
        init(width, height);
        activateDefaultMode();
    }

    public synchronized void stop() {
        if (heldAim != null) heldAim.stop();
        if (mouseAimHandler != null) mouseAimHandler.stop();
        if (mouseCameraHandler != null) mouseCameraHandler.stop();
        if (mouseWalkActive) stopMouseWalk();
        if (leftMappedDown) mInput.injectEvent(leftClick.x, leftClick.y, 0, pointerId);
        if (rightMappedDown) mInput.injectEvent(rightClick.x, rightClick.y, 0, PointerId.pid3.id);
        if (pointer_down) mInput.injectEvent(x1, y1, 0, pointerId);
        leftMappedDown = rightMappedDown = pointer_down = mouseAimActive = mouseWalkActive = false;
        mouseAimOrCameraHandler = null;
        heldAim = null;
        scrollZoomHandler = null;
        pinchZoom = null;
        mouseAimHandler = null;
        mouseCameraHandler = null;
        mouseWalkHandler = null;
    }
}
