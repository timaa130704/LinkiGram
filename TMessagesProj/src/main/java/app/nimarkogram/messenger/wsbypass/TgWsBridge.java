package app.nimarkogram.messenger.wsbypass;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import java.io.File;

/** Android lifecycle bridge to the original MIT-licensed Flowseal/tg-ws-proxy core. */
public final class TgWsBridge {
    private TgWsBridge() {}

    private static PyObject module() {
        synchronized (Python.class) {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            }
        }
        return Python.getInstance().getModule("flowseal_tg_ws.android");
    }

    public static boolean isAvailable() {
        try {
            module();
            return true;
        } catch (Throwable t) {
            FileLog.e("Flowseal proxy initialization failed", t);
            return false;
        }
    }

    public static int start(String host, int port, String dcIps, String secret, boolean verbose) {
        try {
            return module().callAttr("start", host, port, secret).toInt();
        } catch (Throwable t) {
            FileLog.e("Flowseal proxy start failed", t);
            return -100;
        }
    }

    public static void stop() {
        if (!Python.isStarted()) return;
        try { module().callAttr("stop"); }
        catch (Throwable t) { FileLog.e("Flowseal proxy stop failed", t); }
    }

    public static void setPoolSize(int size) {
        module().callAttr("set_pool_size", size);
    }

    public static void setSecret(String secret) {
        if (secret != null) module().callAttr("set_secret", secret);
    }

    public static void setCfProxy(boolean enabled, String domain) {
        module().callAttr("set_cf_proxy", enabled, domain == null ? "" : domain);
    }

    public static String stats() {
        try {
            PyObject result = module().callAttr("stats");
            return result == null ? "" : result.toString();
        } catch (Throwable t) { return ""; }
    }

    public static String secretWithPrefix() {
        return module().callAttr("secret_with_prefix").toString();
    }

    public static File cacheDir() {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "tgwsproxy");
        dir.mkdirs();
        return dir;
    }
}
