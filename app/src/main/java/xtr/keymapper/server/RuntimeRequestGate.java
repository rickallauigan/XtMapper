package xtr.keymapper.server;

/** Lifecycle identity and teardown barrier; never hold a monitor across runtime/Binder callbacks. */
public final class RuntimeRequestGate<T> {
    private volatile T owner;
    private long generation;
    private int cleanupsInProgress;
    private int lifecycleActions;
    private final java.util.List<java.lang.ref.WeakReference<T>> disposed = new java.util.ArrayList<>();
    public synchronized long currentRequest() { return generation; }
    public synchronized long next() { return ++generation; }
    public synchronized boolean isCurrent(long request) { return request == generation; }
    /** Only a short state publication belongs here, never IPC or runtime initialization. */
    public synchronized boolean publish(long request, Runnable publication) {
        if (!isCurrent(request)) return false;
        publication.run();
        return true;
    }
    public T currentOwner() { return owner; }
    public static final class Frame<T> {
        private final long generation;
        public final T owner;
        private Frame(long generation, T owner) { this.generation = generation; this.owner = owner; }
    }
    public synchronized Frame<T> captureFrame() { return new Frame<>(generation, owner); }
    /** Called on the lifecycle looper, before dispatching any queued input. */
    public synchronized boolean accepts(Frame<T> frame) {
        return frame.generation == generation && frame.owner == owner && cleanupsInProgress == 0 && lifecycleActions == 0;
    }
    public synchronized boolean publishOwned(long request, T candidate) {
        if (!isCurrent(request) || owner != null || cleanupsInProgress != 0) return false;
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
    /** Nonblocking publication barrier; no monitor is held during cleanup/IPC. */
    public void discardOwned(T candidate, java.util.function.BiConsumer<T, Boolean> cleanup) {
        if (candidate == null) return;
        boolean releaseSharedResources;
        synchronized (this) {
            disposed.removeIf(reference -> reference.get() == null);
            for (java.lang.ref.WeakReference<T> reference : disposed)
                if (reference.get() == candidate) return;
            disposed.add(new java.lang.ref.WeakReference<>(candidate));
            if (owner == candidate) owner = null;
            releaseSharedResources = owner == null && cleanupsInProgress == 0;
            ++cleanupsInProgress;
        }
        try {
            cleanup.accept(candidate, releaseSharedResources);
        } finally {
            synchronized (this) { --cleanupsInProgress; }
        }
    }
    public boolean commit(long request, Runnable action) {
        synchronized (this) {
            if (!isCurrent(request)) return false;
            ++lifecycleActions;
        }
        try {
            action.run();
            return isCurrent(request);
        } finally {
            synchronized (this) { --lifecycleActions; }
        }
    }
    public void invalidate(Runnable cleanup) {
        next();
        cleanup.run();
    }
}
