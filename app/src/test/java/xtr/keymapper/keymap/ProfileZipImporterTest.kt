package xtr.keymapper.keymap

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*
import java.util.zip.*
import xtr.keymapper.structured.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileZipImporterTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val legacy = "APPLICATION example.game\nSCREENSIZE 1000 1000\nKEY_Q 10 20 0\n"
    private fun zip(entries: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, text) ->
            z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry()
        } }
    }.toByteArray()
    private fun reject(bytes: ByteArray): Exception {
        val p = context.getSharedPreferences("zip", 0)
        p.edit().clear().putStringSet("existing", setOf(legacy)).commit()
        val prior = p.all.toMap()
        val files = context.cacheDir.list()!!.toSet()
        val error = assertThrows(Exception::class.java) { ProfileZipImporter.importInto(ByteArrayInputStream(bytes), context.cacheDir, p) }
        assertEquals(prior, p.all); assertEquals(files, context.cacheDir.list()!!.toSet())
        return error
    }
    @Test fun nineLegacyProfilesImportAtomicallyWithOriginalAssociation() {
        val p = context.getSharedPreferences("nine", 0); p.edit().clear().commit()
        ProfileZipImporter.importInto(ByteArrayInputStream(zip((1..9).associate { "Game $it" to legacy })), context.cacheDir, p)
        assertEquals(9, p.all.size)
        for (i in 1..9) assertEquals(ProfileConfiguration.validate(legacy), p.getStringSet("Game $i", null))
    }
    @Test fun structuredMetadataAndDisabledBindingsSurviveZip() {
        val doc = ProfileCodec.fromLegacy("structured", ProfileConfiguration.validate(legacy))
        val disabled = doc.copy(profile = doc.profile.copy(description = "Preserve metadata"), bindings = doc.bindings.map { it.copy(enabled = false, description = "Keep disabled") })
        val lines = StructuredProfiles.pack(disabled)
        val result = ProfileZipImporter.read(ByteArrayInputStream(zip(mapOf("structured" to lines.joinToString("\n")))), context.cacheDir)
        assertEquals(lines, result["structured"]); assertEquals(StructuredProfiles.document(lines), StructuredProfiles.document(result.getValue("structured")))
        assertFalse(StructuredProfiles.document(result.getValue("structured"))!!.bindings.single().enabled)
    }
    @Test fun oversizedEntryAndLineRejectBeforeValidationOrStorage() {
        assertTrue(reject(zip(mapOf("large" to "x".repeat(ProfileZipImporter.MAX_ENTRY_BYTES + 1)))).message!!.contains("entry exceeds"))
        assertTrue(reject(zip(mapOf("line" to "x".repeat(ProfileZipImporter.MAX_LINE_BYTES + 1)))).message!!.contains("line exceeds"))
    }
    @Test fun entryCountAndAggregateDecompressionAreBounded() {
        assertTrue(reject(zip((1..ProfileZipImporter.MAX_ENTRIES + 1).associate { "p$it" to legacy })).message!!.contains("entry count"))
        val text = ("KEY_Q 10 20 0\n").repeat(140000)
        assertTrue(text.length < ProfileZipImporter.MAX_ENTRY_BYTES)
        assertTrue(reject(zip((1..10).associate { "p$it" to text })).message!!.contains("total decompressed"))
    }
    @Test fun missingCentralDirectoryCorruptionAndInvalidInputAreRecoverable() {
        val good = zip(mapOf("a" to legacy))
        reject(good.copyOf(good.size - 22)); reject(byteArrayOf(1, 2, 3)); reject(zip(emptyMap()))
        val corrupt = good.copyOf()
        // Central directory CRC differs from the actual decompressed bytes.
        val i = central(corrupt); corrupt[i + 16] = (corrupt[i + 16].toInt() xor 1).toByte()
        assertTrue(reject(corrupt).message!!.contains("CRC mismatch"))
    }
    private fun central(bytes: ByteArray): Int = (0..bytes.size-4).first { bytes[it] == 0x50.toByte() && bytes[it+1] == 0x4b.toByte() && bytes[it+2] == 1.toByte() && bytes[it+3] == 2.toByte() }
    @Test fun forgedSmallDeclaredSizeCannotBypassActualInflationLimit() {
        val bytes = zip(mapOf("large" to (" ".repeat(1024) + "\n").repeat(2200)))
        val i = central(bytes)
        for (j in 0..3) bytes[i + 24 + j] = if (j == 0) 1 else 0
        assertTrue(reject(bytes).message!!.contains("decompressed byte limit"))
    }
    @Test fun duplicateInvalidAndExistingNamesNeverPartiallyWrite() {
        val duplicate = zip(linkedMapOf("aaa" to legacy, "bbb" to legacy))
        // Rename equal-length local and central names to construct a duplicate archive.
        for (i in 0..duplicate.size-3) if (duplicate[i] == 'b'.code.toByte() && duplicate[i+1] == 'b'.code.toByte() && duplicate[i+2] == 'b'.code.toByte()) for (j in 0..2) duplicate[i+j] = 'a'.code.toByte()
        assertTrue(reject(duplicate).message!!.contains("Duplicate"))
        for (name in listOf("../escape", "/absolute", "a/b", "a\\b", ".", "..")) reject(zip(mapOf(name to legacy)))
        reject(zip(linkedMapOf("new" to legacy, "existing" to legacy)))
        reject(zip(linkedMapOf("new" to legacy, "invalid" to "not a profile")))
        val badName = reject(zip(mapOf("../escape" to "x".repeat(ProfileZipImporter.MAX_ENTRY_BYTES + 1))))
        assertTrue(badName.message!!.contains("Invalid profile name"))
    }
    @Test fun compressedInputReadStopsAtLimitPlusOneAndCleansTempFile() {
        var supplied = 0
        val endless = object : InputStream() {
            override fun read() = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int { supplied += len; b.fill(0, off, off+len); return len }
        }
        val before = context.cacheDir.list()!!.toSet()
        val error = assertThrows(IOException::class.java) { ProfileZipImporter.read(endless, context.cacheDir) }
        assertTrue(error.message!!.contains("compressed byte limit"))
        assertEquals(ProfileZipImporter.MAX_ARCHIVE_BYTES + 1, supplied)
        assertEquals(before, context.cacheDir.list()!!.toSet())
    }
    @Test fun activityShowsRecoverableErrorForMalformedZipWithoutMutation() {
        val prefs = KeymapProfiles(context).sharedPref
        prefs.edit().clear().putStringSet("keep", setOf(legacy)).commit()
        val before = prefs.all.toMap()
        val uri = android.net.Uri.parse("content://test/malformed.zip")
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(byteArrayOf(1, 2, 3)))
        val controller = org.robolectric.Robolectric.buildActivity(xtr.keymapper.activity.ImportExportActivity::class.java).setup()
        try {
            org.robolectric.util.ReflectionHelpers.callInstanceMethod<Void>(controller.get(), "importProfiles",
                org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.net.Uri::class.java, uri))
            assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().startsWith("Import failed:"))
            assertEquals(before, prefs.all)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun manyShortLinesRejectedDuringInflationWithoutMutationOrTempLeak() {
        for (separator in listOf("\n", "\r\n", "\r")) {
            val text = "x$separator".repeat(ProfileZipImporter.MAX_LINES_PER_ENTRY + 1)
            assertTrue(text.length < ProfileZipImporter.MAX_ENTRY_BYTES)
            assertTrue(reject(zip(mapOf("short" to text))).message!!.contains("line count limit"))
        }
        // The last unterminated line counts, and CRLF does not consume two slots.
        val valid = legacy.trimEnd() + "\r\n" + "\r\n".repeat(ProfileZipImporter.MAX_LINES_PER_ENTRY - 4) + " "
        ProfileZipImporter.read(ByteArrayInputStream(zip(mapOf("at-limit" to valid))), context.cacheDir)
        assertTrue(reject(zip(mapOf("over-limit" to valid + "\r\nx"))).message!!.contains("line count limit"))
        val head = legacy.trimEnd()
        val crossBuffer = head + " ".repeat(8191 - head.toByteArray().size) + "\r\n" +
            "\r\n".repeat(ProfileZipImporter.MAX_LINES_PER_ENTRY - 4) + " "
        ProfileZipImporter.read(ByteArrayInputStream(zip(mapOf("cross-buffer" to crossBuffer))), context.cacheDir)
        assertTrue(reject(zip(mapOf("over-buffer" to crossBuffer + "\r\nx"))).message!!.contains("line count limit"))
    }
    @Test fun malformedUtf8RejectedWithValidZipCrcWithoutMutationOrTempLeak() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("invalid-utf8"))
            zip.write(byteArrayOf(0xc3.toByte(), 0x28))
            zip.closeEntry()
        }
        assertTrue(reject(out.toByteArray()) is java.nio.charset.CharacterCodingException)
    }
    @Test fun existingLegacyAndStructuredFixtureContainersFitLineBudget() {
        val files = File("../profiles/mlbb/samsung-s10e").listFiles()!!.filter { it.extension == "txt" }
        assertTrue(files.isNotEmpty())
        for (file in files) {
            val lines = file.readLines().toSet()
            val doc = ProfileCodec.fromLegacy(file.nameWithoutExtension, lines)
            val container = StructuredProfiles.pack(doc)
            assertTrue(lines.size <= ProfileZipImporter.MAX_LINES_PER_ENTRY)
            assertTrue(container.size <= ProfileZipImporter.MAX_LINES_PER_ENTRY)
            ProfileZipImporter.read(ByteArrayInputStream(zip(mapOf("legacy" to file.readText(), "structured" to container.joinToString("\n")))), context.cacheDir)
        }
    }

    @Test fun failedTempDeletionPreventsPersistenceAndReportsRecoverableIoError() {
        val cache = File(context.cacheDir, "delete-failure").also { check(it.mkdir()) }
        val prefs = context.getSharedPreferences("cleanup", 0)
        prefs.edit().clear().putStringSet("existing", setOf(legacy)).commit()
        val before = prefs.all.toMap()
        val input = object : ByteArrayInputStream(zip(mapOf("new" to legacy))) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = super.read(b, off, len)
                if (count == -1) check(cache.setWritable(false, false))
                return count
            }
        }
        try {
            val error = assertThrows(IOException::class.java) { ProfileZipImporter.importInto(input, cache, prefs) }
            assertTrue(error.message!!.contains("Cannot remove temporary"))
            assertEquals(before, prefs.all)
        } finally {
            check(cache.setWritable(true, true))
            cache.listFiles()!!.forEach { check(it.delete()) }
            check(cache.delete())
        }
    }

}
