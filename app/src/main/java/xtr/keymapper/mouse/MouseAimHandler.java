package xtr.keymapper.mouse;

import static xtr.keymapper.InputEventCodes.BTN_EXTRA;
import static xtr.keymapper.InputEventCodes.BTN_MIDDLE;
import static xtr.keymapper.InputEventCodes.BTN_MOUSE;
import static xtr.keymapper.InputEventCodes.BTN_RIGHT;
import static xtr.keymapper.InputEventCodes.BTN_SIDE;
import static xtr.keymapper.InputEventCodes.REL_X;
import static xtr.keymapper.InputEventCodes.REL_Y;
import static xtr.keymapper.server.InputService.DOWN;
import static xtr.keymapper.server.InputService.MOVE;
import static xtr.keymapper.server.InputService.UP;

import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;

import xtr.keymapper.keymap.element.Camera;
import xtr.keymapper.keymap.element.MouseAimConfig;
import xtr.keymapper.server.IInputInterface;
import xtr.keymapper.server.pid.PointerId;

public class MouseAimHandler {

    private final MouseAimConfig config;
    private float currentX, currentY;
    private final RectF area = new RectF();
    private IInputInterface service;
    private final int pointerIdMouse = PointerId.pid1.id;
    private final int pointerIdAim = PointerId.pid2.id;
    private boolean pointerDown;
    private boolean automaticCamera;
    private boolean movementActive;
    private long lastMouseMotion;
    private Runnable finishCameraDrag;
    private int generation;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public MouseAimHandler(MouseAimConfig config){
        currentX = config.xCenter;
        currentY = config.yCenter;
        this.config = config;
    }

    public MouseAimHandler(Camera camera) {
        this(new MouseAimConfig(camera.x,
                camera.y,
                0,
                0,
                0,
                0,
                true,
                camera.xSensitivity,
                camera.ySensitivity,
                false));
        automaticCamera = camera.autoActive;
    }

    public void setInterface(IInputInterface input) {
        this.service = input;
    }

    public void setDimensions(int width, int height){
        if (config.width == 0 || config.height == 0) {
            // Reset pointer if jumping out of screenspace
            area.left = area.top = 0;
            area.right = width;
            area.bottom = height;
        } else {
            // An area around the center point
            area.left = currentX - config.width;
            area.right = currentX + config.width;
            area.top = currentY - config.height;
            area.bottom = currentY + config.height;
        }

    }

    public synchronized void resetPointer() {
        ++generation;
        mHandler.removeCallbacksAndMessages(null);
        if (pointerDown) service.injectEvent(currentX, currentY, UP, pointerIdAim);
        pointerDown = false;
        currentY = config.yCenter;
        currentX = config.xCenter;
        if (automaticCamera) return;
        final int token = generation;
        mHandler.postDelayed(() -> {
            synchronized (MouseAimHandler.this) {
                if (generation != token) return;
                pointerDown = true;
                service.injectEvent(currentX, currentY, DOWN, pointerIdAim);
            }
        }, service.getKeymapConfig().swipeDelayMs);
    }

    public synchronized void handleEvent(int code, int value, OnButtonClickListener listener) {
        if (automaticCamera && (code == REL_X || code == REL_Y)) {
            if (finishCameraDrag != null) mHandler.removeCallbacks(finishCameraDrag);
            if (!pointerDown) {
                currentX = config.xCenter;
                currentY = config.yCenter;
                pointerDown = true;
                service.injectEvent(currentX, currentY, DOWN, pointerIdAim);
            }
            lastMouseMotion = android.os.SystemClock.uptimeMillis();
            if (movementActive) scheduleCameraRelease(250);
        }
        switch (code) {
            case REL_X:
                currentX += calculateScaledX(value);
                if (automaticCamera) currentX = Math.max(area.left, Math.min(area.right - 1, currentX));
                else if (config.limitedBounds && (currentX > area.right || currentX < area.left))
                    resetPointer();
                if (pointerDown) service.injectEvent(currentX, currentY, MOVE, pointerIdAim);
                break;
            case REL_Y:
                currentY += calculateScaledY(value);
                if (automaticCamera) currentY = Math.max(area.top, Math.min(area.bottom - 1, currentY));
                else if (config.limitedBounds && (currentY > area.bottom || currentY < area.top))
                    resetPointer();
                if (pointerDown) service.injectEvent(currentX, currentY, MOVE, pointerIdAim);
                break;

            case BTN_MOUSE:
                service.injectEvent(config.xleftClick, config.yleftClick, value, pointerIdMouse);
                break;

            case BTN_SIDE:
            case BTN_MIDDLE:
            case BTN_EXTRA:
            case BTN_RIGHT:
                listener.onButtonClick(code, value);
                break;
        }
    }

    public float calculateScaledX(int value) {
        if (config.applyNonLinearScaling) {
            double dx = config.xCenter - currentX;
            double dy = config.yCenter - currentY;
            double distance = Math.hypot(dx, dy);

            double maxWidth = area.right - area.left;
            double minDistanceToApplyScaling = maxWidth / 20;
            if (distance > minDistanceToApplyScaling) {
                return config.xSensitivity * value * (float)Math.sqrt(minDistanceToApplyScaling / distance);
            } else {
                return value * config.xSensitivity;
            }
        } else if (config.xSensitivity != 1.0) {
            return value * config.xSensitivity;
        } else {
            return value;
        }
    }

    private float calculateScaledY(int value) {
        if (config.ySensitivity != 1.0) {
            return value * config.ySensitivity;
        } else {
            return value;
        }
    }

    public synchronized void setMovementActive(boolean active) {
        if (!automaticCamera || movementActive == active) return;
        movementActive = active;
        ++generation;
        if (finishCameraDrag != null) mHandler.removeCallbacks(finishCameraDrag);
        if (active && pointerDown) {
            long remaining = Math.max(0, 250 - (android.os.SystemClock.uptimeMillis() - lastMouseMotion));
            if (remaining == 0) stop();
            else scheduleCameraRelease(remaining);
        }
    }

    private void scheduleCameraRelease(long delayMs) {
        final int token = ++generation;
        finishCameraDrag = () -> {
            synchronized (MouseAimHandler.this) {
                if (movementActive && generation == token) stop();
            }
        };
        mHandler.postDelayed(finishCameraDrag, delayMs);
    }

    public synchronized void stop() {
        ++generation;
        mHandler.removeCallbacksAndMessages(null);
        if (pointerDown) service.injectEvent(currentX, currentY, UP, pointerIdAim);
        pointerDown = false;
    }

    public interface OnButtonClickListener {
        void onButtonClick(int code, int value);
    }
}
