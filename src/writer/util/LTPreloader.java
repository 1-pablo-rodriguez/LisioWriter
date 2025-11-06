// writer.util.LTPreloader
package writer.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import writer.spell.SpellCheckLT;

public final class LTPreloader {
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lt-preloader");
        t.setDaemon(true);
        return t;
    });

    // accessible pour vérifier l'état si besoin
    static volatile CompletableFuture<Void> preloadFuture;

    private LTPreloader() {}

    // writer.util.LTPreloader.preloadInBackground()
    public static void preloadInBackground() {
        if (preloadFuture == null) {
            synchronized (LTPreloader.class) {
                if (preloadFuture == null) {
                    preloadFuture = SpellCheckLT.preloadInBackground();
                }
            }
        }
    }

    public static boolean isPreloaded() {
        return preloadFuture != null && preloadFuture.isDone();
    }

    public static void shutdown() {
        EXEC.shutdownNow();
    }
}