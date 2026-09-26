package app.nimarkogram.messenger;

import android.content.SharedPreferences;
import android.graphics.Color;

import org.telegram.messenger.ApplicationLoader;

/**
 * Palette override for the settings rows.
 *
 * Every settings entry is created through
 * {@code SettingsActivity.SettingCell.Factory.of(id, top, bottom, icon, ...)},
 * where the two gradient stops are baked in per call site from
 * {@code IconBackgroundColors}. That makes the icon colours a per-row constant,
 * so the only way to restyle them globally is to remap them where the cell binds
 * its data. The glyph itself is drawn from the drawable's own white tint, so a
 * light background also needs the glyph flipped dark to stay readable.
 *
 * Default is {@link #COLORFUL}, i.e. stock Telegram appearance and no
 * behaviour change.
 */
public final class NimarkoSettingsIconStyle {

    public static final int COLORFUL = 0;
    public static final int GRAY = 1;
    public static final int BLACK = 2;
    public static final int LIGHT = 3;

    private static final String KEY = "settingsIconStyle";

    private static volatile int cached = Integer.MIN_VALUE;

    private NimarkoSettingsIconStyle() {}

    public static int get() {
        int v = cached;
        if (v == Integer.MIN_VALUE) {
            try {
                SharedPreferences p = ApplicationLoader.applicationContext
                        .getSharedPreferences("nimarkoconfig", 0);
                v = p.getInt(KEY, COLORFUL);
            } catch (Throwable ignored) {
                v = COLORFUL;
            }
            cached = v;
        }
        return v;
    }

    public static void set(int value) {
        cached = value;
        try {
            ApplicationLoader.applicationContext
                    .getSharedPreferences("nimarkoconfig", 0)
                    .edit().putInt(KEY, value).apply();
        } catch (Throwable ignored) {}
    }

    public static boolean isCustom() {
        return get() != COLORFUL;
    }

    /**
     * Maps one gradient stop. In COLORFUL the original value is returned
     * untouched, so the stock palette is bit-for-bit preserved.
     */
    public static int stop(int color, boolean top) {
        switch (get()) {
            case GRAY: {
                // Desaturate the original, then compress the range so rows stay
                // distinguishable instead of all collapsing to one flat gray.
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                hsv[1] = 0f;
                hsv[2] = top ? 0.62f : 0.46f;
                return Color.HSVToColor(Color.alpha(color), hsv);
            }
            case BLACK: {
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                hsv[1] = 0f;
                hsv[2] = top ? 0.22f : 0.10f;
                return Color.HSVToColor(Color.alpha(color), hsv);
            }
            case LIGHT: {
                float[] hsv = new float[3];
                Color.colorToHSV(color, hsv);
                hsv[1] = 0f;
                hsv[2] = top ? 0.99f : 0.88f;
                return Color.HSVToColor(Color.alpha(color), hsv);
            }
            default:
                return color;
        }
    }

    /**
     * Tint for the glyph drawn on top of the remapped background. LIGHT needs a
     * dark glyph; the dark backgrounds keep the stock white one.
     */
    public static int glyph() {
        return get() == LIGHT ? 0xFF202020 : 0xFFFFFFFF;
    }
}
