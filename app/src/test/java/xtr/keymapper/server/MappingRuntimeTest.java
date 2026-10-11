package xtr.keymapper.server;

import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.Shadows;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.dpad.DpadHandler;
import xtr.keymapper.keymap.*;
import xtr.keymapper.mouse.HeldAimHandler;
import xtr.keymapper.server.event.*;
import static org.junit.Assert.*;
import static xtr.keymapper.InputEventCodes.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class MappingRuntimeTest {
    private FakeInput fake;
    private static final String AIM = "AIM_KEY KEY_Q 500 600 200 1 1\n"
            + "AIM_KEY KEY_E 700 600 200 1 1\nAIM_KEY KEY_R 900 600 200 1 1\n"
            + "AIM_KEY BTN_RIGHT 700 600 200 1 1\n";
    private static final String CAMERA = "CAMERA 1000 400 1 1 1 KEY_GRAVE 1\n";

    @Before public void setup() {
        fake = new FakeInput(ProfileConfiguration.parse(AIM + CAMERA + "MOUSE_LEFT 1100 800"));
        fake.mouse.init(2280, 1080);
    }
    private void idle() { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100)); }

    @Test public void heldSkillsStartMoveReleaseAndIgnoreRepeat() {
        for (String code : List.of("KEY_Q", "KEY_E", "KEY_R")) {
            fake.events.clear();
            assertTrue(fake.mouse.handleAimTrigger(code, 1));
            fake.mouse.handleAimTrigger(code, 1);
            fake.mouse.handleEvent(REL_X, 40);
            fake.mouse.handleEvent(REL_Y, -30);
            fake.mouse.handleAimTrigger(code, 0);
            fake.mouse.handleAimTrigger(code, 0);
            assertEquals(4, fake.events.size());
            assertEquals(1, fake.events.get(0).action);
            assertEquals(0, fake.events.get(3).action);
            assertEquals(fake.events.get(0).x + 40, fake.events.get(3).x, 0f);
            assertEquals(fake.events.get(0).y - 30, fake.events.get(3).y, 0f);
        }
    }

    @Test public void cameraYieldsAndResumesAndButtonsRemainFixed() {
        fake.mouse.activateDefaultMode(); idle();
        assertTrue(fake.hidden);
        assertTrue(fake.events.isEmpty());
        fake.mouse.handleEvent(REL_X, 20);
        assertEquals(37, fake.events.get(0).pid);
        fake.mouse.handleAimTrigger("KEY_Q", 1);
        assertTrue(fake.events.stream().anyMatch(e -> e.pid == 37 && e.action == 0));
        int size = fake.events.size();
        fake.mouse.handleEvent(REL_X, 30);
        assertEquals(42, fake.events.get(size).pid);
        fake.mouse.handleEvent(BTN_MOUSE, 1);
        fake.mouse.handleEvent(BTN_MOUSE, 0);
        assertEquals(1100, fake.events.get(fake.events.size()-1).x, 0f);
        fake.mouse.handleAimTrigger("KEY_Q", 0); idle();
        assertEquals(0, fake.events.get(fake.events.size()-1).action);
        fake.mouse.handleEvent(REL_Y, 15);
        assertEquals(415, fake.events.get(fake.events.size()-1).y, 0f);
        fake.mouse.stop(); idle();
        assertEquals(0, fake.events.get(fake.events.size()-1).action);
    }

    @Test public void automaticCameraHoldsInspectionAndReturnsToHeroWhenMovementStarts() {
        fake.mouse.activateDefaultMode(); idle();
        assertTrue(fake.events.isEmpty());
        fake.mouse.handleEvent(REL_X, 20);
        assertEquals(1, fake.events.get(0).action);
        assertEquals(1000, fake.events.get(0).x, 0f);
        assertEquals(1020, fake.events.get(1).x, 0f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
        assertEquals(2, fake.events.size()); // brief pauses do not interrupt a continuous drag
        fake.mouse.handleEvent(REL_X, 5000);
        assertEquals(2279, fake.events.get(2).x, 0f);
        fake.mouse.handleEvent(REL_Y, -5000);
        assertEquals(0, fake.events.get(3).y, 0f);
        assertEquals(1, fake.events.stream().filter(e -> e.action == 1).count());
        assertEquals(0, fake.events.stream().filter(e -> e.action == 0).count());
        fake.mouse.handleEvent(REL_X, -10);
        assertEquals(2269, fake.events.get(4).x, 0f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertEquals(5, fake.events.size()); // standing still preserves inspection
        fake.mouse.setMovementActive(true);
        assertEquals(6, fake.events.size());
        assertEquals(0, fake.events.get(5).action);
        fake.mouse.handleEvent(REL_Y, 10);
        assertEquals(1, fake.events.get(6).action);
        assertEquals(1000, fake.events.get(6).x, 0f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));
        assertEquals(9, fake.events.size()); // mouse idle during movement resumes hero follow
        fake.mouse.setMovementActive(false);
        fake.mouse.handleEvent(REL_X, 10);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertEquals(11, fake.events.size());
        fake.mouse.stop(); idle();
        assertEquals(12, fake.events.size());
        assertEquals(0, fake.events.get(11).action);
    }

    @Test public void dequeuedCameraTimeoutCannotReleaseANewerDrag() throws Exception {
        fake.mouse.activateDefaultMode();
        fake.mouse.setMovementActive(true);
        fake.mouse.handleEvent(REL_X, 10);
        java.lang.reflect.Field cameraField = MouseEventHandler.class.getDeclaredField("mouseCameraHandler");
        cameraField.setAccessible(true); Object camera = cameraField.get(fake.mouse);
        java.lang.reflect.Field timerField = camera.getClass().getDeclaredField("finishCameraDrag");
        timerField.setAccessible(true); Runnable oldTimer = (Runnable) timerField.get(camera);
        fake.mouse.handleEvent(REL_Y, 10);
        int count = fake.events.size(); oldTimer.run();
        assertEquals(count, fake.events.size());
        fake.mouse.stop();
        oldTimer.run(); idle();
        assertEquals(count + 1, fake.events.size());
    }

    @Test public void dpadKeysNotifyCameraAndProfileCameraTriggerOverridesGlobalShortcut() throws Exception {
        Recorder recorder = new Recorder();
        InputService service = service(CAMERA + "DPAD 0 0 50 50 50 100 100 KEY_W KEY_S KEY_A KEY_D", null, recorder);
        service.getKeymapConfig().mouseAimShortcutKey = "KEY_GRAVE";
        service.getKeymapConfig().keyGraveMouseAim = true;
        service.getMouseEventHandler().handleEvent(REL_X, 10);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000));
        assertEquals(List.of(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE), recorder.actions);
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_W DOWN");
        assertTrue(recorder.actions.contains(MotionEvent.ACTION_UP));
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_W UP");
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_GRAVE DOWN");
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_GRAVE UP");
        assertFalse(service.getMouseEventHandler().mouseAimActive);
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_GRAVE DOWN");
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_GRAVE UP");
        assertTrue(service.getMouseEventHandler().mouseAimActive);
        service.stop();
    }

    @Test public void fixedMouseButtonsHaveSymmetricPositionsAndDistinctIds() {
        fake = new FakeInput(ProfileConfiguration.parse("MOUSE_LEFT 123 456\nMOUSE_RIGHT 321 654"));
        fake.mouse.init(2280,1080);
        fake.config.rightClickMouseAim = true; // explicit mapping takes priority
        for (int code : new int[]{BTN_MOUSE, BTN_RIGHT}) {
            fake.mouse.handleEvent(code, 1); fake.mouse.handleEvent(code, 1);
            fake.mouse.handleEvent(REL_X, 10);
            fake.mouse.handleEvent(code, 0); fake.mouse.handleEvent(code, 0);
        }
        assertEquals(4, fake.events.size());
        Event leftDown = fake.events.get(0), leftUp = fake.events.get(1);
        Event rightDown = fake.events.get(2), rightUp = fake.events.get(3);
        assertEquals(1,leftDown.action); assertEquals(0,leftUp.action);
        assertEquals(leftDown.x,leftUp.x,0f); assertEquals(leftDown.y,leftUp.y,0f);
        assertEquals(1,rightDown.action); assertEquals(0,rightUp.action);
        assertEquals(rightDown.x,rightUp.x,0f); assertEquals(rightDown.y,rightUp.y,0f);
        assertNotEquals(leftDown.pid,rightDown.pid);
    }

    @Test public void rightMouseAimsSkillTwoAndAliasReleaseWaitsForBothTriggers() {
        fake.mouse.handleAimTrigger("KEY_E",1);
        fake.mouse.handleEvent(BTN_RIGHT,1);
        assertEquals(1,fake.events.size());
        fake.mouse.handleEvent(REL_X,50);
        fake.mouse.handleAimTrigger("KEY_E",0);
        assertEquals(2,fake.events.size());
        fake.mouse.handleEvent(BTN_RIGHT,0);
        assertEquals(3,fake.events.size());
        assertEquals(0,fake.events.get(2).action);
        assertEquals(750,fake.events.get(2).x,0f);
    }

    @Test public void independentSkillsLatestOwnsMouseAndStopReleasesEveryPointer() {
        fake.mouse.handleAimTrigger("KEY_Q",1);
        fake.mouse.handleAimTrigger("KEY_E",1);
        fake.mouse.handleEvent(BTN_MOUSE,1);
        assertNotEquals(fake.events.get(0).pid,fake.events.get(1).pid);
        assertNotEquals(fake.events.get(1).pid,fake.events.get(2).pid);
        fake.mouse.handleEvent(REL_X,15);
        assertEquals(43,fake.events.get(3).pid);
        fake.mouse.handleAimTrigger("KEY_E",0);
        fake.mouse.handleEvent(REL_Y,10);
        assertEquals(42,fake.events.get(5).pid);
        fake.mouse.stop(); idle();
        assertEquals(3,fake.events.stream().filter(e -> e.action==0).count());
    }

    @Test public void aimRangeClampsAndStopBeforeCameraDownCancelsDelayedTouch() {
        HeldAimHandler aim = new HeldAimHandler(fake.profile.aimKeys,fake::injectEvent,2280,1080);
        aim.trigger("KEY_Q",1); aim.move(true,5000);
        assertEquals(700,fake.events.get(1).x,0f);
        aim.stop();
        fake.events.clear();
        fake.mouse.activateDefaultMode(); fake.mouse.stop(); idle();
        assertTrue(fake.events.isEmpty());
    }

    @Test public void dpadCoexistsTransitionsOppositesAndOrdersQuickRelease() {
        DpadHandler dpad = new DpadHandler(ProfileConfiguration.parse(
                "DPAD 50 50 50 100 100 100 100 KEY_W KEY_S KEY_A KEY_D").dpadArray[0],
                39,new Handler(Looper.getMainLooper()),10);
        dpad.setInterface(fake);
        dpad.handleEvent("KEY_W",1); dpad.handleEvent("KEY_W",1);
        dpad.handleEvent("KEY_D",1); dpad.handleEvent("KEY_W",0);
        dpad.handleEvent("KEY_D",0); idle();
        assertEquals(5,fake.events.size());
        assertEquals(1,fake.events.get(0).action);
        assertEquals(50,fake.events.get(1).y,0f);
        assertEquals(150,fake.events.get(3).x,0f);
        assertEquals(100,fake.events.get(3).y,0f);
        assertEquals(0,fake.events.get(4).action);
        fake.mouse.handleAimTrigger("KEY_Q",1);
        assertNotEquals(39,fake.events.get(5).pid);
        fake.events.clear();
        dpad.handleEvent("KEY_W",1); dpad.handleEvent("KEY_S",1);
        dpad.handleEvent("KEY_W",0); idle();
        assertEquals(150,fake.events.get(fake.events.size()-1).y,0f);
        dpad.handleEvent("KEY_S",0); dpad.stop(); idle();
        assertEquals(4,fake.events.size()); // queued UP canceled; service owns final release
    }

    @Test public void editorMarkersExportAndImportCompleteAimAndLeftButtonConfiguration() {
        android.content.Context context = new android.view.ContextThemeWrapper(
                org.robolectric.RuntimeEnvironment.getApplication(), xtr.keymapper.R.style.Theme_XtMapper);
        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        xtr.keymapper.editor.EditorUiComponentCallback callback =
                (xtr.keymapper.editor.EditorUiComponentCallback) Proxy.newProxyInstance(
                        xtr.keymapper.editor.EditorUiComponentCallback.class.getClassLoader(),
                        new Class[]{xtr.keymapper.editor.EditorUiComponentCallback.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getKeysContainerView")) return container;
                            if (method.getName().equals("getProfile")) return fake.profile;
                            if (method.getReturnType() == boolean.class) return false;
                            return null;
                        });
        xtr.keymapper.editor.component.AimKey aim = new xtr.keymapper.editor.component.AimKey(
                callback, context, fake.profile.aimKeys.get(0));
        xtr.keymapper.editor.component.LeftClick left = new xtr.keymapper.editor.component.LeftClick(
                callback, context, fake.profile.leftClick);
        container.getChildAt(0).setX(550); container.getChildAt(0).setY(650);
        KeymapProfile imported = ProfileConfiguration.parse(aim.getDataLine() + "\n" + left.getDataLine());
        assertEquals("KEY_Q", imported.aimKeys.get(0).code);
        assertEquals(550, imported.aimKeys.get(0).x, 0f);
        assertEquals(650, imported.aimKeys.get(0).y, 0f);
        assertEquals(200, imported.aimKeys.get(0).radius, 0f);
        assertEquals(1, imported.aimKeys.get(0).xSensitivity, 0f);
        assertEquals(1100, imported.leftClick.x, 0f);
        assertEquals(800, imported.leftClick.y, 0f);
    }

    @Test public void parcelRoundTripPreservesAimButtonsAndCameraDefault() {
        Parcel parcel=Parcel.obtain(); fake.profile.writeToParcel(parcel,0); parcel.setDataPosition(0);
        KeymapProfile copy=KeymapProfile.CREATOR.createFromParcel(parcel); parcel.recycle();
        assertEquals(4,copy.aimKeys.size()); assertEquals("BTN_RIGHT",copy.aimKeys.get(3).code);
        assertEquals(1100,copy.leftClick.x,0f); assertTrue(copy.camera.autoActive);
    }

    @Test public void middleButtonWithoutCameraDoesNotCrashOrFallThroughToScroll() {
        fake=new FakeInput(new KeymapProfile());fake.mouse.init(2280,1080);
        fake.mouse.handleEvent(BTN_MIDDLE,1);fake.mouse.handleEvent(BTN_MIDDLE,0);
        assertEquals(2,fake.middleClicks);assertEquals(0,fake.scrolls);
    }

    public static class Recorder {
        final List<Integer> actions=new ArrayList<>();
        public boolean inject(InputEvent event, int mode) {
            actions.add(((MotionEvent)event).getActionMasked()); return true;
        }
    }
    private InputService service(String text, KeymapProfile reload, Recorder recorder) throws Exception {
        Input.injectInputEventMethod=Recorder.class.getMethod("inject",InputEvent.class,int.class);
        Input.inputManager=recorder; Input.setDisplayIdMethod=null;
        KeymapConfig config=new KeymapConfig(null);
        config.pointerMode=KeymapConfig.POINTER_OVERLAY;config.swipeDelayMs=10;
        IRemoteServiceCallback cb=(IRemoteServiceCallback)Proxy.newProxyInstance(
                IRemoteServiceCallback.class.getClassLoader(),new Class[]{IRemoteServiceCallback.class},
                (proxy,method,args)->switch(method.getName()) {
                    case "requestKeymapProfile" -> reload;
                    case "requestKeymapConfig" -> config;
                    default -> null;
                });
        return new InputService(ProfileConfiguration.parse(text),config,cb,2280,1080,null,true,0);
    }

    @Test public void mouseDisconnectReleasesSkillAndAttackWhileJoystickRemainsIndependent() throws Exception {
        Recorder recorder = new Recorder();
        InputService service = service(AIM + CAMERA + "MOUSE_LEFT 1100 800", null, recorder);
        service.injectEvent(100, 100, 1, 39);
        service.getMouseEventHandler().handleEvent(BTN_RIGHT, 1);
        service.getMouseEventHandler().handleEvent(BTN_MOUSE, 1);
        service.sendMouseEvent(-1, 0);
        assertEquals(2, recorder.actions.stream().filter(a -> a == MotionEvent.ACTION_POINTER_UP).count());
        service.injectEvent(110, 100, 2, 39);
        assertEquals(MotionEvent.ACTION_MOVE, (int)recorder.actions.get(recorder.actions.size()-1));
        service.stop();
        assertEquals(MotionEvent.ACTION_UP, (int)recorder.actions.get(recorder.actions.size()-1));
    }

    @Test public void serviceStopReleasesJoystickSkillAndAttackAndRejectsLateMoves() throws Exception {
        Recorder recorder=new Recorder();
        InputService service=service(AIM + "MOUSE_LEFT 1100 800",null,recorder);
        service.injectEvent(100,100,1,39);
        service.getMouseEventHandler().handleAimTrigger("KEY_Q",1);
        service.getMouseEventHandler().handleEvent(BTN_MOUSE,1);
        service.stop();
        assertEquals(3,recorder.actions.stream().filter(a -> a==MotionEvent.ACTION_UP || a==MotionEvent.ACTION_POINTER_UP).count());
        int size=recorder.actions.size();
        service.injectEvent(120,100,2,39);service.injectEvent(120,100,1,39);
        assertEquals(size,recorder.actions.size());
    }

    @Test public void profileReloadCleansOldPointersAndDoesNotReviveOldCameraCallback() throws Exception {
        Recorder recorder=new Recorder();
        InputService service=service(AIM + CAMERA,ProfileConfiguration.parse("KEY_F 400 500 0"),recorder);
        service.getMouseEventHandler().handleAimTrigger("KEY_Q",1);
        service.reloadKeymap();idle();
        assertEquals("KEY_F",service.getKeymapProfile().keys.get(0).code);
        assertEquals(List.of(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),recorder.actions);
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_F DOWN");
        service.getKeyEventHandler().handleEvent(" EV_KEY KEY_F UP");
        assertEquals(4,recorder.actions.size());service.stop();
    }

    @Test public void pauseReleasesSkillAndResumeRestoresCameraWithoutOffsetDrift() throws Exception {
        Recorder recorder=new Recorder();
        InputService service=service(AIM + CAMERA + "KEY_F 400 500 35",null,recorder);
        idle();service.getMouseEventHandler().handleAimTrigger("KEY_Q",1);
        service.pauseResumeKeymap();idle();
        assertTrue(service.stopEvents);
        service.pauseResumeKeymap();idle();
        assertFalse(service.stopEvents);assertTrue(service.getMouseEventHandler().mouseAimActive);
        assertEquals(400,service.getKeymapProfile().keys.get(0).x,0f);
        assertEquals(MotionEvent.ACTION_UP,(int)recorder.actions.get(recorder.actions.size()-1));
        service.getMouseEventHandler().handleEvent(REL_X, 10);
        assertEquals(MotionEvent.ACTION_MOVE,(int)recorder.actions.get(recorder.actions.size()-1));
        service.stop();
    }

    @Test public void controllerMonitorStopPauseAndReloadReleasePointersWithMouseAndKeyboard() throws Exception {
        for (String lifecycle : List.of("stop", "pause", "reload")) {
            Recorder recorder = new Recorder();
            InputService service = service("BTN_TL2 100 100 0 150\nSTICK_AIM BTN_GAMEPAD 300 600 180 1 1 .15 0\nSTICK_AIM BTN_TR 500 600 180 1 1 .15 0\nCHORD BTN_SELECT BTN_TR2 700 200 0\n" + AIM,
                ProfileConfiguration.parse("KEY_F 400 500 0"), recorder);
            var monitor = new xtr.keymapper.controller.ControllerDeviceMonitor(service, 2280, 1080,
                () -> java.util.Map.of("/dev/input/event99", new long[]{1,2,0,255,128,0,255,128}));
            var field = InputService.class.getDeclaredField("controllers");field.setAccessible(true);field.set(service,monitor);
            monitor.start();
            monitor.event("/dev/input/event99","EV_KEY BTN_TL2 DOWN");
            monitor.event("/dev/input/event99","EV_KEY BTN_GAMEPAD DOWN");
            service.getMouseEventHandler().handleAimTrigger("KEY_Q",1);
            monitor.event("/dev/input/event99","EV_KEY BTN_TR DOWN");
            monitor.event("/dev/input/event99","EV_KEY BTN_SELECT DOWN");
            monitor.event("/dev/input/event99","EV_KEY BTN_TR2 DOWN");
            assertEquals(5,recorder.actions.size());
            switch(lifecycle) { case "stop": service.stop();break;case "pause": service.pauseResumeKeymap();break;default:service.reloadKeymap(); }
            assertEquals(5,recorder.actions.stream().filter(action -> action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP).count());
            int size=recorder.actions.size();assertFalse(monitor.event("/dev/input/event99","EV_ABS ABS_RX 000000ff"));
            assertEquals(size,recorder.actions.size());service.stop();
        }
    }

    @Test public void foregroundExitCancelsRuntimeStartsReleasesTouchesAndRejectsUnrelatedAppInput() throws Exception {
        Recorder recorder = new Recorder();
        InputService runtime = service("KEY_F 400 500 0 150\n" + AIM + CAMERA, null, recorder);
        runtime.getKeyEventHandler().handleEvent(" EV_KEY KEY_F DOWN");
        runtime.getMouseEventHandler().handleAimTrigger("KEY_Q", 1);
        RuntimeRequestGate gate = new RuntimeRequestGate(); long delayedStart = gate.next();
        gate.invalidate(() -> runtime.pauseResumeKeymap());
        assertTrue(runtime.stopEvents);
        assertEquals(2, recorder.actions.stream().filter(action -> action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP).count());
        int size = recorder.actions.size();
        assertFalse(gate.commit(delayedStart, () -> runtime.pauseResumeKeymap()));
        // Even a racing down/move after cleanup is blocked by InputService's existing touch gate.
        runtime.injectEvent(100, 100, InputService.DOWN, 25);
        runtime.injectEvent(200, 200, InputService.MOVE, 25);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
        assertEquals(size, recorder.actions.size()); runtime.stop();
    }

    @Test public void measuredV03CombatAndUpgradeTouchesReleaseOnForegroundExitWithoutChatOrMovementInjection() throws Exception {
        // Snapshot of the verified v0.3 control contract; no controller layout is changed by this PR.
        String v03 = """
            APPLICATION com.mobile.legends
            SCREENSIZE 2280 1080
            ENABLED
            BTN_TL2 1981.08 946.58 0 150
            BTN_EAST 1341.5 955.02 0
            BTN_WEST 1172.29 970.84 0
            STICK_AIM BTN_TR 1650.44 965.04 180 1 1 0.15 0
            STICK_AIM BTN_TR2 1775.13 753.31 180 1 1 0.15 0
            STICK_AIM BTN_TL 1986.09 648.63 180 1 1 0.15 0
            STICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 0.15 0
            STICK_CAMERA 1609.8 312.19 1 1 0.15
            BTN_SELECT 1860 536 0
            BTN_THUMBR 1551 846.5 0
            BTN_THUMBL 1660.5 646 0
            """;
        Recorder recorder = new Recorder(); InputService runtime = service(v03, null, recorder);
        var monitor = new xtr.keymapper.controller.ControllerDeviceMonitor(runtime, 2280, 1080,
            () -> java.util.Map.of("pad", new long[]{1, 2, 0, 255, 128, 0, 255, 128}));
        var field = InputService.class.getDeclaredField("controllers"); field.setAccessible(true); field.set(runtime, monitor);
        monitor.start();
        for (String event : List.of("EV_KEY BTN_START DOWN", "EV_KEY BTN_START UP",
                "EV_ABS ABS_X 000000ff", "EV_ABS ABS_Y 00000000")) monitor.event("pad", event);
        assertTrue(recorder.actions.isEmpty()); // Chat and native movement have no mapped touch.
        for (String code : List.of("BTN_TL2", "BTN_GAMEPAD", "BTN_TR", "BTN_TR2", "BTN_TL",
                "BTN_SELECT", "BTN_THUMBR", "BTN_THUMBL")) monitor.event("pad", "EV_KEY " + code + " DOWN");
        assertEquals(8, recorder.actions.size());
        monitor.event("pad", "EV_ABS ABS_RX 000000ff"); monitor.event("pad", "EV_SYN SYN_REPORT 00000000");
        assertTrue(recorder.actions.contains(MotionEvent.ACTION_MOVE));
        runtime.pauseResumeKeymap();
        assertEquals(8, recorder.actions.stream().filter(action -> action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP).count());
        int size = recorder.actions.size();
        monitor.event("pad", "EV_KEY BTN_TL2 DOWN"); monitor.event("pad", "EV_KEY BTN_SELECT DOWN");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
        assertEquals(size, recorder.actions.size()); runtime.stop();
    }

    private record Event(float x,float y,int action,int pid) {}
    private static IRemoteServiceCallback callback() {
        return (IRemoteServiceCallback) Proxy.newProxyInstance(IRemoteServiceCallback.class.getClassLoader(),
                new Class[]{IRemoteServiceCallback.class},(proxy,method,args)->null);
    }
    private static class FakeInput implements IInputInterface {
        final List<Event> events=new ArrayList<>();
        final KeymapProfile profile; final KeymapConfig config=new KeymapConfig(null);
        final KeyEventHandler keys=new KeyEventHandler(this);
        final MouseEventHandler mouse=new MouseEventHandler(this);
        boolean hidden;int middleClicks,scrolls;
        FakeInput(KeymapProfile profile){this.profile=profile;config.swipeDelayMs=10;}
        public void injectEvent(float x,float y,int action,int id){events.add(new Event(x,y,action,id));}
        public void injectHoverEvent(float x,float y,int id){}
        public void injectRightClickEvent(float x,float y,int id,boolean pressed){}
        public void injectMiddleClickEvent(float x,float y,int id,boolean pressed){middleClicks++;}
        public void injectScroll(float x,float y,float value){scrolls++;}
        public void pauseResumeKeymap(){}
        public KeymapConfig getKeymapConfig(){return config;}
        public KeyEventHandler getKeyEventHandler(){return keys;}
        public MouseEventHandler getMouseEventHandler(){return mouse;}
        public KeymapProfile getKeymapProfile(){return profile;}
        public IRemoteServiceCallback getCallback(){return callback();}
        public void moveCursorX(int x){}
        public void moveCursorY(int y){}
        public void hideCursor(){hidden=true;}
        public void showCursor(){hidden=false;}
    }
}
