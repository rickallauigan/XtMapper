package xtr.keymapper.editor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.os.Handler
import android.provider.Settings
import android.view.InputDevice
import android.view.View
import androidx.core.content.ContextCompat
import xtr.keymapper.IRemoteService

/** Same editor pause lease; this helper only tears down foreground UI on interruption. */
internal class CaptureInterruptionGuard(private val context: Context, private val view: View,
    private val handler: Handler, private val overlay: Boolean, private val remote: () -> IRemoteService?,
    private val isCapturing: () -> Boolean, private val close: () -> Unit) {
    private var disposed = false
    private var hadRuntime = false
    private var sawFocus = false
    private val manager = context.getSystemService(InputManager::class.java)
    private val controllerIds = InputDevice.getDeviceIds().filter { id ->
        InputDevice.getDevice(id)?.let { it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK) } == true
    }.toMutableSet()
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { interrupt() }
    }
    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) {
            InputDevice.getDevice(id)?.let { if (it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK)) controllerIds.add(id) }
        }
        override fun onInputDeviceChanged(id: Int) {}
        override fun onInputDeviceRemoved(id: Int) { if (controllerIds.remove(id)) interrupt() }
    }
    private val focus = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (focused) sawFocus = true else if (sawFocus && isCapturing()) interrupt()
    }
    private val attachment = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) { interrupt() }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (disposed) return
            if (overlay && !Settings.canDrawOverlays(context)) { interrupt(); return }
            try {
                remote()?.let { service ->
                    val active = service.isActive
                    if (hadRuntime && !active || !service.isMappingPaused) { interrupt(); return }
                    hadRuntime = hadRuntime || active
                }
            } catch (_: Exception) { interrupt(); return }
            handler.postDelayed(this, 300)
        }
    }
    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        manager.registerInputDeviceListener(listener, handler)
        view.viewTreeObserver.addOnWindowFocusChangeListener(focus)
        view.addOnAttachStateChangeListener(attachment)
        // Initial pause acknowledgement may be asynchronous; allow EditorUI to obtain it first.
        handler.postDelayed(poll, 2200)
    }
    fun connection(active: Boolean) { hadRuntime = hadRuntime || active }
    fun interrupt() { if (!disposed) { dispose(); close() } }
    fun dispose() {
        if (disposed) return
        disposed = true
        handler.removeCallbacks(poll)
        context.unregisterReceiver(receiver)
        manager.unregisterInputDeviceListener(listener)
        view.removeOnAttachStateChangeListener(attachment)
        if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focus)
    }
}
