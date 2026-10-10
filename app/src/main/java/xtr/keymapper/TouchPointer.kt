package xtr.keymapper

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.SharedPreferences
import android.hardware.input.InputManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Display
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import xtr.keymapper.databinding.CursorBinding
import xtr.keymapper.editor.EditorActivity
import xtr.keymapper.editor.EditorService
import xtr.keymapper.editor.ShowKeymapService
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfile
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.profiles.ProfileSelector
import xtr.keymapper.profiles.AutomaticProfileResolver
import xtr.keymapper.profiles.ForegroundProfileSession
import xtr.keymapper.devices.*
import xtr.keymapper.server.RemoteServiceHelper

class TouchPointer : Service() {
    private val binder: IBinder = TouchPointerBinder()
    var activityCallback: MainActivityCallback? = null
    @Volatile var mService: IRemoteService? = null
    @Volatile var selectedProfile: String? = null
    private val mHandler = Handler(Looper.getMainLooper())
    private var activityRemoteCallback = false
    private var mWindowManager: WindowManager? = null
    private var displayId = 0
    private val foreground = ForegroundProfileSession()
    private var profileDialog: AlertDialog? = null
    private var displayListener: DisplayListener? = null
    private var connectedIdentities = emptySet<String>()
    private val profilePreferences by lazy { getSharedPreferences("profiles", MODE_PRIVATE) }
    private val devicePreferences by lazy { getSharedPreferences("device_mapping_v1", MODE_PRIVATE) }
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        refreshForeground()
    }
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = devicesChanged()
        override fun onInputDeviceRemoved(deviceId: Int) = devicesChanged()
        override fun onInputDeviceChanged(deviceId: Int) = devicesChanged()
    }

    override fun onCreate() {
        super.onCreate()
        profilePreferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        devicePreferences.registerOnSharedPreferenceChangeListener(preferencesListener)
        connectedIdentities = connectedDeviceIdentities()
        getSystemService(InputManager::class.java).registerInputDeviceListener(deviceListener, mHandler)
    }


    interface MainActivityCallback {
        fun updateCmdView1(line: String?)
        fun stopPointer()
    }


    inner class TouchPointerBinder : Binder() {
        val service: TouchPointer
            get() =// Return this instance of TouchPointer so clients can call public methods
                this@TouchPointer
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        if (i == null) {
            stopSelf()
            return super.onStartCommand(null, flags, startId)
        }

        foreground.start()
        profileDialog?.dismiss()
        pauseMapping()
        // Launch default profile
        this.selectedProfile = i.getStringExtra(EditorActivity.PROFILE_NAME)
        if (this.selectedProfile == null) {
            this.selectedProfile = "Default"
        }



        val name = "Overlay"
        val channel =
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
                .setName(name).build()

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(channel)

        val keymapConfig = KeymapConfig(this)

        val pendingIntent: PendingIntent?

        if (keymapConfig.editorOverlay) {
            val intent = Intent(this, EditorService::class.java)
            pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            val intent = Intent(this, EditorActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                .putExtra(EditorActivity.PROFILE_NAME, selectedProfile)
            pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
        val notification = builder.setOngoing(true)
            .setContentTitle("Keymapper service running")
            .setContentText("Touch to launch editor")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2, notification)
        }

        this.displayId = i.getIntExtra(DISPLAY_ID, Display.DEFAULT_DISPLAY)

        val keymapProfile = KeymapProfiles(this).getProfile(selectedProfile, true)
        connectRemoteService(keymapProfile)

        displayListener?.let { getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        displayListener = object :
            DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId != this@TouchPointer.displayId) return
                // Re-read the current profile and scale on connection, never the startup snapshot.
                if (KeymapConfig(this@TouchPointer).disableAutoProfiling) launchProfile(selectedProfile)
                else refreshForeground()
            }

            override fun onDisplayRemoved(displayId: Int) {
            }
        }
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener!!, mHandler)


        return super.onStartCommand(i, flags, startId)
    }

    fun launchProfile(profileName: String?) {
        if (profileName == null) return
        this.selectedProfile = profileName
        val keymapProfile = KeymapProfiles(this).getProfile(selectedProfile, true)
        connectRemoteService(keymapProfile)
    }

    private fun connectRemoteService(profile: KeymapProfile,
                                     request: ForegroundProfileSession.Request? = foreground.current()) {
        if (request == null) return
        val profileName = selectedProfile
        val expectedDecision = request.packageName?.let { decision(it) }
        val expectedLines = profileName?.let { profilePreferences.getStringSet(it, null)?.toSet() }
        activityCallback?.updateCmdView1("connecting to server..")
        RemoteServiceHelper.getInstance(this) { service ->
            mHandler.post {
                foreground.commit(request) {
                    if (selectedProfile != profileName ||
                        expectedLines != profileName?.let { profilePreferences.getStringSet(it, null)?.toSet() } ||
                        (request.packageName != null &&
                            (decision(request.packageName) != expectedDecision || !canActivate(request, profileName))))
                        return@commit
                    try {
                        if (service == null) throw IllegalStateException("Root service unavailable")
                        if (mService?.asBinder() != service.asBinder()) activityRemoteCallback = false
                        mService = service
                        val config = KeymapConfig(this)
                        val display = getSystemService(DisplayManager::class.java).getDisplay(displayId)
                        val size = Point()
                        display.getRealSize(size)
                        mWindowManager = displayContext?.getSystemService(WindowManager::class.java)
                        if (!config.disableAutoProfiling && !activityRemoteCallback) {
                            service.registerActivityObserver(mActivityObserverCallback)
                            activityRemoteCallback = true
                        }
                        if (config.disableAutoProfiling || (request.packageName != null && !profile.disabled)) {
                            service.startServer(profile, config, mCallback, size.x, size.y, displayId)
                            if (config.showControls && !profile.disabled) ShowKeymapService.start(this, profileName)
                        }
                    } catch (error: Exception) {
                        foreground.stop { pauseMapping() }
                        activityCallback?.updateCmdView1(error.toString())
                        if (activityCallback != null) activityCallback?.stopPointer() else stopSelf()
                        Log.e("startServer", error.toString(), error)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        foreground.stop { pauseMapping() }
        profileDialog?.dismiss()
        profileDialog = null
        displayListener?.let { getSystemService(DisplayManager::class.java).unregisterDisplayListener(it) }
        displayListener = null
        profilePreferences.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        devicePreferences.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(deviceListener)
        activityRemoteCallback = false
        if (mService != null) try {
            mService!!.unregisterActivityObserver(mActivityObserverCallback)
            stopServer()
        } catch (e: Exception) {
            Log.e("stopServer", e.toString(), e)
        }
        mService = null
        activityCallback = null
        super.onDestroy()
    }

    @Throws(RemoteException::class)
    private fun stopServer() {
        stopService(Intent(this, ShowKeymapService::class.java))
        mService!!.stopServer()
    }

    /**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    val mCallback: IRemoteServiceCallback = object : IRemoteServiceCallback.Stub() {
        private var cursorView: View? = null

        override fun launchEditor() {
            val intent = Intent(this@TouchPointer, EditorService::class.java)
            intent.putExtra(EditorActivity.PROFILE_NAME, selectedProfile)
            startService(intent)
        }

        override fun alertMouseAimActivated() {
            // Notifying user that shooting mode was activated
            mHandler.post {
                Toast.makeText(
                    this@TouchPointer, R.string.mouse_aim_activated, Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun requestKeymapProfile(): KeymapProfile {
            return selectedProfile?.let { KeymapProfiles(this@TouchPointer).getProfile(it, true) }
                ?: KeymapProfile().apply { disabled = true }
        }

        override fun requestKeymapConfig(): KeymapConfig {
            return KeymapConfig(this@TouchPointer)
        }

        @UiThread
        override fun switchProfiles() {
            val request = foreground.current() ?: return
            mHandler.post {
                foreground.commit(request) {
                    val profiles = KeymapProfiles(this@TouchPointer)
                    val application = if (KeymapConfig(this@TouchPointer).disableAutoProfiling) {
                        selectedProfile?.let { profiles.getProfile(it, false).packageName } ?: return@commit
                    } else request.packageName ?: return@commit
                    profileDialog?.dismiss()
                    profileDialog = ProfileSelector.select(this@TouchPointer, { profile ->
                        foreground.commit(request) {
                            if (KeymapConfig(this@TouchPointer).disableAutoProfiling) {
                                if (profiles.getAllProfilesForApp(application).containsKey(profile)) launchProfile(profile)
                            } else activate(request, profile)
                        }
                    }, application) { foreground.isCurrent(request) }
                }
            }
        }

        override fun enablePointer() {
            val keymapConfig = requestKeymapConfig()

            // Set combined pointer mode automatically for 14 QPR3 and above
            if (keymapConfig.pointerMode == KeymapConfig.POINTER_OVERLAY) {
                keymapConfig.pointerMode = KeymapConfig.POINTER_COMBINED
                keymapConfig.applySharedPrefs()
                activityCallback!!.stopPointer()
                try {
                    stopServer()
                } catch (_: RemoteException) {
                }
                return
            }

            mHandler.post {
                if (cursorView == null) {
                    cursorView = CursorBinding.inflate(
                        LayoutInflater.from(
                            ContextThemeWrapper(displayContext, R.style.Theme_XtMapper)
                        )
                    ).getRoot()

                    val mParams =
                        Utils.getPointerLayoutParams(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)

                    mWindowManager!!.addView(cursorView, mParams)
                }
            }
        }

        override fun disablePointer() {
            mHandler.post {
                if (cursorView != null) {
                    mWindowManager!!.removeView(cursorView)
                    cursorView = null
                }
            }
        }

        override fun setCursorX(x: Int) {
            mHandler.post {
                if (cursorView != null) cursorView!!.x = x.toFloat()
            }
        }

        override fun setCursorY(y: Int) {
            mHandler.post {
                if (cursorView != null) cursorView!!.y = y.toFloat()
            }
        }
    }

    private val displayContext: Context?
        get() {
            val displayManager =
                getSystemService(DisplayManager::class.java)
            val display = displayManager.getDisplay(displayId)
            val context: Context? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                createDisplayContext(display).createWindowContext(
                    display,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    null
                )
            } else {
                createDisplayContext(display)
            }
            return context
        }

    /**
     * This implementation is used to receive callbacks from the remote
     * service.
     */
    private fun pauseMapping() {
        try {
            mService?.pauseMouse()
        } catch (error: RemoteException) {
            Log.e("profile switch", "Could not pause previous mapping", error)
        }
        stopService(Intent(this, ShowKeymapService::class.java))
    }

    private fun connectedDeviceIdentities() = InputDeviceDiscovery.connected().map { it.persistentIdentity }.toSet()

    private fun devicesChanged() {
        val connected = connectedDeviceIdentities()
        if (connected == connectedIdentities) return
        connectedIdentities = connected
        refreshForeground()
    }

    private fun decision(packageName: String): AutomaticProfileResolver.Decision {
        val profiles = KeymapProfiles(this).getAllProfilesForApp(packageName)
        val store = DeviceMappingStore(this)
        return AutomaticProfileResolver.resolve(packageName, profiles, store.listGroups(),
            store.listBindings(), connectedDeviceIdentities())
    }

    private fun canActivate(request: ForegroundProfileSession.Request, profile: String?): Boolean {
        val packageName = request.packageName ?: return false
        val existing = KeymapProfiles(this).getAllProfilesForApp(packageName)[profile] ?: return false
        if (existing.disabled) return false
        return when (val next = decision(packageName)) {
            is AutomaticProfileResolver.Decision.Activate -> next.profile == profile
            is AutomaticProfileResolver.Decision.SelectExisting -> profile in next.profiles
            else -> false
        }
    }

    private fun activate(request: ForegroundProfileSession.Request, profile: String?) {
        if (!canActivate(request, profile)) return
        selectedProfile = profile
        connectRemoteService(KeymapProfiles(this).getProfile(profile, true), request)
    }

    private fun refreshForeground() {
        if (KeymapConfig(this).disableAutoProfiling) return
        foreground.current()?.packageName?.let { observeForeground(it, true) }
    }

    private fun observeForeground(packageName: String, force: Boolean = false) {
        val request = foreground.observe(packageName, { pauseMapping() }, force) ?: return
        mHandler.post {
            foreground.commit(request) {
                profileDialog?.dismiss()
                profileDialog = null
                when (val next = decision(packageName)) {
                    AutomaticProfileResolver.Decision.Unconfigured -> selectedProfile = null
                    is AutomaticProfileResolver.Decision.Disabled -> selectedProfile = next.profile
                    is AutomaticProfileResolver.Decision.Activate -> activate(request, next.profile)
                    is AutomaticProfileResolver.Decision.SelectExisting -> {
                        selectedProfile = null
                        profileDialog = ProfileSelector.select(this, { profile ->
                            foreground.commit(request) { activate(request, profile) }
                        }, packageName) { foreground.isCurrent(request) }
                    }
                }
            }
        }
    }

    private val mActivityObserverCallback: ActivityObserver = object : ActivityObserver.Stub() {
        override fun onForegroundActivitiesChanged(packageName: String) = observeForeground(packageName)
    }

    companion object {
        const val CHANNEL_ID: String = "pointer_service"
        const val DISPLAY_ID: String = "display_id"
    }
}
