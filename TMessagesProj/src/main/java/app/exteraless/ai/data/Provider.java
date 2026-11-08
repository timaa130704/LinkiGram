package app.exteraless.ai.data;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Provider {

    private static final String FAVICON = "https://www.google.com/s2/favicons?domain=%s&sz=64";

    public static final List<Provider> PRESETS = new ArrayList<>(Arrays.asList(
            // Keyless preset: the OpenCode Zen gateway answers without a login and
            // enforces per-IP free-usage limits instead.
            new Provider("opencode", "OpenCode Zen",
                    "https://opencode.ai/zen/v1",
                    "big-pickle",
                    null,
                    "opencode.ai", true),
            new Provider("gemini", "Gemini",
                    "https://generativelanguage.googleapis.com/v1beta/openai",
                    "gemini-3.5-flash",
                    "https://aistudio.google.com/apikey",
                    "gemini.google.com"),
            new Provider("openai", "OpenAI",
                    "https://api.openai.com/v1",
                    "gpt-5-mini",
                    "https://platform.openai.com/api-keys",
                    "openai.com"),
            new Provider("openrouter", "OpenRouter",
                    "https://openrouter.ai/api/v1",
                    "openai/gpt-5-mini",
                    "https://openrouter.ai/keys",
                    "openrouter.ai"),
            new Provider("deepseek", "DeepSeek",
                    "https://api.deepseek.com/v1",
                    "deepseek-chat",
                    "https://platform.deepseek.com/api_keys",
                    "deepseek.com")));

    private final String id;
    private final String title;
    private final String url;
    private final String model;
    private final String keyUrl;
    private final String iconDomain;
    private final boolean keyless;

    private Provider(String id, String title, String url, String model, String keyUrl,
                     String iconDomain) {
        this(id, title, url, model, keyUrl, iconDomain, false);
    }

    private Provider(String id, String title, String url, String model, String keyUrl,
                     String iconDomain, boolean keyless) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.model = model;
        this.keyUrl = keyUrl;
        this.iconDomain = iconDomain;
        this.keyless = keyless;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getModel() {
        return model;
    }

    public String getKeyUrl() {
        return keyUrl;
    }

    public String getIconUrl() {
        return TextUtils.isEmpty(iconDomain) ? null : String.format(FAVICON, iconDomain);
    }

    /** True when the preset works without an API key (for example per-IP free tiers). */
    public boolean isKeyless() {
        return keyless;
    }

    public static boolean isKeylessUrl(String url) {
        Provider provider = matching(url);
        return provider != null && provider.isKeyless();
    }

    public static Provider matching(String url) {
        String normalized = normalize(url);
        if (normalized == null) {
            return null;
        }
        for (Provider provider : PRESETS) {
            if (normalized.equals(normalize(provider.url))) {
                return provider;
            }
        }
        return null;
    }

    private static String normalize(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String trimmed = url.trim().toLowerCase();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
