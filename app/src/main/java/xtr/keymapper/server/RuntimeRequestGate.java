package xtr.keymapper.server;

/** Request identity only; never hold a monitor across runtime/Binder callbacks. */
public final class RuntimeRequestGate {
    private long generation;
    public synchronized long next() { return ++generation; }
    public synchronized boolean isCurrent(long request) { return request == generation; }
    /** Only a short state publication belongs here, never IPC or runtime initialization. */
    public synchronized boolean publish(long request, Runnable publication) {
        if (!isCurrent(request)) return false;
        publication.run();
        return true;
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
