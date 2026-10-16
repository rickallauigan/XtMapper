package xtr.keymapper.editor

import android.content.ContextWrapper
import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class CapturedTargetStoreSafetyTest {
    private fun target(id: String) = CapturedTarget(id,id,"notes",1f,2f,100,100,123)
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("captured_targets_v1",0)
    // Same memory-before-disk fault injection convention as ProfileStorageTransactionsTest.
    private class Faults(val real: SharedPreferences, vararg val outcomes: Boolean) {
        var commits=0
        val memoryAfterCommit=mutableListOf<Map<String,*>>()
        var persisted=real.all.toMap()
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,arrayOf(SharedPreferences::class.java)) { _,m,a ->
            if(m.name!="edit") m.invoke(real,*(a?:emptyArray())) else {
                val editor=real.edit()
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,arrayOf(SharedPreferences.Editor::class.java)) { proxy,e,v ->
                    when(e.name) {
                        "commit" -> {
                            check(editor.commit()); memoryAfterCommit+=real.all.toMap()
                            outcomes.getOrElse(commits++){true}.also {if(it) persisted=real.all.toMap()}
                        }
                        "apply" -> error("Unchecked persistence forbidden")
                        else -> {e.invoke(editor,*(v?:emptyArray()));proxy}
                    }
                }
            }
        } as SharedPreferences
    }
    private fun store(p: SharedPreferences): CapturedTargetStore {
        val context=object: ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSharedPreferences(name: String,mode: Int)=p
        }
        return CapturedTargetStore(context)
    }
    @Test fun successfulCommitPersistsTargetsAndUnrelatedValues() {
        val p=prefs();check(p.edit().putString("other","unchanged").commit())
        val f=Faults(p,true);val s=store(f.prefs);s.save(emptyList(),listOf(target("first")))
        assertEquals(listOf(target("first")),s.read());assertEquals(p.all,f.persisted)
        assertEquals("unchanged",p.getString("other",null));assertEquals(1,f.commits)
    }
    @Test fun failedCommitMutatesMemoryThenRollbackRestoresExactPriorTargets() {
        val p=prefs();CapturedTargetStore(RuntimeEnvironment.getApplication()).save(emptyList(),listOf(target("first"),target("second")))
        check(p.edit().putString("other","unchanged").commit());val before=p.all.toMap()
        val f=Faults(p,false,true);val s=store(f.prefs)
        val error=assertThrows(IllegalStateException::class.java){s.save(s.read(),listOf(target("replacement")))}
        assertTrue(error.message!!.contains("prior state restored"))
        assertNotEquals(before,f.memoryAfterCommit.first());assertEquals(before,p.all);assertEquals(before,f.persisted)
        assertEquals(2,f.commits);assertEquals(listOf(target("first"),target("second")),s.read())
    }
    @Test fun failedFirstSaveRestoresMissingKeyRatherThanCreatingEmptyLibrary() {
        val p=prefs();val f=Faults(p,false,true);val s=store(f.prefs)
        assertThrows(IllegalStateException::class.java){s.save(emptyList(),listOf(target("new")))}
        assertFalse(p.contains("targets"));assertFalse(f.persisted.containsKey("targets"));assertEquals(2,f.commits)
    }
    @Test fun rollbackPersistenceFailureIsReportedWithoutClaimingRestoration() {
        val p=prefs();CapturedTargetStore(RuntimeEnvironment.getApplication()).save(emptyList(),listOf(target("old")))
        val before=p.all.toMap();val f=Faults(p,false,false);val s=store(f.prefs)
        val error=assertThrows(IllegalStateException::class.java){s.save(s.read(),listOf(target("new")))}
        assertTrue(error.message!!.contains("rollback persistence also failed"));assertFalse(error.message!!.contains("prior state restored"))
        assertEquals(before,p.all);assertEquals(before,f.persisted);assertEquals(2,f.commits)
    }
    @Test fun staleWriterRejectedBeforeAnyCommit() {
        val p=prefs();val f=Faults(p);val s=store(f.prefs);s.save(emptyList(),listOf(target("current")))
        assertThrows(IllegalArgumentException::class.java){s.save(emptyList(),listOf(target("stale")))}
        assertEquals(1,f.commits);assertEquals(listOf(target("current")),s.read())
    }
    @Test fun concurrentWritersFromSameSnapshotCannotOverwriteWinner() {
        val context=RuntimeEnvironment.getApplication();val start=CountDownLatch(1)
        val successes=CopyOnWriteArrayList<String>();val failures=CopyOnWriteArrayList<Throwable>()
        val threads=listOf("a","b").map { id -> Thread {
            try {start.await();CapturedTargetStore(context).save(emptyList(),listOf(target(id)));successes+=id}
            catch(t: Throwable){failures+=t}
        }.also {it.start()} }
        start.countDown();threads.forEach {it.join(3000);assertFalse("Writer hung",it.isAlive)}
        assertEquals(1,successes.size);assertEquals(1,failures.size);assertTrue(failures.single() is IllegalArgumentException)
        assertEquals(listOf(target(successes.single())),CapturedTargetStore(context).read())
    }
}
