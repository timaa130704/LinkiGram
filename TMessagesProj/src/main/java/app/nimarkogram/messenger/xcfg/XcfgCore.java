package app.nimarkogram.messenger.xcfg;

/*
 * Ядро Xray внутри процесса Telegram (AndroidLibXrayLite, как в X-CFG).
 * Ядро распространяется с сервера X-VPN как DEX + .so.gz с SHA-256.
 * Один загрузчик на процесс (библиотеку нельзя загрузить дважды).
 * Порт _core_* из плагина X-CFG (автор @x_vare, GPL).
 */
import android.util.Log;

import org.telegram.messenger.ApplicationLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.zip.GZIPInputStream;

import dalvik.system.DexClassLoader;

public final class XcfgCore {

    private static volatile DexClassLoader loader;
    private static volatile Object controller;

    private XcfgCore() {
    }

    static File coreDir() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "xcfgcore");
    }

    public static boolean isReady() {
        File d = coreDir();
        return new File(d, "classes.dex").exists() && new File(d, "libgojni.so").exists();
    }

    /** Скачивает ядро с сайта X-VPN (он в РФ — доступен и без VPN) и сверяет SHA-256. */
    public static void download(XcfgApi.ProgressCallback progress) throws Exception {
        File d = coreDir();
        if (!d.exists() && !d.mkdirs()) {
            throw new RuntimeException("не создан каталог ядра");
        }
        if (isReady()) {
            return;
        }
        File dexTmp = new File(d, "classes.dex.part");
        File gzTmp = new File(d, "core.gz.part");
        File soTmp = new File(d, "libgojni.so.part");
        try {
            downloadWithRetry(XcfgPrefs.CORE_BASE + XcfgPrefs.CORE_DEX, dexTmp, XcfgPrefs.CORE_DEX_SHA256, null, "dex");
            downloadWithRetry(XcfgPrefs.CORE_BASE + XcfgPrefs.CORE_SO_GZ, gzTmp, XcfgPrefs.CORE_SO_GZ_SHA256, progress, "ядро");
            try (GZIPInputStream src = new GZIPInputStream(new FileInputStream(gzTmp));
                 OutputStream dst = new FileOutputStream(soTmp)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = src.read(buf)) > 0) {
                    dst.write(buf, 0, n);
                }
            }
            gzTmp.delete();
            if (!XcfgApi.fileSha256(soTmp).equals(XcfgPrefs.CORE_SO_SHA256)) {
                throw new RuntimeException("распакованное ядро не совпало");
            }
            File so = new File(d, "libgojni.so");
            File dex = new File(d, "classes.dex");
            if (so.exists()) {
                so.delete();
            }
            if (dex.exists()) {
                dex.delete();
            }
            // Android 14+ не грузит dex/so, доступные на запись
            soTmp.setExecutable(true, false);
            soTmp.setWritable(false, false);
            if (!soTmp.renameTo(so)) {
                throw new RuntimeException("не установлен libgojni.so");
            }
            dexTmp.setReadOnly();
            if (!dexTmp.renameTo(dex)) {
                throw new RuntimeException("не установлен classes.dex");
            }
            Log.i("xcfg", "vpn: ядро скачано");
        } finally {
            dexTmp.delete();
            gzTmp.delete();
            soTmp.delete();
        }
    }

    /** Скачивание с логом этапов и fallback на Range-куски, если сервер обрывает стрим. */
    private static void downloadWithRetry(String url, File out, String sha256, XcfgApi.ProgressCallback cb, String name) throws Exception {
        try {
            Log.i("xcfg", "vpn: качаю " + name + " (прямое скачивание)");
            XcfgApi.downloadChecked(url, out, sha256, cb);
            Log.i("xcfg", "vpn: " + name + " скачан");
            return;
        } catch (Exception e) {
            Log.i("xcfg", "vpn: " + name + ": прямое скачивание не удалось (" + e + "), пробую кусками");
            out.delete();
        }
        XcfgApi.downloadChunked(url, out, sha256, cb);
        Log.i("xcfg", "vpn: " + name + " скачан (Range-кусками)");
    }

    private static void ensureLoader() throws Exception {
        if (loader != null) {
            return;
        }
        synchronized (XcfgCore.class) {
            if (loader != null) {
                return;
            }
            File d = coreDir();
            loader = new DexClassLoader(
                    new File(d, "classes.dex").getAbsolutePath(),
                    d.getAbsolutePath(),
                    d.getAbsolutePath(),
                    ApplicationLoader.applicationContext.getClassLoader());
        }
    }

    private static Object ensureController() throws Exception {
        if (controller != null) {
            return controller;
        }
        synchronized (XcfgCore.class) {
            if (controller != null) {
                return controller;
            }
            ensureLoader();
            ClassLoader cl = loader;
            Class<?> lib = cl.loadClass("libv2ray.Libv2ray");
            Class<?> iface = cl.loadClass("libv2ray.CoreCallbackHandler");
            Object cb = Proxy.newProxyInstance(cl, new Class<?>[]{iface}, (proxy, method, args) -> {
                String name = method.getName();
                if ("onEmitStatus".equals(name)) {
                    try {
                        Log.i("xcfg", "vpn: ядро: " + args[1]);
                    } catch (Exception ignore) {
                    }
                    return 0L;
                }
                if ("startup".equals(name) || "shutdown".equals(name)) {
                    return 0L;
                }
                if ("hashCode".equals(name)) {
                    return 7;
                }
                if ("equals".equals(name)) {
                    return Boolean.FALSE;
                }
                if ("toString".equals(name)) {
                    return "xcfg-core-callback";
                }
                return null;
            });
            Method newController = null;
            for (Method m : lib.getMethods()) {
                if ("newCoreController".equals(m.getName()) && m.getParameterCount() == 1) {
                    newController = m;
                    break;
                }
            }
            if (newController == null) {
                throw new RuntimeException("newCoreController не найден");
            }
            controller = newController.invoke(null, cb);
            return controller;
        }
    }

    /** Вызов метода контроллера по имени; long/int аргументы приводятся к нужному типу. */
    private static Object invoke(String name, Object... args) throws Exception {
        Object ctl = controller;
        if (ctl == null) {
            throw new RuntimeException("ядро не создано");
        }
        Exception last = null;
        for (Method m : ctl.getClass().getMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p.length != args.length) {
                continue;
            }
            Object[] fixed = new Object[args.length];
            boolean ok = true;
            for (int i = 0; i < p.length; i++) {
                Object a = args[i];
                if (a instanceof Long && (p[i] == int.class || p[i] == Integer.class)) {
                    fixed[i] = (int) (long) (Long) a;
                } else if (a instanceof Integer && (p[i] == long.class || p[i] == Long.class)) {
                    fixed[i] = (long) (int) (Integer) a;
                } else if (a != null && !p[i].isInstance(a) && !p[i].isPrimitive()) {
                    ok = false;
                    break;
                } else {
                    fixed[i] = a;
                }
            }
            if (!ok) {
                continue;
            }
            try {
                return m.invoke(ctl, fixed);
            } catch (IllegalArgumentException e) {
                last = e;
            }
        }
        throw new RuntimeException("метод ядра не найден: " + name, last);
    }

    public static boolean isRunning() {
        try {
            if (controller == null) {
                return false;
            }
            Object r = invoke("getIsRunning");
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void start(String configJson) throws Exception {
        Object ctl = ensureController();
        if (isRunning()) {
            stop();
        }
        invoke("startLoop", configJson, 0L);
        if (!isRunning()) {
            throw new RuntimeException("ядро не запустилось");
        }
    }

    public static void stop() {
        try {
            if (controller != null) {
                invoke("stopLoop");
            }
        } catch (Throwable t) {
            Log.i("xcfg", "vpn stop: " + t);
        }
    }
}
