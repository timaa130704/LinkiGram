package app.nimarkogram.messenger.plugins;

import android.app.Activity;
import android.app.AlertDialog;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Permissions for operations exposed by LinkiGram's Python plugin SDK. */
public final class PluginPermissions {
    public static final String TELEGRAM = "telegram";
    public static final String FILES = "files";

    private static final String PREFIX = "plugin_permission_";

    private PluginPermissions() {}

    public static boolean has(String pluginId, String permission) {
        if (!isKnown(permission) || pluginId == null || pluginId.isEmpty()) return false;
        return PluginsController.getInstance().preferences.getBoolean(
                key(pluginId, permission), false);
    }

    public static void set(String pluginId, String permission, boolean allowed) {
        if (!isKnown(permission) || pluginId == null || pluginId.isEmpty()) return;
        PluginsController.getInstance().preferences.edit()
                .putBoolean(key(pluginId, permission), allowed).apply();
    }

    /** Fails closed for plugin-owned calls; host calls outside a plugin runtime pass. */
    public static void require(String permission) {
        PluginsController controller = PluginsController.getInstance();
        PluginsController.PluginRuntimeToken runtime =
                controller.captureCurrentPluginRuntime();
        if (runtime == null) return;
        if (!has(runtime.getPluginId(), permission)) {
            throw new SecurityException("Plugin " + runtime.getPluginId()
                    + " has no permission: " + permission);
        }
    }

    public static void showEnablePrompt(Activity activity, String pluginId,
                                        Runnable onAllowed, Runnable onCancelled) {
        if (activity == null || activity.isFinishing() || pluginId == null) {
            if (onCancelled != null) onCancelled.run();
            return;
        }
        boolean[] checked = {has(pluginId, TELEGRAM), has(pluginId, FILES)};
        String[] labels = {
                LocaleController.getString(R.string.PluginPermissionTelegram),
                LocaleController.getString(R.string.PluginPermissionFiles)
        };
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.PluginPermissionsTitle)
                .setMultiChoiceItems(labels, checked,
                        (d, which, value) -> checked[which] = value)
                .setPositiveButton(R.string.Continue, (d, which) -> {
                    set(pluginId, TELEGRAM, checked[0]);
                    set(pluginId, FILES, checked[1]);
                    if (onAllowed != null) onAllowed.run();
                })
                .setNegativeButton(R.string.Cancel, (d, which) -> {
                    if (onCancelled != null) onCancelled.run();
                })
                .create();
        dialog.setOnCancelListener(d -> {
            if (onCancelled != null) onCancelled.run();
        });
        dialog.show();
    }

    private static boolean isKnown(String permission) {
        return TELEGRAM.equals(permission) || FILES.equals(permission);
    }

    private static String key(String pluginId, String permission) {
        return PREFIX + pluginId + '_' + permission;
    }
}
