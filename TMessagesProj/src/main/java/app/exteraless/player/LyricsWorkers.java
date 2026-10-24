package app.exteraless.player;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Keep a stalled public provider from preventing Liner from starting on the next track. */
final class LyricsWorkers {
    // Allow the old and new track to overlap while Android releases cancelled sockets.
    // Never queue a new track behind sockets which may ignore interruption. If both
    // generations are still stuck, reject promptly so the caller can report an error.
    private static final ExecutorService PUBLIC = create(10, "lyrics-provider");
    private static final ExecutorService LINER = create(2, "lyrics-liner");
    private static final ExecutorService CATALOG = create(2, "lyrics-catalog");

    private static ExecutorService create(int maximum, String name) {
        return new ThreadPoolExecutor(0, maximum, 30, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, name);
                    thread.setDaemon(true);
                    return thread;
                });
    }

    static Future<?> submit(boolean liner, Runnable task) {
        return (liner ? LINER : PUBLIC).submit(task);
    }

    static <T> Future<T> submitCatalog(java.util.concurrent.Callable<T> task) {
        return CATALOG.submit(task);
    }

    private LyricsWorkers() { }
}
