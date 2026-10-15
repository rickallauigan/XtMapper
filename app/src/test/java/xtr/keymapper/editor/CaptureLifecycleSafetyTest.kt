package xtr.keymapper.editor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.view.InputDevice
import android.view.View
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.shadows.ShadowSettings
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import xtr.keymapper.IRemoteService
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.server.RemoteServiceHelper

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CaptureLifecycleSafetyTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun tick(ms: Long=2300)=Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    private fun service(broken: () -> Boolean={false})=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)) { _,m,_ ->
        if(broken() && m.name in listOf("isActive","isMappingPaused")) throw RemoteException("binder disconnected")
        when(m.name){"isActive","isMappingPaused"->true;"asBinder"->Binder();else->null}
    } as IRemoteService
    private fun focus(view: View,value: Boolean)=ReflectionHelpers.callInstanceMethod<Void>(view.viewTreeObserver,"dispatchOnWindowFocusChange",ClassParameter.from(Boolean::class.javaPrimitiveType!!,value))

    @Test fun nullRemoteAfterReadyClosesOnceAndCancelsGesture() {
        var remote: IRemoteService?=service();var closes=0
        val session=CoordinateCaptureSession(100f,100f);session.select(1f,2f);session.start();session.begin(20f,30f)
        val guard=CaptureInterruptionGuard(context,View(context),Handler(Looper.getMainLooper()),false,{remote},{session.capturing},{closes++;session.stop()})
        try {
            tick();remote=null;tick(350)
            assertEquals(1,closes);assertFalse(session.capturing);assertEquals(1f to 2f,session.point)
            guard.interrupt();tick(1000);assertEquals(1,closes)
        } finally {guard.dispose()}
    }
    @Test fun thrownBinderFailureClosesOnce() {
        var broken=false;var closes=0;val remote=service{broken}
        val guard=CaptureInterruptionGuard(context,View(context),Handler(Looper.getMainLooper()),false,{remote},{true},{closes++})
        try {tick();broken=true;tick(350);assertEquals(1,closes);tick(1000);assertEquals(1,closes)}finally{guard.dispose()}
    }
    @Test fun initialMissingRemoteDoesNotPretendReadyOrCloseUntilConnected() {
        var remote: IRemoteService?=null;var closes=0
        val guard=CaptureInterruptionGuard(context,View(context),Handler(Looper.getMainLooper()),false,{remote},{false},{closes++})
        try {tick();assertEquals(0,closes);remote=service();tick(350);remote=null;tick(350);assertEquals(1,closes)}finally{guard.dispose()}
    }
    @Test fun frameworkFocusDispatchInterruptsOnlyAfterFocusWasAcquired() {
        val view=View(context);var closes=0;val remote=service()
        val guard=CaptureInterruptionGuard(context,view,Handler(Looper.getMainLooper()),false,{remote},{true},{closes++})
        try {focus(view,false);assertEquals(0,closes);focus(view,true);focus(view,false);assertEquals(1,closes);focus(view,false);assertEquals(1,closes)}finally{guard.dispose()}
    }
    @Test fun actualInputManagerRemovalListenerIgnoresOtherDevicesAndClosesForController() {
        val manager=context.getSystemService(InputManager::class.java);val shadow=Shadows.shadowOf(manager)
        fun add(id: Int,sources: Int) {
            val d=ShadowInputDevice.makeInputDeviceNamed("test-$id")
            ReflectionHelpers.setField(d,"mId",id);ReflectionHelpers.setField(d,"mSources",sources);shadow.addInputDevice(d)
        }
        add(0,InputDevice.SOURCE_KEYBOARD);add(91,InputDevice.SOURCE_GAMEPAD);add(92,InputDevice.SOURCE_KEYBOARD)
        assertTrue("Controller registered with framework",InputDevice.getDeviceIds().contains(91));assertTrue(InputDevice.getDevice(91)!!.supportsSource(InputDevice.SOURCE_GAMEPAD))
        var closes=0;val remote=service()
        val guard=CaptureInterruptionGuard(context,View(context),Handler(Looper.getMainLooper()),false,{remote},{true},{closes++})
        try {
            // Robolectric removeInputDevice only changes its map; it does not notify listeners.
            // Deliver Android's real device-change callback, which dispatches the registered listener.
            // Keep the built-in device at index zero, as Android's removal loop expects.
            val global=ReflectionHelpers.callStaticMethod<Any>(Class.forName("android.hardware.input.InputManagerGlobal"),"getInstance")
            ReflectionHelpers.callInstanceMethod<Void>(global,"onInputDevicesChanged",ClassParameter.from(IntArray::class.java,intArrayOf(0,0,91,0)))
            tick(1);assertEquals(0,closes)
            assertTrue("Guard tracked controller",ReflectionHelpers.getField<Set<Int>>(guard,"controllerIds").contains(91))
            ReflectionHelpers.callInstanceMethod<Void>(global,"onInputDevicesChanged",ClassParameter.from(IntArray::class.java,intArrayOf(0,0)))
            tick(1);assertEquals(1,closes);tick();assertEquals(1,closes)
        } finally {guard.dispose()}
    }
    @Test fun permissionRevocationCancelsWatchers() {
        ShadowSettings.setCanDrawOverlays(true);var closes=0;val remote=service()
        val guard=CaptureInterruptionGuard(context,View(context),Handler(Looper.getMainLooper()),true,{remote},{true},{closes++})
        try {tick();ShadowSettings.setCanDrawOverlays(false);tick(350);assertEquals(1,closes);guard.dispose();tick(1000);assertEquals(1,closes)}finally{guard.dispose()}
    }
    @Test fun configurationBroadcastClosesRealEditorRestoresGestureAndReleasesLease() {
        val remote=service();ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",remote)
        val before=context.getSharedPreferences("profiles",0).all.toMap()
        val activity=Robolectric.buildActivity(EditorActivity::class.java,Intent(context,EditorActivity::class.java).putExtra(EditorActivity.CAPTURE,true)).create().start().resume()
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            val editor=ReflectionHelpers.getField<EditorUI>(activity.get(),"editor")
            val session=ReflectionHelpers.getField<CoordinateCaptureSession>(editor,"captureSession")
            session.select(1f,2f);session.start();session.begin(20f,30f)
            context.sendBroadcast(Intent(Intent.ACTION_CONFIGURATION_CHANGED));Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(session.capturing);assertEquals(1f to 2f,session.point);assertFalse(EditorSessionGuard.active)
            editor.hideView();assertFalse(EditorSessionGuard.active);assertEquals(before,context.getSharedPreferences("profiles",0).all)
        } finally {activity.pause().stop().destroy();ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",null)}
    }
    @Test fun serviceConnectionDisconnectClosesReadyEditorWithoutResumingDeadRemote() {
        val calls=mutableListOf<String>();val base=service()
        val remote=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)){_,m,a->calls+=m.name;m.invoke(base,*(a?:emptyArray()))} as IRemoteService
        KeymapConfig(context).apply{disableAutoProfiling=true}.applySharedPrefs()
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",remote)
        val activity=Robolectric.buildActivity(EditorActivity::class.java,Intent(context,EditorActivity::class.java).putExtra(EditorActivity.CAPTURE,true)).create().start().resume()
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(EditorSessionGuard.active)
            val editor=ReflectionHelpers.getField<EditorUI>(activity.get(),"editor")
            assertTrue(ReflectionHelpers.getField<androidx.compose.runtime.MutableState<Boolean>>(editor,"visualReady\$delegate").value)
            val connection=RemoteServiceHelper.RemoteServiceConnection { s -> ReflectionHelpers.callInstanceMethod<Void>(activity.get(),"onConnection",ClassParameter.from(IRemoteService::class.java,s)) }
            connection.onServiceDisconnected(ComponentName(context,"test"))
            assertFalse(EditorSessionGuard.active);assertFalse(ReflectionHelpers.getField<androidx.compose.runtime.MutableState<Boolean>>(editor,"visualReady\$delegate").value)
            assertFalse(calls.any{it=="resumeMouse"||it=="reloadKeymap"});editor.hideView();assertFalse(EditorSessionGuard.active)
        } finally {activity.pause().stop().destroy();ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",null)}
    }
}
