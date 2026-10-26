package app.nimarkogram.messenger.appearance;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.SparseIntArray;
import app.nimarkogram.messenger.NimarkoConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** ColorOS-inspired palette for LinkiGram's native Android views. */
public final class ColorOs17Theme {
    private static final String PREF_ENABLED = "colorOs17ThemeEnabled";
    private static final String PREF_PREVIOUS_YOU = "colorOs17PreviousYouTheme";
    private static volatile boolean loaded;
    private static volatile boolean enabled;

    private ColorOs17Theme() {}

    public static boolean isEnabled() {
        if (!loaded) {
            synchronized (ColorOs17Theme.class) {
                if (!loaded) {
                    enabled = NimarkoConfig.getPreferences().getBoolean(PREF_ENABLED, false);
                    loaded = true;
                }
            }
        }
        return enabled;
    }

    public static void setEnabled(boolean value) {
        boolean previous = isEnabled();
        if (previous == value) return;
        boolean restoreYou = !value && NimarkoConfig.getPreferences().getBoolean(PREF_PREVIOUS_YOU, false);
        if (value) {
            NimarkoConfig.getPreferences().edit().putBoolean(PREF_PREVIOUS_YOU,
                    LinkiGramYouTheme.isEnabled()).apply();
        }
        enabled = value;
        loaded = true;
        NimarkoConfig.getPreferences().edit().putBoolean(PREF_ENABLED, value).apply();
        if (value) LinkiGramYouTheme.setEnabled(false);
        else if (restoreYou) LinkiGramYouTheme.setEnabled(true);
    }

    public static int resolveColor(int key, int original) {
        return isEnabled() ? ClientTheme.resolveColor(key, original) : original;
    }

    public static int accentColor() { return ClientTheme.color(ClientTheme.PRIMARY); }
    public static int controlTrackColor() { return ClientTheme.color(ClientTheme.TRACK); }
    public static Drawable surfaceDrawable(Drawable original, int color, boolean topOnly) {
        return ClientTheme.surfaceDrawable(original, color, topOnly);
    }

    public static int navigationBarColor(int role, int fallback) {
        final boolean dark = Theme.isCurrentThemeDark();
        switch (role) {
            case LinkiGramYouTheme.NAV_COLOR_SURFACE: return dark ? 0xFF28282A : 0xFFF0F0F0;
            case LinkiGramYouTheme.NAV_COLOR_INDICATOR: return dark ? 0xFF414144 : 0xFFFFFFFF;
            case LinkiGramYouTheme.NAV_COLOR_SELECTED: return dark ? 0xFFF2F2F2 : 0xFF171717;
            case LinkiGramYouTheme.NAV_COLOR_UNSELECTED: return dark ? 0xFFB8B8BA : 0xFF626266;
            default: return fallback;
        }
    }
}
