package app.exteraless.player;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Cancels sockets as well as worker threads when a lyrics batch exceeds its budget. */
final class LyricsDeadline implements AutoCloseable {
    static final ThreadLocal<LyricsDeadline> CURRENT = new ThreadLocal<>();
    private static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "lyrics-deadline"); thread.setDaemon(true); return thread;
    });
    private static final java.util.concurrent.ExecutorService SOCKET_CLOSER = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "lyrics-socket-close"); thread.setDaemon(true); return thread;
    });
    private final java.util.concurrent.ConcurrentLinkedQueue<java.util.concurrent.Future<?>> tasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final Set<HttpURLConnection> connections = ConcurrentHashMap.newKeySet();
    volatile boolean active = true;
    private final ScheduledFuture<?> timer;

    LyricsDeadline(long timeoutSeconds) {
        timer = TIMER.schedule(this::close, timeoutSeconds, TimeUnit.SECONDS);
    }

    static HttpURLConnection track(HttpURLConnection connection) throws IOException {
        LyricsDeadline deadline = CURRENT.get();
        if (deadline != null) {
            if (!deadline.active || Thread.currentThread().isInterrupted()) {
                connection.disconnect(); throw new IOException("Lyrics request cancelled");
            }
            deadline.connections.add(connection);
            if (!deadline.active) {
                connection.disconnect(); throw new IOException("Lyrics request cancelled");
            }
        }
        return connection;
    }

    void add(java.util.concurrent.Future<?> task) {
        tasks.add(task);
        if (!active) task.cancel(true);
    }

    void cancelAsync() { TIMER.execute(this::close); }

    @Override public void close() {
        active = false;
        for (java.util.concurrent.Future<?> task : tasks) task.cancel(true);
        for (HttpURLConnection connection : connections) {
            // Android disconnect() can wait on a blocked TLS/socket operation. Never
            // block the deadline timer or the coordinator delivering the final UI result.
            SOCKET_CLOSER.execute(() -> {
                try { connection.disconnect(); } catch (RuntimeException ignored) { }
            });
        }
        connections.clear();
        // A timer callback can run before construction completes only for zero timeouts.
        if (timer != null) timer.cancel(false);
    }
}
