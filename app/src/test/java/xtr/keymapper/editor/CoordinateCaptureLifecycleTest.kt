package xtr.keymapper.editor

import android.content.Intent
import android.os.Binder
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import xtr.keymapper.IRemoteService
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.server.RemoteServiceHelper
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CoordinateCaptureLifecycleTest {
    @Test fun captureInterruptionReleasesLeaseWithoutRawListenerProfileOrSettingsWrites() {
        val context=RuntimeEnvironment.getApplication()
        KeymapConfig(context).apply{disableAutoProfiling=false}.applySharedPrefs()
        val original=setOf("APPLICATION com.mobile.legends","SCREENSIZE 2280 1080","BTN_TL2 100 200 0 150")
        val prefs=KeymapProfiles(context).sharedPref;prefs.edit().putStringSet("G8",original).commit()
        val settings=context.getSharedPreferences("settings",0).all.toMap();val calls=mutableListOf<String>();val binder=Binder()
        val remote=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)){_,m,_->
            calls+=m.name;when(m.name){"asBinder"->binder;"isMappingPaused","isActive"->true;else->null}
        } as IRemoteService
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",remote)
        val intent=Intent(context,EditorActivity::class.java).putExtra(EditorActivity.PROFILE_NAME,"G8").putExtra(EditorActivity.CAPTURE,true)
        val activity=Robolectric.buildActivity(EditorActivity::class.java,intent).create().start().resume()
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(EditorSessionGuard.active);assertTrue(calls.contains("pauseMouse"));assertFalse(calls.contains("registerOnKeyEventListener"))
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF));Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(EditorSessionGuard.active);assertEquals(original,prefs.getStringSet("G8",null))
            assertEquals(settings,context.getSharedPreferences("settings",0).all)
            assertFalse(calls.any{it in listOf("startServer","reloadKeymap","resumeMouse")})
        } finally {activity.pause().stop().destroy();ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",null)}
    }
    @Test fun standaloneCaptureNeedsNoProfileAndDestructionCleansUp() {
        val context=RuntimeEnvironment.getApplication()
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",null)
        val intent=Intent(context,EditorActivity::class.java).putExtra(EditorActivity.CAPTURE,true)
        val activity=Robolectric.buildActivity(EditorActivity::class.java,intent).create().start().resume()
        assertTrue(EditorSessionGuard.active)
        activity.pause().stop().destroy();assertFalse(EditorSessionGuard.active)
        assertTrue(context.getSharedPreferences("captured_targets_v1",0).all.isEmpty())
    }
    @Test fun runtimeStopPermissionRevocationAndExplicitDisposalRemoveWatchersExactlyOnce() {
        val context=RuntimeEnvironment.getApplication()
        for(reason in listOf("stop","permission","disconnect")) {
            var active=true;var closes=0
            val remote=Proxy.newProxyInstance(IRemoteService::class.java.classLoader,arrayOf(IRemoteService::class.java)){_,m,_->
                when(m.name){"isActive"->active;"isMappingPaused"->true;else->null}
            } as IRemoteService
            if(reason=="permission") org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(true)
            val guard=CaptureInterruptionGuard(context,android.view.View(context),android.os.Handler(Looper.getMainLooper()),reason=="permission",{remote},{true},{closes++})
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2300))
            assertEquals(0,closes)
            if(reason=="permission") org.robolectric.shadows.ShadowSettings.setCanDrawOverlays(false)
            else if(reason=="disconnect") guard.interrupt() else active=false
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350))
            assertEquals(1,closes);guard.dispose();guard.interrupt();assertEquals(1,closes)
        }
    }
}
