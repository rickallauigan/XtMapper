package xtr.keymapper.server;

import org.junit.Test;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class RuntimeRequestGateTest {
    @Test public void pauseCancelsQueuedStartAndLaterGameStartCanRestore() {
        var gate = new RuntimeRequestGate(); var actions = new ArrayList<String>();
        long game = gate.next(); gate.invalidate(() -> actions.add("cleanup"));
        assertFalse(gate.commit(game, () -> actions.add("stale injection")));
        long restored = gate.next(); assertTrue(gate.commit(restored, () -> actions.add("restore")));
        assertEquals(java.util.List.of("cleanup", "restore"), actions);
    }
    @Test public void latestStartWinsAndStopInvalidatesEveryPendingStart() {
        var gate = new RuntimeRequestGate(); long first = gate.next(); long latest = gate.next();
        assertFalse(gate.commit(first, () -> fail("stale")));
        gate.next(); assertFalse(gate.commit(latest, () -> fail("stopped")));
    }
    @Test public void runtimeCallbackCanInvalidateOnAnotherThreadWithoutDeadlock() throws Exception {
        var gate = new RuntimeRequestGate(); long request = gate.next();
        var completed = new java.util.concurrent.CountDownLatch(1);
        assertFalse(gate.commit(request, () -> {
            Thread callback = new Thread(() -> { gate.invalidate(() -> {}); completed.countDown(); });
            callback.start();
            try { assertTrue(completed.await(2, java.util.concurrent.TimeUnit.SECONDS)); }
            catch (InterruptedException error) { throw new AssertionError(error); }
        }));
    }
    @Test public void pauseDuringConstructionPreventsRuntimePublication() {
        var gate = new RuntimeRequestGate(); long request = gate.next();
        var published = new ArrayList<String>();
        gate.commit(request, () -> {
            gate.invalidate(() -> {});
            assertFalse(gate.publish(request, () -> published.add("runtime")));
        });
        assertTrue(published.isEmpty());
        long restored = gate.next(); assertTrue(gate.publish(restored, () -> published.add("restored")));
        assertEquals(java.util.List.of("restored"), published);
    }
}
