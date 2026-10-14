package xtr.keymapper.server

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import xtr.keymapper.IRemoteService
import xtr.keymapper.TouchPointer
import xtr.keymapper.keymap.KeymapConfig
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class CursorStartupTest {
    @Test fun initialOverlayMigrationWithoutBoundActivityReturnsBeforeRemoteTeardown() {
        val pointer=Robolectric.buildService(TouchPointer::class.java).create().get()
        KeymapConfig(pointer).apply { pointerMode=KeymapConfig.POINTER_OVERLAY }.applySharedPrefs()
        var stopped=0
        var callbackReturned=false
        pointer.mService=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)) { _, method, _ ->
            if(method.name=="stopServer") {
                assertTrue("must not re-enter remote construction during synchronous callback",callbackReturned)
                stopped++
            }
            null
        } as IRemoteService
        assertNull(pointer.activityCallback)
        pointer.mCallback.enablePointer()
        assertEquals(0,stopped)
        assertEquals(KeymapConfig.POINTER_COMBINED,KeymapConfig(pointer).pointerMode)
        callbackReturned=true
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1,stopped)
    }
    @Test fun disappearanceOfRemoteBeforeQueuedMigrationIsSafe() {
        val pointer=Robolectric.buildService(TouchPointer::class.java).create().get()
        KeymapConfig(pointer).apply { pointerMode=KeymapConfig.POINTER_OVERLAY }.applySharedPrefs()
        pointer.mCallback.enablePointer()
        pointer.mService=null
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(KeymapConfig.POINTER_COMBINED,KeymapConfig(pointer).pointerMode)
    }
}
