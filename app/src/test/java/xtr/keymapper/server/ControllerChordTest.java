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
import xtr.keymapper.server.event.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ControllerChordTest {
    private static final String CHORDS = "CHORD BTN_SELECT BTN_TR 100 200 0\n"
        + "CHORD BTN_SELECT BTN_TR2 300 200 0\nCHORD BTN_SELECT BTN_TL 500 200 0\n";
    private record Touch(float x, float y, int action, int pointer) {}

    @Test public void precedencePressOrderDuplicateAndEarlyModifierRelease() {
        List<Touch> touches = new ArrayList<>();
        var handler = new ControllerChordHandler(ProfileConfiguration.parse(CHORDS).chords,
            (x,y,a,p)->touches.add(new Touch(x,y,a,p)));
        assertTrue(handler.event("BTN_SELECT",1));
        for (String trigger : List.of("BTN_TR","BTN_TR2","BTN_TL")) {
            assertTrue(handler.event(trigger,1)); assertTrue(handler.event(trigger,1));
        }
        assertEquals(3,touches.size());
        assertEquals(Set.of(59,60,61),new HashSet<>(touches.stream().map(Touch::pointer).toList()));
        handler.event("BTN_SELECT",0);
        for (String trigger : List.of("BTN_TR","BTN_TR2","BTN_TL")) assertTrue(handler.event(trigger,0));
        assertEquals(6,touches.size());
        assertFalse(handler.event("BTN_TR",1));
        handler.event("BTN_SELECT",1);
        assertFalse(handler.event("BTN_TR",0)); // No retroactive conversion.
        assertTrue(handler.event("BTN_TR",1));
        handler.reset(); int count=touches.size(); handler.reset();
        assertEquals(count,touches.size()); assertTrue(handler.event("BTN_TR",0));
        assertEquals(touches.stream().filter(t->t.action==1).count(),touches.stream().filter(t->t.action==0).count());
    }
    @Test public void validationPersistenceScalingAndEditorRoundTrip() {
        var profile=ProfileConfiguration.parse("SCREENSIZE 1000 500\n"+CHORDS);
        assertTrue(profile.hasControllerBindings());
        Parcel parcel=Parcel.obtain();profile.writeToParcel(parcel,0);parcel.setDataPosition(0);
        var copy=KeymapProfile.CREATOR.createFromParcel(parcel);parcel.recycle();
        copy.scale(500,250);assertEquals(50,copy.chords.get(0).x,0);
        assertEquals("SELECT + RB",copy.chords.get(0).label());
        var context=new android.view.ContextThemeWrapper(org.robolectric.RuntimeEnvironment.getApplication(),xtr.keymapper.R.style.Theme_XtMapper);
        var container=new android.widget.FrameLayout(context);
        var callback=(xtr.keymapper.editor.EditorUiComponentCallback)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{xtr.keymapper.editor.EditorUiComponentCallback.class},
            (p,m,a)->m.getName().equals("getKeysContainerView")?container:null);
        var marker=new xtr.keymapper.editor.component.ControllerChord(callback,context,copy.chords.get(0));
        assertEquals("BTN_TR",ProfileConfiguration.parse(marker.getDataLine()).chords.get(0).trigger);
        var store=new KeymapProfiles(context);
        store.saveProfile("chords",new ArrayList<>(ProfileConfiguration.validate(CHORDS)),"example.game",true,1000,500);
        assertEquals(3,store.getProfile("chords",false).chords.size());
        for(String invalid:List.of("CHORD BTN_SELECT BTN_TR 1 2", "CHORD KEY_A BTN_TR 1 2 0",
            "CHORD BTN_SELECT BTN_SELECT 1 2 0", "CHORD BTN_SELECT BTN_TR NaN 2 0",
            CHORDS+"CHORD BTN_MODE BTN_TR 1 2 0", CHORDS+"BTN_SELECT 1 2 0",
            CHORDS+"STICK_AIM BTN_SELECT 1 2 30 1 1 .15 0",
            CHORDS+"CHORD BTN_TR BTN_EAST 1 2 0",CHORDS+"MACRO id BTN_SELECT",
            CHORDS+"SWIPE_KEY BTN_SELECT 1 2 BTN_EAST 3 4",
            CHORDS+"CAMERA 1 2 1 1 1 BTN_SELECT"))
            assertThrows(invalid,IllegalArgumentException.class,()->ProfileConfiguration.parse(invalid));
    }
    private static final class Rig {
        final List<Touch> touches=new ArrayList<>();
        final Map<String,long[]> nodes=new HashMap<>();
        final ControllerDeviceMonitor monitor;
        final KeyEventHandler keys;
        final MouseEventHandler mouse;
        Rig(String text) {
            var profile=ProfileConfiguration.parse(text);
            var config=new KeymapConfig(org.robolectric.RuntimeEnvironment.getApplication());
            KeyEventHandler[] kh=new KeyEventHandler[1];MouseEventHandler[] mh=new MouseEventHandler[1];
            IInputInterface input=(IInputInterface)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IInputInterface.class},(p,m,a)->{
                switch(m.getName()) {
                    case "getKeymapProfile":return profile;
                    case "getKeymapConfig":return config;
                    case "getKeyEventHandler":return kh[0];
                    case "getMouseEventHandler":return mh[0];
                    case "injectEvent":touches.add(new Touch((Float)a[0],(Float)a[1],(Integer)a[2],(Integer)a[3]));break;
                }return null;
            });
            mouse=mh[0]=new MouseEventHandler(input);mouse.init(2280,1080);
            keys=kh[0]=new KeyEventHandler(input);keys.init();
            monitor=new ControllerDeviceMonitor(input,2280,1080,()->new HashMap<>(nodes));monitor.start();
        }
        void connect(){nodes.put("pad",new long[]{1,2,0,255,128,0,255,128});}
        void key(String code,String action){monitor.event("pad","EV_KEY "+code+" "+action);}
        void close(){monitor.stop();keys.stop();mouse.stop();}
    }
    @Test public void monitorChordsSuppressAimCoexistWithAttackAndCleanUpOnLoss() {
        String aims="STICK_AIM BTN_TR 800 700 180 1 1 .15 0\n"
            +"STICK_AIM BTN_TR2 1000 700 180 1 1 .15 0\nSTICK_AIM BTN_TL 1200 700 180 1 1 .15 0\n";
        for(String cleanup:List.of("stop","drop","disconnect","replace")) {
            var rig=new Rig(CHORDS+aims+"BTN_TL2 900 900 0 150\nSTICK_CAMERA 1600 300 1 1 .15");
            rig.connect();rig.key("BTN_SELECT","DOWN");
            for(String code:List.of("BTN_TR","BTN_TR2","BTN_TL"))rig.key(code,"DOWN");
            rig.key("BTN_TL2","DOWN");
            rig.monitor.event("pad","EV_ABS ABS_RX 000000ff");rig.monitor.event("pad","EV_SYN SYN_REPORT 00000000");
            assertTrue(rig.touches.stream().noneMatch(t->t.pointer>=50&&t.pointer<=57));
            assertEquals(3,rig.touches.stream().filter(t->t.pointer>=59&&t.action==1).count());
            switch(cleanup) {
                case "stop":rig.monitor.stop();break;
                case "drop":rig.monitor.event("pad","EV_SYN SYN_DROPPED 00000000");break;
                case "disconnect":rig.nodes.clear();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));break;
                case "replace":rig.nodes.put("pad",new long[]{1,3,0,255,128,0,255,128});Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));break;
            }
            assertEquals(3,rig.touches.stream().filter(t->t.pointer>=59&&t.action==0).count());
            rig.monitor.stop();int count=rig.touches.size();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));assertEquals(count,rig.touches.size());
            rig.connect();rig.monitor.start();rig.key("BTN_TR","DOWN");
            assertEquals(50,rig.touches.get(rig.touches.size()-1).pointer); // Modifier cleared.
            rig.key("BTN_TR","UP");rig.close();
        }
    }
    @Test public void firstHatEventDiscoversReconnectedDeviceAndDigitalDpadAliasesWork() {
        var rig=new Rig("DPAD_LEFT 100 200 0\nDPAD_RIGHT 300 200 0");rig.connect();
        assertTrue(rig.monitor.event("pad","EV_ABS ABS_HAT0X ffffffff"));
        rig.monitor.event("pad","EV_ABS ABS_HAT0X 00000001");
        rig.monitor.event("pad","EV_ABS ABS_HAT0X 00000000");
        assertEquals(List.of(1,0,1,0),rig.touches.stream().map(Touch::action).toList());
        rig.key("BTN_DPAD_LEFT","DOWN");rig.key("BTN_DPAD_LEFT","UP");
        assertEquals(6,rig.touches.size());rig.close();
        assertEquals("DPAD_LEFT",ControllerBindings.capture("/dev/input/event7: EV_KEY BTN_DPAD_LEFT DOWN"));
    }
    @Test public void v02AttackSpellAndSkillsKeepIndependentPointersAndDigitalOnlyActivation() {
        var rig=new Rig("BTN_TL2 1981.08 946.58 0 150\nSTICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 .15 0\n"
            +"STICK_AIM BTN_TR 1650.44 965.04 180 1 1 .15 0\nSTICK_AIM BTN_TR2 1775.13 753.31 180 1 1 .15 0\n"
            +"STICK_AIM BTN_TL 1986.09 648.63 180 1 1 .15 0");
        rig.connect();rig.key("BTN_TL2","DOWN");rig.key("BTN_TL2","DOWN");
        for(String code:List.of("BTN_GAMEPAD","BTN_TR","BTN_TR2","BTN_TL"))rig.key(code,"DOWN");
        assertEquals(5,rig.touches.size());
        for(String axis:List.of("ABS_X","ABS_Y","ABS_Z","ABS_RZ"))rig.monitor.event("pad","EV_ABS "+axis+" 000000ff");
        assertEquals(5,rig.touches.size());
        rig.monitor.event("pad","EV_ABS ABS_RX 000000ff");rig.monitor.event("pad","EV_SYN SYN_REPORT 00000000");
        assertEquals(53,rig.touches.get(5).pointer);
        for(String code:List.of("BTN_GAMEPAD","BTN_TR","BTN_TR2","BTN_TL","BTN_TL2"))rig.key(code,"UP");
        rig.close();assertEquals(5,rig.touches.stream().filter(t->t.action==0).count());
    }
}
