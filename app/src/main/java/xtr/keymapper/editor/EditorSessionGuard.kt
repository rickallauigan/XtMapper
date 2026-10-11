package xtr.keymapper.editor

/** UI lifecycle lease, not an input-state manager. Observers defer activation during calibration. */
object EditorSessionGuard {
    private val owners = mutableSetOf<Any>()
    private val listeners = mutableSetOf<() -> Unit>()
    val active get() = synchronized(this) { owners.isNotEmpty() }
    fun acquire(owner: Any) { val notify = synchronized(this) { if (owners.add(owner)) listeners.toList() else emptyList() }; notify.forEach { it() } }
    fun release(owner: Any) { val notify = synchronized(this) { if (owners.remove(owner)) listeners.toList() else emptyList() }; notify.forEach { it() } }
    fun listen(listener: () -> Unit) { synchronized(this) { listeners.add(listener) } }
    fun remove(listener: () -> Unit) { synchronized(this) { listeners.remove(listener) } }
}
