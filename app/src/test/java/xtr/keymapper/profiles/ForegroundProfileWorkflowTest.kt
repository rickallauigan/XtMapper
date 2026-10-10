package xtr.keymapper.profiles

import android.content.Context
import android.hardware.input.InputManager
import android.os.Binder
import android.os.Looper
import android.view.InputDevice
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers
import xtr.keymapper.ActivityObserver
import xtr.keymapper.IRemoteService
import xtr.keymapper.TouchPointer
import xtr.keymapper.devices.*
import xtr.keymapper.keymap.*
import xtr.keymapper.server.RemoteServiceHelper
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundProfileWorkflowTest {
    private lateinit var pointer: TouchPointer
    private lateinit var profiles: KeymapProfiles
    private lateinit var observer: ActivityObserver
    private lateinit var session: ForegroundProfileSession
    private val started = mutableListOf<String?>()
    private var pauses = 0
    private val mlbb = "com.mobile.legends"

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("profiles", 0).edit().clear().commit()
        app.getSharedPreferences("device_mapping_v1", 0).edit().clear().commit()
        KeymapConfig(app).apply { disableAutoProfiling = false; showControls = true }.applySharedPrefs()
        val g8 = ShadowInputDevice.makeInputDeviceNamed("GameSir-G8")
        ReflectionHelpers.setField(g8, "mId", 99)
        ReflectionHelpers.setField(g8, "mDescriptor", "g8")
        ReflectionHelpers.setField(g8, "mIsExternal", true)
        ReflectionHelpers.setField(g8, "mSources", InputDevice.SOURCE_GAMEPAD)
        shadowOf(app.getSystemService(InputManager::class.java)).addInputDevice(g8)
        profiles = KeymapProfiles(app)
        for (name in listOf("KBM", "G8 v0.3"))
            profiles.saveProfile(name, arrayListOf("BTN_TL2 100 100 0 150"), mlbb, true, 2280, 1080)
        val store = DeviceMappingStore(app)
        val group = store.createGroup("GameSir G8 Setup", listOf(DeviceMember("g8", "GameSir-G8")))
        store.setBinding(group.id, mlbb, "G8 v0.3")
        pointer = Robolectric.buildService(TouchPointer::class.java).create().get()
        val binder = Binder()
        val remote = Proxy.newProxyInstance(IRemoteService::class.java.classLoader,
            arrayOf(IRemoteService::class.java)) { _, method, _ ->
            when (method.name) {
                "startServer" -> { started += pointer.selectedProfile; null }
                "pauseMouse" -> { pauses++; null }
                "asBinder" -> binder
                "isActive" -> true
                else -> null
            }
        } as IRemoteService
        pointer.mService = remote
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java, "service", remote)
        ReflectionHelpers.setField(pointer, "activityRemoteCallback", true)
        session = ReflectionHelpers.getField(pointer, "foreground")
        session.start()
        observer = ReflectionHelpers.getField(pointer, "mActivityObserverCallback")
        idle()
    }
    @After fun cleanup() {
        pointer.onDestroy()
        ReflectionHelpers.setStaticField(RemoteServiceHelper::class.java, "service", null)
    }
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun foreground(pkg: String) { observer.onForegroundActivitiesChanged(pkg); idle() }
    private fun storage() = profiles.sharedPref.all
    private fun bindings() = pointer.getSharedPreferences("device_mapping_v1", Context.MODE_PRIVATE).all

    @Test fun unknownAppPausesHidesOverlayAndRestoresPreferredWithoutMutationOrDialog() {
        val beforeProfiles = storage(); val beforeBindings = bindings(); val dialog = ShadowDialog.getLatestDialog()
        foreground(mlbb)
        assertEquals(listOf("G8 v0.3"), started)
        foreground("file.manager")
        assertNull(pointer.selectedProfile); assertEquals(2, pauses)
        assertEquals(listOf("G8 v0.3"), started)
        assertEquals(dialog, ShadowDialog.getLatestDialog())
        val stopped = shadowOf(pointer).nextStoppedService
        assertEquals("xtr.keymapper.editor.ShowKeymapService", stopped.component!!.className)
        foreground(mlbb)
        assertEquals(listOf("G8 v0.3", "G8 v0.3"), started)
        assertEquals(beforeProfiles, storage()); assertEquals(beforeBindings, bindings())
    }
    @Test fun rapidSwitchInvalidatesBothSelectionAndQueuedConnection() {
        observer.onForegroundActivitiesChanged(mlbb)
        // Execute only the resolver, leaving the remote-connection commit queued.
        shadowOf(Looper.getMainLooper()).runOneTask()
        observer.onForegroundActivitiesChanged("file.manager")
        observer.onForegroundActivitiesChanged(mlbb)
        idle()
        assertEquals(listOf("G8 v0.3"), started)
        assertEquals("G8 v0.3", pointer.selectedProfile)
    }
    @Test fun stopPreventsQueuedActivationAndPreferencesAreUntouched() {
        val before = storage(); observer.onForegroundActivitiesChanged(mlbb)
        pointer.onDestroy(); idle()
        assertTrue(started.isEmpty()); assertEquals(before, storage())
    }
    @Test fun disabledAndDeletedPreferredProfilesCannotActivateFromDelayedCallback() {
        foreground(mlbb); started.clear()
        profiles.setProfileEnabled("G8 v0.3", false); idle()
        assertTrue(started.isEmpty()); assertTrue(profiles.getProfile("G8 v0.3", false).disabled)
        assertEquals("G8 v0.3", pointer.selectedProfile)
        profiles.deleteProfile("G8 v0.3"); profiles.deleteProfile("KBM"); idle()
        assertTrue(started.isEmpty()); assertNull(pointer.selectedProfile)
        assertTrue(storage().isEmpty())
    }
    @Test fun controllerDisconnectInvalidatesQueuedPreferredActivation() {
        observer.onForegroundActivitiesChanged(mlbb)
        shadowOf(Looper.getMainLooper()).runOneTask()
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)).removeInputDevice(99)
        idle()
        // No group preference remains: configured-app selection is allowed, but must not auto-start.
        assertTrue(started.isEmpty())
    }
    @Test fun virtualOrUnchangedInputNotificationsDoNotRestartTheMapping() {
        foreground(mlbb)
        val listener: InputManager.InputDeviceListener = ReflectionHelpers.getField(pointer, "deviceListener")
        val virtual = ShadowInputDevice.makeInputDeviceNamed("XtMapper Virtual Touch")
        ReflectionHelpers.setField(virtual, "mId", -1)
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)).addInputDevice(virtual)
        listener.onInputDeviceAdded(-1); listener.onInputDeviceChanged(99); idle()
        assertEquals(listOf("G8 v0.3"), started); assertEquals(1, pauses)
    }
}
