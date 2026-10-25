package app.exteraless.player;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.utilities.DynamicScheme;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.QuantizerCelebi;
import com.google.android.material.color.utilities.SchemeTonalSpot;
import com.google.android.material.color.utilities.Score;

import org.telegram.ui.ActionBar.Theme;

import java.util.List;
import java.util.Map;

public final class PlayerColors {

    public final boolean dark;
    public final int surface;
    public final int surfaceLow;
    public final int surfaceContainer;
    public final int surfaceHigh;
    public final int onSurface;
    public final int onSurfaceVariant;
    public final int outlineVariant;
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int secondaryContainer;
    public final int onSecondaryContainer;

    private PlayerColors(boolean dark, int[] c) {
        this.dark = dark;
        surface = c[0];
        surfaceLow = c[1];
        surfaceContainer = c[2];
        surfaceHigh = c[3];
        onSurface = c[4];
        onSurfaceVariant = c[5];
        outlineVariant = c[6];
        primary = c[7];
        onPrimary = c[8];
        primaryContainer = c[9];
        onPrimaryContainer = c[10];
        secondaryContainer = c[11];
        onSecondaryContainer = c[12];
    }

    private int[] values() {
        return new int[]{surface, surfaceLow, surfaceContainer, surfaceHigh, onSurface, onSurfaceVariant, outlineVariant,
                primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer};
    }

    public static PlayerColors fromSeed(int seed, boolean dark) {
        if (app.nimarkogram.messenger.appearance.ColorOs17Theme.isEnabled()) {
            return new PlayerColors(dark, dark
                    ? new int[]{0xFF101010, 0xFF1C1C1E, 0xFF28282A, 0xFF363639,
                        0xFFF2F2F2, 0xFFA6A6AA, 0xFF404045, 0xFF65AEFF,
                        0xFF101010, 0xFF203E60, 0xFFDCEEFF, 0xFF333336, 0xFFF2F2F2}
                    : new int[]{0xFFF5F5F5, 0xFFFFFFFF, 0xFFF0F0F0, 0xFFE8E8EB,
                        0xFF171717, 0xFF737377, 0xFFDEDEE2, 0xFF0080FF,
                        0xFFFFFFFF, 0xFFDCEEFF, 0xFF0056B3, 0xFFE9E9EC, 0xFF171717});
        }
        DynamicScheme s = new SchemeTonalSpot(Hct.fromInt(seed), dark, 0.0);
        return new PlayerColors(dark, new int[]{
                s.getSurface(), s.getSurfaceContainerLow(), s.getSurfaceContainer(), s.getSurfaceContainerHigh(),
                s.getOnSurface(), s.getOnSurfaceVariant(), s.getOutlineVariant(),
                s.getPrimary(), s.getOnPrimary(), s.getPrimaryContainer(), s.getOnPrimaryContainer(),
                s.getSecondaryContainer(), s.getOnSecondaryContainer()
        });
    }

    public static PlayerColors lerp(PlayerColors a, PlayerColors b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int[] from = a.values();
        int[] to = b.values();
        int[] out = new int[from.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = ColorUtils.blendARGB(from[i], to[i], t);
        }
        return new PlayerColors(b.dark, out);
    }

    public static Bitmap sample(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return null;
        }
        try {
            Bitmap source = bitmap;
            if (Build.VERSION.SDK_INT >= 26 && bitmap.getConfig() == Bitmap.Config.HARDWARE) {
                source = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                if (source == null) {
                    return null;
                }
            }
            int w = source.getWidth();
            int h = source.getHeight();
            float k = Math.min(1f, 112f / Math.max(w, h));
            int sw = Math.max(1, Math.round(w * k));
            int sh = Math.max(1, Math.round(h * k));
            Bitmap small = Bitmap.createScaledBitmap(source, sw, sh, true);
            if (small == source) {
                small = source.copy(Bitmap.Config.ARGB_8888, false);
            }
            return small;
        } catch (Throwable e) {
            return null;
        }
    }

    public static int seedFromSample(Bitmap small, int fallback) {
        if (small == null) {
            return fallback;
        }
        try {
            int w = small.getWidth();
            int h = small.getHeight();
            int[] pixels = new int[w * h];
            small.getPixels(pixels, 0, w, 0, 0, w, h);
            Map<Integer, Integer> quantized = QuantizerCelebi.quantize(pixels, 128);
            List<Integer> ranked = Score.score(quantized, 1, fallback, true);
            return ranked.isEmpty() ? fallback : ranked.get(0);
        } catch (Throwable e) {
            return fallback;
        }
    }

    public static boolean isDark(Theme.ResourcesProvider resourcesProvider) {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)) < 0.5;
    }

    public static int noCoverSeed(Theme.ResourcesProvider resourcesProvider) {
        // exteraless seeds this from the system Monet accent on API 31+; LinkiGram
        // has no Monet helper, so fall back to the themed accent colour.
        return fallbackSeed(resourcesProvider);
    }

    public static int fallbackSeed(Theme.ResourcesProvider resourcesProvider) {
        int color = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        return Color.alpha(color) == 0 ? 0xff4285f4 : color | 0xff000000;
    }

    public boolean lightStatusBar() {
        return ColorUtils.calculateLuminance(surface) > 0.5;
    }

    public int scrim() {
        return dark ? 0x8c000000 : 0x52000000;
    }

    public int shadow() {
        return dark ? 0xff000000 : ColorUtils.blendARGB(primary, Color.BLACK, 0.4f);
    }

    public int ripple() {
        return ColorUtils.setAlphaComponent(onSurface, 0x1f);
    }

    public Theme.ResourcesProvider provider(Theme.ResourcesProvider base) {
        return new Theme.ResourcesProvider() {
            @Override
            public int getColor(int key) {
                if (key == Theme.key_actionBarDefaultSubmenuBackground || key == Theme.key_dialogBackground) {
                    return surfaceContainer;
                } else if (key == Theme.key_actionBarDefaultSubmenuItem || key == Theme.key_dialogTextBlack
                        || key == Theme.key_windowBackgroundWhiteBlackText) {
                    return onSurface;
                } else if (key == Theme.key_actionBarDefaultSubmenuItemIcon || key == Theme.key_dialogTextGray2
                        || key == Theme.key_windowBackgroundWhiteGrayText) {
                    return onSurfaceVariant;
                } else if (key == Theme.key_actionBarDefaultSubmenuSeparator || key == Theme.key_windowBackgroundGray) {
                    return surfaceLow;
                } else if (key == Theme.key_dialogButtonSelector || key == Theme.key_listSelector) {
                    return ripple();
                } else if (key == Theme.key_windowBackgroundWhite) {
                    return surface;
                } else if (key == Theme.key_featuredStickers_addButton || key == Theme.key_windowBackgroundWhiteBlueText) {
                    return primary;
                }
                return Theme.getColor(key, base);
            }

            @Override
            public boolean isDark() {
                return dark;
            }
        };
    }
}
