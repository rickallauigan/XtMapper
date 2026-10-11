package xtr.keymapper.profiles

import xtr.keymapper.devices.*
import xtr.keymapper.keymap.KeymapProfile

/** Read-only policy. Provisioning and preference writes never belong here. */
object AutomaticProfileResolver {
    sealed interface Decision {
        data object Unconfigured : Decision
        data class Activate(val profile: String) : Decision
        data class Disabled(val profile: String) : Decision
        data class SelectExisting(val profiles: List<String>) : Decision
    }

    fun resolve(packageName: String, profiles: Map<String, KeymapProfile>,
                groups: List<DeviceGroup>, bindings: List<ProfileBinding>, connected: Set<String>): Decision {
        val existing = profiles.filterValues { it.packageName == packageName }
        if (existing.isEmpty()) return Decision.Unconfigured
        val preferred = DeviceGroupResolver.preferredProfile(groups, bindings, connected,
            packageName, existing.mapValues { it.value.packageName })
        val name = preferred ?: existing.keys.singleOrNull()
            ?: return Decision.SelectExisting(existing.keys.sorted())
        return if (existing.getValue(name).disabled) Decision.Disabled(name) else Decision.Activate(name)
    }
}

/** Serializes runtime commits with observation/stop; an A -> B -> A request is still stale. */
class ForegroundProfileSession {
    data class Request(val generation: Long, val packageName: String?)
    private var generation = 0L
    private var running = false
    private var packageName: String? = null

    @Synchronized fun start(): Request {
        running = true
        packageName = null
        return Request(++generation, null)
    }
    @Synchronized fun current(): Request? = if (running) Request(generation, packageName) else null
    @Synchronized fun observe(packageName: String, cleanup: () -> Unit, force: Boolean = false): Request? {
        if (!running || (!force && this.packageName == packageName)) return null
        this.packageName = packageName
        val request = Request(++generation, packageName)
        cleanup()
        return request
    }
    @Synchronized fun isCurrent(request: Request): Boolean = running &&
        generation == request.generation && packageName == request.packageName
    @Synchronized fun commit(request: Request, action: () -> Unit): Boolean {
        if (!isCurrent(request)) return false
        action()
        return true
    }
    @Synchronized fun stop(cleanup: () -> Unit) {
        running = false
        ++generation
        cleanup()
    }
}
