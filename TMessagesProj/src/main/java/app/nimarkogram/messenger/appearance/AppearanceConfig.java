package app.nimarkogram.messenger.appearance;

import app.nimarkogram.messenger.NimarkoConfig;

/**
 * Совместимый слой настроек для компонентов, портированных из exteraless
 * (шторка-меню, заголовок шторки, менеджер пунктов меню). Все значения
 * хранятся в {@link NimarkoConfig}.
 *
 * exteraless хранит их в своём {@code app.exteraless.appearance.AppearanceConfig}
 * (ConfigItem-обёртки); здесь оставлен тот же небольшой API, чтобы портированный
 * код не пришлось переписывать целиком.
 */
public final class AppearanceConfig {

    private AppearanceConfig() {
    }

    public static void ensureLoaded() {
    }

    // Style switches from exteraless/OpenExteraAppearanceActivity (GPL-2.0-or-later).
    public static final String[] MATERIAL_KEYS = {"newLoadingStyle", "md3Slider", "md3Switch",
            "newChatHeaderStyle", "newNavigationBarStyle", "m3ListItems", "md3Player", "md3MiniPlayer"};
    public static final String[] IOS_KEYS = {"iosNavigationBarStyle", "iosChatHeader"};

    public static boolean enabled(String key) {
        if (ColorOs17Theme.isEnabled()) {
            if ("newNavigationBarStyle".equals(key) || "m3ListItems".equals(key)) return true;
            if ("iosNavigationBarStyle".equals(key)) return false;
        }
        if (LinkiGramYouTheme.isEnabled()) {
            for (String materialKey : MATERIAL_KEYS) if (materialKey.equals(key)) return true;
        }
        switch (key) {
            case "md3Switch": return NimarkoConfig.switchStyle == NimarkoConfig.SWITCH_STYLE_MD3;
            case "md3Player": return NimarkoConfig.md3Player();
            case "md3MiniPlayer": return NimarkoConfig.md3MiniPlayer();
            default: return NimarkoConfig.getPreferences().getBoolean(key, false);
        }
    }

    public static void setEnabled(String key, boolean value) {
        if ("md3Switch".equals(key)) {
            NimarkoConfig.setSwitchStyle(value ? NimarkoConfig.SWITCH_STYLE_MD3 : NimarkoConfig.SWITCH_STYLE_DEFAULT);
        } else if ("md3Player".equals(key)) {
            NimarkoConfig.setMd3Player(value);
        } else if ("md3MiniPlayer".equals(key)) {
            NimarkoConfig.setMd3MiniPlayer(value);
        }
        android.content.SharedPreferences.Editor editor = NimarkoConfig.getPreferences().edit().putBoolean(key, value);
        if (value && "newNavigationBarStyle".equals(key)) {
            editor.putBoolean("iosNavigationBarStyle", false);
            // MainTabsActivity docks Material tabs while this flag is on.
            // Preserve the user's navigation mode for when Material is disabled.
        } else if (value && "iosNavigationBarStyle".equals(key)) {
            editor.putBoolean("newNavigationBarStyle", false);
        }
        if (value && "newChatHeaderStyle".equals(key)) editor.putBoolean("iosChatHeader", false);
        if (value && "iosChatHeader".equals(key)) editor.putBoolean("newChatHeaderStyle", false);
        editor.apply();
    }

    public static boolean newLoadingStyle() { return enabled("newLoadingStyle"); }
    public static boolean newChatHeaderStyle() { return enabled("newChatHeaderStyle"); }
    public static boolean newNavigationBarStyle() { return enabled("newNavigationBarStyle"); }
    public static boolean iosNavigationBarStyle() { return enabled("iosNavigationBarStyle"); }
    public static boolean iosChatHeader() { return enabled("iosChatHeader"); }

    public static boolean navigationDrawer() {
        return NimarkoConfig.navigationDrawer;
    }

    public static void setNavigationDrawer(boolean value) {
        NimarkoConfig.setNavigationDrawer(value);
    }

    public static boolean immersiveDrawerAnimation() {
        return NimarkoConfig.immersiveDrawerAnimation;
    }

    public static void setImmersiveDrawerAnimation(boolean value) {
        NimarkoConfig.setImmersiveDrawerAnimation(value);
    }

    public static int getAvatarCorners(float size) {
        return com.exteragram.messenger.ExteraConfig.getAvatarCorners(size);
    }

    /** Обёртка над строковым значением раскладки главного меню. */
    public static final StringItem mainMenuLayout = new StringItem();

    public static final class StringItem {
        public String String() {
            return NimarkoConfig.mainMenuLayout;
        }

        public void setConfigString(String value) {
            NimarkoConfig.setMainMenuLayout(value);
        }
    }
}
