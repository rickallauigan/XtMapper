package xtr.keymapper.server;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Deterministic scheduling of construction, publication and deferred cleanup. */
public class RuntimeOwnershipTest {
    private static final class Runtime {
        final String name;
        final List<String> touches;
        boolean active = true;
        boolean held;
        boolean sharedResourcesReleased;
        Runtime(String name, List<String> touches) { this.name = name; this.touches = touches; }
        void down() { if (active && !held) { held = true; touches.add(name + " down"); } }
        void pause() { active = false; if (held) { held = false; touches.add(name + " up"); } }
        void stop(boolean shared) { pause(); sharedResourcesReleased |= shared; }
    }
    private void discard(RuntimeRequestGate<Runtime> gate, Runtime runtime) {
        gate.discardOwned(runtime, Runtime::stop);
    }
    private void successorSurvives(RuntimeRequestGate<Runtime> gate, Runtime stale, Runtime newer) {
        assertSame(newer, gate.currentOwner()); assertTrue(newer.active); assertTrue(newer.held);
        assertFalse(newer.sharedResourcesReleased); assertFalse(stale.active); assertFalse(stale.held);
        int size = stale.touches.size(); stale.down(); assertEquals(size, stale.touches.size());
    }
    @Test public void olderConstructionFinishingAfterNewerStartCannotReplaceOrStopSuccessor() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        long first = gate.next(); var stale = new Runtime("first", touches); stale.down();
        long second = gate.next(); var newer = new Runtime("second", touches);
        assertTrue(gate.publishOwned(second, newer)); newer.down();
        assertFalse(gate.publishOwned(first, stale)); discard(gate, stale);
        successorSurvives(gate, stale, newer); assertFalse(stale.sharedResourcesReleased);
        assertEquals(List.of("first down", "second down", "first up"), touches);
    }
    @Test public void postPublicationStaleCheckDisposesOnlyItsOwnInstance() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        long first = gate.next(); var stale = new Runtime("first", touches);
        assertTrue(gate.publishOwned(first, stale)); stale.down();
        long second = gate.next(); Runtime detached = gate.takeOwned(second);
        var newer = new Runtime("second", touches); assertTrue(gate.publishOwned(second, newer)); newer.down();
        assertFalse(gate.isCurrent(first)); discard(gate, stale);
        successorSurvives(gate, stale, newer); assertSame(stale, detached);
    }
    @Test public void deferredPauseTargetsCapturedRuntimeEvenAfterNewerStartPublishes() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        var first = new Runtime("first", touches); assertTrue(gate.publishOwned(gate.next(), first)); first.down();
        Runtime pauseTarget = gate.invalidateAndGetOwner();
        long restored = gate.next(); discard(gate, gate.takeOwned(restored));
        var newer = new Runtime("restored", touches); assertTrue(gate.publishOwned(restored, newer)); newer.down();
        pauseTarget.pause(); successorSurvives(gate, first, newer);
    }
    @Test public void pauseDuringConstructionReleasesCandidateWithoutPublishingOrResumingIt() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        long request = gate.next(); var candidate = new Runtime("candidate", touches); candidate.down();
        assertNull(gate.invalidateAndGetOwner());
        assertFalse(gate.publishOwned(request, candidate)); discard(gate, candidate);
        assertNull(gate.currentOwner()); assertFalse(candidate.active); assertFalse(candidate.held);
        assertEquals(List.of("candidate down", "candidate up"), touches);
    }
    @Test public void deferredStopCleanupCannotDetachOrTearDownRestoredRuntime() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        var first = new Runtime("first", touches); assertTrue(gate.publishOwned(gate.next(), first)); first.down();
        long stop = gate.next(); Runtime stopTarget = gate.takeOwned(stop);
        long restart = gate.next(); var newer = new Runtime("restart", touches);
        assertTrue(gate.publishOwned(restart, newer)); newer.down(); discard(gate, stopTarget);
        successorSurvives(gate, first, newer); assertFalse(first.sharedResourcesReleased);
        assertFalse(gate.commit(stop, () -> fail("stale stop must not exit the server")));
    }
    @Test public void stopCancelsConstructionAndStaleStopCannotTakeNewerOwner() {
        var gate = new RuntimeRequestGate<Runtime>(); var touches = new ArrayList<String>();
        long start = gate.next(); var candidate = new Runtime("candidate", touches); candidate.down();
        long stop = gate.next(); assertNull(gate.takeOwned(stop));
        assertFalse(gate.publishOwned(start, candidate)); discard(gate, candidate);
        long restart = gate.next(); var newer = new Runtime("restart", touches);
        assertTrue(gate.publishOwned(restart, newer)); newer.down();
        assertNull(gate.takeOwned(stop)); successorSurvives(gate, candidate, newer);
    }
}
