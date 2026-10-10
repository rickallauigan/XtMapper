package xtr.keymapper.profiles

import org.junit.Assert.*
import org.junit.Test
import xtr.keymapper.devices.*
import xtr.keymapper.keymap.KeymapProfile

class AutomaticProfileResolverTest {
    private val mlbb = "com.mobile.legends"
    private fun profile(enabled: Boolean = true) = KeymapProfile().apply {
        packageName = mlbb; disabled = !enabled
    }
    private val group = DeviceGroup("g8", "GameSir G8 Setup", listOf(DeviceMember("g8", "G8")), 1)
    private val binding = ProfileBinding("g8", mlbb, "G8 v0.3")
    private fun resolve(profiles: Map<String, KeymapProfile>, connected: Set<String> = setOf("g8"),
                        packageName: String = mlbb) = AutomaticProfileResolver.resolve(packageName, profiles,
        listOf(group), listOf(binding), connected)

    @Test fun unknownAppIsPassiveEvenWithPreferredBinding() {
        val profiles = mapOf("G8 v0.3" to profile())
        assertEquals(AutomaticProfileResolver.Decision.Unconfigured, resolve(profiles, packageName = "file.manager"))
        assertEquals(setOf("G8 v0.3"), profiles.keys)
        assertEquals("G8 v0.3", binding.profileName)
    }
    @Test fun singletonAndPreferredProfileActivateWithoutSelection() {
        assertEquals(AutomaticProfileResolver.Decision.Activate("KBM"), resolve(mapOf("KBM" to profile())))
        assertEquals(AutomaticProfileResolver.Decision.Activate("G8 v0.3"),
            resolve(mapOf("KBM" to profile(), "G8 v0.3" to profile())))
    }
    @Test fun disabledSingletonAndPreferredRemainDisabled() {
        assertEquals(AutomaticProfileResolver.Decision.Disabled("KBM"), resolve(mapOf("KBM" to profile(false))))
        val disabled = profile(false)
        assertEquals(AutomaticProfileResolver.Decision.Disabled("G8 v0.3"),
            resolve(mapOf("KBM" to profile(), "G8 v0.3" to disabled)))
        assertTrue(disabled.disabled)
    }
    @Test fun missingPreferredAndDisconnectedControllerFallBackDeterministically() {
        assertEquals(AutomaticProfileResolver.Decision.SelectExisting(listOf("a", "z")),
            resolve(mapOf("z" to profile(), "a" to profile())))
        assertEquals(AutomaticProfileResolver.Decision.SelectExisting(listOf("G8 v0.3", "KBM")),
            resolve(mapOf("KBM" to profile(), "G8 v0.3" to profile()), emptySet()))
        assertEquals(AutomaticProfileResolver.Decision.Unconfigured, resolve(emptyMap()))
    }
    @Test fun rapidTransitionsInvalidateAllEarlierRequestsIncludingSamePackage() {
        val session = ForegroundProfileSession(); session.start()
        var pauses = 0
        val first = session.observe(mlbb, { pauses++ })!!
        val file = session.observe("files", { pauses++ })!!
        val restored = session.observe(mlbb, { pauses++ })!!
        val actions = mutableListOf<String>()
        assertFalse(session.commit(first) { actions += "old game" })
        assertFalse(session.commit(file) { actions += "files" })
        assertTrue(session.commit(restored) { actions += "G8 v0.3" })
        assertEquals(listOf("G8 v0.3"), actions); assertEquals(3, pauses)
        assertNull(session.observe(mlbb, { pauses++ }))
    }
    @Test fun stopAndConfigurationRefreshInvalidateDelayedCommits() {
        val session = ForegroundProfileSession(); session.start()
        val request = session.observe(mlbb, {})!!
        val disconnected = session.observe(mlbb, {}, true)!!
        assertFalse(session.isCurrent(request))
        session.stop {}
        assertFalse(session.commit(disconnected) { fail("stopped") })
        assertNull(session.observe(mlbb, {}))
        session.start()
        assertFalse(session.isCurrent(disconnected))
    }
}
