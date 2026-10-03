package app.nimarkogram.messenger.wsbypass;

import java.io.File;

/**
 * JNI bridge to the embedded tg-ws-proxy-android core (GPL-3.0).
 *
 * <p>tg-ws-linki is a Rust MTProto proxy that keeps a WebSocket pool to the Telegram
 * datacenters. Unlike the in-house {@link WsBypassCore} route it is a proven upstream
 * implementation whose transport survives networks that throttle the Java one.
 */
public final class TgWsBridge {

    private static volatile boolean available;
    private static boolean checked;

    private TgWsBridge() {}

    private static native int nativeStart(String host, int port, String dcIps, String secret, boolean verbose);
    private static native int nativeStop();
    private static native void nativeSetPoolSize(int size);
    private static native void nativeSetSecret(String secret);
    private static native void nativeSetCfProxy(boolean enabled, String domain);
    private static native String nativeStats();
    private static native String nativeSecretWithPrefix();

    /** True when the embedded core loaded and the symbols resolved. */
    public static boolean isAvailable() {
        if (!checked) {
            synchronized (TgWsBridge.class) {
                if (!checked) {
                    boolean ok;
                    try {
                        nativeSetPoolSize(4);
                        ok = true;
                    } catch (Throwable t) {
                        ok = false;
                    }
                    available = ok;
                    checked = true;
                }
            }
        }
        return available;
    }

    /**
     * Starts the local proxy.
     *
     * @return 0 on success, negative on failure.
     */
    public static int start(String host, int port, String dcIps, String secret, boolean verbose) {
        if (!isAvailable()) return -100;
        try {
            return nativeStart(host, port, dcIps == null ? "" : dcIps, secret, verbose);
        } catch (Throwable t) {
            return -100;
        }
    }

    public static void stop() {
        if (!isAvailable()) return;
        try {
            nativeStop();
        } catch (Throwable ignored) {
        }
    }

    public static void setPoolSize(int size) {
        if (!isAvailable()) return;
        try {
            nativeSetPoolSize(size);
        } catch (Throwable ignored) {
        }
    }

    public static void setSecret(String secret) {
        if (!isAvailable() || secret == null) return;
        try {
            nativeSetSecret(secret);
        } catch (Throwable ignored) {
        }
    }

    public static void setCfProxy(boolean enabled, String domain) {
        if (!isAvailable()) return;
        try {
            nativeSetCfProxy(enabled, domain == null ? "" : domain);
        } catch (Throwable ignored) {
        }
    }

    public static String stats() {
        if (!isAvailable()) return "";
        try {
            String s = nativeStats();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    public static String secretWithPrefix() {
        if (!isAvailable()) return "";
        try {
            String s = nativeSecretWithPrefix();
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /** Cache dir the core may use for its optional config fetches. */
    public static File cacheDir() {
        try {
            File dir = new File(org.telegram.messenger.ApplicationLoader.applicationContext.getCacheDir(), "tgwsproxy");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            return dir;
        } catch (Throwable t) {
            return null;
        }
    }
}
