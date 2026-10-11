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
@Config(sdk = [35])
class VisualEditorLifecycleTest {
    @Test fun activityBackAndDestructionDiscardWithoutAutosaveOrSettingsChanges() {
        val context = RuntimeEnvironment.getApplication()
        KeymapConfig(context).apply { disableAutoProfiling = false }.applySharedPrefs()
        val saved = setOf("APPLICATION com.mobile.legends", "SCREENSIZE 2280 1080", "BTN_TL2 100 200 0 150")
        val prefs = KeymapProfiles(context).sharedPref; prefs.edit().putStringSet("G8",saved).commit()
        val settings = context.getSharedPreferences("settings",0).all.toMap()
        val calls = mutableListOf<String>(); val binder = Binder()
        val service = Proxy.newProxyInstance(IRemoteService::class.java.classLoader, arrayOf(IRemoteService::class.java)) { _, method, _ ->
            calls += method.name
            when (method.name) { "asBinder" -> binder; "isMappingPaused", "isActive" -> true; else -> null }
        } as IRemoteService
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",service)
        val intent = Intent(context,EditorActivity::class.java).putExtra(EditorActivity.PROFILE_NAME,"G8").putExtra(EditorActivity.VISUAL,true)
        val activity = Robolectric.buildActivity(EditorActivity::class.java,intent).create().start().resume()
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(EditorSessionGuard.active); assertTrue(calls.contains("pauseMouse"))
            activity.get().onBackPressed(); assertFalse(EditorSessionGuard.active)
            assertEquals(saved,prefs.getStringSet("G8",null)); assertEquals(settings,context.getSharedPreferences("settings",0).all)
            assertFalse(calls.contains("startServer")); assertFalse(calls.contains("reloadKeymap")); assertFalse(calls.contains("resumeMouse"))
        } finally {
            activity.pause().stop().destroy()
            ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java,"service",null)
        }
    }
}
