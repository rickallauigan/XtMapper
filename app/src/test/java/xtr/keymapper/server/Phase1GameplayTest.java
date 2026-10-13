package xtr.keymapper.server;

import android.os.Handler;
import android.os.Looper;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import xtr.keymapper.IRemoteServiceCallback;
import xtr.keymapper.controller.ControllerDeviceMonitor;
import xtr.keymapper.keymap.*;
import static org.junit.Assert.*;

/** Exercises the real key scheduler, controller router, service ledger and Android event assembler. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34, manifest=Config.NONE)
public class Phase1GameplayTest {
    public static class Capture {
        final List<MotionEvent> events = new ArrayList<>();
        final List<Integer> modes = new ArrayList<>();
        public boolean inject(InputEvent event, int mode) {
            events.add(MotionEvent.obtain((MotionEvent)event)); modes.add(mode); return true;
        }
        void close() { events.forEach(MotionEvent::recycle); }
    }
    private static final String BASE = "BTN_TL2 1981.08 946.58 0 150\n"
        + "STICK_AIM BTN_GAMEPAD 1477.88 982.97 180 1 1 .15 0\n"
        + "STICK_AIM BTN_TR 1650.44 965.04 180 1 1 .15 0\n"
        + "STICK_AIM BTN_TR2 1775.13 753.31 180 1 1 .15 0\n"
        + "STICK_AIM BTN_TL 1986.09 648.63 180 1 1 .15 0\n";
    private final class Rig implements AutoCloseable {
        final Capture capture = new Capture();
        final InputService service;
        final ControllerDeviceMonitor monitor;
        final Map<String,long[]> nodes=new HashMap<>();
        final Looper repeatLooper;
        Rig(boolean repeat) throws Exception {
            Input.injectInputEventMethod=Capture.class.getMethod("inject",InputEvent.class,int.class);
            Input.inputManager=capture; Input.setDisplayIdMethod=null;
            var config=new KeymapConfig(null);config.pointerMode=KeymapConfig.POINTER_OVERLAY;
            var callback=(IRemoteServiceCallback)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IRemoteServiceCallback.class},
                (p,m,a)->switch(m.getName()) {case "requestKeymapProfile"->ProfileConfiguration.parse(BASE);case "requestKeymapConfig"->config;default->null;});
            service=new InputService(ProfileConfiguration.parse(repeat?BASE:BASE.replace("0 150", "0")),config,callback,2280,1080,null,true,0);
            var handlerField=service.getKeyEventHandler().getClass().getDeclaredField("eventHandler");handlerField.setAccessible(true);
            repeatLooper=((Handler)handlerField.get(service.getKeyEventHandler())).getLooper();
            nodes.put("pad",new long[]{1,2,0,255,128,0,255,128});
            monitor=new ControllerDeviceMonitor(service,2280,1080,()->new HashMap<>(nodes));monitor.start();
            var field=InputService.class.getDeclaredField("controllers");field.setAccessible(true);field.set(service,monitor);
        }
        void button(String code, boolean down) { assertTrue(monitor.event("pad","EV_KEY "+code+(down?" DOWN":" UP"))); }
        void tick(int ms) {Shadows.shadowOf(repeatLooper).idleFor(Duration.ofMillis(ms));}
        void aim() {monitor.event("pad","EV_ABS ABS_RX 000000ff");monitor.event("pad","EV_SYN SYN_REPORT 00000000");}
        @Override public void close() { service.stop();capture.close(); }
    }
    private static void assertValid(List<MotionEvent> events) {
        Set<Integer> held=new HashSet<>();long downTime=-1;
        for(var event:events) {
            int action=event.getActionMasked(), index=event.getActionIndex();
            Set<Integer> ids=new HashSet<>();
            for(int i=0;i<event.getPointerCount();i++)assertTrue(ids.add(event.getPointerId(i)));
            if(action==MotionEvent.ACTION_DOWN) {assertTrue(held.isEmpty());downTime=event.getDownTime();}
            else assertEquals(downTime,event.getDownTime());
            assertTrue(event.getEventTime()>=event.getDownTime());
            if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)assertTrue(held.add(event.getPointerId(index)));
            assertEquals(held,ids);
            for(int i=0;i<event.getPointerCount();i++) {
                boolean releasing=(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&i==index;
                assertEquals(releasing?0:1,event.getPressure(i),0);
            }
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)assertTrue(held.remove(event.getPointerId(index)));
        }
        assertTrue("stuck Android touch IDs",held.isEmpty());
    }
    @Test public void ltAndEverySkillBothOrdersPreserveNativeAxesAndIndependentTouches() throws Exception {
        for(String skill:List.of("BTN_TR","BTN_TR2","BTN_TL","BTN_GAMEPAD"))for(boolean skillFirst:List.of(false,true)) {
            try(var rig=new Rig(true)) {
                rig.button(skillFirst?skill:"BTN_TL2",true);rig.button(skillFirst?"BTN_TL2":skill,true);
                rig.aim();rig.tick(40);rig.tick(110);rig.aim();
                int before=rig.capture.events.size();
                for(int i=0;i<100;i++) {
                    assertTrue(rig.monitor.event("pad","EV_ABS ABS_X 000000ff"));
                    assertTrue(rig.monitor.event("pad","EV_ABS ABS_Y 00000000"));
                    rig.monitor.event("pad","EV_ABS ABS_Z 000000ff");rig.monitor.event("pad","EV_ABS ABS_RZ 000000ff");
                    rig.monitor.event("pad","EV_SYN SYN_REPORT 00000000");
                }
                assertEquals("native axes/analog triggers must not synthesize touches",before,rig.capture.events.size());
                assertTrue(rig.capture.events.stream().anyMatch(e->e.getPointerCount()==2));
                rig.button(skill,false);rig.tick(40);rig.tick(110);
                assertEquals(MotionEvent.ACTION_DOWN,rig.capture.events.get(rig.capture.events.size()-1).getActionMasked());
                rig.button("BTN_TL2",false);int stopped=rig.capture.events.size();rig.tick(500);assertEquals(stopped,rig.capture.events.size());
                assertValid(rig.capture.events);
            }
        }
    }
    @Test public void ordinaryHoldAndRepeatAreDistinctAndLifecycleReleasesBoth() throws Exception {
        for(boolean repeat:List.of(false,true))for(String cleanup:List.of("release","disconnect","stop","reload","pause")) {
            try(var rig=new Rig(repeat)) {
                rig.button("BTN_TL2",true);rig.button("BTN_TR",true);rig.aim();
                int size=rig.capture.events.size();rig.tick(40);
                assertEquals(size+(repeat?1:0),rig.capture.events.size());
                switch(cleanup) {
                    case "release":rig.button("BTN_TL2",false);rig.aim();rig.button("BTN_TR",false);break;
                    case "disconnect":rig.nodes.clear();Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250));break;
                    case "reload":rig.service.reloadKeymap();break;
                    case "pause":rig.service.pauseResumeKeymap();break;
                    default:rig.service.stop();
                }
                size=rig.capture.events.size();
                if (cleanup.equals("release") || cleanup.equals("disconnect")) rig.tick(500);
                else assertFalse("shutdown must reject work on the old repeat looper",
                    new Handler(rig.repeatLooper).post(() -> fail("old scheduler ran after shutdown")));
                Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
                assertEquals(size,rig.capture.events.size());
                assertValid(rig.capture.events);
            }
        }
    }
    @Test public void concurrentTouchProducersAndHoverQueriesKeepOneValidStream() throws Exception {
        Capture capture=new Capture();Input.injectInputEventMethod=Capture.class.getMethod("inject",InputEvent.class,int.class);
        Input.inputManager=capture;Input.setDisplayIdMethod=null;var input=new Input(0);
        var workers=java.util.concurrent.Executors.newFixedThreadPool(3);
        var start=new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<?>> jobs=new ArrayList<>();
            for(int pointer:List.of(0,50))jobs.add(workers.submit(()->{
                try {start.await();}catch(InterruptedException error){throw new AssertionError(error);}
                for(int i=0;i<50;i++) {
                    input.injectTouch(MotionEvent.ACTION_DOWN,pointer,1,100+pointer,200);
                    input.injectTouch(MotionEvent.ACTION_MOVE,pointer,1,110+pointer,200);
                    input.injectTouch(MotionEvent.ACTION_UP,pointer,0,110+pointer,200);
                }
            }));
            jobs.add(workers.submit(()->{
                try {start.await();}catch(InterruptedException error){throw new AssertionError(error);}
                for(int i=0;i<200;i++)input.noPointersDown();
            }));
            start.countDown();for(var job:jobs)job.get(5,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(300,capture.events.size());assertValid(capture.events);assertTrue(input.noPointersDown());
        } finally {workers.shutdownNow();capture.close();}
    }
    @Test public void displayRoutingDoesNotBecomeSynchronousInjectionMode() throws Exception {
        Capture capture=new Capture();Input.injectInputEventMethod=Capture.class.getMethod("inject",InputEvent.class,int.class);Input.inputManager=capture;Input.setDisplayIdMethod=null;
        var input=new Input(2);input.injectTouch(0,1,1,10,20);input.injectTouch(1,1,0,10,20);
        assertEquals(List.of(0,0),capture.modes);capture.close();
    }
}
