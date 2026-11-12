package app.nimarkogram.messenger.appearance;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import app.nimarkogram.messenger.NimarkoConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * The third complete client appearance: Google's Material 3 (not the dynamic
 * Material You layer). Turning it on enables the whole Material Design feature
 * set at once — MD3 navigation bar, switches, sliders, loaders, chat header,
 * list items and player — and switches the shared role map to an M3 tonal
 * palette seeded from {@code material3Seed}.
 *
 * <p>LinkiGram You keeps the wallpaper-derived scheme; Material 3 uses its own
 * fixed seed, so the two styles stay visually distinct.
 */
public final class Material3Theme {
    private static final String PREF_ENABLED = "material3ThemeEnabled";
    private static final String PREF_SEED = "material3Seed";
    /** Canonical Material 3 baseline seed; recognisable as Google's Material palette. */
    private static final int DEFAULT_SEED = 0xFF6750A4;
    private static final String PREF_PREVIOUS_YOU = "material3PreviousYouTheme";

    private static volatile boolean loaded;
    private static volatile boolean enabled;

    private Material3Theme() { }

    public static boolean isEnabled() {
        if (!loaded) {
            synchronized (Material3Theme.class) {
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
        if (value) {
            ColorOs17Theme.setEnabled(false);
            LinkiGramYouTheme.setEnabled(false);
        } else if (restoreYou) {
            LinkiGramYouTheme.setEnabled(true);
        }
    }

    public static int getSeedColor() {
        return NimarkoConfig.getPreferences().getInt(PREF_SEED, DEFAULT_SEED);
    }

    public static void setSeedColor(int color) {
        NimarkoConfig.getPreferences().edit()
                .putInt(PREF_SEED, color == 0 ? DEFAULT_SEED : color | 0xff000000).apply();
    }

    public static int resolveColor(int key, int original) {
        return isEnabled() ? ClientTheme.resolveColor(key, original) : original;
    }

    public static int accentColor() { return ClientTheme.color(ClientTheme.PRIMARY); }
    public static int controlTrackColor() { return ClientTheme.color(ClientTheme.TRACK); }

    /**
     * The M3 navigation bar is a full-width {@code surfaceContainer} dock that
     * sits on the system navigation strip, with the standard pill indicator
     * drawn by the tab views.
     */
    public static int navigationBarColor(int role, int fallback) {
        if (!isEnabled()) return fallback;
        switch (role) {
            case LinkiGramYouTheme.NAV_COLOR_SURFACE: return ClientTheme.color(ClientTheme.SURFACE_HIGH);
            case LinkiGramYouTheme.NAV_COLOR_INDICATOR: return ClientTheme.color(ClientTheme.CONTAINER_SELECTED);
            case LinkiGramYouTheme.NAV_COLOR_SELECTED: return ClientTheme.color(ClientTheme.ON_CONTAINER);
            case LinkiGramYouTheme.NAV_COLOR_UNSELECTED: return ClientTheme.color(ClientTheme.SECONDARY);
            default: return fallback;
        }
    }

    public static Drawable navigationBarBackgroundDrawable() {
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(navigationBarColor(LinkiGramYouTheme.NAV_COLOR_SURFACE, 0xFFFFFFFF));
        // M3 tonal elevation at its lowest step carries a hairline outline only.
        GradientDrawable hairline = new GradientDrawable();
        hairline.setColor(navigationBarOutlineColor());
        LayerDrawable background = new LayerDrawable(new Drawable[]{hairline, surface});
        background.setLayerGravity(0, Gravity.TOP);
        background.setLayerHeight(0, AndroidUtilities.dp(1));
        return background;
    }

    /** Hairline separator between the M3 dock and the content above it. */
    public static int navigationBarOutlineColor() {
        return Theme.multAlpha(ClientTheme.color(ClientTheme.OUTLINE),
                Theme.isCurrentThemeDark() ? 0.24f : 0.16f);
    }
}
