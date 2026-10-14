package xtr.keymapper.keymap

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy
import xtr.keymapper.structured.StructuredProfiles

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileStorageTransactionsTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("transactions", 0).also { it.edit().clear().commit() }
    // Model Android's memory-before-disk behavior: apply the actual edit, then inject disk failure.
    private fun failing(real: SharedPreferences, vararg outcomes: Boolean): SharedPreferences {
        var call = 0
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            if (method.name != "edit") method.invoke(real, *(args ?: emptyArray())) else {
                val editor = real.edit()
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, edit, values ->
                    when (edit.name) {
                        "commit" -> { editor.commit(); outcomes.getOrElse(call++) { true } }
                        "apply" -> error("Unchecked persistence forbidden")
                        else -> { edit.invoke(editor, *(values ?: emptyArray())); proxy }
                    }
                }
            }
        } as SharedPreferences
    }
    private val data = setOf("APPLICATION example.game", "SCREENSIZE 1000 1000", "ENABLED", "metadata preserved verbatim")
    @Test fun renamePreservesAllLinesAndAssociationWithoutMutatingSnapshot() {
        val p = prefs(); p.edit().putStringSet("old", data).commit()
        val prior = p.getStringSet("old", null)!!.toSet()
        ProfileStorageTransactions.rename(p, "old", "new")
        assertFalse(p.contains("old")); assertEquals(prior, p.getStringSet("new", null)); assertEquals(data, prior)
    }
    @Test fun renameFailureRestoresOldAndRemovesNewAndReportsRollbackFailure() {
        for (rollback in listOf(true, false)) {
            val p = prefs(); p.edit().putStringSet("old", data).commit()
            val ex = assertThrows(IllegalStateException::class.java) { ProfileStorageTransactions.rename(failing(p, false, rollback), "old", "new") }
            assertEquals(data, p.getStringSet("old", null)); assertFalse(p.contains("new"))
            assertEquals(!rollback, ex.message!!.contains("rollback persistence also failed"))
        }
    }
    @Test fun importsAreAtomicAndFailedWriteLeavesExistingProfilesUntouched() {
        for (success in listOf(true, false)) {
            val p = prefs(); p.edit().putStringSet("existing", data).commit()
            val input = mapOf("a" to setOf("one"), "b" to setOf("two"))
            if (success) ProfileStorageTransactions.importNew(p, input)
            else assertThrows(IllegalStateException::class.java) { ProfileStorageTransactions.importNew(failing(p, false, true), input) }
            assertEquals(data, p.getStringSet("existing", null))
            assertEquals(success, p.contains("a")); assertEquals(success, p.contains("b"))
        }
    }
    @Test fun collisionAndInvalidNamesDoNotPartiallyImportOrRename() {
        val p = prefs(); p.edit().putStringSet("existing", data).putStringSet("old", setOf("original")).commit()
        val before = p.all.toMap()
        assertThrows(IllegalArgumentException::class.java) { ProfileStorageTransactions.importNew(p, linkedMapOf("new" to data, "existing" to setOf("replacement"))) }
        assertThrows(IllegalArgumentException::class.java) { ProfileStorageTransactions.rename(p, "old", "existing") }
        assertThrows(IllegalArgumentException::class.java) { ProfileStorageTransactions.rename(p, "old", "../bad") }
        assertEquals(before, p.all)
    }
    @Test fun importRollbackFailureAndSingleSaveRollbackFailureAreExplicit() {
        val p = prefs()
        val ex = assertThrows(IllegalStateException::class.java) { ProfileStorageTransactions.importNew(failing(p, false, false), mapOf("a" to data)) }
        assertTrue(ex.message!!.contains("rollback persistence also failed")); assertFalse(p.contains("a"))
        p.edit().putStringSet("old", data).commit()
        val single = assertThrows(IllegalStateException::class.java) { StructuredProfiles.writeSet(failing(p, false, false), "old", setOf("changed")) }
        assertTrue(single.message!!.contains("rollback persistence also failed")); assertEquals(data, p.getStringSet("old", null))
    }
}
