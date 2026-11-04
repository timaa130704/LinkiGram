package app.nimarkogram.messenger.plugins;

import com.chaquo.python.PyObject;
import app.nimarkogram.messenger.plugins.utils.ClassProxyFactory;

/** Runtime-owned facade for the exteraless Java class generator. */
public final class PluginServices {
    private PluginServices() {}

    private static boolean owns(String pluginId) {
        PluginsController controller = PluginsController.getInstance();
        PluginsController.PluginRuntimeToken token = controller.captureCurrentPluginRuntime();
        return token != null && pluginId != null && pluginId.equals(token.getPluginId())
                && controller.isPluginRuntimeCurrent(token);
    }

    public static String generateProxyClass(String pluginId, String specJson) {
        if (!owns(pluginId)) {
            ClassProxyFactory.setLastError("Plugin runtime is not active");
            return null;
        }
        return ClassProxyFactory.generateProxyClass(pluginId, specJson);
    }

    public static String getProxyError() { return ClassProxyFactory.getLastError(); }

    public static Object newProxyInstance(String pluginId, String key, String signature,
                                          Object[] args, PyObject peer) {
        if (!owns(pluginId) || key == null || !key.startsWith(pluginId + ":")) return null;
        return ClassProxyFactory.newProxyInstance(pluginId, key, signature, args, peer);
    }

    public static Object getProxyClass(String key) { return ClassProxyFactory.getProxyClass(key); }

    public static Object invokeSuper(String key, Object proxy, String signature, Object[] args) {
        if (key == null) return null;
        int separator = key.indexOf(':');
        if (separator <= 0 || !owns(key.substring(0, separator))) return null;
        return ClassProxyFactory.invokeSuper(key, proxy, signature, args);
    }
}
