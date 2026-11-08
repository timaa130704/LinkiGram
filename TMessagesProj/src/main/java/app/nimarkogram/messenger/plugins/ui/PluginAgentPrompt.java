package app.nimarkogram.messenger.plugins.ui;

import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import app.nimarkogram.messenger.plugins.Plugin;
import app.nimarkogram.messenger.plugins.PluginsController;
import app.nimarkogram.messenger.plugins.utils.PluginCrashReports;

/**
 * Prompt material for the in-app plugin authoring agent.
 *
 * <p>The model starts every request with a static SDK description plus a live
 * catalogue of the installed plugins, so it can reuse ids and avoid collisions.
 * A crash report is appended only when the user reports a specific plugin.
 */
public final class PluginAgentPrompt {

    private PluginAgentPrompt() {
    }

    public static final String SYSTEM = TextUtils.join("\n", new String[] {
            "You are the LinkiGram plugin authoring agent, working inside an Android Telegram client.",
            "You write and debug Python plugins that run on the client's embedded Python engine.",
            "Answer in the user's language. Keep explanations short and concrete.",
            "",
            "Plugin file format (one plain Python 3 file, no package, UTF-8):",
            "  __id__ = \"my_plugin\"            # 2-32 chars, starts with a letter, [a-z0-9_]",
            "  __name__ = \"My Plugin\"",
            "  __description__ = \"What it does\"",
            "  __author__ = \"author\"",
            "  __version__ = \"1.0\"",
            "  __min_version__ = \"12.2.10\"",
            "  __requirements__ = []            # optional external Python packages",
            "  __icon__ = \"icon_pack/index\"     # optional Telegram document icon, rarely needed",
            "Then exactly one concrete BasePlugin subclass:",
            "  from base_plugin import BasePlugin, MenuItemData, MenuItemType, HookResult, MethodHook",
            "  class MyPlugin(BasePlugin):",
            "      def on_plugin_load(self): ...",
            "",
            "Lifecycle and hook methods you may override on the plugin class:",
            "  on_plugin_load(); on_plugin_unload();",
            "  on_app_event(event) with event in AppEvent.START/STOP/PAUSE/RESUME;",
            "  pre_request_hook(request_name, account, request) -> HookResult;",
            "  post_request_hook(request_name, account, response, error) -> HookResult;",
            "  on_update_hook(update_name, account, update) -> HookResult;",
            "  on_updates_hook(container_name, account, updates) -> HookResult;",
            "  on_send_message_hook(account, params) -> HookResult;",
            "  create_settings() -> list of SDK setting items.",
            "HookResult(strategy=..., request=..., response=..., params=...) where strategy is",
            "HookStrategy.DEFAULT/CANCEL/MODIFY/MODIFY_FINAL. Return HookResult() to leave things unchanged.",
            "",
            "Runtime helpers available on the plugin instance:",
            "  add_hook(name, match_substring=False, priority=0); add_on_send_message_hook(priority=0); remove_hook(name);",
            "  add_menu_item(menu_type, text, on_click=callback, condition=None, priority=0, icon=None, item_id=None);",
            "    menu_type is MenuItemType.MESSAGE_CONTEXT_MENU / CHAT_ACTION_MENU / PROFILE_ACTION_MENU / DRAWER_MENU / MAIN_MENU;",
            "    on_click receives a dict (for example account, context, message/messageObject, fragment).",
            "  get_setting(key, default); set_setting(key, value); reload_settings();",
            "  hook_method(java_member, before=callable, after=callable);",
            "  hook_all_methods(java_class, method_name, before=..., after=...); hook_all_constructors(...);",
            "  unhook_method(handle); log(text); client.send_text(peer_id, text), client.send_request(request, callback).",
            "  Java classes are available through normal Java interop, for example:",
            "    from org.telegram.messenger import UserConfig",
            "    from org.telegram.ui import LaunchActivity",
            "",
            "Requirements for generated code:",
            "1. Return the complete plugin file inside one ```python fenced block, ready to save as a .plugin file.",
            "2. Never use placeholders; every referenced name must exist in the SDK described above.",
            "3. Prefer explicit imports, guard optional features with try/except, and log errors with self.log().",
            "4. Do not include secrets, webhooks or background services that are not needed for the requested feature.",
            "5. When fixing a broken plugin, keep its __id__ and existing settings keys unless the user asks otherwise.",
            "6. If a request cannot be done with the API above, say so instead of inventing an API." });

    public static String catalog() {
        PluginsController controller = PluginsController.getInstance();
        ArrayList<String> lines = new ArrayList<>();
        List<Plugin> plugins = new ArrayList<>(controller.plugins.values());
        Collections.sort(plugins, Comparator.comparing(p -> p.getName() == null ? "" : p.getName()));
        for (Plugin plugin : plugins) {
            StringBuilder line = new StringBuilder();
            line.append("- ").append(plugin.getName())
                    .append(" (id: ").append(plugin.getId())
                    .append(", v").append(plugin.getVersion())
                    .append(", ").append(plugin.isEnabled() ? "enabled" : "disabled").append(")");
            if (plugin.hasError()) {
                String message = plugin.getError().getMessage();
                line.append(" ERROR: ").append(TextUtils.isEmpty(message)
                        ? plugin.getError().getClass().getSimpleName() : message);
            }
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            return "Installed plugins: none.";
        }
        return "Installed plugins:\n" + TextUtils.join("\n", lines);
    }

    /** Reads the saved crash report and the current source of one plugin for a bug report. */
    public static String report(Plugin plugin) {
        StringBuilder text = new StringBuilder();
        text.append("Плагин: ").append(plugin.getName())
                .append(" (id: ").append(plugin.getId()).append(")\n");
        if (plugin.hasError()) {
            text.append("Текущая ошибка: ").append(plugin.getError()).append("\n");
        }
        String crash = PluginCrashReports.reportText(plugin, plugin.getError());
        if (!TextUtils.isEmpty(crash)) {
            text.append("\nОтчёт о сбое:\n").append(crash).append("\n");
        }
        String source = source(plugin.getId());
        if (!TextUtils.isEmpty(source)) {
            text.append("\nТекущий код плагина:\n```python\n").append(source).append("\n```\n");
        }
        return text.toString();
    }

    public static String source(String pluginId) {
        try {
            PluginsController controller = PluginsController.getInstance();
            String path = controller.getPluginPath(pluginId);
            File file = path == null
                    ? new File(controller.pluginsDir, pluginId + app.nimarkogram.messenger.plugins.PluginsConstants.PLUGINS_EXT)
                    : new File(path);
            if (!file.isFile() || file.length() > 512 * 1024) {
                return null;
            }
            return readText(file);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readText(File file) throws Exception {
        try (java.io.InputStream input = new java.io.FileInputStream(file)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) > 0) {
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        }
    }

    /** Last fenced Python block of an answer, or null when there is none. */
    public static String extractCode(String answer) {
        if (TextUtils.isEmpty(answer)) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?s)```(?:python|py)?\\s*\\n(.*?)```")
                .matcher(answer);
        String last = null;
        while (matcher.find()) {
            String block = matcher.group(1);
            if (block != null && block.contains("__id__")) {
                last = block;
            }
        }
        return last;
    }

    public static String pluginIdOf(String code) {
        if (code == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?m)^\\s*__id__\\s*=\\s*['\"]([A-Za-z][A-Za-z0-9_]{1,31})['\"]")
                .matcher(code);
        return matcher.find() ? matcher.group(1) : null;
    }
}