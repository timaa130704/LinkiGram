package app.nimarkogram.messenger.appearance;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;

import androidx.core.graphics.ColorUtils;

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
    private static final int DEFAULT_SEED = 0xFF3F6FD8;
    private static final int LEGACY_DEFAULT_SEED = 0xFF6750A4;
    private static final String PREF_PREVIOUS_YOU = "material3PreviousYouTheme";

    public static final int NAV_HEIGHT_DP = 64;
    public static final int ACTION_BAR_HEIGHT_DP = 56;
    public static final int SMALL_SHAPE_DP = 8;
    public static final int MEDIUM_SHAPE_DP = 16;
    public static final int LARGE_SHAPE_DP = 28;
    public static final int EXTRA_LARGE_SHAPE_DP = 32;
    public static final int BUBBLE_SHAPE_DP = 20;
    private static final float SURFACE_DARKEN = 0.30f;

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
        int stored = NimarkoConfig.getPreferences().getInt(PREF_SEED, DEFAULT_SEED);
        // The first M3 preview shipped Google's purple seed. Migrate only that
        // untouched default; explicitly selected accents remain intact.
        return stored == LEGACY_DEFAULT_SEED ? DEFAULT_SEED : stored;
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

    /** Yougram expressive tonal roles, computed directly from the accent seed. */
    public static int roleColor(int seed, boolean dark, int role) {
        final int primary = primary(seed, dark);
        switch (role) {
            case ClientTheme.PRIMARY:
                return primary;
            case ClientTheme.ON_PRIMARY:
                return ensureContrast(dark ? Color.BLACK : Color.WHITE, primary, 4.5f);
            case ClientTheme.CONTAINER:
                return dark ? mix(seed, Color.BLACK, 0.35f) : mix(seed, Color.WHITE, 0.75f);
            case ClientTheme.ON_CONTAINER:
                return dark ? mix(seed, Color.WHITE, 0.90f) : mix(seed, Color.BLACK, 0.65f);
            case ClientTheme.SECONDARY:
                // onSurfaceVariant in Yougram: light grey on dark, muted grey on light.
                return dark ? 0xFFD0D0D0 : 0xFF4A4A4A;
            case ClientTheme.CONTAINER_SELECTED:
                return dark ? mix(0xFF121212, seed, 0.28f) : mix(0xFFFCFCFC, seed, 0.25f);
            case ClientTheme.ON_SURFACE:
                return dark ? 0xFFF2F2F2 : 0xFF141414;
            case ClientTheme.TRACK:
                return dark ? mix(0xFF242424, primary, 0.09f) : mix(0xFFE6E6E6, primary, 0.09f);
            case ClientTheme.OUTLINE:
                return dark ? 0xFF8A8A8A : 0xFF7A7A7A;
            case ClientTheme.BACKGROUND:
            case ClientTheme.SURFACE:
            case ClientTheme.SURFACE_HIGH:
                return surfaceRole(seed, dark, role);
            default:
                return primary;
        }
    }

    /** Dark primary is lightened from the seed; light primary is the seed itself. */
    private static int primary(int seed, boolean dark) {
        return dark ? mix(seed, Color.WHITE, 0.20f) : seed;
    }

    /** Yougram derives every background from the accent rather than using a neutral grey. */
    private static int surfaceRole(int seed, boolean dark, int role) {
        final int primary = primary(seed, dark);
        int surface = mix(dark ? 0xFF121212 : 0xFFFCFCFC, primary, dark ? 0.12f : 0.07f);
        if (dark) surface = mix(surface, Color.BLACK, SURFACE_DARKEN);
        if (role == ClientTheme.BACKGROUND || role == ClientTheme.SURFACE) return surface;
        // surfaceContainerHigh: one tonal step above the base surface.
        if (dark) {
            int lift = mix(primary, Color.WHITE, 0.20f);
            int container = mix(mix(0xFF121212, primary, 0.12f), lift, 0.13f);
            return mix(container, Color.BLACK, SURFACE_DARKEN);
        }
        return mix(surface, primary, 0.10f);
    }

    public static int mix(int from, int to, float fraction) {
        return ColorUtils.blendARGB(from, to, fraction);
    }

    /** Compose-style hue/saturation/value shift, used for the secondary accent. */
    public static int hsv(int color, float hueShift, float saturation, float value) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + hueShift + 360f) % 360f;
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * saturation));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * value));
        return Color.HSVToColor(hsv);
    }

    /** Contrast correction ported from Yougram's {@code ensureContrast}. */
    public static int ensureContrast(int foreground, int background, float minimum) {
        if (contrastRatio(foreground, background) >= minimum) return foreground;
        final int target = ColorUtils.calculateLuminance(background) < 0.4f ? Color.WHITE : Color.BLACK;
        int result = foreground;
        for (int i = 0; i < 10; i++) {
            result = mix(result, target, 0.2f);
            if (contrastRatio(result, background) >= minimum) return result;
        }
        return target;
    }

    public static float contrastRatio(int first, int second) {
        double a = ColorUtils.calculateLuminance(first) + 0.05;
        double b = ColorUtils.calculateLuminance(second) + 0.05;
        return (float) (Math.max(a, b) / Math.min(a, b));
    }

    /**
     * Yougram bubble palette. The argument is the Telegram theme key (not a
     * resolved colour), so incoming and outgoing bubbles, their text, links and
     * metadata each get their own contrast-checked colour.
     *
     * @return the colour to use, or {@code 0} when the key is not a bubble colour.
     */
    public static int resolveMessageColor(int key, boolean dark, int seed) {
        if (!isEnabled()) return 0;
        final boolean outgoing = key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected
                || key == Theme.key_chat_messageTextOut || key == Theme.key_chat_messageLinkOut
                || key == Theme.key_chat_outTimeText || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_outViews || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_outReplyLine || key == Theme.key_chat_outReplyLine2
                || key == Theme.key_chat_outReplyNameText || key == Theme.key_chat_outReplyMessageText
                || key == Theme.key_chat_outForwardedNameText || key == Theme.key_chat_outAdminText
                || key == Theme.key_chat_outAdminSelectedText;
        final boolean incoming = key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected
                || key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageLinkIn
                || key == Theme.key_chat_inTimeText || key == Theme.key_chat_inTimeSelectedText
                || key == Theme.key_chat_inViews || key == Theme.key_chat_inViewsSelected
                || key == Theme.key_chat_inReplyLine || key == Theme.key_chat_inReplyNameText
                || key == Theme.key_chat_inReplyMessageText || key == Theme.key_chat_inForwardedNameText
                || key == Theme.key_chat_inAdminText || key == Theme.key_chat_inAdminSelectedText;
        if (!outgoing && !incoming) return 0;
        final int accent = primary(seed, dark);
        int bubble = outgoing
                ? (dark ? mix(0xFF26282E, accent, 0.38f) : mix(0xFFE2ECF8, accent, 0.28f))
                : (dark ? 0xFF2A2C31 : 0xFFE9EAEE);
        if (dark) bubble = ensureContrast(bubble, Color.WHITE, 4.5f);
        final int text = dark ? Color.WHITE
                : (contrastRatio(bubble, Color.WHITE) >= contrastRatio(bubble, Color.BLACK) ? Color.WHITE : Color.BLACK);
        final int secondary = ensureContrast(mix(text, bubble, 0.35f), bubble, 3.0f);
        final int link = ensureContrast(accent, bubble, 4.5f);
        if (key == Theme.key_chat_inBubble || key == Theme.key_chat_inBubbleSelected
                || key == Theme.key_chat_outBubble || key == Theme.key_chat_outBubbleSelected) return bubble;
        if (key == Theme.key_chat_messageTextIn || key == Theme.key_chat_messageTextOut
                || key == Theme.key_chat_inReplyMessageText || key == Theme.key_chat_outReplyMessageText) return text;
        if (key == Theme.key_chat_inTimeText || key == Theme.key_chat_outTimeText
                || key == Theme.key_chat_inTimeSelectedText || key == Theme.key_chat_outTimeSelectedText
                || key == Theme.key_chat_inViews || key == Theme.key_chat_outViews
                || key == Theme.key_chat_inViewsSelected || key == Theme.key_chat_outViewsSelected
                || key == Theme.key_chat_inAdminText || key == Theme.key_chat_outAdminText
                || key == Theme.key_chat_inAdminSelectedText || key == Theme.key_chat_outAdminSelectedText) return secondary;
        return link;
    }

    /**
     * The M3 navigation bar is a floating {@code surfaceContainerHigh} pill with
     * an accent-tinted pill indicator on the selected tab.
     */
    public static int navigationBarColor(int role, int fallback) {
        if (!isEnabled()) return fallback;
        switch (role) {
            case LinkiGramYouTheme.NAV_COLOR_SURFACE: return ClientTheme.color(ClientTheme.SURFACE_HIGH);
            case LinkiGramYouTheme.NAV_COLOR_INDICATOR: return ClientTheme.color(ClientTheme.CONTAINER);
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
