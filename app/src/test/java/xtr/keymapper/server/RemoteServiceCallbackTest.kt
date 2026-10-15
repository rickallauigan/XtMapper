package xtr.keymapper.server

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.RemoteException
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import xtr.keymapper.IRemoteService
import xtr.keymapper.editor.EditorActivity
import xtr.keymapper.editor.EditorService
import xtr.keymapper.keymap.KeymapConfig

/** Capture only service acquisition; helper consumers and ServiceConnection remain production code. */
@Implements(value=RemoteServiceHelper::class,isInAndroidSdk=false)
class CallbackAcquisitionShadow {
    companion object {
        var callback: RemoteServiceHelper.RootRemoteServiceCallback?=null
        var stopBeforeWindow=false
        class Captured: RuntimeException()
        @JvmStatic @Implementation
        fun getInstance(context: Context,cb: RemoteServiceHelper.RootRemoteServiceCallback) {
            callback=cb
            if(stopBeforeWindow) throw Captured()
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],shadows=[CallbackAcquisitionShadow::class],instrumentedPackages=["xtr.keymapper.server"])
class RemoteServiceCallbackTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun checkConsumer(request: () -> Unit,expected: String) {
        CallbackAcquisitionShadow.callback=null;CallbackAcquisitionShadow.stopBeforeWindow=false
        request()
        val cb=CallbackAcquisitionShadow.callback ?: error("Acquisition was not captured")
        val calls=mutableListOf<String>()
        var broken=false
        val remote=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)){_,method,_->
            if(method.name=="asBinder") return@newProxyInstance Binder()
            calls+=method.name
            if(broken) throw RemoteException("Disconnected")
            if(method.name=="isActive") true else null
        } as IRemoteService
        // A local Binder returns this interface through the actual Stub.asInterface path.
        val binder=Binder();binder.attachInterface(remote,"xtr.keymapper.IRemoteService")
        val connection=RemoteServiceHelper.RemoteServiceConnection(cb)
        connection.onServiceConnected(ComponentName(context,"test"),binder)
        assertEquals(listOf(expected),calls)
        connection.onServiceDisconnected(ComponentName(context,"test"))
        connection.onServiceDisconnected(ComponentName(context,"test"))
        assertEquals("Disconnect must not invoke stale service",listOf(expected),calls)
        cb.onConnection(null) // Initial unavailable service is also legal.
        assertEquals(listOf(expected),calls)
        broken=true
        cb.onConnection(remote) // Existing RemoteException handling must still work.
        assertEquals(listOf(expected,expected),calls)
    }
    @Test fun pauseCallbackAcceptsDisconnectAndUnavailableService() = checkConsumer({RemoteServiceHelper.pauseKeymap(context)},"pauseMouse")
    @Test fun resumeCallbackAcceptsDisconnectAndUnavailableService() = checkConsumer({RemoteServiceHelper.resumeKeymap(context)},"resumeMouse")
    @Test fun reloadCallbackRemainsNullSafe() = checkConsumer({RemoteServiceHelper.reloadKeymap(context)},"reloadKeymap")
    @Test fun overlayEditorConsumerAcceptsNullDisconnect() {
        KeymapConfig(context).apply{editorOverlay=true}.applySharedPrefs()
        val controller=Robolectric.buildService(EditorService::class.java).create()
        try {
            CallbackAcquisitionShadow.callback=null;CallbackAcquisitionShadow.stopBeforeWindow=true
            // Capture its real callback before onStartCommand opens an unrelated legacy overlay.
            assertThrows(CallbackAcquisitionShadow.Companion.Captured::class.java) {
                controller.get().onStartCommand(Intent(context,EditorService::class.java).putExtra(EditorActivity.PROFILE_NAME,"callback-test"),0,1)
            }
            val cb=CallbackAcquisitionShadow.callback ?: error("Editor callback not captured")
            val connection=RemoteServiceHelper.RemoteServiceConnection(cb)
            connection.onServiceDisconnected(ComponentName(context,"test"));connection.onServiceDisconnected(ComponentName(context,"test"))
            cb.onConnection(null)
        } finally {CallbackAcquisitionShadow.stopBeforeWindow=false;controller.destroy()}
    }
    @Test fun runIfActiveDoesNotRunOnDisconnect() {
        var runs=0
        checkConsumer({RemoteServiceHelper.runIfActive(context){runs++}},"isActive")
        assertEquals(1,runs)
    }
}
