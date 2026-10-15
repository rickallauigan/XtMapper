package xtr.keymapper.server;

import android.os.Handler;
import android.os.Looper;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.controller.ControllerDeviceMonitor;
import xtr.keymapper.keymap.*;
import xtr.keymapper.server.event.RepeatKeyHandler;
import static org.junit.Assert.*;

/** Host-only: real dispatch/ledger/MotionEvent code; fake injection endpoint, discovery and clock. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class ConcurrentControllerInputTest {
    record Frame(int action, int index, List<Integer> ids, List<Float> xs,
                 List<Float> ys, List<Float> pressures, int mode, int source, long down, long time) {}
    public static class Recorder {
        final List<Frame> frames = new ArrayList<>();
        public boolean inject(InputEvent input, int mode) {
            // Never assert here: reflection failures are caught by production Input.
            MotionEvent e = (MotionEvent) input;
            List<Integer> ids = new ArrayList<>();
            List<Float> xs = new ArrayList<>(), ys = new ArrayList<>(), pressures = new ArrayList<>();
            for (int i=0;i<e.getPointerCount();i++) {
                ids.add(e.getPointerId(i)); xs.add(e.getX(i)); ys.add(e.getY(i));
                pressures.add(e.getPressure(i));
            }
            frames.add(new Frame(e.getActionMasked(),e.getActionIndex(),ids,xs,ys,pressures,
                mode,e.getSource(),e.getDownTime(),e.getEventTime()));
            return true;
        }
        void validLifecycle() {
            assertFalse("Recorder must observe emitted events",frames.isEmpty());
            Set<Integer> live = new HashSet<>(); long down=-1, previous=-1;
            for (Frame f:frames) {
                assertEquals(0,f.mode); assertEquals(android.view.InputDevice.SOURCE_TOUCHSCREEN,f.source);
                for(int i=0;i<f.ids.size();i++) {
                    assertTrue(f.ids.get(i)>=0 && f.ids.get(i)<32);
                    assertTrue(Float.isFinite(f.xs.get(i))); assertTrue(Float.isFinite(f.ys.get(i)));
                    if(f.action!=MotionEvent.ACTION_UP && !(f.action==MotionEvent.ACTION_POINTER_UP && i==f.index))
                        assertTrue("Held pointer must have pressure",f.pressures.get(i)>0);
                }
                assertEquals(f.ids.size(),new HashSet<>(f.ids).size());
                assertTrue(f.index>=0 && f.index<f.ids.size()); assertTrue(f.ids.size()<=10);
                assertTrue(f.time>=previous); previous=f.time;
                int id=f.ids.get(f.index);
                switch(f.action) {
                    case MotionEvent.ACTION_DOWN -> { assertTrue(live.isEmpty()); down=f.down; assertTrue(live.add(id)); }
                    case MotionEvent.ACTION_POINTER_DOWN -> { assertFalse(live.isEmpty()); assertTrue(live.add(id)); }
                    case MotionEvent.ACTION_MOVE -> assertFalse(live.isEmpty());
                    case MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                        assertEquals(live,new HashSet<>(f.ids)); assertTrue(live.remove(id));
                        if(f.action==MotionEvent.ACTION_UP) assertTrue(live.isEmpty());
                        else assertFalse(live.isEmpty());
                    }
                    default -> fail("Unexpected touch action "+f.action);
                }
                if(f.action!=MotionEvent.ACTION_UP && f.action!=MotionEvent.ACTION_POINTER_UP)
                    assertEquals(live,new HashSet<>(f.ids));
                assertEquals(down,f.down); assertTrue(f.time>=f.down);
            }
            assertTrue("No stuck fingers",live.isEmpty());
        }
    }
    final class Harness implements AutoCloseable {
        final Recorder recorder=new Recorder();
        final java.lang.reflect.Method originalMethod=Input.injectInputEventMethod;
        final java.lang.reflect.Method originalDisplay=Input.setDisplayIdMethod;
        final Object originalManager=Input.inputManager;
        final InputService runtime;
        final ControllerDeviceMonitor monitor;
        Harness() throws Exception {
            Input.injectInputEventMethod=Recorder.class.getMethod("inject",InputEvent.class,int.class);
            Input.inputManager=recorder; Input.setDisplayIdMethod=null;
            KeymapConfig config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
            IRemoteServiceCallback callback=(IRemoteServiceCallback)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IRemoteServiceCallback.class},(p,m,a)->null);
            // Read existing committed fixture; no invented gameplay coordinates.
            String text=Files.readString(Path.of("../profiles/mlbb/samsung-s10e/mlbb-gamesir-g8-v0.3-tuned.txt"));
            runtime=new InputService(ProfileConfiguration.parse(text),config,callback,2280,1080,null,true,0);
            // Keep production repeat state machine but make its scheduler deterministic on the host.
            ReflectionHelpers.setField(runtime.getKeyEventHandler(),"repeatKeys",new RepeatKeyHandler(new Handler(Looper.getMainLooper()),runtime::injectEvent));
            monitor=new ControllerDeviceMonitor(runtime,2280,1080,()->Map.of("pad",new long[]{1,2,0,255,128,0,255,128},"other",new long[]{3,4,0,255,128,0,255,128}));
            monitor.start();
        }
        void key(String code,boolean down) { assertTrue(monitor.event("pad","EV_KEY "+code+(down?" DOWN":" UP"))); }
        void tick(long ms) { Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)); }
        void axis(String code,String value) { monitor.event("pad","EV_ABS "+code+" "+value); monitor.event("pad","EV_SYN SYN_REPORT 00000000"); }
        Map<Integer,float[]> live() { return ReflectionHelpers.getField(runtime,"activeTouches"); }
        int attackPointer() {
            var keys=runtime.getKeymapProfile().keys;
            return keys.indexOf(keys.stream().filter(k->k.code.equals("BTN_TL2")).findFirst().orElseThrow());
        }
        @Override public void close() {
            try { monitor.stop(); runtime.stop(); }
            finally {
                Input.injectInputEventMethod=originalMethod; Input.setDisplayIdMethod=originalDisplay;
                Input.inputManager=originalManager;
            }
        }
    }
    @Test public void ltFirstRepeatsWhileEachSkillMovesAndReleasesWithoutLosingOwnership() throws Exception {
        for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) try(Harness h=new Harness()) {
            h.key("BTN_TL2",true); h.key(skill,true);
            int skillPointer=h.live().keySet().stream().filter(p->p>=50&&p<58).findFirst().orElseThrow();
            float originalX=h.live().get(skillPointer)[0];
            int originalFrames=h.recorder.frames.size();
            Frame beforeMove=h.recorder.frames.get(originalFrames-1);
            int skillIndex=beforeMove.xs.indexOf(originalX); assertTrue(skillIndex>=0);
            int skillLocalId=beforeMove.ids.get(skillIndex);
            h.axis("ABS_RX","000000ff");
            assertTrue("Skill pointer actually moved",h.live().get(skillPointer)[0]>originalX);
            Frame moved=h.recorder.frames.get(h.recorder.frames.size()-1);
            assertEquals(MotionEvent.ACTION_MOVE,moved.action);
            assertTrue(moved.ids.contains(skillLocalId));
            assertEquals(h.live().get(skillPointer)[0],moved.xs.get(moved.ids.indexOf(skillLocalId)),0.001f);
            for(int i=0;i<5;i++) {
                h.tick(40);assertFalse("LT pulse releases without releasing skill",h.live().containsKey(h.attackPointer()));
                assertTrue(h.live().containsKey(skillPointer));
                h.tick(110);assertTrue("Next LT pulse starts",h.live().containsKey(h.attackPointer()));
                assertTrue(h.live().containsKey(skillPointer));
                Frame pulse=h.recorder.frames.get(h.recorder.frames.size()-1);
                assertTrue("Skill local ID survives LT pointer reuse",pulse.ids.contains(skillLocalId));
                assertEquals(h.live().get(skillPointer)[0],pulse.xs.get(pulse.ids.indexOf(skillLocalId)),0.001f);
            }
            assertTrue(h.recorder.frames.size()>=originalFrames+11);
            h.axis("ABS_RX","00000080"); // Neutral camera input before testing quiet LT cancellation.
            h.key(skill,false);assertFalse(h.live().containsKey(skillPointer));
            h.key("BTN_TL2",false);int count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());
            assertTrue(h.recorder.frames.stream().anyMatch(f->f.ids.size()==2));h.recorder.validLifecycle();
        }
    }
    @Test public void skillFirstSurvivesBothRepeatPhasesAndLtReleaseThenEmitsUp() throws Exception {
        for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) for(int phase:List.of(20,60)) try(Harness h=new Harness()) {
            h.key(skill,true);h.key("BTN_TL2",true);h.tick(phase);
            h.key("BTN_TL2",false);assertEquals(1,h.live().size());int count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());
            h.axis("ABS_RX","000000ff");assertTrue(h.recorder.frames.size()>count);
            h.key(skill,false);assertTrue(h.live().isEmpty());h.recorder.validLifecycle();
        }
    }
    @Test public void nativeLeftAxesDoNotGenerateTouchOrSuppressMappedSkillsDuringLt() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);
            for(int i=0;i<20;i++) {
                int count=h.recorder.frames.size();
                h.axis("ABS_X",i%2==0?"000000ff":"00000000");h.axis("ABS_Y","00000080");
                assertEquals(count,h.recorder.frames.size()); // Does NOT prove native/game delivery.
                h.tick(20);
            }
            assertTrue("LT continued pulsing during axis traffic",h.recorder.frames.size()>=6);
            h.key("BTN_TR",true);assertTrue(h.live().keySet().stream().anyMatch(p->p>=50&&p<58));
            h.key("BTN_TL2",false);h.key("BTN_TR",false);h.recorder.validLifecycle();
        }
    }
    @Test public void secondControllerCannotStealHeldPointersAndResetCancelsRepeat() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);h.key("BTN_TR",true);int count=h.recorder.frames.size();
            h.monitor.event("other","EV_KEY BTN_TR UP");assertEquals(count,h.recorder.frames.size());assertEquals(2,h.live().size());
            h.monitor.stop();h.runtime.stop();assertTrue(h.live().isEmpty());count=h.recorder.frames.size();h.tick(500);assertEquals(count,h.recorder.frames.size());h.recorder.validLifecycle();
        }
    }
    @Test public void allThreeSkillsStayHeldAcrossLtPulsesAndNonLifoRelease() throws Exception {
        try(Harness h=new Harness()) {
            h.key("BTN_TL2",true);
            for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL")) h.key(skill,true);
            assertEquals(4,h.live().size());
            Set<Integer> skillPointers=new HashSet<>(h.live().keySet());skillPointers.removeIf(p->p<50||p>=58);
            assertEquals(3,skillPointers.size());h.tick(340);assertTrue(h.live().keySet().containsAll(skillPointers));
            h.key("BTN_TR2",false);assertEquals(2,h.live().keySet().stream().filter(p->p>=50&&p<58).count());
            h.key("BTN_TL2",false);h.key("BTN_TR",false);h.key("BTN_TL",false);
            assertTrue(h.live().isEmpty());h.recorder.validLifecycle();
        }
    }

}
