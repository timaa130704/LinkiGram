package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        DEFAULT("DefaultIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconDefault),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        // LinkiGram: stock Telegram icons now carry the LinkiGram plane and are free for everyone
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox),

        LINKI_MONO("LinkiMonoIcon", R.drawable.linki_mono_bg, R.drawable.linki_mono_fg, R.string.LF_IconMono),
        LINKI_NEON("LinkiNeonIcon", R.drawable.linki_neon_bg, R.drawable.linki_neon_fg, R.string.LF_IconNeon),
        LINKI_SUNSET("LinkiSunsetIcon", R.drawable.linki_sunset_bg, R.drawable.linki_sunset_fg, R.string.LF_IconSunset),

        // --- LinkiGram: user-selectable icon colours (see res/drawable/icon_custom_bg_XX.xml)
        CUSTOM_01("CustomIcon01", R.drawable.icon_custom_bg_01, R.mipmap.icon_foreground_sa, 0, 0xFFE53935),
        CUSTOM_02("CustomIcon02", R.drawable.icon_custom_bg_02, R.mipmap.icon_foreground_sa, 0, 0xFFFB8C00),
        CUSTOM_03("CustomIcon03", R.drawable.icon_custom_bg_03, R.mipmap.icon_foreground_sa, 0, 0xFFFDD835),
        CUSTOM_04("CustomIcon04", R.drawable.icon_custom_bg_04, R.mipmap.icon_foreground_sa, 0, 0xFF43A047),
        CUSTOM_05("CustomIcon05", R.drawable.icon_custom_bg_05, R.mipmap.icon_foreground_sa, 0, 0xFF00ACC1),
        CUSTOM_06("CustomIcon06", R.drawable.icon_custom_bg_06, R.mipmap.icon_foreground_sa, 0, 0xFF1E88E5),
        CUSTOM_07("CustomIcon07", R.drawable.icon_custom_bg_07, R.mipmap.icon_foreground_sa, 0, 0xFF3949AB),
        CUSTOM_08("CustomIcon08", R.drawable.icon_custom_bg_08, R.mipmap.icon_foreground_sa, 0, 0xFF8E24AA),
        CUSTOM_09("CustomIcon09", R.drawable.icon_custom_bg_09, R.mipmap.icon_foreground_sa, 0, 0xFFD81B60),
        CUSTOM_10("CustomIcon10", R.drawable.icon_custom_bg_10, R.mipmap.icon_foreground_sa, 0, 0xFF6D4C41),
        CUSTOM_11("CustomIcon11", R.drawable.icon_custom_bg_11, R.mipmap.icon_foreground_sa, 0, 0xFF546E7A),
        CUSTOM_12("CustomIcon12", R.drawable.icon_custom_bg_12, R.mipmap.icon_foreground_sa, 0, 0xFF00897B),
        CUSTOM_13("CustomIcon13", R.drawable.icon_custom_bg_13, R.mipmap.icon_foreground_sa, 0, 0xFFF4511E),
        CUSTOM_14("CustomIcon14", R.drawable.icon_custom_bg_14, R.mipmap.icon_foreground_sa, 0, 0xFFC0CA33),
        CUSTOM_15("CustomIcon15", R.drawable.icon_custom_bg_15, R.mipmap.icon_foreground_sa, 0, 0xFF5E35B1),
        CUSTOM_16("CustomIcon16", R.drawable.icon_custom_bg_16, R.mipmap.icon_foreground_sa, 0, 0xFF455A64);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        /** ARGB value used by the custom colour picker; 0 for the built-in icons. */
        public final int colorArgb;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        public boolean isCustom() {
            return colorArgb != 0;
        }

        public String getHex() {
            return colorArgb == 0 ? null : String.format("#%06X", colorArgb & 0xFFFFFF);
        }

        LauncherIcon(String key, int background, int foreground, int title, int colorArgb) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = false;
            this.colorArgb = colorArgb;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
            this.colorArgb = 0;
        }

        /** All icons whose colour the user may pick (the 16 custom variants). */
        public static LauncherIcon[] customIcons() {
            LauncherIcon[] all = values();
            int n = 0;
            for (LauncherIcon icon : all) {
                if (icon.isCustom()) {
                    n++;
                }
            }
            LauncherIcon[] res = new LauncherIcon[n];
            int j = 0;
            for (LauncherIcon icon : all) {
                if (icon.isCustom()) {
                    res[j++] = icon;
                }
            }
            return res;
        }

        /**
         * Android cannot recolour an adaptive icon at runtime, so an arbitrary picker colour
         * is snapped to the closest colour that is actually baked into the APK.
         * Returns null when there is nothing to snap to.
         */
        public static LauncherIcon nearestCustom(int argb) {
            LauncherIcon best = null;
            long bestDist = Long.MAX_VALUE;
            for (LauncherIcon icon : customIcons()) {
                int c = icon.colorArgb;
                int dr = ((argb >> 16) & 0xFF) - ((c >> 16) & 0xFF);
                int dg = ((argb >> 8) & 0xFF) - ((c >> 8) & 0xFF);
                int db = (argb & 0xFF) - (c & 0xFF);
                long dist = (long) dr * dr * 2 + (long) dg * dg * 4 + (long) db * db;
                if (dist < bestDist) {
                    bestDist = dist;
                    best = icon;
                }
            }
            return best;
        }
    }
}
