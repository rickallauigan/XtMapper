package xtr.keymapper.editor

import org.junit.Assert.*
import org.junit.Test

class EditorSessionGuardTest {
    @Test fun leasesDoNotRestoreWhileAnotherEditorStillExistsAndReleaseIsIdempotent() {
        val a = Any(); val b = Any(); var notifications = 0; val listener = { notifications++; Unit }
        EditorSessionGuard.listen(listener)
        try {
            EditorSessionGuard.acquire(a); EditorSessionGuard.acquire(a); EditorSessionGuard.acquire(b)
            assertTrue(EditorSessionGuard.active); assertEquals(2,notifications)
            EditorSessionGuard.release(a); assertTrue(EditorSessionGuard.active)
            EditorSessionGuard.release(a); assertEquals(3,notifications)
            EditorSessionGuard.release(b); assertFalse(EditorSessionGuard.active); assertEquals(4,notifications)
        } finally { EditorSessionGuard.remove(listener); EditorSessionGuard.release(a); EditorSessionGuard.release(b) }
    }
}
