package xtr.keymapper.server;

import android.os.Looper;
import android.os.Parcel;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import xtr.keymapper.controller.*;
import xtr.keymapper.keymap.*;
import xtr.keymapper.keymap.element.AimKey;
import xtr.keymapper.server.event.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControllerRuntimeTest {
    private static final String STICK = "STICK_AIM BTN_TR 500 600 180 1 1 0.15 0\n"
        + "STICK_AIM BTN_TR2 700 600 180 1 1 0.15 0\n";
    private record Touch(float x, float y, int action, int pointer) {}
    @Test public void normalizationExtremaCenterAndAsymmetricRanges() {
        assertEquals(-1, ControllerAimHandler.normalize(0, 0, 255, 128), 0);
        assertEquals(0, ControllerAimHandler.normalize(128, 0, 255, 128), 0);
        assertEquals(1, ControllerAimHandler.normalize(255, 0, 255, 128), 0);
        assertEquals(-1, ControllerAimHandler.normalize(-40000, -32768, 32767, 0), 0);
        assertEquals(0, ControllerAimHandler.normalize(12, 12, 12, 12), 0);
    }
    @Test public void radialDeadZoneDiagonalMagnitudeAndInversion() {
        assertArrayEquals(new float[]{0,0}, ControllerAimHandler.vector(.1f,.1f,.15f,1,1,false), 0);
        float[] diagonal = ControllerAimHandler.vector(1,1,.15f,1,1,false);
        assertEquals(1, Math.hypot(diagonal[0], diagonal[1]), 0.0001);
        assertEquals(Math.sqrt(.5), diagonal[0], .0001);
        assertArrayEquals(new float[]{0,-1}, ControllerAimHandler.vector(0,1,.15f,1,1,true), .00001f);
        assertArrayEquals(new float[]{.5f,0}, ControllerAimHandler.vector(.575f,0,.15f,1,1,false), .0001f);
    }
    @Test public void displacedPressRepeatCenterReturnReleaseAndIndependentPointers() {
        List<Touch> touches = new ArrayList<>();
        ControllerAimHandler handler = new ControllerAimHandler(ProfileConfiguration.parse(STICK).aimKeys,
            (x,y,a,p) -> touches.add(new Touch(x,y,a,p)),2280,1080);
        handler.axes(1,0); handler.trigger("BTN_TR",1); handler.trigger("BTN_TR",1);
        assertEquals(2,touches.size()); assertEquals(1,touches.get(0).action);
        assertEquals(680,touches.get(1).x,0);
        handler.trigger("BTN_TR2",1);
        assertNotEquals(touches.get(0).pointer,touches.get(2).pointer);
        handler.axes(0,0); assertEquals(700,touches.get(touches.size()-1).x,0);
        handler.trigger("BTN_TR2",0); handler.trigger("BTN_TR2",0);
        handler.trigger("BTN_TR",0);
        assertEquals(2,touches.stream().filter(t -> t.action == 0).count());
        assertTrue(touches.stream().allMatch(t -> t.pointer >= 50 && t.pointer < 58));
    }
    @Test public void displayEdgesPreserveAnalogDirectionInsteadOfSkewingIt() {
        List<Touch> touches=new ArrayList<>();
        var handler=new ControllerAimHandler(ProfileConfiguration.parse("STICK_AIM BTN_TR 500 1000 180 1 1 .15 0").aimKeys,
            (x,y,action,pointer)->touches.add(new Touch(x,y,action,pointer)),2280,1080);
        handler.axes(1,1);handler.trigger("BTN_TR",1);
        Touch move=touches.get(1);assertEquals(1079,move.y,.0001f);
        assertEquals(move.y-1000,move.x-500,.0001f);
        assertTrue(Math.hypot(move.x-500,move.y-1000)<=180);handler.reset();
    }
    @Test public void centeredPressHasNoJitterAndResetReleasesAll() {
        List<Touch> touches = new ArrayList<>();
        ControllerAimHandler handler = new ControllerAimHandler(ProfileConfiguration.parse(STICK).aimKeys,
            (x,y,a,p) -> touches.add(new Touch(x,y,a,p)),2280,1080);
        handler.trigger("BTN_TR",1); handler.axes(.01f,-.02f);
        assertEquals(1,touches.size());
        handler.trigger("BTN_TR2",1); handler.reset(); handler.reset();
        assertEquals(4,touches.size());
    }
    @Test public void hatSignChangesReleaseBeforePressAndReset() {
        List<String> events = new ArrayList<>();
        ControllerDpad dpad = new ControllerDpad((code,action)->events.add(code+":"+action));
        dpad.axis(true,-1); dpad.axis(true,-1); dpad.axis(true,1); dpad.axis(true,0);
        dpad.axis(false,-1); dpad.axis(false,1); dpad.reset();
        assertEquals(List.of("DPAD_LEFT:1","DPAD_LEFT:0","DPAD_RIGHT:1","DPAD_RIGHT:0",
            "DPAD_UP:1","DPAD_UP:0","DPAD_DOWN:1","DPAD_DOWN:0"),events);
    }
    @Test public void controllerParcelableScalingAndLegacyRoundTrip() {
        KeymapProfile profile = ProfileConfiguration.parse(STICK + "BTN_GAMEPAD 900 800 0\nKEY_Q 10 20 0\nSCREENSIZE 2280 1080");
        Parcel parcel = Parcel.obtain(); profile.writeToParcel(parcel,0); parcel.setDataPosition(0);
        KeymapProfile copy = KeymapProfile.CREATOR.createFromParcel(parcel); parcel.recycle();
        assertTrue(copy.aimKeys.get(0).stick); assertEquals(.15f,copy.aimKeys.get(0).deadZone,0);
        copy.scale(1140,540); assertEquals(90,copy.aimKeys.get(0).radius,0);
        assertEquals(1,copy.aimKeys.get(0).xSensitivity,0);
        assertTrue(ProfileConfiguration.parse(copy.aimKeys.get(0).getData()).aimKeys.get(0).stick);
        assertEquals(2,profile.keys.size());
    }
    @Test public void allButtonsRoundTripAndReadableAliases() {
        for(String code:List.of("BTN_GAMEPAD","BTN_EAST","BTN_WEST","BTN_NORTH","BTN_TL","BTN_TR",
            "BTN_TL2","BTN_TR2","BTN_THUMBL","BTN_THUMBR","BTN_SELECT","BTN_START","BTN_MODE",
            "DPAD_UP","DPAD_DOWN","DPAD_LEFT","DPAD_RIGHT")) {
            KeymapProfile profile = ProfileConfiguration.parse(code+" 100 200 0");
            assertEquals(code,profile.keys.get(0).code);
            assertEquals(code,ControllerBindings.editorCode(code));
            assertFalse(ControllerBindings.label(code).startsWith("BTN_"));
        }
        assertEquals("KEY_A",ControllerBindings.editorCode("A"));
        assertEquals("BTN_GAMEPAD",ControllerBindings.canonical("BTN_SOUTH"));
        assertFalse(ControllerBindings.isBinding("KEY_BTN_GAMEPAD"));
    }
    @Test public void editorCaptureAndMarkersKeepRawCodesWithReadableLabels() {
        assertEquals("BTN_TR",ControllerBindings.capture("/dev/input/event99: EV_KEY BTN_TR DOWN"));
        assertNull(ControllerBindings.capture("/dev/input/event99: EV_KEY BTN_TR UP"));
        assertEquals("DPAD_LEFT",ControllerBindings.capture("/dev/input/event99: EV_ABS ABS_HAT0X ffffffff"));
        assertNull(ControllerBindings.capture("/dev/input/event99: EV_ABS ABS_HAT0X 00000000"));
        android.content.Context context = new android.view.ContextThemeWrapper(
            org.robolectric.RuntimeEnvironment.getApplication(),xtr.keymapper.R.style.Theme_XtMapper);
        android.widget.FrameLayout container=new android.widget.FrameLayout(context);
        xtr.keymapper.editor.EditorUiComponentCallback callback=(xtr.keymapper.editor.EditorUiComponentCallback)
            Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{xtr.keymapper.editor.EditorUiComponentCallback.class},
                (proxy,method,args)->method.getName().equals("getKeysContainerView")?container:null);
        xtr.keymapper.editor.component.Key key=new xtr.keymapper.editor.component.Key(callback,context,
            ProfileConfiguration.parse("BTN_GAMEPAD 900 800 0").keys.get(0));
        KeymapProfile imported=ProfileConfiguration.parse(key.getDataLine());
        assertEquals("BTN_GAMEPAD",imported.keys.get(0).code);
        xtr.keymapper.editor.component.AimKey aim=new xtr.keymapper.editor.component.AimKey(callback,context,
            ProfileConfiguration.parse(STICK).aimKeys.get(0));
        assertTrue(ProfileConfiguration.parse(aim.getDataLine()).aimKeys.get(0).stick);
        xtr.keymapper.floatingkeys.MovableFloatingActionKey marker=new xtr.keymapper.floatingkeys.MovableFloatingActionKey(context,container);
        marker.setText("BTN_TR");assertEquals("RB",marker.getText());
        assertTrue(marker.getData().startsWith("BTN_TR "));
        marker.setText("Q");assertTrue(marker.getData().startsWith("KEY_Q "));
    }
    @Test public void realZipImportExportPreservesControllerAndKeyboardProfiles() throws Exception {
        var controller=org.robolectric.Robolectric.buildActivity(xtr.keymapper.activity.ImportExportActivity.class);
        controller.get().setTheme(xtr.keymapper.R.style.Theme_XtMapper);
        var activity=controller.setup().get();
        KeymapProfiles profiles=new KeymapProfiles(activity);
        profiles.saveProfile("controller",new ArrayList<>(ProfileConfiguration.validate(STICK+"BTN_GAMEPAD 900 800 0")),"example.game",true,2280,1080);
        profiles.saveProfile("keyboard",new ArrayList<>(List.of("KEY_Q 10 20 0")),"example.other",true,2280,1080);
        var export=activity.getClass().getDeclaredMethod("exportProfiles",ArrayList.class);export.setAccessible(true);
        export.invoke(activity,new ArrayList<>(List.of("controller","keyboard")));
        var bytes=activity.getClass().getDeclaredField("byteArrayOutputStream");bytes.setAccessible(true);
        byte[] zip=((java.io.ByteArrayOutputStream)bytes.get(activity)).toByteArray();
        java.io.File file=java.io.File.createTempFile("controller-test", ".zip",activity.getCacheDir());
        java.nio.file.Files.write(file.toPath(),zip);
        profiles.deleteProfile("controller");profiles.deleteProfile("keyboard");
        var load=activity.getClass().getDeclaredMethod("importProfiles",android.net.Uri.class);load.setAccessible(true);
        load.invoke(activity,android.net.Uri.fromFile(file));
        assertTrue(profiles.getProfile("controller",false).aimKeys.get(0).stick);
        assertEquals("BTN_GAMEPAD",profiles.getProfile("controller",false).keys.get(0).code);
        assertEquals("KEY_Q",profiles.getProfile("keyboard",false).keys.get(0).code);
        try(var output = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(file))) {
            output.putNextEntry(new java.util.zip.ZipEntry("new-valid"));
            output.write("BTN_EAST 1 2 0\n".getBytes());output.closeEntry();
            output.putNextEntry(new java.util.zip.ZipEntry("controller"));
            output.write("STICK_AIM BTN_TR 1 2 3 1 1 2 0\n".getBytes());output.closeEntry();
        }
        load.invoke(activity,android.net.Uri.fromFile(file));
        assertFalse(profiles.getAllProfiles().containsKey("new-valid"));
        assertTrue(profiles.getProfile("controller",false).aimKeys.get(0).stick);
        file.delete();controller.pause().stop().destroy();
    }
    @Test public void pollingCachesCapabilitiesIncludingRejectedSensorsUntilIdentityChanges() {
        Map<String,long[]> identities=new HashMap<>();identities.put("controller",new long[]{1,2});identities.put("sensor",new long[]{1,3});
        java.util.concurrent.atomic.AtomicInteger probes=new java.util.concurrent.atomic.AtomicInteger();
        var discovery=new ControllerDeviceMonitor.NodeDiscovery(()->new ArrayList<>(identities.keySet()),identities::get,
            path->{probes.incrementAndGet();return path.equals("controller")?new long[]{1,2,0,255,128,0,255,128}:null;});
        assertEquals(Set.of("controller"),discovery.get().keySet());
        for(int i=0;i<100;i++)discovery.get();assertEquals(2,probes.get());
        identities.put("controller",new long[]{1,4});discovery.get();assertEquals(3,probes.get());
        identities.remove("sensor");discovery.get();identities.put("sensor",new long[]{1,5});discovery.get();assertEquals(4,probes.get());
        discovery.invalidate("controller");discovery.get();assertEquals(5,probes.get());
    }
    @Test public void controllerDiscoveryStartsForEverySupportedBindingKindOnly() {
        assertFalse(ProfileConfiguration.parse("KEY_Q 1 2 0").hasControllerBindings());
        assertFalse(ProfileConfiguration.parse("AIM_KEY BTN_RIGHT 1 2 30 1 1").hasControllerBindings());
        for(String text:List.of(STICK,"BTN_GAMEPAD 1 2 0","AIM_KEY BTN_TR 1 2 30 1 1",
            "CAMERA 1 2 1 1 1 BTN_SELECT", "MACRO id BTN_TR", "SWIPE_KEY BTN_TR 1 2 BTN_TL 3 4",
            "DPAD 0 0 50 50 50 100 100 DPAD_UP DPAD_DOWN DPAD_LEFT DPAD_RIGHT"))
            assertTrue(ProfileConfiguration.parse(text).hasControllerBindings());
    }
    @Test public void malformedControllerConfigsRejected() {
        for (String line : List.of("STICK_AIM BTN_TR 1 2 3 1 1 1 0","STICK_AIM BTN_TR 1 2 3 1 1 NaN 0",
            "STICK_AIM KEY_Q 1 2 3 1 1 .15 0","STICK_AIM BTN_TR 1 2 3 1 1 .15 2",
            "BTN_UNKNOWN 1 2 0","KEY_BTN_TR 1 2 0",STICK+"BTN_TR 1 2 0"))
            assertThrows(IllegalArgumentException.class,()->ProfileConfiguration.parse(line));
    }
    @Test public void monitorDigitalTriggersSynFramesNativeLeftStickDisconnectAndNodeReuse() {
        KeymapProfile profile = ProfileConfiguration.parse(STICK+"BTN_GAMEPAD 900 800 0\nDPAD_UP 800 300 0");
        List<Touch> touches = new ArrayList<>();
        KeyEventHandler[] keys = new KeyEventHandler[1]; MouseEventHandler[] mouse = new MouseEventHandler[1];
        KeymapConfig config = new KeymapConfig(org.robolectric.RuntimeEnvironment.getApplication());
        IInputInterface input=(IInputInterface)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IInputInterface.class},(p,m,a)->{
            switch(m.getName()) {
                case "getKeymapProfile": return profile;
                case "getKeymapConfig": return config;
                case "getKeyEventHandler": return keys[0];
                case "getMouseEventHandler": return mouse[0];
                case "injectEvent": touches.add(new Touch((Float)a[0],(Float)a[1],(Integer)a[2],(Integer)a[3])); break;
            } return null;
        });
        mouse[0]=new MouseEventHandler(input); mouse[0].init(2280,1080);
        keys[0]=new KeyEventHandler(input); keys[0].init();
        Map<String,long[]> nodes=new HashMap<>(); nodes.put("/dev/input/event99",new long[]{1,2,0,255,128,0,255,128});
        ControllerDeviceMonitor monitor=new ControllerDeviceMonitor(input,2280,1080,()->new HashMap<>(nodes)); monitor.start();
        assertTrue(monitor.event("/dev/input/event98"," EV_KEY BTN_GAMEPAD DOWN"));
        for(String line:List.of("EV_ABS ABS_X 000000ff","EV_ABS ABS_Y 00000000","EV_ABS ABS_RZ 000000ff")) monitor.event("/dev/input/event99",line);
        assertTrue(touches.isEmpty());
        monitor.event("/dev/input/event99","EV_KEY BTN_TR2 DOWN");
        monitor.event("/dev/input/event99","EV_KEY BTN_TR2 DOWN");
        monitor.event("/dev/input/event99","EV_ABS ABS_RX 000000ff");
        assertEquals(1,touches.size());
        monitor.event("/dev/input/event99","EV_SYN SYN_REPORT 00000000");
        assertEquals(880,touches.get(1).x,0);
        monitor.event("/dev/input/event99","EV_KEY BTN_GAMEPAD DOWN");
        assertEquals(900,touches.get(2).x,0);
        nodes.clear(); Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        assertEquals(2,touches.stream().filter(t->t.action==0).count());
        nodes.put("/dev/input/event99",new long[]{1,3,0,255,128,0,255,128});
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));
        monitor.event("/dev/input/event99","EV_KEY BTN_TR DOWN");
        monitor.stop();
        assertEquals(3,touches.stream().filter(t->t.action==0).count());
        nodes.clear();
        var fresh=new ControllerDeviceMonitor(input,2280,1080,()->new HashMap<>(nodes));fresh.start();
        nodes.put("/dev/input/event100",new long[]{1,4,0,255,128,0,255,128});
        assertTrue(fresh.event("/dev/input/event100","EV_KEY BTN_GAMEPAD DOWN"));
        assertTrue(fresh.event("/dev/input/event100","EV_KEY BTN_GAMEPAD UP"));
        assertEquals(4,touches.stream().filter(t->t.action==0).count());fresh.stop(); keys[0].stop(); mouse[0].stop();
    }
}
