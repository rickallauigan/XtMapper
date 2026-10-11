package xtr.keymapper.server;

/** Request identity only; never hold a monitor across runtime/Binder callbacks. */
public final class RuntimeRequestGate<T> {
    private volatile T owner;
    private long generation;
    public synchronized long next() { return ++generation; }
    public synchronized boolean isCurrent(long request) { return request == generation; }
    /** Only a short state publication belongs here, never IPC or runtime initialization. */
    public synchronized boolean publish(long request, Runnable publication) {
        if (!isCurrent(request)) return false;
        publication.run();
        return true;
    }
    public T currentOwner() { return owner; }
    public synchronized boolean publishOwned(long request, T candidate) {
        if (!isCurrent(request) || owner != null) return false;
        owner = candidate;
        return true;
    }
    public synchronized T takeOwned(long request) {
        if (!isCurrent(request)) return null;
        T previous = owner;
        owner = null;
        return previous;
    }
    public synchronized T invalidateAndGetOwner() {
        ++generation;
        return owner;
    }
    /** Detach only this candidate. Cleanup never receives a successor instance. */
    public void discardOwned(T candidate, java.util.function.BiConsumer<T, Boolean> cleanup) {
        if (candidate == null) return;
        boolean noSuccessor;
        synchronized (this) {
            if (owner == candidate) owner = null;
            noSuccessor = owner == null;
        }
        // Native initialization/teardown is serialized on the remote main looper.
        // Process-wide native/client resources must be left alone if a successor owns them.
        cleanup.accept(candidate, noSuccessor);
    }
    public boolean commit(long request, Runnable action) {
        if (!isCurrent(request)) return false;
        action.run();
        return isCurrent(request);
    }
    public void invalidate(Runnable cleanup) {
        next();
        cleanup.run();
    }
}
