package xtr.keymapper.editor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import xtr.keymapper.IRemoteService
import xtr.keymapper.OnKeyEventListener
import xtr.keymapper.R
import xtr.keymapper.Utils
import xtr.keymapper.activity.MainActivity
import xtr.keymapper.editor.component.Camera
import xtr.keymapper.editor.component.Crosshair
import xtr.keymapper.editor.component.MouseWalk
import xtr.keymapper.editor.component.RightClick
import xtr.keymapper.keymap.KeymapConfig
import xtr.keymapper.keymap.KeymapProfile
import xtr.keymapper.keymap.ProfileConfiguration
import xtr.keymapper.keymap.KeymapProfiles
import xtr.keymapper.macro.MacroIdUtils
import xtr.keymapper.macro.MacroStatus
import xtr.keymapper.macro.MacroView
import xtr.keymapper.macro.MacroView.OnFinishListener
import xtr.keymapper.server.RemoteServiceHelper

class EditorUI(
    private val context: Context, // Callback when the editor UI is hidden/closed
    private val editorCallback: EditorCallback?, /* Name of the currently loaded keymap profile */
    private val profileName: String?, private val startMode: Int
) : OnKeyEventListener.Stub(), LifecycleOwner, SavedStateRegistryOwner {

    // When a keyboard key is pressed,
    // Receives keyboard input while editing a key binding
    private var keyInFocus: KeyInFocusListener? = null

    private val mHandler = Handler(Looper.getMainLooper())

    /* The active keymap config and a backup copy */
    private var profile: KeymapProfile? = null
    private var profileBackup: Set<String>? = null

    private var overlayOpen = false
    private var visualSession: VisualMappingSession? = null
    private var visualComposeView: ComposeView? = null
    private var visualReady by androidx.compose.runtime.mutableStateOf(false)
    private var visualCloseRequested by androidx.compose.runtime.mutableStateOf(false)
    private var closed = false
    private var captureSession: CoordinateCaptureSession? = null
    private var captureGuard: CaptureInterruptionGuard? = null
    private val draftEditor get() = startMode == VISUAL_EDITOR || startMode == COORDINATE_CAPTURE

    // The settings/catalog UI (hidden in SHOW_KEYMAP_ONLY mode)
    private val settingsOverlay: SettingsOverlay?

    // The main editor view and the container holding all key buttons
    private val mainView: ViewGroup?
    private val keysContainerView: ViewGroup

    private var macroDialog: MacroDialog? = null

    private val editorUiComponents = EditorUiComponentList()

    // IPC connection to the remote service that captures raw input
    private var mService: IRemoteService? = null

    // Compose Dialog Management
    private var importExportComposeView: ComposeView? = null
    private var importExportLifecycle: LifecycleRegistry? = null

    // LifecycleOwner implementation
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle = lifecycleRegistry

    // SavedStateRegistryOwner implementation
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    init {
        val layoutInflater = context.getSystemService(LayoutInflater::class.java)

        if (startMode != SHOW_KEYMAP_ONLY) {
            context.stopService(Intent(context, ShowKeymapService::class.java))
            settingsOverlay = SettingsOverlay(context, startMode)
            mainView = settingsOverlay.createView(layoutInflater)
            keysContainerView = settingsOverlay.binding.keyContainer

            settingsOverlay.inflateMenuResource(startMode, layoutInflater)
            settingsOverlay.setOnActionSelectedListener { id: Int ->
                this.onActionSelected(
                    id
                )
            }
        } else {
            keysContainerView = FrameLayout(context)
            keysContainerView.setLayoutParams(
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            settingsOverlay = null
            mainView = null
        }
    }

    fun unregisterOnKeyEventListener() {
        settingsOverlay!!.onUnRegisterKeyEventListener()
        if (mService != null) {
            try {
                if (startMode != COORDINATE_CAPTURE) mService!!.unregisterOnKeyEventListener(this)
                if (!draftEditor) {
                    mService!!.resumeMouse()
                    mService!!.reloadKeymap()
                } else if (KeymapConfig(context).disableAutoProfiling) {
                    mService!!.resumeMouse(); mService!!.reloadKeymap()
                }
            } catch (ignored: RemoteException) {
            }
        }
        mService = null
    }

    @Throws(RemoteException::class)
    fun registerOnKeyEventListener(service: IRemoteService?) {
        mService = service
        if (startMode != COORDINATE_CAPTURE) mService!!.registerOnKeyEventListener(this)
        settingsOverlay!!.onRegisterKeyEventListener()
        mService!!.pauseMouse()
        if (startMode == COORDINATE_CAPTURE) captureGuard?.connection(mService!!.isActive)
        if (draftEditor) checkVisualPause(service!!, 0)
    }

    private fun checkVisualPause(service: IRemoteService, attempt: Int) {
        mHandler.post {
            if (closed || mService !== service) return@post
            try {
                visualReady = service.isMappingPaused
                if (!visualReady && attempt < 40) mHandler.postDelayed({ checkVisualPause(service, attempt + 1) }, 50)
            } catch (_: RemoteException) { visualReady = false }
        }
    }

    interface KeyInFocusListener {
        fun setText(key: String?)
    }

    fun open(overlayWindow: Boolean) {
        settingsOverlay!!.overlayWindow = overlayWindow
        if (mainView!!.windowToken == null && mainView.parent == null) if (overlayWindow) openOverlayWindow()
        else {
            overlayOpen = false
            if (context is EditorActivity) (context as Activity).setContentView(mainView)
            else  // For MainActivity
                (context as Activity).addContentView(
                    mainView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
        }

        if (mService == null) {
            mainView.setOnKeyListener(View.OnKeyListener { v: View?, keyCode: Int, event: KeyEvent? ->
                this.onKey(
                    v,
                    keyCode,
                    event!!
                )
            })
            mainView.setFocusable(true)
        }

        if (startMode == COORDINATE_CAPTURE) openCaptureEditor()
        else if (startMode == VISUAL_EDITOR) openVisualEditor() else loadKeymapAfterView()
    }

    fun requestClose() {
        captureSession?.stop()
        if (startMode == COORDINATE_CAPTURE || startMode == VISUAL_EDITOR && visualSession?.dirty == true) visualCloseRequested = true
        else hideView()
    }

    private fun openVisualEditor() {
        val saved = (KeymapProfiles(context).sharedPref.all[profileName] as? Set<*>)?.filterIsInstance<String>()?.toSet()
        val session = try {
            require(saved != null) { "Profile was deleted or renamed." }
            VisualMappingSession(profileName!!, saved)
        } catch (error: IllegalArgumentException) {
            Toast.makeText(context, error.message ?: "Invalid profile", Toast.LENGTH_LONG).show()
            hideView(); return
        }
        visualSession = session
        EditorSessionGuard.acquire(this)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        mainView!!.setViewTreeLifecycleOwner(this)
        mainView.setViewTreeSavedStateRegistryOwner(this)
        mainView.removeAllViews()
        val compose = ComposeView(context)
        visualComposeView = compose
        mainView.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val devices = xtr.keymapper.devices.InputDeviceDiscovery.connected().filter {
            it.type == xtr.keymapper.devices.DeviceType.GAMEPAD || it.type == xtr.keymapper.devices.DeviceType.BLUETOOTH_CONTROLLER
        }.joinToString { it.name }.ifBlank { "Controller disconnected — edit saved bindings offline; verify input mode before play." }
        val applicationName = try {
            context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(session.profile.packageName, 0)).toString()
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) { session.profile.packageName }
        compose.setContent {
            VisualControllerEditor(session, overlayOpen, visualReady, visualCloseRequested,
                onKeepEditing = { visualCloseRequested = false },
                onSave = { try { session.save(context); null } catch (error: Exception) { error.message ?: "Save failed" } },
                onClose = { hideView() }, onPlaceTarget = {
                    try {
                        context.packageManager.getLaunchIntentForPackage(session.profile.packageName)?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    } catch (error: Exception) { Toast.makeText(context, "Open the game manually behind the overlay.", Toast.LENGTH_LONG).show() }
                }, deviceDescription = devices, applicationName = applicationName)
        }
    }

    private fun openCaptureEditor() {
        val p = try {
            if (profileName == null) null else {
                val saved = KeymapProfiles(context).sharedPref.getStringSet(profileName, null)
                require(saved != null) { "Profile was deleted or renamed" }
                ProfileConfiguration.parse(saved.sorted().joinToString("\n"))
            }
        } catch (e: Exception) {
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show(); hideView(); return
        }
        val display = android.graphics.Point()
        @Suppress("DEPRECATION")
        context.getSystemService(WindowManager::class.java).defaultDisplay.getRealSize(display)
        val session = try { CoordinateCaptureSession(p?.xRes?.toFloat() ?: display.x.toFloat(), p?.yRes?.toFloat() ?: display.y.toFloat()) }
        catch (e: Exception) { Toast.makeText(context, "Profile needs a valid screen size", Toast.LENGTH_LONG).show(); hideView(); return }
        captureSession = session
        EditorSessionGuard.acquire(this)
        savedStateRegistryController.performAttach(); savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        mainView!!.setViewTreeLifecycleOwner(this); mainView.setViewTreeSavedStateRegistryOwner(this)
        mainView.removeAllViews()
        val compose = ComposeView(context); visualComposeView = compose
        mainView.addView(compose, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        captureGuard = CaptureInterruptionGuard(context, mainView, mHandler, overlayOpen, { mService }, { session.capturing }, { hideView() })
        compose.setContent {
            CoordinateCaptureUi(session, profileName, p?.packageName, overlayOpen, visualReady, visualCloseRequested,
                onKeepEditing = { visualCloseRequested = false }, onClose = { hideView() }, onOpenGame = {
                    p?.packageName?.let { pkg -> context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } }
                })
        }
    }

    fun openSettings() {
        (context as MainActivity).addContentView(
            mainView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun showControls(alpha: Float) {
        openOverlayWindow(alpha)
    }

    private fun openOverlayWindow(alpha: Float = -1f) {
        val overlayView =
            (if (startMode == EditorUI.Companion.SHOW_KEYMAP_ONLY) keysContainerView else mainView)!!
        if (overlayOpen) {
            removeView(overlayView)
        }

        val mWindowManager = context.getSystemService(WindowManager::class.java)
        val mParams =
            if (startMode == SHOW_KEYMAP_ONLY) Utils.getPointerLayoutParams(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT) else WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
                PixelFormat.TRANSLUCENT
            )

        if (startMode != SHOW_KEYMAP_ONLY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mParams.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        if (draftEditor) mParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        if (alpha < 1 && alpha > 0) overlayView.setAlpha(alpha)

        mWindowManager.addView(overlayView, mParams)
        overlayOpen = true
        if (startMode != SHOW_KEYMAP_ONLY) hideSystemBars()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val overlayView =
                (if (startMode == SHOW_KEYMAP_ONLY) keysContainerView else mainView)!!
            val windowInsetsController = overlayView.getWindowInsetsController()
            if (windowInsetsController != null) {
                windowInsetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /**
     * For events received by view
     * @return true if we consume the event
     */
    fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
        if (draftEditor && keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) requestClose()
            return true
        }
        if (keyInFocus != null) {
            val key = event.displayLabel.toString()
            if (key.matches("[a-zA-Z0-9]+".toRegex())) {
                keyInFocus!!.setText(key)
                return true
            }
        }
        return false
    }

    /**
     * For key events received from getevent running in remote process
     * @param event A line of output from getevent -ql
     */
    override fun onKeyEvent(event: String) {
        if (startMode == COORDINATE_CAPTURE || closed) return
        // line: /dev/input/event3 EV_KEY KEY_X DOWN
        val code = xtr.keymapper.controller.ControllerBindings.capture(event) ?: return
        val key = if (code.startsWith("KEY_")) code.substring(4) else code

        // Incoming calls are not guaranteed to be executed on the main thread
        mHandler.post(Runnable {
            if (macroDialog != null) macroDialog!!.onKey(key)
            else if (keyInFocus != null) keyInFocus!!.setText(key)
            settingsOverlay!!.onKey(key)
        })
    }


    /**
     * Called when a button in catalog has been clicked.
     * 
     * @param id the relevant id of the menu item for the card.
     */
    fun onActionSelected(id: Int) {
        // X y coordinates of center of root view
        val defaultX = mainView!!.pivotX
        val defaultY = mainView.pivotY

        when (id) {
            R.id.save -> {
                hideView()
            }
            R.id.macro -> {
                showMacroDialog()
            }
            R.id.reset -> {
                profile = KeymapProfiles.getProfile(profileBackup)
                reloadKeymap()
            }
            R.id.import_export -> {
                // Show Compose-based UI from ImportExportDialog.kt
                val configString = generateConfigString()
                showImportExportDialog(configString)
            }
            else -> {
                editorUiComponents.addMatchingComponentForId(
                    id,
                    mCallback,
                    context,
                    defaultX,
                    defaultY
                ) { editorUiComponents.add(it) }
            }
        }
    }

    private fun showMacroDialog() {
        macroDialog = MacroDialog(context, overlayOpen, profile)
        macroDialog!!.show(View.OnClickListener { v: View? ->
            if (v != null) addMacro()
            if (macroDialog != null) {
                macroDialog!!.dismiss()
                macroDialog = null
            }
        })
    }

    /**
     * MacroStatus for displaying elapsed time
     * Stop when receiving any keyboard input from User or stopwatch is clicked and show macro dialog finally
     * MacroView for visualization
     */
    private fun addMacro() {
        if (mService != null) {
            mainView!!.setFocusable(true)
        }
        val macroStatus = MacroStatus(context, settingsOverlay!!.binding.catalog)
        val macroView = MacroView(context, OnFinishListener { macroView_: MacroView? ->
            // Stop counting time in stopwatch
            macroStatus.stop()

            // Remove the macro view
            keysContainerView.removeView(macroView_)
            macroView_!!.invalidate()

            // Redirect keyboard input
            mainView!!.setOnKeyListener(View.OnKeyListener { v: View?, keyCode: Int, event: KeyEvent? ->
                this@EditorUI.onKey(
                    v,
                    keyCode,
                    event!!
                )
            })
            settingsOverlay.unHideButtons()
            if (mService == null) mainView.setFocusable(false)
            showMacroDialog()
        })
        // Hide existing buttons in catalog till macro finish
        settingsOverlay.hideButtons()
        macroStatus.start()

        keysContainerView.addView(macroView)
        // Redirect keyboard input
        mainView!!.setOnKeyListener(View.OnKeyListener { v: View?, keyCode: Int, event: KeyEvent? ->
            macroView.onKey(
                event
            )
        })

        settingsOverlay.binding.catalog.setOnClickListener(View.OnClickListener { v: View? -> macroView.clearCanvasAndFinish() })
    }

    @JvmOverloads
    fun hideView(forceDiscard: Boolean = false) {
        if (closed) return
        closed = true
        captureSession?.stop()
        captureGuard?.dispose(); captureGuard = null
        if (draftEditor) {
            visualComposeView?.disposeComposition()
            if (lifecycleRegistry.currentState != Lifecycle.State.INITIALIZED) lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        if (startMode == START_EDITOR && !forceDiscard) {
            try { saveKeymap() } catch (e: Exception) {
                closed = false
                Toast.makeText(context,"Profile not saved: ${e.message?.take(300)}",Toast.LENGTH_LONG).show()
                return
            }
        }
        if (startMode != SHOW_KEYMAP_ONLY) {
            unregisterOnKeyEventListener()
            if (!draftEditor) settingsOverlay!!.onDestroyView()
            if (draftEditor) EditorSessionGuard.release(this)

            removeView(mainView!!)
            if (editorCallback != null) editorCallback.onHideView()
            else RemoteServiceHelper.reloadKeymap(context)

            // Load keymap config after settingsOverlay wrote config
            val keymapConfig = KeymapConfig(context)
            // We stopped the service, so we must restart it
            if (!draftEditor) RemoteServiceHelper.runIfActive(context, Runnable {
                if (keymapConfig.showControls) ShowKeymapService.start(context, profileName)
            })
        } else removeView(keysContainerView)
        
        // Cleanup Compose Dialog if it's open
        closeImportExportDialog()
    }

    private fun removeView(view: ViewGroup) {
        if (overlayOpen && view.isAttachedToWindow) {
            try { context.getSystemService(WindowManager::class.java).removeView(view) }
            catch (_: IllegalArgumentException) { /* Window already removed during interruption. */ }
            catch (_: SecurityException) { /* Overlay permission was revoked. */ }
        }
        view.removeAllViews()
        view.invalidate()
    }

    fun loadKeymapAfterView() {
        profileBackup = KeymapProfiles(context).sharedPref.getStringSet(profileName, null)?.toSet()
        keysContainerView.addOnLayoutChangeListener(OnLayoutChangeListener { v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int ->
            if (right != oldRight || left != oldLeft || top != oldTop || bottom != oldBottom) {
                if (profile != null) {
                    // Snapshot edits in memory; resizing must not persist an import.
                    profile = KeymapProfiles.getProfile(generateConfigString(oldRight - oldLeft, oldBottom - oldTop).lines().toSet())
                    reloadKeymap()
                }
            }
        })
        keysContainerView.post {
            profile = KeymapProfiles.getProfile(profileBackup)
            reloadKeymap()
        }
    }

    private fun reloadKeymap() {
        keyInFocus = null
        keysContainerView.removeAllViews()
        editorUiComponents.clear()
        loadKeymap()
    }

    private fun loadKeymap() {
        // Scale to current display size
        profile!!.scale(
            keysContainerView.width.toFloat(),
            keysContainerView.height.toFloat()
        )

        val editorUiComponentFactory = editorUiComponents.newFactory(mCallback, context)

        editorUiComponentFactory.addKeys(profile!!.keys)
        profile!!.chords.forEach { editorUiComponents.add(xtr.keymapper.editor.component.ControllerChord(mCallback, context, it)) }
        profile!!.aimKeys.forEach { editorUiComponents.add(xtr.keymapper.editor.component.AimKey(mCallback, context, it)) }
        profile!!.leftClick?.let { editorUiComponents.add(xtr.keymapper.editor.component.LeftClick(mCallback, context, it)) }
        editorUiComponentFactory.addSwipeKeys(profile!!.swipeKeys)
        editorUiComponentFactory.addDpads(profile!!.dpadArray)

        if (profile!!.mouseAimConfig != null) {
            editorUiComponents.add(Crosshair(mCallback, context, profile!!.mouseAimConfig))
        }
        if (profile!!.rightClick != null) {
            editorUiComponents.add(RightClick(mCallback, context, profile!!.rightClick))
        }
        if (profile!!.camera != null) {
            editorUiComponents.add(
                Camera(
                    mCallback,
                    context,
                    profile!!.camera.x,
                    profile!!.camera.y
                )
            )
        }
        if (profile!!.mouseWalk != null) {
            editorUiComponents.add(MouseWalk(mCallback, context, profile!!.mouseWalk))
        }
    }


    private fun saveKeymap() {
        val linesToWrite = ArrayList<String?>()

        editorUiComponents.forEach { linesToWrite.add(it.dataLine) }

        // Enabled macro ids
        MacroIdUtils.getLines(linesToWrite, profile)

        // Save Config
        val profiles = KeymapProfiles(context)

        profiles.saveProfile(
            profileName, linesToWrite, profile!!.packageName, !profile!!.disabled,
            keysContainerView.width, keysContainerView.height
        )
    }

    /**
     * Generates a configuration string exactly matching the save format, 
     * used for passing to the Compose Import/Export dialog.
     */
    private fun generateConfigString(
        width: Int = keysContainerView.width,
        height: Int = keysContainerView.height
    ): String {
        val linesToWrite = ArrayList<String>()
        editorUiComponents.forEach { linesToWrite.add(it.dataLine) }

        MacroIdUtils.getLines(linesToWrite, profile)
        linesToWrite.add("APPLICATION ${profile!!.packageName}")
        linesToWrite.add("SCREENSIZE $width $height")
        if (!profile!!.disabled) linesToWrite.add("ENABLED")
        return linesToWrite.joinToString("\n")
    }

    private fun handleImportAction(text: String): String? {
        val imported = try {
            ProfileConfiguration.parse(text)
        } catch (error: IllegalArgumentException) {
            return error.message ?: "Invalid configuration"
        }
        profile = imported
        reloadKeymap()
        closeImportExportDialog()
        Toast.makeText(context, "Configuration imported. Save to keep changes.", Toast.LENGTH_LONG).show()
        return null
    }

    private fun handleExportAction(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("XtMapper configuration", text))
        Toast.makeText(context, "Configuration copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showImportExportDialog(configCode: String) {
        if (importExportComposeView != null) return // Prevent duplicate overlays

        // Each dialog owns a fresh lifecycle and saved-state registry.
        val dialogOwner = object : LifecycleOwner, SavedStateRegistryOwner {
            val registry = LifecycleRegistry(this)
            val controller = SavedStateRegistryController.create(this)
            override val lifecycle = registry
            override val savedStateRegistry = controller.savedStateRegistry
            init {
                controller.performAttach()
                controller.performRestore(null)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            }
        }
        importExportLifecycle = dialogOwner.registry
        importExportComposeView = ComposeView(context)
        importExportComposeView!!.setViewTreeLifecycleOwner(dialogOwner)
        importExportComposeView!!.setViewTreeSavedStateRegistryOwner(dialogOwner)

        // 3. Attach Compose content (ImportExportDialog from Kotlin)
        importExportComposeView!!.setContent {
            // Kotlin's default parameters allow 4-arg Java signature
            ImportExportDialog(
                configCode,
                { closeImportExportDialog() },
                { handleImportAction(it) },
                { handleExportAction(it) }
            )
        }

        // 4. Attach the view tree to the window manager or Activity
        if (context is Activity) {
            context.addContentView(
                importExportComposeView,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        } else {
            val wm = context.getSystemService(WindowManager::class.java)
            val params = WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(importExportComposeView, params)
        }
        
        importExportLifecycle!!.handleLifecycleEvent(Lifecycle.Event.ON_START)
        importExportLifecycle!!.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun closeImportExportDialog() {
        val view = importExportComposeView ?: return
        
        importExportLifecycle?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        importExportLifecycle?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        importExportLifecycle?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        try {
            if (context !is Activity && view.isAttachedToWindow) context.getSystemService(
                WindowManager::class.java
            ).removeView(view)
        } catch (_: IllegalArgumentException) {
            // ignored
        }

        (view.parent as? ViewGroup)?.removeView(view)
        view.disposeComposition()
        importExportComposeView = null
        importExportLifecycle = null
    }

    private val mCallback: EditorUiComponentCallback = object : EditorUiComponentCallback {
        override fun isOverlayOpen(): Boolean {
            return overlayOpen
        }

        override fun getProfile(): KeymapProfile? =this@EditorUI.profile

        override fun getKeysContainerView(): ViewGroup = this@EditorUI.keysContainerView

        override fun removeComponent(component: EditorUiComponent?, vararg viewsToRemove: View?) {
            for (view in viewsToRemove) {
                keysContainerView.removeView(view)
            }
            editorUiComponents.remove(component)
        }

        override fun setOnKeyListener(keyInFocusListener: KeyInFocusListener?) {
            keyInFocus = keyInFocusListener
        }
    }

    companion object {
        private val TAG = "EditorUI"

        /* Start modes */
        const val START_SETTINGS: Int = 0 // Shows full editor with catalog/menu
        const val START_EDITOR: Int = 1 // Shows editor without catalog (save/reset available)
        const val COORDINATE_CAPTURE: Int = 4
        const val VISUAL_EDITOR: Int = 3 // Explicit draft save; exit always discards unapplied changes.
        const val SHOW_KEYMAP_ONLY: Int = 2 // Used to show an overlay with active keymapping
        fun resizeView(view: View, x: Int, y: Int) {
            val layoutParams = view.layoutParams
            layoutParams.width += x
            layoutParams.height += y
            view.requestLayout()
        }
    }
}
