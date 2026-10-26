package app.nimarkogram.messenger.appearance;

import android.content.Context;
import android.os.Build;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;

/** Global Material You layer for Telegram's existing native views. */
public final class LinkiGramYouTheme {
    private static final String PREF_ENABLED = "linkigramYouEnabled";
    public static final int NAV_COLOR_SURFACE = 0;
    public static final int NAV_COLOR_INDICATOR = 1;
    public static final int NAV_COLOR_SELECTED = 2;
    public static final int NAV_COLOR_UNSELECTED = 3;
    private static volatile boolean enabled;
    private static volatile boolean loaded;

    private LinkiGramYouTheme() { }

    public static boolean isEnabled() {
        if (loaded) return enabled;
        try {
            synchronized (LinkiGramYouTheme.class) {
                if (!loaded) {
                    enabled = app.nimarkogram.messenger.NimarkoConfig.getPreferences().getBoolean(PREF_ENABLED, true);
                    loaded = true;
                }
                return enabled;
            }
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setEnabled(boolean enabled) {
        if (enabled) ColorOs17Theme.setEnabled(false);
        LinkiGramYouTheme.enabled = enabled;
        loaded = true;
        app.nimarkogram.messenger.NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    /** One role map serves dialogs, chat content, profiles, media and controls. */
    public static int resolveColor(int key, int original) {
        return ClientTheme.resolveColor(key, original);
    }

    public static int navigationBarColor(Context context, int role, int fallback) {
        if (!ClientTheme.isEnabled()) return fallback;
        if (ColorOs17Theme.isEnabled()) return ColorOs17Theme.navigationBarColor(role, fallback);
        switch (role) {
            case NAV_COLOR_SURFACE: return ClientTheme.color(ClientTheme.SURFACE_HIGH);
            case NAV_COLOR_INDICATOR: return ClientTheme.color(ClientTheme.CONTAINER);
            case NAV_COLOR_SELECTED: return ClientTheme.color(ClientTheme.ON_CONTAINER);
            case NAV_COLOR_UNSELECTED: return ClientTheme.color(ClientTheme.SECONDARY);
            default: return fallback;
        }
    }
}
