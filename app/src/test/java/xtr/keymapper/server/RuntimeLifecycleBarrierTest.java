package xtr.keymapper.server;

import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuntimeLifecycleBarrierTest {
    private static void await(CountDownLatch latch) {
        try { assertTrue("controlled operation did not complete", latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    @Test public void publicationWaitsForActualTeardownWithoutHoldingMonitor() throws Exception {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>();
        Object a = new Object(), b = new Object();
        assertTrue(gate.publishOwned(gate.next(), a));
        CountDownLatch cleanupEntered = new CountDownLatch(1), finishCleanup = new CountDownLatch(1);
        AtomicInteger shared = new AtomicInteger(1);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<?> cleanup = worker.submit(() -> gate.discardOwned(a, (candidate, release) -> {
                assertSame(a, candidate); assertTrue(release);
                cleanupEntered.countDown(); await(finishCleanup); shared.set(0);
            }));
            await(cleanupEntered);
            // This must acquire the gate monitor while cleanup is deliberately blocked.
            long next = gate.next();
            assertFalse(gate.publishOwned(next, b));
            assertEquals(1, shared.get());
            finishCleanup.countDown(); cleanup.get(5, TimeUnit.SECONDS);
            assertTrue(gate.publishOwned(next, b)); shared.set(2);
            gate.discardOwned(a, (candidate, release) -> fail("duplicate teardown"));
            assertSame(b, gate.currentOwner()); assertEquals(2, shared.get());
        } finally { finishCleanup.countDown(); worker.shutdownNow(); }
    }

    @Test public void exceptionalCleanupReleasesBarrierAndIsClaimedExactlyOnce() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>(); Object a = new Object();
        assertTrue(gate.publishOwned(gate.next(), a));
        try { gate.discardOwned(a, (candidate, shared) -> { throw new IllegalStateException("failure"); }); fail(); }
        catch (IllegalStateException expected) { }
        gate.discardOwned(a, (candidate, shared) -> fail("already claimed"));
        assertTrue(gate.publishOwned(gate.next(), new Object()));
    }

    @Test public void queuedCallbacksCannotInjectAfterPauseStopOrReplacement() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>(); Object a = new Object();
        assertTrue(gate.publishOwned(gate.next(), a));
        var beforePause = gate.captureFrame(); gate.invalidateAndGetOwner();
        assertFalse(gate.accepts(beforePause));
        var beforeStop = gate.captureFrame();
        gate.discardOwned(gate.takeOwned(gate.next()), (candidate, shared) -> {});
        assertFalse(gate.accepts(beforeStop));
        Object b = new Object(); assertTrue(gate.publishOwned(gate.next(), b));
        assertFalse(gate.accepts(beforeStop)); assertTrue(gate.accepts(gate.captureFrame()));
    }

    @Test public void disconnectAndRapidRestorationInvalidateEveryOlderCallback() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>();
        for (int i = 0; i < 50; i++) {
            Object runtime = new Object(); assertTrue(gate.publishOwned(gate.next(), runtime));
            var active = gate.captureFrame(); gate.invalidateAndGetOwner();
            assertFalse(gate.accepts(active));
            var paused = gate.captureFrame(); gate.next(); // resume / new foreground observation
            assertFalse(gate.accepts(paused));
            var disconnect = gate.captureFrame();
            gate.discardOwned(gate.takeOwned(gate.next()), (candidate, shared) -> assertTrue(shared));
            assertFalse(gate.accepts(disconnect));
        }
    }
    @Test public void delayedPauseCannotOverrideResumeOfTheSameRuntime() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>(); Object owner = new Object();
        assertTrue(gate.publishOwned(gate.next(), owner));
        long pause = gate.next(), resume = gate.next();
        assertTrue(gate.commit(resume, () -> assertSame(owner, gate.currentOwner())));
        assertFalse(gate.commit(pause, () -> fail("stale pause must not touch native resources")));
    }

    @Test public void nestedCandidateCleanupAlwaysRunsWithoutDuplicateSharedTeardown() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>(); Object a = new Object(), b = new Object();
        assertTrue(gate.publishOwned(gate.next(), a));
        AtomicInteger privateStops = new AtomicInteger(), sharedStops = new AtomicInteger();
        gate.discardOwned(a, (candidate, shared) -> {
            privateStops.incrementAndGet(); if (shared) sharedStops.incrementAndGet();
            gate.discardOwned(b, (other, release) -> {
                privateStops.incrementAndGet(); assertFalse(release);
                assertFalse(gate.publishOwned(gate.next(), new Object()));
            });
        });
        assertEquals(2, privateStops.get()); assertEquals(1, sharedStops.get());
        assertTrue(gate.publishOwned(gate.next(), new Object()));
    }

    @Test public void controllerDisconnectCancelsPendingReplacement() {
        RuntimeRequestGate<Object> gate = new RuntimeRequestGate<>(); Object first = new Object();
        assertTrue(gate.publishOwned(gate.next(), first));
        var callback = gate.captureFrame(); long replace = gate.next(); Object candidate = new Object();
        long disconnect = gate.next();
        gate.discardOwned(gate.takeOwned(disconnect), (runtime, shared) -> assertSame(first, runtime));
        assertFalse(gate.publishOwned(replace, candidate));
        gate.discardOwned(candidate, (runtime, shared) -> assertSame(candidate, runtime));
        assertNull(gate.currentOwner()); assertFalse(gate.accepts(callback));
    }

}
